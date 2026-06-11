import { fileURLToPath } from 'node:url'

import { configDefaults, defineConfig, mergeConfig } from 'vitest/config'

import viteConfig from './vite.config'

export default defineConfig((configEnv) =>
  mergeConfig(typeof viteConfig === 'function' ? viteConfig(configEnv) : viteConfig, {
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
      globals: true,
      css: false,
      include: ['src/**/*.{spec,test}.ts', 'src/__tests__/**/*.{spec,test}.ts'],
      setupFiles: ['./src/__tests__/setup.ts'],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html'],
        include: ['src/**/*.{ts,vue}'],
        exclude: ['src/**/*.spec.ts', 'src/**/*.test.ts', 'src/__tests__/**'],
      },
    },
  }),
)
