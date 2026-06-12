# deploy-platform

基于 Web 控制台的运维部署平台。通过 SSH/SFTP 把 Java 后端 jar 推送到远程 Linux 主机并管理启停/重启/版本更新，把前端 dist 推送到 nginx 站点目录；同时提供环境安装、Nginx/Redis 配置管理、文件资源托管、浏览器内 SSH 终端、操作审计等能力。

部署动作以**消息驱动**异步执行：作业经 RocketMQ 事务/顺序/延迟/广播消息流转，操作审计经 Kafka 投递。后端是 Spring Boot 单体应用（Authorization Server 与 Resource Server 同进程自签 token），前端是 Vue 3 单页应用，两个子工程独立部署。

## 功能概览

| 控制台页面 | 能力 |
| --- | --- |
| 控制台 Dashboard | 在线主机数、运行中实例等 KPI 与资源监控 |
| 主机管理 | 录入/编辑 Host（SSH 连接信息），连通性检测，点选连接 |
| 软件安装 | 分步骤远程安装 Nginx / Redis（实时终端回显） |
| 环境配置 | Nginx / Redis 配置文件在线编辑与下发 |
| 部署发布 | 选包 + 选主机创建部署作业（START/STOP/RESTART/UPDATE） |
| 应用实例 | 管理 DeploymentRecord 的运行态、版本更新、作业历史 |
| 死信队列 | 耗尽重试的部署作业人工重试 |
| 审计日志 | 用户操作流水（经 Kafka 落库） |
| 文件资源 | 上传/管理待发布的 jar / zip 制品 |

## 架构总览

```text
浏览器 (Vue 3 SPA, https://localhost:5173/ui)
   │  REST / OIDC / STOMP   （开发期经 Vite 反代，同源 https）
   ▼
deploy-server (Spring Boot, http://localhost:6060)
   ├─ OAuth2 Authorization Server + Resource Server（同进程自签 OIDC token）
   ├─ JSch SSH/SFTP ───────────────► 远程主机（部署目标）
   ├─ JPA / Hibernate ─────────────► MySQL 8
   ├─ RocketMQ（事务/顺序/延迟/广播）► 部署作业异步执行 + 配置广播
   └─ Kafka ───────────────────────► 操作审计日志
```

## 仓库结构

```text
deploy-platform/
├── deploy-server/         # Spring Boot 3.5 / Java 21 后端
├── deploy-web/            # Vue 3 / Vite 7 / TypeScript 前端
├── docker/                # 本地依赖：RocketMQ Compose + 部署目标测试容器
├── samples/               # 端到端测试用目标产物（后端 jar + 前端 dist）
├── docs/                  # 架构文档：adr/、design-system.md、console_dashboard_plan.md
├── CLAUDE.md              # 项目细则（给协作者 / AI）
├── CONTEXT.md             # 领域术语词典
└── MQ模块设计方案.md       # 消息模块设计稿（6 个业务场景）
```

两个子工程从同一 git 根目录提交，构建产物互不耦合：后端 Maven 只打后端 jar，前端独立 `npm run build` 出 `dist/`。

## 技术栈

**后端**（`deploy-server`）
- Spring Boot 3.5.14 / Java 21 / Maven
- Spring Data JPA + Hibernate（构建期字节码增强）+ MySQL 8
- Spring Authorization Server + Resource Server（OAuth2 / OIDC，公开客户端 + PKCE）
- Spring WebSocket + `spring-security-messaging`（浏览器 SSH 终端走 STOMP）
- RocketMQ Spring Boot Starter 2.3.4 + Spring Kafka（消息驱动部署作业 + 审计日志）
- JSch（`com.github.mwiede:jsch`）做 SSH/SFTP，FreeMarker 渲染 Nginx 配置
- MapStruct + Lombok（实体映射），SpringDoc OpenAPI（Swagger）

**前端**（`deploy-web`）
- Vue 3.5 + Vite 7 + TypeScript 5.8
- Element Plus（自动导入 + Sass），Pinia 3 / vue-router 4
- oidc-client-ts（OIDC 公开客户端 + PKCE），axios
- @stomp/stompjs + @xterm/xterm（浏览器 SSH 终端），codemirror 6 + lang-yaml（配置编辑器）
- Vitest（单测）+ Playwright（E2E），ESLint 9 + Prettier

**本地依赖**（`docker/`）
- RocketMQ 5.x：NameServer + Broker + Dashboard（`docker-compose-mq.yml`）
- 部署目标测试容器：debian + sshd + JRE + nginx + supervisord（`Dockerfile`）

## 快速开始

详细步骤见各子工程 README，最小链路如下：

1. **MySQL 8**：建库 `deploy_platform`（默认 `root`/`123456`，可经 `MYSQL_*` 环境变量覆盖）
2. **RocketMQ**：`cd docker && docker compose -f docker-compose-mq.yml up -d`（NameServer 9876 / Dashboard 8180）
3. **Kafka**（审计日志，可选）：在 `127.0.0.1:9092` 提供一个 broker；未就绪时审计自动落本地 `logs/audit-fallback.jsonl` 并稍后回放，不阻塞主流程
4. **后端**：`cd deploy-server && mvn spring-boot:run` → `http://localhost:6060`
5. **前端**：`cd deploy-web && npm install && npm run dev` → `https://localhost:5173/ui`
6. **端到端验证**：起部署目标容器，把 `samples/` 的 jar / dist 推送过去（见 [samples/README.md](samples/README.md)）

## 文档导航

- [CLAUDE.md](CLAUDE.md) —— 仓库结构、命令、运行架构、已知坑（协作者 / AI 权威说明）
- [CONTEXT.md](CONTEXT.md) —— 领域术语词典：DeploymentRecord / DeploymentJob / OrderingKey / IdempotencyKey / DLQ 等
- [docs/adr/](docs/adr/) —— 21 篇架构决策记录（消息顺序键、幂等、重试/死信、延迟取消、审计非事务、前端设计令牌/布局等）
- [MQ模块设计方案.md](MQ模块设计方案.md) —— 消息模块 6 个业务场景设计
- [docker/README.md](docker/README.md)、[samples/README.md](samples/README.md) —— 本地依赖容器与端到端部署示例

## 环境要求

- JDK 21+、Maven 3.6+
- Node.js ≥ 20.19（或 ≥ 22.12）
- MySQL 8
- Docker（运行 RocketMQ / Kafka / 部署目标容器）
