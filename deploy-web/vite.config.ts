import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import basicSsl from '@vitejs/plugin-basic-ssl'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
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
          additionalData: `@use "@/styles/element/index.scss" as *;`,
        },
      },
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        // 规则一：代理所有 API 请求
        [env.VITE_API_ROOT]: {
          target: env.VITE_PROXY_TARGET,
          changeOrigin: true,
          secure: false, // 忽略 SSL 证书错误
        },
        // 规则二：代理 OIDC 的发现端点
        '/.well-known': {
          target: env.VITE_PROXY_TARGET,
          changeOrigin: true,
          secure: false, // 忽略 SSL 证书错误
        },
      },
    },
  }
})
