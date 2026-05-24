import { UserManager, WebStorageStateStore, type SigninRedirectArgs, type UserManagerSettings } from 'oidc-client-ts'

const oidcSettings: UserManagerSettings = {
  // 认证服务器的地址
  authority: import.meta.env.VITE_OIDC_AUTHORITY,
  // 客户端ID
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID,
  // 授权成功后，认证服务器重定向回来的地址
  // 必须和后端配置的 redirectUri 完全一致
  redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URI,
  // 登出成功后，认证服务器重定向回来的地址
  post_logout_redirect_uri: import.meta.env.VITE_OIDC_POST_LOGOUT_REDIRECT_URI,
  // 响应类型，'code' 表示使用授权码流程，oidc-client-ts 会自动启用 PKCE
  response_type: 'code',
  // 申请的权限范围，'openid' 'profile' 是必须的，这样才能拿到用户信息
  scope: import.meta.env.VITE_OIDC_SCOPE,
  // 使用浏览器的 sessionStorage 来存储 OIDC 状态
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  // 自动静默刷新令牌
  automaticSilentRenew: true,
  // 如果 access token 在 60 秒内过期，就尝试刷新
  accessTokenExpiringNotificationTimeInSeconds: 60,
}

const userManager = new UserManager(oidcSettings)

export const oidcService = {
  /**
   * 直接暴露 userManager.events，以便监听 OIDC 事件
   */
  events: userManager.events,
  /**
   * 触发 OIDC 授权码流程
   * 将用户浏览器重定向到认证服务器的授权端点 (`/oauth2/authorize`)。
   * @param args 可选参数，可以包含 `state` 等，用于在回调时恢复前端状态。
   */
  handleAuthorizationRedirect: (args?: SigninRedirectArgs) => {
    // 调用 signinRedirect 会将页面重定向到认证服务器的授权端点
    return userManager.signinRedirect(args)
  },
  /**
   * 触发 OIDC 登出流程
   * 将用户的浏览器重定向到认证服务器的登出端点 (`end_session_endpoint`)。
   * @param args 可选参数，可以包含 `id_token_hint` 等。
   */
  handleLogoutRedirect: (args?: SigninRedirectArgs) => {
    // 调用 signoutRedirect 会将页面重定向到认证服务器中配置的客户端登出端点
    return userManager.signoutRedirect(args)
  },
  /**
   * 处理从认证服务器的授权端点 (`/oauth2/authorize`) 重定向回来的回调请求
   */
  handleAuthorizationCallback: () => {
    // 用户从认证服务器重定向回来后，调用此方法完成授权流程
    return userManager.signinCallback()
  },
  /**
   * 处理静默刷新的回调
   * 这个过程通常在一个隐藏的 `iframe` 中进行，用于在 `access_token` 即将过期时，
   * 无缝地获取一个新的 `access_token`，而无需用户交互。
   */
  handleSilentCallback: () => {
    return userManager.signinSilentCallback()
  },
  /**
   * 从存储中异步加载已认证的当前用户信息
   */
  getUser: () => {
    return userManager.getUser()
  },
  /**
   * 从存储中移除已认证的当前用户信息
   */
  removeUser: () => {
    return userManager.removeUser()
  },
  /**
   * 触发静默刷新令牌
   */
  renewTokens: () => {
    return userManager.signinSilent()
  },
}
