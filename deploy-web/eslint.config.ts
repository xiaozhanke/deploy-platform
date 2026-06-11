import pluginVitest from '@vitest/eslint-plugin'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import { globalIgnores } from 'eslint/config'
import pluginPlaywright from 'eslint-plugin-playwright'
import simpleImportSort from 'eslint-plugin-simple-import-sort'
import unusedImports from 'eslint-plugin-unused-imports'
import pluginVue from 'eslint-plugin-vue'

// To allow more languages other than `ts` in `.vue` files, uncomment the following lines:
// import { configureVueProject } from '@vue/eslint-config-typescript'
// configureVueProject({ scriptLangs: ['ts', 'tsx'] })
// More info at https://github.com/vuejs/eslint-config-typescript/#advanced-setup

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },

  globalIgnores(['**/dist/**', '**/dist-ssr/**', '**/coverage/**']),

  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommendedTypeChecked,

  {
    ...pluginPlaywright.configs['flat/recommended'],
    files: ['e2e/**/*.{test,spec}.{js,ts,jsx,tsx}'],
  },

  {
    ...pluginVitest.configs.recommended,
    files: ['src/**/__tests__/*'],
  },

  {
    name: 'app/strict-rules',
    rules: {
      // SFC 块顺序固定为 script → template → style：先读组件逻辑，再看模板结构，最后看样式
      'vue/block-order': [
        'error',
        {
          order: ['script', 'template', 'style'],
        },
      ],
      // 统一标签自闭合风格：void 原生元素（如 <br>）必须自闭合；普通原生元素（如 <div>）
      // 保留成对写法；组件以及 svg / math 元素一律自闭合
      'vue/html-self-closing': [
        'error',
        {
          html: {
            void: 'always',
            normal: 'never',
            component: 'always',
          },
          svg: 'always',
          math: 'always',
        },
      ],
      // 统一 <script setup> 编译器宏的书写顺序，让 defineOptions / defineProps / defineEmits 等
      // 在文件顶部有稳定且可预期的排列（采用规则内置默认顺序）
      'vue/define-macros-order': 'error',
      // 组件元信息（name、inheritAttrs 等）优先用 defineOptions 声明，
      // 取代再开一个普通 <script> 块导出默认对象的旧写法
      'vue/prefer-define-options': 'error',
      // 报出在模板里用 ref="xxx" 声明、却从未在 <script> 中引用的悬空模板 ref
      'vue/no-unused-refs': 'error',
      // 清理无意义的 v-bind（如 :foo="'bar'" 应写作 foo="bar"）
      'vue/no-useless-v-bind': 'error',
      // 清理无意义的插值（如 {{ 'text' }} 应直接写作 text）
      'vue/no-useless-mustaches': 'error',
      // 强制 Vue 的 <template>, <script>, <style> 块之间必须有一行空行隔开
      'vue/padding-line-between-blocks': ['error', 'always'],
      // 强迫 <script> 必须声明 lang="ts"，禁止混用纯 JS
      'vue/block-lang': ['error', { script: { lang: 'ts' } }],
      // 强制必须且只能使用 Script Setup 语法，禁用过时的 Options API / Setup 导出函数
      'vue/component-api-style': ['error', ['script-setup']],
      // 强迫使用 TypeScript 泛型声明 Props，禁止运行时（runtime-based）声明
      'vue/define-props-declaration': ['error', 'type-based'],
      // 强迫使用 TypeScript 泛型声明 Emits
      'vue/define-emits-declaration': ['error', 'type-based'],
      // 强迫 HTML 模板中引用的组件名字必须全用 kebab-case（连字符连接，如 <my-card />）
      'vue/component-name-in-template-casing': [
        'error',
        'kebab-case',
        {
          registeredComponentsOnly: false,
        },
      ],
      // 强制绑定事件采用连字符
      'vue/v-on-event-hyphenation': ['error', 'always'],
      // 强制属性布尔值简写，例如 disabled 而不是 :disabled="true"
      'vue/prefer-true-attribute-shorthand': ['error', 'always'],
      // 仅用于类型的导入强制写成 `import type { X }`，并禁止 `let a: import('x').Y` 内联类型注解；
      // 使类型导入在编译期被完整擦除，与运行时值导入区分清楚，也能规避部分循环依赖
      '@typescript-eslint/consistent-type-imports': [
        'error',
        {
          prefer: 'type-imports',
          disallowTypeAnnotations: true,
        },
      ],
      // 禁止显式 any，强制写出精确类型（确需逃逸时用 unknown 再行收窄），守住类型安全底线
      '@typescript-eslint/no-explicit-any': 'error',
      // 强制使用 === / !==，杜绝 == 带来的隐式类型转换陷阱（与 null/undefined 比较也需显式）
      eqeqeq: ['error', 'always'],
      // console.warn / console.error 视为有意保留的诊断日志，始终放行；其余 console.*（log / debug / info 等）
      // 在开发期降级为告警，生产构建（NODE_ENV=production）视为错误以阻断发布。debugger 同此策略
      'no-console': [
        process.env.NODE_ENV === 'production' ? 'error' : 'warn',
        { allow: ['warn', 'error'] },
      ],
      'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'warn',
    },
  },

  {
    name: 'app/imports',
    plugins: {
      'simple-import-sort': simpleImportSort,
      'unused-imports': unusedImports,
    },
    rules: {
      // 自动按分组排序 import / export 语句，统一书写顺序，消除「import 排序」类的评审争论
      'simple-import-sort/imports': 'error',
      'simple-import-sort/exports': 'error',
      // 关掉 typescript-eslint 自带的未用检查，统一交由 unused-imports 处理，避免重复报告
      '@typescript-eslint/no-unused-vars': 'off',
      // 未使用的 import 直接报错，并可由 --fix 自动删除
      'unused-imports/no-unused-imports': 'error',
      // 未使用的变量降为告警（不会被自动删除，需人工确认）；下划线前缀的参数 / 变量视为有意保留
      'unused-imports/no-unused-vars': [
        'warn',
        {
          args: 'after-used',
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
    },
  },

  skipFormatting,
)
