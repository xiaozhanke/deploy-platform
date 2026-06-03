import router from '@/router'
import { defineStore } from 'pinia'
import { useWebSocketStore } from './websocket'
import type { User } from 'oidc-client-ts'
import { oidcService } from '@/services/oidcService'
import type { UserProfile } from '@/types/auth'
import { authUserCurrent } from '@/api/api'

interface AuthStore {
  oidcUser: User | null
  userProfile: UserProfile | null
  isLoading: boolean
  oidcEventsInitialized: boolean
  sessionAbortController: AbortController | null
}

const getWebSocketUrl = () => {
  if (import.meta.env.VITE_WEBSOCKET_URL) {
    return import.meta.env.VITE_WEBSOCKET_URL
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  return `${protocol}//${host}/websocket`
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthStore => ({
    oidcUser: null,
    userProfile: null,
    isLoading: true, // 应用启动时，默认为加载中
    oidcEventsInitialized: false, // oidc 事件已初始化
    sessionAbortController: null, // 会话中止控制器
  }),

  getters: {
    // !!state.user 检查用户对象是否存在
    // !state.user.expired 检查 token 是否过期
    isAuthenticated: (state) => !!state.oidcUser && !state.oidcUser.expired,
    // 直接从 user.profile 中获取用户信息
    // userProfile: (state) => state.oidcUser?.profile,
    // 获取 access token，用于 API 请求
    accessToken: (state) => state.oidcUser?.access_token,
    userAuthorities: (state): string[] => {
      // 你可以根据后端返回的 claim 自定义权限字段，比如 'authorities'
      // 用 Array.isArray 守卫替代 `as string[]` 强转，避免后端意外返回字符串 / 对象时整个 store 取值崩溃
      const claim = state.oidcUser?.profile?.authorities
      return Array.isArray(claim) ? claim.filter((item): item is string => typeof item === 'string') : []
    },
    profile: (state): UserProfile | null => state.userProfile,
  },

  actions: {
    // 内部 action，用于处理用户成功加载后的所有逻辑
    async handleUserLoaded(user: User | null) {
      // 重置会话中止控制器
      this.sessionAbortController = null
      this.oidcUser = user
      if (user && !user.expired) {
        await this.fetchUserProfile()
        const websocketStore = useWebSocketStore()
        // 每次 userLoaded（含初次登录与静默续签）都重连：connect 内部先 disconnect 再用新 token 重新握手，
        // 避免续签后服务器收到带过期 Authorization 的 STOMP 帧
        await websocketStore.connect(getWebSocketUrl())
      }
    },

    // 内部 action，用于处理用户登出或会话失效后的所有清理逻辑
    async handleUserUnloaded() {
      const websocketStore = useWebSocketStore()
      await websocketStore.disconnect()
      this.oidcUser = null
      this.userProfile = null
    },

    // 初始化 OIDC 事件监听器
    initOidcEvents() {
      if (this.oidcEventsInitialized) return
      this.oidcEventsInitialized = true

      // 静默刷新或通过回调获取到新用户时触发
      oidcService.events.addUserLoaded(async (user) => {
        console.log('OIDC 事件：用户加载完成')
        await this.handleUserLoaded(user)
      })

      // 用户登出或会话丢失时触发
      oidcService.events.addUserUnloaded(async () => {
        console.log('OIDC 事件：用户卸载')
        await this.handleUserUnloaded()
      })

      // access token 过期且静默刷新失败时触发
      oidcService.events.addAccessTokenExpired(async () => {
        console.error('访问令牌已过期，无法续订。正在注销。')
        // 非交互登出：优先借仍可能存活的 AS 会话静默恢复，不主动踢掉
        await this.logout({ interactive: false })
      })
    },

    async fetchUserProfile() {
      if (!this.isAuthenticated) {
        this.userProfile = null
        return
      }
      try {
        const user = await authUserCurrent()
        this.userProfile = user
      } catch (error) {
        ElNotification.error('获取用户信息失败:' + extractErrorMessage(error))
        this.userProfile = null
      }
    },

    // 应用加载时，从 authService 加载用户信息
    async loadUser() {
      this.initOidcEvents()
      this.isLoading = true
      try {
        const user = await oidcService.getUser()
        await this.handleUserLoaded(user)
      } catch (error) {
        console.error('加载用户信息失败:', error)
        await this.handleUserUnloaded()
      } finally {
        this.isLoading = false
      }
    },

    // 触发 OAuth2 授权码流程
    // @param redirectPath 登录成功后要跳回的 SPA 路径（守卫传入目标页 to.fullPath）；
    //        缺省（如落地页主动重登）则不带回跳路径，handleOAuth2Callback 默认回首页。
    //        该路径写进 OAuth state，由 handleOAuth2Callback 读 state.redirectPath 跳回。
    async oauth2Authorize(redirectPath?: string) {
      // 重置会话中止控制器
      this.sessionAbortController = null
      await oidcService.handleAuthorizationRedirect({ state: { redirectPath } })
    },

    // 登出（OIDC RP-Initiated Logout）
    // @param options.interactive true（默认，用户主动退出 / 改密后）→ 彻底结束 AS 会话；
    //        false（access_token 过期自动触发）→ 优先借仍可能存活的 SSO 会话静默恢复，不主动踢掉。
    async logout(options?: { interactive?: boolean }) {
      const interactive = options?.interactive ?? true
      this.sessionAbortController = null
      // 先断 WebSocket、清 pinia 本地态
      await this.handleUserUnloaded()
      const user = await oidcService.getUser()
      if (interactive && user?.id_token) {
        // 只要本地还存有 id_token（即便 access_token 已过期）即可发起 RP-Initiated Logout：
        // signoutRedirect 自动带 id_token_hint + post_logout_redirect_uri 失效 AS 会话；
        // 即便 AS 因 token 过期 / 会话不存在而拒，OidcLogoutErrorRedirectHandler 也会失效会话再跳落地页。
        await oidcService.handleLogoutRedirect()
      } else {
        // 非交互，或本地已无 id_token（无从构造 id_token_hint）→ 清本地态并跳落地页，借 SSO 重新发起授权。
        await oidcService.removeUser()
        await router.push('/login/landing')
      }
    },

    // OAuth2 授权回调处理
    async handleOAuth2Callback() {
      try {
        const user = await oidcService.handleAuthorizationCallback()
        if (user && !user.expired) {
          // user.state 是 oidcAuthorize 写入的回调态，类型不可信，先 narrow 再读 redirectPath
          const state =
            user.state && typeof user.state === 'object' && 'redirectPath' in user.state
              ? (user.state as { redirectPath?: unknown })
              : null
          const redirectPath = typeof state?.redirectPath === 'string' ? state.redirectPath : '/'
          await router.push(redirectPath)
        } else {
          throw new Error('登录成功，但获取到的用户信息无效或已过期。')
        }
      } catch (error) {
        console.error('登录回调处理失败:', error)
        await this.handleUserUnloaded()
        // 跳落地页（/login/landing）而非 /login —— 后者 redirect 到 /login/callback 会再次进回调、
        // 无 code/state 再抛错 → 无限重定向。落地页负责 removeUser + 重新发起授权。
        await router.push('/login/landing')
      }
    },

    // 会话过期处理
    async handleSessionExpired() {
      if (this.sessionAbortController) {
        console.warn('会话过期处理已在进行中，忽略重复调用。')
        return
      }
      // 创建会话中止控制器并中止后续请求
      this.sessionAbortController = new AbortController()
      this.sessionAbortController.abort('由于会话过期，请求被取消。')
      // 清理用户
      await this.handleUserUnloaded()
      await oidcService.removeUser()
      await ElMessageBox.alert('未授权或登录已过期，请重新登录', '认证失败', {
        confirmButtonText: '确定',
      })
      // 重定向到登录落地页（触发重新登录）
      await router.push('/login/landing')
    },
  },
})
