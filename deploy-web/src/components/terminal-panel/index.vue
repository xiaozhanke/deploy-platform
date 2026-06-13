<script setup lang="ts">
import '@xterm/xterm/css/xterm.css'

import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import { type ITheme, Terminal } from '@xterm/xterm'

import { useTheme } from '@/composables/useTheme'

defineOptions({
  name: 'TerminalPanel',
})

const terminalRef = ref<HTMLElement>()
let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null
// 全局主题单例：终端纯跟随全局明暗、无独立开关
const { isDark } = useTheme()

// 从当前生效的 --term-* 令牌构造 xterm 主题对象。
// xterm 主题是 JS 对象、不吃 CSS 变量，故每次都用 getComputedStyle 现读一次；
// 切主题时由下方 watch 重新调用本函数热换。getPropertyValue 常带前导空格，必须 trim。
const buildTheme = (): ITheme => {
  const styles = getComputedStyle(document.documentElement)
  const term = (name: string) => styles.getPropertyValue(name).trim()
  return {
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
  }
}

// 渲染终端显示
const renderTerminal = () => {
  if (terminalRef.value) {
    terminal = new Terminal({
      // 允许输入
      disableStdin: false,
      theme: buildTheme(),
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

// 切全局主题时热换终端配色：重读 --term-* 令牌、整体替换 theme。
// xterm 以引用比较探测 options.theme 是否变化，buildTheme 每次都返回新对象、足以触发重绘。
watch(isDark, () => {
  if (terminal) {
    terminal.options.theme = buildTheme()
  }
})

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
