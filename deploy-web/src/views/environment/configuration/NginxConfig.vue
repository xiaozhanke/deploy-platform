<script setup lang="ts">
import { View, Plus, Edit, EditPen, Delete, Refresh, Odometer, Loading, SwitchButton } from '@element-plus/icons-vue'
import { sshExecCommand } from '@/api/api'
import CodeEditor from '@/components/code-editor/index.vue'
import type { ExecResult, File, NginxConfigParams } from '@/types/environment'
import NginxConfigAdd from './NginxConfigAdd.vue'
import NginxConfigEdit from './NginxConfigEdit.vue'

const props = defineProps<{
  homeDir: string
}>()

const sessionId = inject('sessionId') as Ref<string>
// 配置文件夹路径
const configDir = computed(() => {
  return `${props.homeDir}/environment/nginx/conf/conf.d`
})
// 文件列表
const fileList = ref<File[]>([])
// 编辑器引用
const codeEditorRef = ref<InstanceType<typeof CodeEditor>>()
const currentNginxConfig = ref<NginxConfigParams>({
  configName: '',
  frontEndHost: 'localhost',
  frontEndPort: 0,
  frontEndStaticDir: '',
  backEndHost: 'localhost',
  backEndPort: 0,
})
const addVisible = ref<boolean>(false)
const editVisible = ref<boolean>(false)

const environmentStatus = inject('environmentStatus') as Ref<Record<string, ExecResult>>

// 检查 Nginx 是否安装
const checkNginxInstalled = () => {
  const nginxStatus = environmentStatus.value['Nginx']
  if (!nginxStatus || nginxStatus.exitCode !== 0) {
    ElMessage.error('未检测到 Nginx 环境，请先安装 Nginx')
    return false
  }
  return true
}

// 获取文件列表
const fetchFileList = async () => {
  if (!sessionId.value) {
    return ElMessage.warning('请先连接服务器')
  }
  try {
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
  }
}

// 新建 Nginx 配置文件
const handleNginxConfigAdd = () => {
  addVisible.value = true
}

// Nginx 配置文件提交
const handleNginxConfigSubmit = async (fileName: string, fileContent: string, edit: boolean) => {
  const command = `cat <<EOF > ${configDir.value}/${fileName}\n${fileContent}\nEOF`
  try {
    await sshExecCommand(sessionId.value, command)
    ElNotification.success(`${edit ? '编辑' : '新建'} Nginx 配置文件成功`)
    await handleRefresh()
  } catch (error) {
    ElNotification.error(`${edit ? '编辑' : '新建'} Nginx 配置文件失败: ${extractErrorMessage(error)}`)
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

// 简化编辑文件
const handleFileEditSimple = async (filePath: string) => {
  currentNginxConfig.value = await parseNginxConfig(filePath)
  editVisible.value = true
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
        await handleRefresh()
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
        await handleRefresh()
      } catch (error) {
        ElNotification.error('文件重命名失败:' + extractErrorMessage(error))
      }
    })
    .catch(() => {
      ElMessage.info('已取消重命名')
    })
}

// 解析 Nginx 配置文件
const parseNginxConfig = async (filePath: string): Promise<NginxConfigParams> => {
  if (!sessionId.value) {
    ElMessage.warning('请先连接服务器')
    throw new Error('请先连接服务器')
  }

  try {
    const data = await sshExecCommand(sessionId.value, `cat ${filePath}`)
    const { exitCode, result } = data
    if (exitCode !== 0) {
      ElNotification.error('获取文件内容失败:' + result)
      throw new Error('获取文件内容失败:' + result)
    }
    const fileName = filePath.split('/').pop() || ''
    const configName = fileName.replace(/\.conf$/, '')
    const params: NginxConfigParams = {
      configName,
      frontEndHost: 'localhost',
      frontEndPort: 0,
      frontEndStaticDir: '',
      backEndHost: 'localhost',
      backEndPort: 0,
    }

    const lines = result.split('\n')
    for (const line of lines) {
      const trimmedLine = line.trim()

      // 匹配后端地址和端口
      if (trimmedLine.startsWith('server')) {
        const match = trimmedLine.match(/server\s+([\w.-]+):(\d+);/)
        if (match) {
          params.backEndHost = match[1]
          params.backEndPort = Number(match[2])
        }
      }

      // 匹配前端端口
      if (trimmedLine.startsWith('listen')) {
        const match = trimmedLine.match(/listen\s+(\d+);/)
        if (match) {
          params.frontEndPort = Number(match[1])
        }
      }

      // 匹配前端地址
      if (trimmedLine.startsWith('server_name')) {
        const match = trimmedLine.match(/server_name\s+([\w.-]+);/)
        if (match) {
          params.frontEndHost = match[1]
        }
      }

      // 匹配前端静态资源路径
      if (trimmedLine.startsWith('root')) {
        const match = trimmedLine.match(/root\s+(.+);/)
        if (match) {
          params.frontEndStaticDir = match[1]
        }
      }
    }
    return params
  } catch (error) {
    ElNotification.error('解析 Nginx 配置文件失败:' + extractErrorMessage(error))
    throw error
  }
}
// Nginx 测试配置
const handleNginxTest = async () => {
  if (!sessionId.value) {
    return ElMessage.warning('请先连接服务器')
  }
  if (!checkNginxInstalled()) return
  try {
    const data = await sshExecCommand(sessionId.value, 'nginx -t')
    const { exitCode, result } = data
    if (exitCode !== 0) {
      return ElNotification.error('测试配置失败:' + result)
    }
    ElNotification.success('测试配置成功')
  } catch (error) {
    ElNotification.error('测试配置失败:' + extractErrorMessage(error))
  }
}

// Nginx 重载配置
const handleNginxReload = async () => {
  if (!sessionId.value) {
    return ElMessage.warning('请先连接服务器')
  }
  if (!checkNginxInstalled()) return
  try {
    const data = await sshExecCommand(sessionId.value, 'nginx -s reload')
    const { exitCode, result } = data
    if (exitCode !== 0) {
      return ElNotification.error('重载配置失败:' + result)
    }
    ElNotification.success('重载配置成功')
  } catch (error) {
    ElNotification.error('重载配置失败:' + extractErrorMessage(error))
  }
}

// Nginx 启动服务
const handleNginxStart = async () => {
  if (!sessionId.value) {
    return ElMessage.warning('请先连接服务器')
  }
  if (!checkNginxInstalled()) return
  try {
    const data = await sshExecCommand(sessionId.value, 'nginx')
    const { exitCode, result } = data
    if (exitCode !== 0) {
      return ElNotification.error('启动服务失败:' + result)
    }
    ElNotification.success('启动服务成功')
  } catch (error) {
    ElNotification.error('启动服务失败:' + extractErrorMessage(error))
  }
}

// Nginx 停止服务
const handleNginxStop = async () => {
  if (!sessionId.value) {
    return ElMessage.warning('请先连接服务器')
  }
  if (!checkNginxInstalled()) return
  try {
    const data = await sshExecCommand(sessionId.value, 'nginx -s stop')
    const { exitCode, result } = data
    if (exitCode !== 0) {
      return ElNotification.error('停止服务失败:' + result)
    }
    ElNotification.success('停止服务成功')
  } catch (error) {
    ElNotification.error('停止服务失败:' + extractErrorMessage(error))
  }
}

const handleRefresh = async () => {
  if (sessionId.value) {
    await fetchFileList()
  }
}

defineExpose({ handleRefresh })

onActivated(async () => {
  await handleRefresh()
})
</script>

<template>
  <section class="config-section">
    <div class="config-header">
      <span class="config-header-title">配置文件管理</span>

      <div class="action-wrapper">
        <el-tooltip content="新建配置文件">
          <el-button class="action-button" :icon="Plus" :disabled="!sessionId" @click="handleNginxConfigAdd" />
        </el-tooltip>
        <el-tooltip content="测试配置">
          <el-button
            class="action-button"
            type="warning"
            :icon="Odometer"
            :disabled="!sessionId"
            @click="handleNginxTest"
          />
        </el-tooltip>
        <el-tooltip content="重载配置">
          <el-button
            class="action-button"
            type="primary"
            :icon="Loading"
            :disabled="!sessionId"
            @click="handleNginxReload"
          />
        </el-tooltip>
        <el-tooltip content="启动服务">
          <el-button
            class="action-button"
            type="success"
            :icon="SwitchButton"
            :disabled="!sessionId"
            @click="handleNginxStart"
          />
        </el-tooltip>
        <el-tooltip content="停止服务">
          <el-button
            class="action-button"
            type="danger"
            :icon="SwitchButton"
            :disabled="!sessionId"
            @click="handleNginxStop"
          />
        </el-tooltip>
        <el-tooltip content="刷新文件列表">
          <el-button
            class="action-button"
            type="primary"
            :icon="Refresh"
            :disabled="!sessionId"
            @click="fetchFileList"
          />
        </el-tooltip>
      </div>
    </div>

    <div class="config-path">
      <span class="config-path-label">文件夹路径:&nbsp;</span>
      <code>{{ configDir }}</code>
    </div>

    <div class="file-list-container">
      <el-empty v-if="fileList.length === 0" :description="sessionId ? '当前目录为空' : '未选择服务器'" />
      <el-table v-else :data="fileList" stripe highlight-current-row show-overflow-tooltip>
        <el-table-column prop="name" label="文件名" min-width="130px" />
        <el-table-column prop="size" label="文件大小" min-width="104px">
          <template #default="{ row }">
            <span>{{ $formatFileSize(row.size) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="172px">
          <template #default="scope">
            {{ $formatDateTime(scope.row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="366px" fixed="right" header-align="center" class-name="file-actions">
          <template #default="scope">
            <el-button type="primary" link :icon="View" @click="handleFileView(scope.row.path)">查看</el-button>
            <el-button type="primary" link :icon="Edit" @click="handleFileEditSimple(scope.row.path)"
              >简化编辑</el-button
            >
            <el-button type="primary" link :icon="Edit" @click="handleFileEditManual(scope.row.path)"
              >手动编辑</el-button
            >
            <el-button type="primary" link :icon="EditPen" @click="handleFileRename(scope.row.name)">重命名</el-button>
            <el-button type="primary" link :icon="Delete" @click="handleFileDelete(scope.row.path)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <code-editor ref="codeEditorRef" @close="handleRefresh" />

    <nginx-config-add v-if="addVisible" v-model="addVisible" @submit="handleNginxConfigSubmit" />

    <nginx-config-edit
      v-if="editVisible"
      v-model="editVisible"
      :params="currentNginxConfig"
      @submit="handleNginxConfigSubmit"
    />
  </section>
</template>

<style lang="scss" scoped>
.config-section {
  display: flex;
  flex-direction: column;
  gap: var(--layout-common-gap);
  padding: var(--layout-common-padding);
  background-color: var(--el-fill-color);
  border-radius: var(--layout-common-border-radius);

  .config-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--layout-common-gap);

    .config-header-title {
      font-size: var(--el-font-size-large);
      font-weight: bold;
    }
    .action-wrapper {
      .action-button {
        font-size: 18px;
        padding: 8px;
      }
    }
  }

  .config-path {
    font-size: 14px;
    background-color: white;
    padding: 4px 8px;
    border-radius: var(--el-border-radius-base);
    border: var(--el-border);
    .config-path-label {
      user-select: none;
    }
  }

  .file-list-container {
    background-color: white;
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
