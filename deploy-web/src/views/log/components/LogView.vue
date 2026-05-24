<script setup lang="ts">
import { sshConnect, sshShellAdd, sshShellClose } from '@/api/api'
import TerminalPanel from '@/components/terminal-panel/index.vue'
import { useWebSocketStore } from '@/stores/websocket'
import { generateRandomId } from '@/utils/common'

const props = defineProps<{
  serverId: string
  logPath: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const websocketStore = useWebSocketStore()
const terminalPanelRef = ref<InstanceType<typeof TerminalPanel>>()
const sessionId = ref('')
const channelId = ref('')

// 连接 Shell 通道
const handleShellConnect = async () => {
  try {
    const sessionIdResult = await sshConnect(props.serverId)
    sessionId.value = sessionIdResult
    // 创建 Shell 通道
    const channelIdResult = await sshShellAdd(sessionId.value)
    channelId.value = channelIdResult
    // 订阅通道输出
    websocketStore.subscribe(`/topic/ssh/sessions/${sessionId.value}/shell/${channelId.value}`, (message) => {
      terminalPanelRef.value?.writeToTerminal(message)
    })
    terminalPanelRef.value?.resizeTerminal()
    const taskId = generateRandomId(6)
    const command = `tail -500f ${props.logPath}`
    setTimeout(() => {
      websocketStore.send(`/app/ssh/sessions/${sessionId.value}/shell/${channelId.value}`, { taskId, command })
    }, 500)
  } catch (error) {
    ElNotification.error(`读取日志文件错误: ${String(error)}`)
  }
}

const handleClose = async () => {
  await sshShellClose(sessionId.value, channelId.value)
  emit('update:modelValue', false)
}

onMounted(async () => {
  await handleShellConnect()
})
</script>

<template>
  <el-dialog :title="logPath" width="75%" top="5vh" draggable :close-on-click-modal="false" :before-close="handleClose">
    <terminal-panel ref="terminalPanelRef" />
    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>
