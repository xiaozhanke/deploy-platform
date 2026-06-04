import { computed, readonly, ref } from 'vue'

/** 主题取值。两态手动切换；留扩展位——将来加「跟随系统」时在此并入 'system'。 */
export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'app-theme'
const TRANSITION_CLASS = 'theme-transition'
const DARK_CLASS = 'dark'

function readStoredTheme(): ThemeMode {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light'
  } catch {
    // 隐私模式 / 存储不可用：回落默认浅色
    return 'light'
  }
}

// 模块级单例：全局唯一主题态，跨组件共享同一引用
const theme = ref<ThemeMode>(readStoredTheme())

// 过渡 class 的移除计时句柄：快速连切时清掉上一个，避免多个定时器堆叠把刚加的过渡提前摘除
let transitionTimer: number | null = null

/**
 * 把主题写到 <html>。withTransition 为真时挂瞬态过渡 class（仅用户手动切换）；
 * 首屏初始化由 index.html 内联脚本同步完成、无过渡，此处再幂等同步一次。
 */
function applyTheme(mode: ThemeMode, withTransition: boolean) {
  const html = document.documentElement
  if (withTransition) {
    html.classList.add(TRANSITION_CLASS)
    if (transitionTimer !== null) {
      window.clearTimeout(transitionTimer)
    }
    transitionTimer = window.setTimeout(() => {
      html.classList.remove(TRANSITION_CLASS)
      transitionTimer = null
    }, 200)
  }
  html.classList.toggle(DARK_CLASS, mode === 'dark')
}

// 模块加载即与持久化值对齐，使 useTheme 不依赖内联脚本也能自洽
applyTheme(theme.value, false)

function setTheme(mode: ThemeMode) {
  theme.value = mode
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    // 存储不可用时仅本次会话生效
  }
  applyTheme(mode, true)
}

function toggleTheme() {
  setTheme(theme.value === 'dark' ? 'light' : 'dark')
}

export function useTheme() {
  return {
    theme: readonly(theme),
    isDark: computed(() => theme.value === 'dark'),
    setTheme,
    toggleTheme,
  }
}
