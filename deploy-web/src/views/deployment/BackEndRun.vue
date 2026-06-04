<script setup lang="ts">
import { deploymentRecordAdd, deploymentRecordStart, fileQueryList, fileQueryPathById, sshExecCommand } from '@/api/api'
import { useWebSocketStore } from '@/stores/websocket'
import type { FileRecord } from '@/types/file'
import type { ServerRecord } from '@/types/server'
import { generateRandomNumber } from '@/utils/common'
import type { FormInstance, FormRules, UploadInstance, UploadUserFile } from 'element-plus'
import { Delete, Edit, EditPen, View } from '@element-plus/icons-vue'
import CodeEditor from '@/components/code-editor/index.vue'
import LogView from '@/views/log/components/LogView.vue'
import { ApplicationTypeEnum, DeploymentStatusEnum, FileScopeEnum } from '@/enums/platform'

const props = defineProps<{
  fileRecord: FileRecord
}>()

// 可见性由父级 v-model 显式接管：AppDrawer 底层非单根，必须 defineModel 才能开合
const visible = defineModel<boolean>()

const sessionId = inject('sessionId') as Ref<string>
const currentServer = inject('currentServer') as Ref<ServerRecord>
const websocketStore = useWebSocketStore()

// 构建默认的上传目录
const bulidUploadDir = () => {
  const { groupId, artifactId } = props.fileRecord
  const { homeDir } = currentServer.value
  return `${homeDir}/resource/${groupId}/${artifactId}`
}

// 上传表单
const uploadFormRef = ref<FormInstance>()
const uploadForm = reactive({
  dir: bulidUploadDir(),
  percentage: 0,
})
const uploadFormRules = reactive<FormRules>({
  dir: [
    { required: true, message: '部署上传目录不能为空', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9-_.\/]+$/,
      message: '部署上传目录只能包含字母、数字、下划线、短横线、点和斜杠',
      trigger: 'blur',
    },
  ],
})

// 运行表单
const runFormRef = ref<FormInstance>()
const runForm = reactive({
  port: Number(generateRandomNumber(4)),
  programArgs: '-Xmx256m',
  activeProfiles: 'pro',
})
const runFormRules = reactive<FormRules>({
  port: [
    { required: true, message: '部署端口不能为空', trigger: 'blur' },
    { type: 'number', min: 0, max: 65535, message: '端口范围在 0 ~ 65535 之间', trigger: 'blur' },
  ],
  programArgs: [
    {
      pattern: /^[a-zA-Z0-9-_.\s]*$/,
      message: '程序参数只能包含字母、数字、下划线、短横线、点和空格',
      trigger: 'blur',
    },
  ],
  activeProfiles: [
    { pattern: /^[a-zA-Z0-9-_,]*$/, message: '激活配置文件只能包含字母、数字、下划线、短横线和逗号', trigger: 'blur' },
  ],
})

// 执行上传 jar 包步骤
const handleUploadJarStep = async () => {
  if (!uploadFormRef.value) return

  await uploadFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const data = await fileQueryPathById(props.fileRecord.id)
        await handleUploadJar(data, uploadForm.dir)
        steps.value[0].status = 'success'
        ElMessage.success('应用部署上传成功')
      } catch (error) {
        steps.value[0].status = 'error'
        ElMessage.error('应用部署上传失败: ' + extractErrorMessage(error))
      }
    }
  })
}

// 上传 jar 包到服务器
const handleUploadJar = (localPath: string, remoteDir: string) =>
  websocketStore.sendAndAwait(
    `/topic/ssh/sessions/${sessionId.value}/sftp/upload`,
    `/app/ssh/sessions/${sessionId.value}/sftp/upload`,
    { localPath, remoteDir },
    (message, done) => {
      const percentage = Number(message)
      uploadForm.percentage = percentage
      if (percentage === 100) done()
    },
  )

// 配置文件夹路径
const configDir = ref(`${uploadForm.dir}/config`)
watch(
  () => uploadForm.dir,
  (newDir) => {
    configDir.value = `${newDir}/config`
  },
)

const configFileList = ref<UploadUserFile[]>([])
const configUploadRef = ref<UploadInstance>()

// 获取配置文件列表
const fetchConfigFileList = async () => {
  const { groupId, artifactId, version } = props.fileRecord
  const data = await fileQueryList(
    { groupId, artifactId, version, scope: FileScopeEnum.CONFIGURATION.value },
    'fileName,asc',
  )
  configFileList.value = data.map((file: FileRecord) => ({
    name: file.fileName,
    url: file.id,
    status: 'ready',
    percentage: 0,
  }))
}

// 上传配置文件到服务器
const handleUploadConfig = (localPath: string, remoteDir: string, file: UploadUserFile) => {
  // 立即标记上传中（后续进度帧再细化）；jar 与各配置文件复用同一频道，靠 sendAndAwait 完成即退订隔离
  file.status = 'uploading'
  return websocketStore.sendAndAwait(
    `/topic/ssh/sessions/${sessionId.value}/sftp/upload`,
    `/app/ssh/sessions/${sessionId.value}/sftp/upload`,
    { localPath, remoteDir },
    (message, done) => {
      const percentage = Number(message)
      file.percentage = percentage
      file.status = percentage === 100 ? 'success' : 'uploading'
      if (percentage === 100) done()
    },
  )
}

// 执行上传配置文件步骤
const handleUploadConfigStep = async () => {
  if (!configFileList.value.length) {
    ElMessage.warning('没有待上传的配置文件')
    return
  }
  try {
    for (const file of configFileList.value) {
      if (!file.url) {
        continue
      }
      const data = await fileQueryPathById(file.url)
      await handleUploadConfig(data, configDir.value, file)
    }
    ElMessage.success('所有配置文件上传成功')
    steps.value[1].status = 'success'
    await fetchFileList()
  } catch (error) {
    steps.value[1].status = 'error'
    ElMessage.error('配置文件上传失败: ' + extractErrorMessage(error))
  }
}

// 文件列表
const fileList = ref<File[]>([])
// 编辑器引用
const codeEditorRef = ref<InstanceType<typeof CodeEditor>>()
const fileLoading = ref<boolean>(false)

// 获取文件列表
const fetchFileList = async () => {
  try {
    fileLoading.value = true
    const data = await sshExecCommand(
      sessionId.value,
      `stat --printf='{"path":"%n","size":%s,"updateTime":"%y"}\n' ${configDir.value}/*`,
    )
    const { exitCode, result } = data
    if (exitCode !== 0) {
      fileList.value = []
      return ElNotification.error('获取文件列表失败:' + result)
    }
    // 将 JSON 字符串解析为对象列表
    fileList.value = result
      .split('\n')
      // 过滤空行
      .filter((line) => line.trim() !== '')
      // 解析 JSON
      .map((line) => {
        const file = JSON.parse(line)
        // 提取文件名
        const name = file.path.split('/').pop() || ''
        return { ...file, name }
      })
      // 过滤掉 .default 结尾的文件
      .filter((file) => !file.name.endsWith('.default'))
  } catch (error) {
    ElNotification.error('获取文件列表失败:' + extractErrorMessage(error))
  } finally {
    fileLoading.value = false
  }
}

// 查看文件
const handleFileView = (filePath: string) => {
  codeEditorRef.value?.viewFile(`${filePath}`)
}

// 手动编辑文件
const handleFileEditManual = (filePath: string) => {
  codeEditorRef.value?.editFile(`${filePath}`)
}

// 删除文件
const handleFileDelete = (filePath: string) => {
  ElMessageBox.confirm('是否删除文件？', '提示', {
    type: 'warning',
    showCancelButton: true,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
    .then(async () => {
      try {
        await sshExecCommand(sessionId.value, `rm -f ${filePath}`)
        ElNotification.success('文件删除成功')
        await fetchFileList()
      } catch (error) {
        ElNotification.error('文件删除失败:' + extractErrorMessage(error))
      }
    })
    .catch(() => {
      ElMessage.info('已取消删除')
    })
}

// 重命名文件
const handleFileRename = (fileName: string) => {
  ElMessageBox.prompt('请输入新文件名', '重命名文件', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^[a-zA-Z0-9_.-]+$/,
    inputErrorMessage: '文件名只能包含字母、数字、下划线和点',
  })
    .then(async ({ value }) => {
      try {
        await sshExecCommand(sessionId.value, `mv ${configDir.value}/${fileName} ${configDir.value}/${value}`)
        ElNotification.success('文件重命名成功')
        await fetchFileList()
      } catch (error) {
        ElNotification.error('文件重命名失败:' + extractErrorMessage(error))
      }
    })
    .catch(() => {
      ElMessage.info('已取消重命名')
    })
}

// 执行运行步骤
const handleRunStep = async () => {
  if (!runFormRef.value) return

  await runFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        // 保存部署记录
        const savedResponse = await deploymentRecordAdd({
          serverRecordId: currentServer.value.id,
          fileRecordId: props.fileRecord.id,
          applicationType: ApplicationTypeEnum.BACKEND.value,
          deploymentPath: uploadForm.dir,
          deploymentConfigPath: configDir.value,
          port: runForm.port,
          programArgs: runForm.programArgs,
          activeProfiles: runForm.activeProfiles,
          status: DeploymentStatusEnum.DEPLOYING.value,
          running: false,
        })
        const { id } = savedResponse
        // 启动后端应用
        await deploymentRecordStart(id)
        steps.value[2].status = 'success'
        ElMessage.success('应用启动成功')
      } catch (error) {
        ElMessage.error('应用启动失败: ' + extractErrorMessage(error))
      }
    }
  })
}

const logVisible = ref<boolean>(false)
const logPath = ref<string>('')
// 查看日志
const handleViewLog = () => {
  logPath.value = `${uploadForm.dir}/nohup.out`
  logVisible.value = true
}

// 步骤
const activeStep = ref<number>(0)
const stepLoading = ref<boolean>(false)
const steps = ref<
  { title: string; status: 'process' | 'wait' | 'error' | 'finish' | 'success'; method?: () => Promise<void> }[]
>([
  { title: '上传应用包', status: 'process', method: handleUploadJarStep },
  { title: '上传配置文件', status: 'wait', method: handleUploadConfigStep },
  { title: '编辑配置文件', status: 'wait' },
  { title: '启动应用程序', status: 'wait', method: handleRunStep },
])
// 上一步
const handlePrevStep = () => {
  if (activeStep.value > 0) {
    activeStep.value--
  }
}
// 下一步
const handleNextStep = async () => {
  const currentStep = steps.value[activeStep.value]
  if (currentStep.status === 'success' || currentStep.status === 'finish') {
    if (activeStep.value < steps.value.length) {
      activeStep.value++
    }
    return
  }
  if (currentStep.method) {
    stepLoading.value = true
    try {
      await currentStep.method()
      currentStep.status = 'success'
      if (activeStep.value < steps.value.length - 1) {
        steps.value[activeStep.value + 1].status = 'process'
      }
      activeStep.value++
    } catch (error) {
      currentStep.status = 'error'
      ElMessage.error('步骤执行失败: ' + extractErrorMessage(error))
    } finally {
      stepLoading.value = false
    }
  } else {
    currentStep.status = 'success'
    if (activeStep.value < steps.value.length - 1) {
      steps.value[activeStep.value + 1].status = 'process'
    }
    activeStep.value++
  }
}

const handleClose = () => {
  uploadFormRef.value?.resetFields()
  visible.value = false
}

onMounted(async () => {
  await fetchConfigFileList()
})
</script>

<template>
  <app-drawer v-model="visible" title="部署后端应用" width="lg">
    <el-steps :active="activeStep" align-center>
      <el-step v-for="(step, index) in steps" :key="index" :title="step.title" :status="step.status" />
    </el-steps>
    <section v-if="activeStep === 0" class="step-section upload-jar-step">
      <el-form
        ref="uploadFormRef"
        :model="uploadForm"
        :rules="uploadFormRules"
        :disabled="stepLoading || steps[0].status === 'success'"
        label-width="110px"
      >
        <el-form-item label="部署上传目录" prop="dir">
          <el-input v-model="uploadForm.dir" placeholder="部署上传目录" clearable />
        </el-form-item>
        <el-form-item label="上传进度">
          <el-progress
            class="progress-bar"
            :percentage="uploadForm.percentage"
            :stroke-width="12"
            striped
            :striped-flow="uploadForm.percentage > 0 && uploadForm.percentage < 100"
            :duration="10"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
    </section>
    <section v-if="activeStep === 1" class="step-section upload-config-step">
      <div class="config-path">
        <span class="config-path-label">配置文件夹路径:&nbsp;</span>
        <el-input v-model="configDir" placeholder="配置文件夹路径" />
      </div>
      <el-upload ref="configUploadRef" :file-list="configFileList" :auto-upload="false">待上传配置文件:</el-upload>
    </section>
    <section v-if="activeStep === 2" class="step-section config-step">
      <div class="config-path">
        <span>
          <span class="config-path-label">文件夹路径:&nbsp;</span>
          <code>{{ configDir }}</code>
        </span>
        <el-icon :class="['refresh-button', { 'is-loading': fileLoading }]" size="26" @click="fetchFileList">
          <RefreshRight />
        </el-icon>
      </div>

      <div class="file-list-container">
        <el-empty v-if="fileList.length === 0" :description="sessionId ? '当前目录为空' : '未选择服务器'" />
        <el-table v-else :data="fileList" highlight-current-row show-overflow-tooltip>
          <el-table-column prop="name" label="文件名" min-width="130px" />
          <el-table-column prop="size" label="文件大小" width="104px">
            <template #default="{ row }">
              <span>{{ $formatFileSize(row.size) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="172px">
            <template #default="scope">
              {{ $formatDateTime(scope.row.updateTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="256px" fixed="right" header-align="center" class-name="file-actions">
            <template #default="scope">
              <el-button type="primary" link :icon="View" @click="handleFileView(scope.row.path)">查看</el-button>
              <el-button type="primary" link :icon="Edit" @click="handleFileEditManual(scope.row.path)">编辑</el-button>
              <el-button type="primary" link :icon="EditPen" @click="handleFileRename(scope.row.name)"
                >重命名</el-button
              >
              <el-button type="primary" link :icon="Delete" @click="handleFileDelete(scope.row.path)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
    <section v-if="activeStep === 3" class="step-section run-step">
      <el-form ref="runFormRef" :model="runForm" :rules="runFormRules" label-width="140px">
        <el-form-item label="部署端口" prop="port">
          <el-input-number v-model="runForm.port" :min="0" :max="65535" />
        </el-form-item>
        <el-form-item label="激活配置文件" prop="activeProfiles">
          <el-input v-model="runForm.activeProfiles" placeholder="激活配置文件" clearable />
        </el-form-item>
        <el-form-item label="程序参数" prop="programArgs">
          <el-input v-model="runForm.programArgs" placeholder="程序参数" clearable />
        </el-form-item>
      </el-form>
    </section>
    <section v-if="activeStep === 4" class="step-section run-result">
      <el-result icon="success" title="部署成功" sub-title="应用启动详情请查看日志">
        <template #extra>
          <el-button type="primary" @click="handleViewLog">查看日志</el-button>
          <el-button @click="handleClose">关闭窗口</el-button>
        </template>
      </el-result>
    </section>
    <!-- 命令式查看/编辑器与日志浮层随抽屉迁入主体末尾，行为不变 -->
    <code-editor ref="codeEditorRef" @close="fetchFileList" />
    <log-view v-if="logVisible" v-model="logVisible" :server-id="currentServer.id" :log-path="logPath" />
    <template #footer>
      <div class="actions-container">
        <el-button :disabled="activeStep === 0 || stepLoading" @click="handlePrevStep">上一步</el-button>
        <el-button type="primary" :disabled="stepLoading || activeStep === steps.length" @click="handleNextStep"
          >下一步</el-button
        >
      </div>
    </template>
  </app-drawer>
</template>

<style lang="scss" scoped>
.step-section {
  margin-top: 20px;
}
.actions-container {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
.upload-config-step {
  .config-path {
    font-size: 14px;
    display: flex;
    align-items: center;
    .config-path-label {
      word-break: keep-all;
    }
  }
}
.config-step {
  display: flex;
  flex-direction: column;
  gap: var(--layout-common-gap);
  padding: var(--layout-common-padding);
  background-color: var(--el-fill-color);
  border-radius: var(--layout-common-border-radius);
  .config-path {
    font-size: 14px;
    background-color: var(--el-bg-color);
    padding: 4px 8px;
    border-radius: var(--el-border-radius-base);
    border: var(--el-border);
    display: flex;
    justify-content: space-between;
    align-items: center;
    .config-path-label {
      user-select: none;
    }
    .refresh-button {
      color: var(--el-color-primary);
      cursor: pointer;
      width: 32px;
      height: 32px;
      &:hover {
        border-radius: 50%;
        background-color: var(--el-color-primary-light-8);
      }
    }
  }

  .file-list-container {
    background-color: var(--el-bg-color);
    padding: var(--layout-common-padding);
    border-radius: var(--el-border-radius-base);
    border: var(--el-border);
    .file-actions {
      .el-button + .el-button {
        margin-left: 0;
      }
    }
  }
}
</style>
