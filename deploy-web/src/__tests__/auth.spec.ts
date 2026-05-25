import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// 让 element-plus 别在 Node 环境里抛 DOM 相关警告
vi.mock('element-plus', () => ({
  ElNotification: { error: vi.fn() },
  ElMessageBox: { alert: vi.fn() },
}))

// router 由 auth 间接 import，提供最小 stub
vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    currentRoute: { value: { query: {} } },
  },
}))

vi.mock('@/services/oidcService', () => ({
  oidcService: {
    events: {
      addUserLoaded: vi.fn(),
      addUserUnloaded: vi.fn(),
      addAccessTokenExpired: vi.fn(),
    },
    getUser: vi.fn(),
    handleAuthorizationRedirect: vi.fn(),
    handleAuthorizationCallback: vi.fn(),
    handleLogoutRedirect: vi.fn(),
    removeUser: vi.fn(),
  },
}))

vi.mock('@/api/api', () => ({
  authLogin: vi.fn(),
  authLogout: vi.fn(),
  authUserCurrent: vi.fn().mockResolvedValue({ id: 'u-1', username: 'tester' }),
}))

const wsConnect = vi.fn().mockResolvedValue(undefined)
const wsDisconnect = vi.fn().mockResolvedValue(undefined)
vi.mock('@/stores/websocket', () => ({
  useWebSocketStore: () => ({
    connect: wsConnect,
    disconnect: wsDisconnect,
    client: null,
  }),
}))

describe('auth.handleUserLoaded WebSocket 重连', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    wsConnect.mockClear()
    wsDisconnect.mockClear()
  })

  it('每次 userLoaded 都触发 WebSocket connect，确保 token 续签后用新 token 重新握手', async () => {
    const { useAuthStore } = await import('@/stores/auth')
    const authStore = useAuthStore()

    const userBeforeRefresh = {
      access_token: 'token-A',
      expired: false,
    } as unknown as import('oidc-client-ts').User
    const userAfterRefresh = {
      access_token: 'token-B',
      expired: false,
    } as unknown as import('oidc-client-ts').User

    await authStore.handleUserLoaded(userBeforeRefresh)
    expect(wsConnect).toHaveBeenCalledTimes(1)

    await authStore.handleUserLoaded(userAfterRefresh)
    // 之前因为 if (!client?.active) 守卫，第二次不会重连；现在必须每次都重连
    expect(wsConnect).toHaveBeenCalledTimes(2)
  })

  it('userLoaded 收到 null 或已过期用户时不应触发 WebSocket 连接', async () => {
    const { useAuthStore } = await import('@/stores/auth')
    const authStore = useAuthStore()

    await authStore.handleUserLoaded(null)
    const expired = { access_token: 'x', expired: true } as unknown as import('oidc-client-ts').User
    await authStore.handleUserLoaded(expired)

    expect(wsConnect).not.toHaveBeenCalled()
  })
})
