<script setup lang="ts">
import type { SetupStep } from '@/types/environment'
import type { ServerRecord } from '@/types/server'
import StepContent from './StepContent.vue'
import { useWebSocketStore } from '@/stores/websocket'
import { generateRandomId } from '@/utils/common'

const props = defineProps<{
  initSteps: SetupStep[]
}>()

const steps = ref<SetupStep[]>(props.initSteps)
const websocketStore = useWebSocketStore()
const sessionId = inject('sessionId') as Ref<string>
const channelId = inject('channelId') as Ref<string>
const currentServer = inject('currentServer') as Ref<ServerRecord>
const activeStep = ref(0)
const stepLoading = ref(false)

watch(
  () => props.initSteps,
  (newSteps) => {
    steps.value = [...newSteps]
  },
  { immediate: true },
)

// 设置错误处理
onMounted(() => {
  websocketStore.setErrorHandler(() => {
    if (steps.value[activeStep.value].status === 'process') {
      steps.value[activeStep.value].status = 'error'
      stepLoading.value = false
      ElMessage.error('执行操作失败，请重试')
    }
  })
})

onUnmounted(() => {
  websocketStore.clearErrorHandler()
})

// 往 Shell 发送命令
const handleSendShellCommand = async (commands: string[]) => {
  for (const command of commands) {
    await new Promise<number>((resolve, reject) => {
      const taskId = generateRandomId(6)
      const subscription = websocketStore.subscribe(
        `/topic/ssh/sessions/${sessionId.value}/shell/${channelId.value}/task/${taskId}`,
        (message) => {
          // 收到退出码即完成：退订一次性订阅，避免注册表累积与跨重连重放
          subscription.unsubscribe()
          resolve(Number(message))
        },
        { replay: false },
      )
      setTimeout(() => {
        try {
          websocketStore.send(`/app/ssh/sessions/${sessionId.value}/shell/${channelId.value}`, { taskId, command })
        } catch (error) {
          // 连接已断时 send 抛错：退订并让步骤以错误结束，而非永久挂起
          subscription.unsubscribe()
          reject(error)
        }
      }, 1000)
    })
  }
}

// SFTP 上传文件
const handleUploadFile = (localPath: string, remoteDir: string) =>
  websocketStore.sendAndAwait(
    `/topic/ssh/sessions/${sessionId.value}/sftp/upload`,
    `/app/ssh/sessions/${sessionId.value}/sftp/upload`,
    { localPath, remoteDir },
    (message, done) => {
      const percentage = Number(message)
      steps.value[activeStep.value].percentage = percentage
      if (percentage === 100) done()
    },
  )

// SFTP 下载文件
const handleDownloadFile = (remotePath: string, localDir: string) =>
  websocketStore.sendAndAwait(
    `/topic/ssh/sessions/${sessionId.value}/sftp/download`,
    `/app/ssh/sessions/${sessionId.value}/sftp/download`,
    { remotePath, localDir },
    (message, done) => {
      const percentage = Number(message)
      steps.value[activeStep.value].percentage = percentage
      if (percentage === 100) done()
    },
  )

// 执行当前步骤
const handleExecuteCurrentStep = async () => {
  if (!currentServer.value || Object.keys(currentServer.value).length === 0) {
    return ElMessage.warning('请先选择服务器')
  }

  if (steps.value[activeStep.value].status === 'error') {
    steps.value[activeStep.value].status = 'process'
  }

  stepLoading.value = true
  try {
    await executeCurrentStep(steps.value[activeStep.value])
  } catch (error) {
    ElNotification.error(`配置过程中出错: ${extractErrorMessage(error)}`)
  } finally {
    stepLoading.value = false
  }
}
const executeCurrentStep = async (step: SetupStep) => {
  if (step.status !== 'process') return

  try {
    switch (step.type) {
      case 'command':
        await handleSendShellCommand(step.commands!)
        step.status = 'success'
        break
      case 'upload':
        await handleUploadFile(step.localPath!, step.remoteDir!)
        step.status = 'success'
        break
      case 'download':
        await handleDownloadFile(step.remotePath!, step.localDir!)
        step.status = 'success'
        break
    }
  } catch (error) {
    step.status = 'error'
    throw error
  }
}

// 上一步
const handlePrevStep = () => {
  if (activeStep.value > 0) {
    activeStep.value--
  }
}

// 下一步
const handleNextStep = () => {
  if (activeStep.value < steps.value.length - 1) {
    activeStep.value++
  }
  const currentStep = steps.value[activeStep.value]
  if (currentStep.status === 'wait') {
    currentStep.status = 'process'
  }
}

// 重置步骤
const resetSteps = () => {
  steps.value = [...props.initSteps]
  activeStep.value = 0
}

// 当前步骤状态 → StatusDot 语义（颜色全部走 intent 对应的 --el-color-*，不写死颜色）
// wait 未开始：info 灰空心环；process 进行中：primary 蓝脉冲；
// success 成功：success 绿实心；error 失败：warning 橙（本向导失败可重新「执行当前步骤」，属可重试，故用橙非红）
const currentStepStatus = computed(() => {
  const status = steps.value[activeStep.value]?.status
  switch (status) {
    case 'process':
      return { intent: 'primary' as const, hollow: false, pulse: true, text: '执行中' }
    case 'success':
      return { intent: 'success' as const, hollow: false, pulse: false, text: '已完成' }
    case 'error':
      return { intent: 'warning' as const, hollow: false, pulse: false, text: '执行失败，可重试' }
    case 'wait':
    default:
      return { intent: 'info' as const, hollow: true, pulse: false, text: '待执行' }
  }
})

defineExpose({
  resetSteps,
})
</script>

<template>
  <div class="steps-container">
    <el-steps :active="activeStep" align-center>
      <el-step v-for="(step, index) in steps" :key="index" :title="step.title" :status="step.status" />
    </el-steps>
    <!-- 当前步骤状态：补充语义指示（步骤条连线仍由上方 el-steps 的 :status 驱动） -->
    <div class="step-status">
      <span class="step-status__label">当前步骤：</span>
      <status-dot
        :intent="currentStepStatus.intent"
        :hollow="currentStepStatus.hollow"
        :pulse="currentStepStatus.pulse"
      >
        {{ currentStepStatus.text }}
      </status-dot>
    </div>
    <div class="step-actions">
      <el-button :disabled="activeStep === 0" @click="handlePrevStep">上一步</el-button>
      <el-button
        plain
        :loading="stepLoading"
        :disabled="steps[activeStep].status !== 'process' && steps[activeStep].status !== 'error'"
        @click="handleExecuteCurrentStep"
      >
        执行当前步骤
      </el-button>
      <el-button
        type="primary"
        :disabled="
          activeStep === steps.length - 1 ||
          steps[activeStep].status === 'process' ||
          steps[activeStep].status === 'error'
        "
        @click="handleNextStep"
        >下一步</el-button
      >
    </div>
    <step-content
      :type="steps[activeStep].type"
      :commands="steps[activeStep].commands"
      :local-path="steps[activeStep].localPath"
      :remote-dir="steps[activeStep].remoteDir"
      :remote-path="steps[activeStep].remotePath"
      :local-dir="steps[activeStep].localDir"
      :percentage="steps[activeStep].percentage"
    />
  </div>
</template>

<style lang="scss" scoped>
.steps-container {
  display: flex;
  flex-direction: column;
  gap: var(--layout-common-gap);
  margin-top: var(--layout-common-padding);
  .step-status {
    display: flex;
    align-items: center;
    gap: var(--app-space-1);
    .step-status__label {
      color: var(--el-text-color-secondary);
    }
  }
  .step-actions {
    display: flex;
    justify-content: space-between;
  }
}
</style>
