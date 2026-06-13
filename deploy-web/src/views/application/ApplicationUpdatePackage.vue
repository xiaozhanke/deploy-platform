<script setup lang="ts">
import { Document, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

import { deploymentJobCreate, fileQueryPathById, sshConnect } from '@/api/api'
import { ApplicationTypeEnum, JobTypeEnum } from '@/enums/platform'
import { useWebSocketStore } from '@/stores/websocket'
import type { DeploymentRecord } from '@/types/deployment'
import type { FileRecord } from '@/types/file'
import FileSelect from '@/views/file/FileSelect.vue'
import LogView from '@/views/log/components/LogView.vue'

const props = defineProps<{
  recordSelection: Array<DeploymentRecord>
}>()

const emit = defineEmits<{
  (e: 'complete'): void
}>()

// 可见性由父级 v-model 显式接管（迁到 AppDrawer 后底层非单根，靠属性透传不再生效）
const visible = defineModel<boolean>()

const websocketStore = useWebSocketStore()

// 上传表单
const formRef = ref<FormInstance>()
const form = reactive({
  fileRecordId: '',
  fileName: '',
})
const formRules = reactive<FormRules>({
  fileName: [{ required: true, message: '请选择新的应用包', trigger: ['blur', 'change'] }],
})

// 上传进度映射
const uploadProgressMap = ref<Record<string, number>>({})

// 更新状态映射
const updateStatusMap = ref<Record<string, 'success' | 'error' | 'pending'>>({})

// 上传文件到主机
const handleUploadToHost = (deploymentRecordId: string, hostId: string, localPath: string, deploymentPath: string) => {
  return sshConnect(hostId).then((sessionId) =>
    websocketStore.sendAndAwait(
      `/topic/ssh/sessions/${sessionId}/sftp/upload`,
      `/app/ssh/sessions/${sessionId}/sftp/upload`,
      { localPath, remoteDir: deploymentPath },
      (message, done) => {
        const percentage = Number(message)
        uploadProgressMap.value[deploymentRecordId] = percentage
        if (percentage === 100) done()
      },
    ),
  )
}

// 执行上传步骤
const handleUploadStep = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        // 获取本地文件路径
        const localPath = await fileQueryPathById(form.fileRecordId)

        // 初始化上传进度
        uploadProgressMap.value = {}
        props.recordSelection.forEach((record) => {
          uploadProgressMap.value[record.id] = 0
        })

        // 并行上传到所有主机
        await Promise.all(
          props.recordSelection.map((record) =>
            handleUploadToHost(record.id, record.hostRecord.id, localPath, record.deploymentPath),
          ),
        )

        steps.value[0].status = 'success'
        ElMessage.success('应用包上传成功')
      } catch (error) {
        steps.value[0].status = 'error'
        ElMessage.error('应用包上传失败: ' + extractErrorMessage(error))
      }
    } else {
      throw new Error('请选择新的应用包')
    }
  })
}

// 提交更新作业(经 MQ 异步执行:换应用包指针 + 重启/解压)
const handleUpdateDeploymentRecord = async () => {
  try {
    // 初始化更新状态
    updateStatusMap.value = {}
    props.recordSelection.forEach((record) => {
      updateStatusMap.value[record.id] = 'pending'
    })

    // 并行为每条部署记录提交一个 UPDATE 作业
    await Promise.all(
      props.recordSelection.map(async (record) => {
        try {
          await deploymentJobCreate(record.id, {
            jobType: JobTypeEnum.UPDATE.value,
            clientRequestId: crypto.randomUUID(),
            fileRecordId: form.fileRecordId,
          })
          updateStatusMap.value[record.id] = 'success'
        } catch (error) {
          updateStatusMap.value[record.id] = 'error'
          throw error
        }
      }),
    )

    steps.value[1].status = 'success'
    ElMessage.success('更新作业已提交,稍后自动执行')
  } catch (error) {
    steps.value[1].status = 'error'
    ElMessage.error('提交更新作业失败: ' + extractErrorMessage(error))
  }
}

const logHostId = ref('')
const logPath = ref('')
const logVisible = ref(false)

const handleViewLog = (record: DeploymentRecord) => {
  const { hostRecord, deploymentPath } = record
  logHostId.value = hostRecord.id
  logPath.value = `${deploymentPath}/nohup.out`
  logVisible.value = true
}

// 步骤
const activeStep = ref(0)
const stepLoading = ref(false)
const steps = ref<
  { title: string; status: 'process' | 'wait' | 'error' | 'finish' | 'success'; method?: () => Promise<void> }[]
>([
  { title: '上传应用包', status: 'process', method: handleUploadStep },
  { title: '提交更新作业', status: 'wait', method: handleUpdateDeploymentRecord },
])

const fileSelectVisible = ref(false)
// 选择文件
const handleFileSelect = () => {
  fileSelectVisible.value = true
}
const handleFileSelectComplete = (file: FileRecord) => {
  const { id, fileName } = file
  form.fileRecordId = id
  form.fileName = fileName
}
// 清空选择文件
const handleFileSelectClear = () => {
  form.fileRecordId = ''
  form.fileName = ''
}

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

// 关闭抽屉即触发父页刷新（保留原行为：关闭就同步最新部署状态）
const handleClose = () => {
  visible.value = false
  emit('complete')
}
</script>

<template>
  <app-drawer v-model="visible" title="更新应用包" width="md" @close="handleClose">
    <el-steps :active="activeStep" align-center>
      <el-step v-for="(step, index) in steps" :key="index" :title="step.title" :status="step.status" />
    </el-steps>
    <section v-if="activeStep === 0" class="step-section">
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        :disabled="stepLoading || steps[0].status === 'success'"
        label-width="110px"
      >
        <el-form-item label="新应用包" prop="fileName">
          <el-input
            v-model="form.fileName"
            placeholder="选择新应用包"
            :suffix-icon="Search"
            clearable
            @clear="handleFileSelectClear"
            @click="handleFileSelect"
          />
        </el-form-item>
        <el-form-item label="上传进度">
          <div class="upload-progress-list">
            <div v-for="record in recordSelection" :key="record.id" class="upload-progress-item">
              <span class="host-name">{{ record.hostRecord.name }}</span>
              <span class="deployment-path">{{ record.deploymentPath }}</span>
              <el-progress
                :percentage="uploadProgressMap[record.id] || 0"
                :stroke-width="12"
                striped
                :striped-flow="uploadProgressMap[record.id] > 0 && uploadProgressMap[record.id] < 100"
                :duration="10"
              />
            </div>
          </div>
        </el-form-item>
      </el-form>
    </section>
    <section v-if="activeStep !== 0" class="step-section">
      <div class="update-status-list">
        <div v-for="record in recordSelection" :key="record.id" class="update-status-item">
          <div class="record-info">
            <span class="host-name">{{ record.hostRecord.name }}</span>
            <span class="deployment-path">{{ record.deploymentPath }}</span>
          </div>
          <div class="status-indicator">
            <el-button
              v-if="record.applicationType === ApplicationTypeEnum.BACKEND.value"
              :icon="Document"
              @click="handleViewLog(record)"
              >查看日志</el-button
            >
            <soft-label v-if="updateStatusMap[record.id] === 'pending'" intent="info">提交中</soft-label>
            <soft-label v-else-if="updateStatusMap[record.id] === 'success'" intent="success">已提交</soft-label>
            <soft-label v-else-if="updateStatusMap[record.id] === 'error'" intent="danger">提交失败</soft-label>
          </div>
        </div>
      </div>
    </section>
    <template #footer>
      <div class="actions-container">
        <el-button :disabled="activeStep === 0 || stepLoading" @click="handlePrevStep">上一步</el-button>
        <el-button type="primary" :disabled="stepLoading || activeStep === steps.length" @click="handleNextStep"
          >下一步</el-button
        >
      </div>
    </template>

    <file-select v-if="fileSelectVisible" v-model="fileSelectVisible" @select="handleFileSelectComplete" />
    <log-view v-if="logVisible" v-model="logVisible" :host-id="logHostId" :log-path="logPath" />
  </app-drawer>
</template>

<style lang="scss" scoped>
.step-section {
  margin-top: 20px;
}
.actions-container {
  // 撑满抽屉脚宽度，使「上一步 / 下一步」保持两端对齐（脚部容器默认右对齐）
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.upload-progress-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  .upload-progress-item {
    display: flex;
    align-items: center;
    gap: 10px;
    .host-name {
      font-weight: bold;
    }
    .el-progress {
      flex: 1;
    }
  }
}
.update-status-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  .update-status-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px;
    border: 1px solid var(--el-border-color);
    border-radius: var(--app-radius-tag);
    .record-info {
      display: flex;
      flex-direction: column;
      gap: 4px;
      .host-name {
        font-weight: bold;
      }
      .deployment-path {
        color: var(--el-text-color-secondary);
        font-size: 12px;
      }
    }
    .status-indicator {
      display: flex;
      align-items: center;
      gap: 10px;
    }
  }
}
</style>
