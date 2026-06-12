# deploy-web

deploy-platform 的 Vue 3 前端：单页应用，经 OIDC 公开客户端 + PKCE 登录到后端 Authorization Server，调用 REST API 管理主机、部署作业、应用实例、文件、Nginx/Redis 配置，并通过 STOMP / WebSocket 提供浏览器内 SSH 终端与配置编辑。

> 项目背景与双子工程关系见根 [README.md](../README.md)；工作细则与协作约定见 [CLAUDE.md](../CLAUDE.md)。

## 技术栈

| 组件 | 用途 | 版本 |
| --- | --- | --- |
| Vue | 前端框架 | ^3.5.13 |
| Vite | 构建工具（dev server 由 `@vitejs/plugin-basic-ssl` 提供 HTTPS） | ^7.3.3 |
| TypeScript | 语言 | ~5.8 |
| vue-router | 路由 | ^4.5.0 |
| Pinia | 状态管理 | ^3.0.1 |
| Element Plus | UI 组件库（`unplugin-vue-components` + `unplugin-auto-import` 自动导入，样式走 Sass） | ^2.11.3 |
| @element-plus/icons-vue | 图标 | ^2.3.1 |
| oidc-client-ts | OIDC 公开客户端 + PKCE | ^3.2.1 |
| axios | HTTP 客户端 | ^1.8.4 |
| @stomp/stompjs | WebSocket（STOMP） | ^7.1.1 |
| @xterm/xterm（+ addon-fit / addon-web-links） | 浏览器 SSH 终端 | ^5.5.0 |
| codemirror（+ lang-yaml / theme-one-dark / vue-codemirror） | 配置文件编辑器 | ^6.0.1 |
| dayjs | 日期处理 | ^1.11.13 |
| Sass | CSS 预处理器 | ^1.86.0 |
| Vitest + @vue/test-utils + jsdom | 单元测试 | ^3.0.0 |
| Playwright | 端到端测试 | ^1.60.0 |
| ESLint 9 + Prettier | 代码规范 / 格式化 | — |

## 环境要求

- Node.js ≥ 20.19（或 ≥ 22.12）
- 后端 `deploy-server` 运行在 `http://localhost:6060`（OIDC discovery / API / WebSocket 都指向它）

## 脚本

```bash
npm install
npm run dev          # Vite 开发服务器 → https://localhost:5173/ui
npm run build        # type-check + vite build → dist/
npm run preview      # 本地预览生产产物
npm run type-check   # vue-tsc --build（仅类型检查不产物）
npm run lint         # eslint . --cache —— 只报告不改文件，校验以此为准
npm run lint:fix     # eslint . --fix --cache —— 自动修复，会改文件
npm run lint:ci      # eslint . --max-warnings=0（CI）
npm run format       # prettier --write src/
npm run test:run     # vitest 单次（test:unit 为 watch、test:coverage 出覆盖率）
npm run test:e2e     # playwright E2E
```

`npm run build` 通过 `npm-run-all2` 并行跑 `type-check` 与 `build-only`，产物输出到 `dist/`。

**校验以 `npm run lint` 为准**：它是 `eslint . --cache`，只报告、不改动文件，可随时放心跑。会改文件的是 `npm run lint:fix`（`eslint --fix`，跑完看 `git diff`）和 `npm run format`。

## 开发模式请求流向

浏览器只与同源 `https://localhost:5173` 通信，Vite 把下列前缀反代到后端 `http://localhost:6060`（`VITE_PROXY_TARGET`），并注入 `X-Forwarded-*` 让后端把 issuer/discovery 重建为 `https://localhost:5173/...`（见 `vite.config.ts`）：

| 前缀 | 用途 |
| --- | --- |
| `${VITE_API_ROOT}`（默认 `/api`） | 业务 REST API |
| `/.well-known`、`/oauth2`、`/connect`、`/userinfo`、`/login` | OIDC discovery / 授权 / 登出 / userinfo / AS 自托管登录页 |
| `/csrf`、`/swagger-ui`、`/v3/api-docs`、`/favicon.svg`、`/favicon.ico` | CSRF 引导 / Swagger / 图标 |
| `/websocket`（`ws: true`） | STOMP，浏览器用同源 `wss://localhost:5173/websocket` |

环境变量（`.env` / `.env.development` / `.env.production`）：

- `.env`：`VITE_PROXY_TARGET`、`VITE_BASE_URL=/ui`、`VITE_API_ROOT=/api`
- `.env.development`：`VITE_OIDC_*`（authority `https://localhost:5173`、client `oidc-client`、回调 `/ui/login/callback`、登出落地 `/ui/login/landing`、scope `openid profile`）
- `.env.production`：同上但用绝对域名，须与后端 `application-pro.yml` 的 redirect-uris **精确字符串匹配**

## 路由与页面

所有业务页在 `CommonLayout` 壳内、`meta.requiresAuth` 默认为 `true`；登录区 `/login` 不需认证。

| 路径 | 页面 | 说明 |
| --- | --- | --- |
| `/dashboard` | 控制台 | KPI 与资源监控 |
| `/host` | 主机管理 | Host 录入 / 连通性检测 / 点选连接 |
| `/installation` | 软件安装 | 分步骤远程安装 Nginx / Redis |
| `/configuration` | 环境配置 | Nginx / Redis 配置在线编辑下发 |
| `/deployment` | 部署发布 | 选包 + 选主机创建部署作业 |
| `/application` | 应用实例 | DeploymentRecord 运行态 / 更新 / 作业历史 |
| `/dead-letter` | 死信队列 | 耗尽重试作业人工重试 |
| `/audit-log` | 审计日志 | 用户操作流水 |
| `/file` | 文件资源 | 制品上传 / 管理 |
| `/user/{profile,password}` | 个人中心 | 基本信息 / 密码设置 |
| `/login/{landing,callback}` | 登录区 | 落地页 / OAuth2 回调 |

## OIDC 登录

- 客户端 `oidc-client`，公开客户端（PKCE，`client-authentication-methods: none`），scope `openid profile`
- authority：`https://localhost:5173`（discovery 经 Vite 反代回后端），回调 `/ui/login/callback`、登出落地 `/ui/login/landing`
- `services/oidcService.ts` 持有 `oidc-client-ts` 的 `UserManager`，`stores/auth.ts` 维持登录态
- 路由 `beforeEach` 守卫：未认证且目标页需认证时触发 `signinRedirect`（整页跳后端授权端点）；登录后跳回原目标
- `api/request.ts` 的 axios 拦截器把 access_token 加到 `Authorization: Bearer` 头，并把错误包成 `ApiError`（`extractErrorMessage` 取友好文案）

## 目录结构

```text
deploy-web/
├── src/
│   ├── main.ts                 # 应用入口
│   ├── App.vue
│   ├── api/                    # api.ts（接口函数）+ request.ts（axios 实例 / 拦截器）
│   ├── assets/                 # css/ 基础样式、icons/ 技术栈图标
│   ├── components/             # 公共组件：table-pagination / terminal-panel / code-editor /
│   │                           #   filter-bar / filter-field / status-dot / empty-state / app-drawer 等
│   ├── composables/            # useBreakpoint（响应式断点）/ useTheme（主题切换）
│   ├── enums/                  # 与后端对齐的枚举（common / platform）
│   ├── layouts/common/         # 应用外壳 + SidebarMenu
│   ├── router/                 # 路由（含 OIDC 回调与认证守卫）
│   ├── services/               # oidcService（OIDC UserManager）
│   ├── stores/                 # Pinia：auth（OIDC 用户）/ websocket（STOMP）
│   ├── styles/                 # tokens.scss / index.scss / element/ / breakpoints.scss / table.scss
│   ├── types/                  # TS 类型；auto-imports.d.ts、components.d.ts 由插件生成（勿手改）
│   ├── utils/                  # 工具：errorMessage / download / formatter/（camelCase）/ nginxLayout
│   ├── views/                  # 页面级组件（按路由分目录）
│   └── __tests__/              # Vitest 单测
├── e2e/                        # Playwright E2E
├── public/  index.html  vite.config.ts  eslint.config.ts
```

## 样式与主题

- `styles/tokens.scss`：浅/深双主题的**语义令牌真源**（画布/面层/描边、间距、圆角、阴影、字体、终端配色），深色在 `html.dark` 覆盖
- `styles/index.scss`：`.common-layout` 下的 `--layout-*` 布局令牌（间距、侧栏宽、页签行高等）与通用工具类
- `styles/element/index.scss`：Element Plus 主题变量覆盖（主色等），经 `@forward` 注入
- `styles/breakpoints.scss`：响应式断点（与 Element Plus 对齐），由 Vite 自动注入到每个 SCSS
- Element Plus 走 Sass（`importStyle: 'sass'`）；颜色语义统一复用 `--el-*`，不另开平行调色板

## 实时能力

- **STOMP**：`stores/websocket.ts` 管理连接与订阅，`getWebSocketUrl()` 按页面协议推导同源 `wss://.../websocket`
- **SSH 终端**：`components/terminal-panel`（`@xterm/xterm` + addon-fit/web-links）订阅会话 STOMP topic 回显
- **配置编辑**：`components/code-editor`（`codemirror` + `lang-yaml`，`vue-codemirror`）做 Nginx/Redis 配置在线编辑

## 约定与注意

1. **自动导入**：Element Plus 组件/图标由 `unplugin-vue-components` + `unplugin-auto-import` 注入；`vue` API、`ElMessage`/`ElNotification` 及 `extractErrorMessage` 全局可用，类型生成在 `types/auto-imports.d.ts`、`types/components.d.ts`，**勿手改**。
2. **全局格式化助手**：模板里可直接用 `$formatFileSize`、`$formatDateTime` 等（见 `types/global.d.ts`）。
3. **命名**：组件文件 PascalCase（`UserCard.vue`）、模板里 kebab-case（`<user-card />`）、formatter 工具函数 camelCase（`formatDateTime.ts`）。
4. **路径别名**：`@` → `src/`。
5. **HTTPS**：dev 前后端均自签证书（前端 basic-ssl），浏览器首次访问需手动信任。

## 与后端的版本协同

`package.json` 的 `version`、后端 `pom.xml` 的 `<version>`、`application.yml` 的 `spring.application.version` 三处保持一致（当前 `1.3.0`），改版本要一起改。

## 推荐 IDE 配置

- VSCode + [Vue - Official (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.volar)（禁用 Vetur）
- [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint) / [Prettier](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) / [EditorConfig](https://marketplace.visualstudio.com/items?itemName=EditorConfig.EditorConfig)
- `.vue` 文件类型检查用 `vue-tsc`，不用裸 `tsc`
