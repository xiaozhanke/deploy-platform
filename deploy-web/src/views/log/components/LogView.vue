<script setup lang="ts">
import { sshConnect, sshShellAdd, sshShellClose } from '@/api/api'
import TerminalPanel from '@/components/terminal-panel/index.vue'
import { useWebSocketStore } from '@/stores/websocket'
import { generateRandomId } from '@/utils/common'

const props = defineProps<{
  serverId: string
  logPath: string
}>()

// 可见性由父级 v-model 显式接管：AppDrawer 内的 el-drawer 非单根，
// model-value 不会再 fallthrough 到浮层，必须显式绑定才能开合（三个宿主共用）
const visible = defineModel<boolean>()

const websocketStore = useWebSocketStore()
const terminalPanelRef = ref<InstanceType<typeof TerminalPanel>>()
const sessionId = ref('')
const channelId = ref('')
// 日志通道订阅句柄：tail -f 长驻订阅，关闭 / 卸载时退订
let shellSubscription: { unsubscribe: () => void } | null = null

// 连接 Shell 通道
const handleShellConnect = async () => {
  try {
    const sessionIdResult = await sshConnect(props.serverId)
    sessionId.value = sessionIdResult
    // 创建 Shell 通道
    const channelIdResult = await sshShellAdd(sessionId.value)
    channelId.value = channelIdResult
    // 订阅通道输出（一次性会话订阅，不跨重连重放）
    shellSubscription = websocketStore.subscribe(
      `/topic/ssh/sessions/${sessionId.value}/shell/${channelId.value}`,
      (message) => {
        terminalPanelRef.value?.writeToTerminal(message)
      },
      { replay: false },
    )
    terminalPanelRef.value?.resizeTerminal()
    const taskId = generateRandomId(6)
    const command = `tail -500f ${props.logPath}`
    setTimeout(() => {
      try {
        websocketStore.send(`/app/ssh/sessions/${sessionId.value}/shell/${channelId.value}`, { taskId, command })
      } catch (error) {
        // 连接已断时 send 在定时器内抛错，单独兜底，避免未捕获异常
        ElNotification.error(`读取日志文件错误: ${extractErrorMessage(error)}`)
      }
    }, 500)
  } catch (error) {
    ElNotification.error(`读取日志文件错误: ${extractErrorMessage(error)}`)
  }
}

const handleClose = async () => {
  shellSubscription?.unsubscribe()
  shellSubscription = null
  await sshShellClose(sessionId.value, channelId.value)
  visible.value = false
}

onMounted(async () => {
  await handleShellConnect()
})

onUnmounted(() => {
  shellSubscription?.unsubscribe()
})
</script>

<template>
  <app-drawer v-model="visible" :title="logPath" width="md">
    <!-- 终端在抽屉主体内需要确定高度的容器，避免塌成 0 导致 xterm 渲染异常 -->
    <div class="log-view__terminal">
      <terminal-panel ref="terminalPanelRef" />
    </div>
    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </app-drawer>
</template>

<style lang="scss" scoped>
.log-view__terminal {
  height: 100%;
}
</style>
