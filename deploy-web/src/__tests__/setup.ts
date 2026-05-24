import { afterEach, vi } from 'vitest'

// 每个用例结束后清理所有 mock，避免污染下一个用例
afterEach(() => {
  vi.restoreAllMocks()
})
