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
        imports: ['vue'],
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
