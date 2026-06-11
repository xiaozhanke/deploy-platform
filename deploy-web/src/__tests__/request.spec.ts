import type { AxiosInstance } from 'axios'
import AxiosMockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// request.ts 通过 AutoImport + ElementPlusResolver 隐式拿到 ElNotification，
// 用 vi.mock 替换 'element-plus' 模块导出即可拦截全部用法
const elNotificationError = vi.fn()
vi.mock('element-plus', () => ({
  ElNotification: { error: elNotificationError },
}))

const handleSessionExpired = vi.fn().mockResolvedValue(undefined)
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    accessToken: '',
    sessionAbortController: null,
    handleSessionExpired,
  }),
}))

describe('request interceptor 401 处理', () => {
  let mockAdapter: AxiosMockAdapter
  let instance: AxiosInstance

  beforeEach(async () => {
    setActivePinia(createPinia())
    vi.resetModules()
    handleSessionExpired.mockClear()
    // dynamic import 让 vi.mock 在模块解析前生效
    const mod = await import('@/api/request')
    instance = mod.default
    mockAdapter = new AxiosMockAdapter(instance)
  })

  afterEach(() => {
    mockAdapter.restore()
  })

  it('在收到 401 时应让调用方 catch 到拒绝', async () => {
    mockAdapter.onGet('/api/anywhere').reply(401, {
      error: { status: 'UNAUTHENTICATED', message: '会话已失效' },
    })

    await expect(instance.get('/api/anywhere')).rejects.toMatchObject({
      code: 401,
      status: 'UNAUTHENTICATED',
    })
    expect(handleSessionExpired).toHaveBeenCalledOnce()
  })

  it('调用方的 finally 能在 401 之后执行', async () => {
    mockAdapter.onGet('/api/another').reply(401)

    const finallyHandler = vi.fn()
    await expect(instance.get('/api/another').finally(finallyHandler)).rejects.toBeDefined()
    expect(finallyHandler).toHaveBeenCalledOnce()
  })
})
