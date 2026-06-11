# deploy-web

deploy-platform 的 Vue 3 前端：单页应用，通过 OIDC 公开客户端 + PKCE 登录到后端 Authorization Server，调用 REST API 管理主机、部署作业、文件、Nginx 配置，并通过 STOMP / WebSocket 提供浏览器内 SSH 终端。

> 项目背景、双子工程关系、与后端的协作约定见仓库根 [README.md](../README.md) 与 [CLAUDE.md](../CLAUDE.md)。

## 技术栈

| 组件 | 用途 | 版本 |
| --- | --- | --- |
| Vue | 前端框架 | ^3.5.13 |
| Vite | 构建工具（dev server 由 `@vitejs/plugin-basic-ssl` 提供 HTTPS） | ^7.3.3 |
| TypeScript | 语言 | ~5.8 |
| vue-router | 官方路由 | ^4.5.0 |
| Pinia | 状态管理 | ^3.0.1 |
| Element Plus | UI 组件库（`unplugin-vue-components` + `unplugin-auto-import` 自动导入，样式走 Sass） | ^2.11.3 |
| @element-plus/icons-vue | 图标 | ^2.3.1 |
| oidc-client-ts | OIDC 公开客户端 + PKCE | ^3.2.1 |
| axios | HTTP 客户端 | ^1.8.4 |
| @stomp/stompjs | WebSocket（STOMP） | ^7.1.1 |
| @xterm/xterm | 浏览器 SSH 终端 | ^5.5.0 |
| codemirror + @codemirror/lang-yaml | 配置文件编辑器 | ^6.0.1 |
| dayjs | 日期处理 | ^1.11.13 |
| nprogress | 路由切换进度条 | ^0.2.0 |
| Sass | CSS 预处理器（注入 `@use "@/styles/element/index.scss" as *`） | ^1.86.0 |
| Vitest + @vue/test-utils + jsdom | 单元测试 | ^3.0.0 |

## 环境要求

- Node.js ≥ 20.19（推荐 LTS）
- 后端 `deploy-server` 在 `https://localhost:6060` 运行（OIDC discovery / API / WebSocket 都指向它）

## 脚本

```bash
npm install
npm run dev          # Vite 开发服务器 → https://localhost:5173/ui
npm run build        # type-check + vite build → dist/
npm run preview      # 本地预览生产产物
npm run lint         # eslint --fix
npm run format       # prettier --write src/
npm run type-check   # vue-tsc --build（仅类型检查不产物）
npm run test         # Vitest watch
npm run test:run     # Vitest 单次
npm run test:coverage
```

`npm run build` 通过 `npm-run-all2` 并行跑 `type-check` 与 `build-only`，构建产物输出到 `dist/`。

> 如果安装缓慢，先切镜像再 install：
>
> ```bash
> npm config set registry https://registry.npmmirror.com
> ```

## 开发模式请求流向

| 请求 | 走向 |
| --- | --- |
| `${VITE_API_ROOT}`（默认 `/api`） | Vite 代理到 `https://localhost:6060` |
| `/.well-known/*`（OIDC discovery） | Vite 代理到 `https://localhost:6060` |
| `wss://localhost:6060/websocket` | 浏览器直连后端，**不**走 Vite 代理 |

具体配置见 `vite.config.ts` 与 `.env.development`。

## OIDC 登录

- 客户端 ID：`oidc-client`，公开客户端（PKCE，`client-authentication-methods: none`）
- Authority：`/`（discovery 走 Vite 代理）
- 回调地址：`https://localhost:5173/ui/login/callback`
- 登出回调：`https://localhost:5173/ui/login`
- Scope：`openid profile`

回调路由 `/ui/login/callback` 在 `src/router/` 内显式声明，登录态通过 `oidc-client-ts` 的 `UserManager` 维持，并由 axios 拦截器把 access_token 加到 `Authorization: Bearer` 头。

## 端口与基础路径

- 开发：`https://localhost:5173/ui`（基础路径 `/ui` 由 `VITE_BASE_URL` 设置）
- 生产：`npm run build` 产出 `dist/`，可部署到任意静态服务器（如 nginx），跟后端独立部署

## 目录结构

```text
deploy-web/
├── src/
│   ├── App.vue
│   ├── main.ts                 # 应用入口
│   ├── api/                    # REST 接口封装
│   ├── assets/                 # 静态资源（图片、字体等）
│   ├── components/             # 公共组件（PascalCase 文件名）
│   ├── enums/                  # 与后端对齐的枚举
│   ├── layouts/                # 布局组件
│   ├── router/                 # 路由（含 OIDC 回调）
│   ├── services/               # 公共服务（OIDC、WebSocket、Xterm 封装等）
│   ├── stores/                 # Pinia store
│   ├── styles/                 # 全局 SCSS / Element Plus 主题入口
│   ├── types/                  # TS 类型定义；auto-imports.d.ts、components.d.ts 由插件生成
│   ├── utils/                  # 工具函数（formatter/ 下用 camelCase 命名）
│   ├── views/                  # 页面级组件
│   └── __tests__/              # Vitest 测试
├── public/
├── index.html
├── vite.config.ts
└── package.json
```

## 注意事项

1. **`npm run lint` 已知坑**：`eslint --fix` 会主动删除"看起来多余但其实必要"的类型断言（例如为了保留字面量类型而加的 `as '' | 'small' | 'default' | 'large'`），删掉后 `vue-tsc` 会因类型推断变宽而报错。lint 完务必跑一次 `npm run type-check` 确认；如果某个断言被反复删，可以加 `// eslint-disable-next-line` 保护。
2. **Element Plus 自动导入**：组件和图标都由 `unplugin-vue-components` + `unplugin-auto-import` 自动注入，类型生成在 `types/auto-imports.d.ts` 与 `types/components.d.ts`，**不要**手写这两个文件。
3. **样式**：Element Plus 走 Sass（`importStyle: 'sass'`），所有 SCSS 自动注入 `@use "@/styles/element/index.scss" as *`，直接用变量即可。
4. **命名约定**：
   - 组件文件用 PascalCase（`UserCard.vue`）
   - `<template>` 中用 kebab-case（`<user-card />`）
   - formatter 工具函数文件用 camelCase（`formatDateTime.ts`）
5. **路径别名**：`@` → `src/`。
6. **HTTPS**：dev 由 `@vitejs/plugin-basic-ssl` 提供自签证书；后端也是自签 HTTPS，浏览器首次访问需手动信任两个证书。

## 与后端的版本协同

`package.json` 的 `version`、后端 `pom.xml` 的 `<version>`、`application.yml` 的 `spring.application.version` 三处保持一致（当前 `1.2.4`），改版本要一起改。

## 推荐 IDE 配置

- VSCode + [Vue - Official (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.volar)（禁用 Vetur）
- [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint) / [Prettier](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) / [EditorConfig](https://marketplace.visualstudio.com/items/?itemName=EditorConfig.EditorConfig)
- TypeScript 对 `.vue` 文件的类型检查用 `vue-tsc`，不用裸 `tsc`
