<script setup lang="ts">
import '@xterm/xterm/css/xterm.css'
import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import { Terminal } from '@xterm/xterm'

defineOptions({
  name: 'TerminalPanel',
})

const terminalRef = ref<HTMLElement>()
let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null

// 渲染终端显示
const renderTerminal = () => {
  if (terminalRef.value) {
    terminal = new Terminal({
      // 禁用输入
      disableStdin: false,
      // 显示主题颜色
      theme: {
        background: 'black',
        foreground: '#42b883',
      },
    })
    // 加载终端插件
    fitAddon = new FitAddon()
    terminal.loadAddon(fitAddon)
    terminal.loadAddon(new WebLinksAddon())
    // 打开终端
    terminal.open(terminalRef.value)
    fitAddon.fit()
    terminal.focus()
  }
}

// 写入内容到终端
const writeToTerminal = (message: string) => {
  terminal?.write(message)
}

// 清空终端
const clearTerminal = () => {
  terminal?.clear()
}

// 重置终端
const resetTerminal = () => {
  terminal?.reset()
}

// 适应窗口大小变化
const resizeTerminal = () => {
  if (fitAddon) {
    fitAddon.fit()
  }
}

onMounted(() => {
  renderTerminal()
  window.addEventListener('resize', resizeTerminal)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeTerminal)
  // 销毁终端实例
  terminal?.dispose()
})

defineExpose({
  writeToTerminal,
  clearTerminal,
  resetTerminal,
  resizeTerminal,
})
</script>

<template>
  <div class="terminal-container">
    <div ref="terminalRef" class="terminal"></div>
  </div>
</template>

<style lang="scss" scoped>
.terminal-container {
  background-color: black;
  height: 600px;
  border-radius: var(--layout-common-border-radius);
  padding: 10px;
  .terminal {
    height: 100%;
  }
}
</style>
