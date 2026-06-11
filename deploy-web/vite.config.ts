import type { ClientRequest } from 'node:http'
import { fileURLToPath, URL } from 'node:url'

import basicSsl from '@vitejs/plugin-basic-ssl'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import Components from 'unplugin-vue-components/vite'
import { defineConfig, loadEnv } from 'vite'
import vueDevTools from 'vite-plugin-vue-devtools'

interface ProxyServer {
  on(event: 'proxyReq', listener: (proxyReq: ClientRequest) => void): void
}

/**
 * 为代理请求注入 X-Forwarded-* 头，让 Spring 的 forward-headers-strategy=framework
 * 据此重建 issuer/discovery/endpoints 为 https://localhost:5173/...
 *
 * 注意：必须操作 proxyReq（出站请求到后端），而非 req（入站请求来自浏览器）。
 */
function withForwardedHeaders(proxy: ProxyServer) {
  proxy.on('proxyReq', (proxyReq: ClientRequest) => {
    proxyReq.setHeader('X-Forwarded-Proto', 'https')
    proxyReq.setHeader('X-Forwarded-Host', 'localhost:5173')
    proxyReq.setHeader('X-Forwarded-Port', '5173')
  })
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  // 反代到后端 API 时注入 X-Forwarded-*，让 Spring(forward-headers-strategy=framework) 据此
  // 重建 issuer/discovery/endpoints 为 https://localhost:5173/...。每次返回独立 options 对象，
  // 避免多个前缀共享同一引用被 http-proxy 写入内部状态。
  const apiProxy = () => ({
    target: env.VITE_PROXY_TARGET,
    changeOrigin: true,
    secure: false,
    configure: withForwardedHeaders,
  })
  return {
    base: env.VITE_BASE_URL,
    plugins: [
      vue(),
      vueDevTools(),
      AutoImport({
        imports: [
          'vue',
          {
            // 错误文案统一来源：响应拦截器把 axios 错误包成 ApiError(message=中文友好文案)，
            // 业务侧用 extractErrorMessage(error) 取 .message 即可，避免再写 `String(error)` 把
            // 类名前缀（"ApiError: xxx"）也带进弹窗。
            '@/utils/errorMessage': ['extractErrorMessage'],
          },
        ],
        resolvers: [
          ElementPlusResolver({
            importStyle: 'sass',
          }),
        ],
        dts: 'types/auto-imports.d.ts',
      }),
      Components({
        resolvers: [
          ElementPlusResolver({
            importStyle: 'sass',
          }),
        ],
        dts: 'types/components.d.ts',
      }),
      basicSsl(),
    ],
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `@use "@/styles/element/index.scss" as *; @use "@/styles/breakpoints.scss" as *;`,
        },
      },
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      // 下列前缀统一反代后端：API、OIDC discovery/授权/登出/userinfo、AS 自托管登录页与表单
      // （/login 仅命中根路径，不命中 SPA 的 /ui/login/*，base 在 /ui）、登录页 favicon、CSRF 引导
      // （/csrf，单独控制器、脱离 /login 前缀）、Swagger UI 与 OpenAPI JSON。
      proxy: {
        [env.VITE_API_ROOT]: apiProxy(),
        '/.well-known': apiProxy(),
        '/oauth2': apiProxy(),
        '/connect': apiProxy(),
        '/userinfo': apiProxy(),
        '/login': apiProxy(),
        // AS 自托管登录页用根路径 /favicon.svg 引图标；前端 base 在 /ui、vite public 落在 /ui/ 下不命中
        // 此根路径，故一并反代到后端由其 static 提供（/favicon.ico 兜底浏览器默认请求）。SPA 自身用
        // /ui/favicon.svg，不命中这两条精确前缀、仍由 vite public 提供，互不影响
        '/favicon.svg': apiProxy(),
        '/favicon.ico': apiProxy(),
        '/csrf': apiProxy(),
        '/swagger-ui': apiProxy(),
        '/v3/api-docs': apiProxy(),
        // WebSocket(STOMP)：浏览器侧用同源 wss://localhost:5173/websocket，经此升级代理到后端明文 ws，
        // 规避 HTTPS 页面直连 ws:// 的混合内容限制。WS 握手不需 issuer 重写，故不挂 withForwardedHeaders。
        '/websocket': { target: env.VITE_PROXY_TARGET, changeOrigin: true, secure: false, ws: true },
      },
    },
  }
})
