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
    // 终端恒暗、两主题同值，配色从 :root 的 --term-* 令牌读取。
    // xterm 主题是构造时的 JS 对象、不吃 CSS 变量，故构造时读一次令牌即可。
    // getPropertyValue 返回值常带前导空格，填进 theme 前必须 trim。
    const styles = getComputedStyle(document.documentElement)
    const term = (name: string) => styles.getPropertyValue(name).trim()
    terminal = new Terminal({
      // 禁用输入
      disableStdin: false,
      // 显示主题颜色
      theme: {
        background: term('--term-bg'),
        foreground: term('--term-fg'),
        cursor: term('--term-cursor'),
        selectionBackground: term('--term-selection'),
        black: term('--term-ansi-black'),
        red: term('--term-ansi-red'),
        green: term('--term-ansi-green'),
        yellow: term('--term-ansi-yellow'),
        blue: term('--term-ansi-blue'),
        magenta: term('--term-ansi-magenta'),
        cyan: term('--term-ansi-cyan'),
        white: term('--term-ansi-white'),
        brightBlack: term('--term-ansi-bright-black'),
        brightRed: term('--term-ansi-bright-red'),
        brightGreen: term('--term-ansi-bright-green'),
        brightYellow: term('--term-ansi-bright-yellow'),
        brightBlue: term('--term-ansi-bright-blue'),
        brightMagenta: term('--term-ansi-bright-magenta'),
        brightCyan: term('--term-ansi-bright-cyan'),
        brightWhite: term('--term-ansi-bright-white'),
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
  background-color: var(--term-bg);
  height: 600px;
  border-radius: var(--layout-common-border-radius);
  padding: 10px;
  .terminal {
    height: 100%;
  }
}
</style>
