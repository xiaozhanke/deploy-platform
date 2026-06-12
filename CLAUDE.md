# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在本仓库工作时提供指引。

## 项目概述

deploy-platform 是一个基于 Web 控制台的运维部署平台：通过 SSH/SFTP 把 Java 后端 jar、前端静态包推送到远程主机并管理启停/重启/更新，同时提供环境安装、Nginx/Redis 配置管理、浏览器内 SSH 终端、操作审计等能力。部署动作以**消息驱动**方式异步执行（RocketMQ 事务/顺序/延迟/广播消息 + Kafka 审计日志）。

## 仓库结构

单一 git 根目录，所有 commit 均从根目录提交：

- `deploy-server/` —— Spring Boot 3.5.14 后端，Java 21，Maven（artifact `com.xiaozhanke:deploy-server`，根包 `com.xiaozhanke.deploy`）
- `deploy-web/` —— Vue 3 + Vite 7 + TypeScript 前端
- `docker/` —— 本地依赖：RocketMQ 单机 Compose（`docker-compose-mq.yml`）+ 部署目标测试容器（`Dockerfile`：sshd + JRE + nginx + supervisord）
- `samples/` —— 端到端部署测试用的目标产物（`sample-app-backend` jar / `sample-app-frontend` dist）
- `docs/` —— 架构文档：`adr/`（21 篇架构决策记录）、`design-system.md`、`console_dashboard_plan.md`
- `CONTEXT.md` —— 领域术语词典（DeploymentRecord / DeploymentJob / OrderingKey / IdempotencyKey 等）
- `MQ模块设计方案.md` —— 消息模块设计稿（6 个业务场景）
- `deploy-server/secrets/`、`deploy-server/files/`、`deploy-server/logs/` —— 运行期资产/日志目录

两个子工程**完全独立部署**：后端 Maven 构建只产出后端 jar，不打包前端；前端 `npm run build` 单独出 `dist/`，部署到任意静态服务器（如 nginx）。

## 常用命令

### 后端（在 `deploy-server/` 目录下运行）
```bash
mvn spring-boot:run                # dev profile 默认激活
mvn clean package                  # 打后端 jar（dev profile）
mvn clean package -Ppro            # 生产 profile（凭据须经环境变量注入）
mvn test                           # JUnit（spring-boot-starter-test）
mvn -Dtest=ClassName#method test   # 跑单个测试方法
```

Maven 中 `dev` profile 标了 `activeByDefault=true`，会设置 `spring.profiles.active=dev`；生产构建用 `-Ppro`。

### 前端（在 `deploy-web/` 目录下运行）
```bash
npm install
npm run dev          # Vite 开发服务器：https://localhost:5173/ui（basic-ssl 提供 HTTPS）
npm run build        # type-check + vite build → dist/
npm run type-check   # vue-tsc --build（仅类型检查不产物）
npm run lint         # eslint . --cache —— 只报告不改文件，前端校验以此为准
npm run lint:fix     # eslint . --fix --cache —— 自动修复，会改文件（跑完看 diff）
npm run format       # prettier --write src/，会改文件
npm run test:run     # vitest 单次；test:unit 为 watch、test:coverage 出覆盖率
npm run test:e2e     # playwright E2E
```

`npm run build` 通过 `npm-run-all2` 并行跑 `type-check` 和 `build-only`。

**前端校验以 `npm run lint` 为准**：它是 `eslint . --cache`，**只报告、不改动文件**，可随时放心跑来确认是否合规（CI 用 `npm run lint:ci`，即 `--max-warnings=0`）。

⚠️ 真正会改动文件的是 `npm run lint:fix`（`eslint --fix`）和 `npm run format`（prettier）：跑完务必 `git diff` 复核。历史上 `--fix` 曾过度删除「看起来多余但其实必要」的类型断言（如 `as '' | 'small' | 'default' | 'large'` 这类为保留字面量类型而加的断言），删后 vue-tsc 会因推断变宽而报错——这是 `lint:fix` 的注意点，`npm run lint`（不带 `--fix`）本身不会发生。

## 运行架构

### 端口与 URL
- 后端 API：`http://localhost:6060`（`application.yml` 里 `server.ssl.enabled=false`，后端只提供 HTTP，TLS 由前置 nginx 终止；`forward-headers-strategy: framework` 据 `X-Forwarded-*` 重建 issuer）
- 前端开发：`https://localhost:5173/ui`（基础路径 `/ui` 由 `VITE_BASE_URL` 设置，HTTPS 由 basic-ssl 提供）
- Swagger UI：`http://localhost:6060/swagger-ui.html`；OpenAPI JSON：`http://localhost:6060/v3/api-docs`
- RocketMQ：NameServer `127.0.0.1:9876`、Dashboard `http://localhost:8180`
- Kafka（审计）：`127.0.0.1:9092`
- MySQL：`127.0.0.1:3306/deploy_platform`

### 开发模式请求流向
浏览器只与 `https://localhost:5173` 同源通信，Vite 把以下前缀反代到后端 `http://localhost:6060`（见 `vite.config.ts`），并注入 `X-Forwarded-Proto/Host/Port` 让后端把 issuer/discovery 重建为 `https://localhost:5173/...`：

- `${VITE_API_ROOT}`（默认 `/api`）—— 业务 API
- `/.well-known`、`/oauth2`、`/connect`、`/userinfo`、`/login` —— OIDC discovery / 授权 / 登出 / userinfo / AS 自托管登录页
- `/csrf`、`/swagger-ui`、`/v3/api-docs`、`/favicon.svg`、`/favicon.ico`
- `/websocket` —— STOMP，`ws: true` 升级代理。浏览器侧用同源 `wss://localhost:5173/websocket`（由 `getWebSocketUrl()` 按页面协议推导），规避 HTTPS 页面直连明文 `ws://` 的混合内容限制

### 安全模型
- Spring Authorization Server + Resource Server **同进程内**部署，API 自己签发 OIDC token，无外部 IdP
- OIDC 客户端 `oidc-client`，**公开客户端**（PKCE，`client-authentication-methods: none`），回调 `/ui/login/callback`、登出落地 `/ui/login/landing`
- JWT 签名密钥持久化到 `secrets/jwt-key.json`（dev 允许首次启动自动生成，pro 须预置）；access token 10m / refresh token 4h（pro）
- 登录失败锁定（`max-failed-logins`、`lockout-cooldown`）、密码有效期（`password-validity-days`）在 `app.security` 配置
- WebSocket 鉴权依赖 `spring-security-messaging`；`security/` 包分 `config/`、`exception/`、`handler/`、`token/`、`user/` 子目录

### 后端包结构（`com.xiaozhanke.deploy`）
- `controller/` —— REST 接口：Auth、Login、Csrf、Host、File、Deployment、DeploymentJob（`POST /deployments/{id}/jobs` 触发部署作业）、Ssh、WebSocketSsh、Config、PlatformRole、PlatformUser、AuditLog、MQMonitor、Test
- `service/` —— 业务逻辑：Host、File­Storage、Deployment、DeploymentJob、DeploymentJobExecution、Config、PlatformRole、PlatformUser、AuditLog、DeadLetter、Ssh
- `messaging/` —— 消息模块（见下「消息架构」），分 `config/`、`producer/`、`consumer/`、`transaction/`、`selector/`、`idempotent/`、`dto/`
- `aspect/` + `audit/` —— `@Auditable` AOP 切面写审计日志，经 Kafka 投递；Kafka 不可用时 `AuditFallbackWriter` 落本地 jsonl、`AuditFallbackReplayJob` 定时回放
- `core/ssh/` —— 基于 JSch（`com.github.mwiede:jsch`，**不是** jcraft fork）的 SSH 命令/Shell 执行
- `core/websocket/` —— 浏览器 SSH 终端与 SFTP 进度的 STOMP / WebSocket 通道
- `model/{base,dto,entity,mapper,request,response,vo,validation}` —— MapStruct 在 entity ↔ DTO/VO 之间映射；实体不直接暴露
- `repository/` —— Spring Data JPA 仓库
- `security/`、`config/`、`exception/`、`validation/`、`constant/`、`enums/`、`util/`

### 消息架构（RocketMQ + Kafka）
部署作业（DeploymentJob）以消息驱动异步执行，6 个场景：

1. **事务消息**（RocketMQ）：`DeploymentMQProducer` + `DeploymentTransactionListener`，本地事务仅 INSERT 一行 `deployment_job`，再 commit 半消息；topic `deploy-job`
2. **顺序消息**（RocketMQ）：事务消息与自定义 `MessageQueueSelector` 互斥，故消费者用 `ConsumeMode.ORDERLY` + 一条 CAS UPDATE（`status='PENDING'` 幂等 + `NOT EXISTS 同记录 IN_PROGRESS` 串行）把"同一记录串行、不同记录并发"下沉到 DB 兜底（详见 ADR-0006），不依赖 producer 端队列选择
3. **延迟消息**（RocketMQ）：`DeploymentDelayedProducer`/`DeploymentDelayedConsumer`，定时部署；取消下沉到业务状态机（PENDING→CANCELLED）；topic `deploy-job-delayed`
4. **Kafka 审计**：`AuditLogProducer`/`AuditLogConsumer`，topic `audit-log`、3 分区并发消费、非事务路线
5. **死信队列**：自定义 DLQ topic `deploy-job-dlq` + `DeadLetterConsumer`，耗尽重试的作业人工重试
6. **广播消费**（RocketMQ）：`ConfigChangeProducer`/`ConfigBroadcastConsumer`，topic `config-broadcast`，BROADCASTING 模式每实例都收

幂等：消费者用 `JobAcquisitionService` 的 CAS `UPDATE deployment_job SET status='IN_PROGRESS' WHERE id=? AND status='PENDING'` 占据作业，受影响 0 行即重复消息直接 ACK。术语与设计细节见 [CONTEXT.md](CONTEXT.md) 与 [docs/adr/](docs/adr/) 0001–0006。

### 持久化
- **MySQL 8**。`application-dev.yml` 默认连 `jdbc:mysql://localhost:3306/deploy_platform`（`root`/`123456`），可经 `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_USER`/`MYSQL_PASSWORD`/`MYSQL_DB` 覆盖；生产同 URL 但凭据须经 env 注入
- JPA：`ddl-auto: update`（dev）/ `none`（pro）、`open-in-view: false`
- 实体：HostRecord、FileRecord、DeploymentRecord、DeploymentJob、DeadLetterMessage、AuditLog、PlatformUser、PlatformRole
- 构建期启用 Hibernate 字节码增强（`hibernate-enhance-maven-plugin`：lazy init、dirty tracking、association management）。**不要**在不理解后果的情况下禁用——它与 JPA 审计字段写入相关
- `classpath:/sql/schema.sql`、`data.sql` 提供建表/种子，`application-dev.yml` 里的 `sql.init` 默认注释

### 前端栈要点
- Element Plus 经 `unplugin-vue-components` + `unplugin-auto-import` 自动导入，类型生成在 `types/auto-imports.d.ts`、`types/components.d.ts`（**不要**手改）；`ElMessage`/`ElNotification`/`ref` 等及 `@/utils/errorMessage` 的 `extractErrorMessage` 全局可用
- Element Plus 样式以 Sass 引入（`importStyle: 'sass'`），SCSS 自动注入 `@use "@/styles/element/index.scss" as *; @use "@/styles/breakpoints.scss" as *;`
- 主题：`styles/tokens.scss` 是浅/深双主题的语义令牌真源，`styles/index.scss` 放 `--layout-*` 布局令牌；`useTheme` 切主题
- 实时：`@stomp/stompjs`（`stores/websocket.ts`）+ `@xterm/xterm`（`components/terminal-panel`）做浏览器 SSH 终端；`codemirror`（yaml，`components/code-editor`）做配置编辑
- 路由懒加载 + `requiresAuth` 守卫（`router/index.ts`），OIDC 用 `oidc-client-ts`（`services/oidcService.ts` + `stores/auth.ts`）
- `@` 别名指向 `src/`；命名：组件文件 PascalCase、模板里 kebab-case、formatter 工具函数 camelCase

## 注解处理器（容易踩坑）

`pom.xml` 在 `annotationProcessorPaths` 里串联了 MapStruct + Lombok + `lombok-mapstruct-binding`。若 IDE 编译失败或找不到生成的 `*Impl.java`：IntelliJ → *Build, Execution, Deployment → Compiler → Annotation Processors* → 启用注解处理、选 "Obtain processors from project classpath"，然后 `mvn clean` 再 rebuild。

## 版本号

后端 `pom.xml`、前端 `package.json`、`application.yml` 的 `spring.application.version` 始终一致（当前 `1.3.0`），变更时三处一起改。

## 约定

- Commit 用中文 Conventional Commits 风格（`feat:`/`fix:`/`refactor:`/`build:` 前缀英文、主旨中文）
- 三个 README（根、server、web）与文档均为简体中文，编辑时跟随
- 后端遵循阿里巴巴 Java 规约；用 Lombok + MapStruct，实体不直接暴露、走 DTO/VO 隔离
- 术语：底层 SSH 主机统一称 **Host**（不用 Server），其网络地址字段为 `address`；面向用户的「实例/发布」对应内部模型 DeploymentRecord，详见 [CONTEXT.md](CONTEXT.md)
