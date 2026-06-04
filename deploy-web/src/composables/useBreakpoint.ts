import { computed, ref, type Ref } from 'vue'

/**
 * 响应式断点（matchMedia 实现，刻意不引入 VueUse）。
 *
 * 侧栏三态只依赖两个下沿（与 styles/breakpoints.scss 的 respond-to 同一刻度、对齐 Element Plus）：
 * sm ≥768（手机 / 平板分界）、lg ≥1200（平板 / 宽桌面分界）。完整断点刻度见 breakpoints.scss。
 *
 * 模块级单例：matchMedia 监听只建一次、全局共享，避免每个组件各挂一份；
 * matchMedia 仅在越过断点时触发，天然消除 resize 抖动。
 */
const BREAKPOINTS = {
  sm: 768,
  lg: 1200,
} as const

type BreakpointName = keyof typeof BREAKPOINTS

// 每个断点一个「视口是否 ≥ 该下沿」的响应式布尔
const matches: Record<BreakpointName, Ref<boolean>> = {
  sm: ref(false),
  lg: ref(false),
}

let initialized = false

function setupMatchMedia() {
  if (initialized || typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return
  }
  initialized = true
  for (const name of Object.keys(BREAKPOINTS) as BreakpointName[]) {
    const mediaQuery = window.matchMedia(`(min-width: ${BREAKPOINTS[name]}px)`)
    matches[name].value = mediaQuery.matches
    mediaQuery.addEventListener('change', (event) => {
      matches[name].value = event.matches
    })
  }
}

setupMatchMedia()

export function useBreakpoint() {
  // <768：手机，侧栏退化为抽屉浮出
  const isMobile = computed(() => !matches.sm.value)
  // 768–1199：平板 / 窄桌面，侧栏收成图标条
  const isTablet = computed(() => matches.sm.value && !matches.lg.value)
  // ≥1200：宽桌面，侧栏完全展开
  const isDesktop = computed(() => matches.lg.value)

  return {
    isMobile,
    isTablet,
    isDesktop,
  }
}
