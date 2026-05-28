# deploy-tool

基于 Web 控制台的 Java 应用部署工具：通过 SSH/SFTP 把 jar 包推送到远程 Linux 主机并管理启停、把前端 dist 推送到 nginx 站点目录。同时提供环境管理（Java/Node/Nginx/Redis 一键安装）、Nginx 配置文件管理、浏览器内 SSH 终端等辅助功能。

后端是 Spring Boot 3 + Spring Authorization Server 同进程签发 token 的单体应用，前端是 Vue 3 + Vite 单页应用，两个子工程**完全独立部署**。

## 仓库结构

```text
deploy-tool/
├── deploy-server/         # Spring Boot 3.5 / Java 21 后端
├── deploy-web/            # Vue 3 / Vite 6 / TypeScript 前端
├── samples/               # 端到端部署测试用的示例应用（后端 jar + 前端 dist）
├── docker/                # 本地依赖：部署目标容器 + RocketMQ Compose
├── secrets/               # 本地开发资产（如 jwt-key.json JWT 签名密钥）
├── files/                 # 用户上传 / 下载的运行期文件
├── logs/                  # 运行期日志
├── CLAUDE.md              # 给 Claude Code / 协作者看的项目细则
└── CONTEXT.md             # 领域术语词典（DeploymentRecord / DeploymentJob 等）
```

两个子工程从同一 git 根目录提交，但构建产物互不耦合：后端 Maven 只打后端 jar，不再把前端打进 `src/main/resources/static/`；前端独立 `npm run build` 出 `dist/`，可部署到任意静态服务器。

## 技术栈总览

**后端**（`deploy-server`）

- Spring Boot 3.5.14 / Java 21 / Maven
- Spring Data JPA + Hibernate（构建期字节码增强）+ MySQL 8
- Spring Authorization Server + Resource Server（OAuth2 / OIDC，同进程内自签 token，公开客户端走 PKCE）
- Spring WebSocket + spring-security-messaging（浏览器 SSH 终端走 STOMP）
- JSch（`com.github.mwiede:jsch`，**不是**原始 jcraft fork）做 SSH/SFTP
- MapStruct + Lombok + Apache FreeMarker（Nginx 配置渲染）
- SpringDoc OpenAPI 3
- RocketMQ Spring Boot Starter 2.3.4（部署作业事务消息 + 顺序消息）

**前端**（`deploy-web`）

- Vue 3.5 + Vite 6 + TypeScript 5.8
- Element Plus（`unplugin-vue-components` + `unplugin-auto-import` 自动导入；样式走 Sass）
- Pinia 3 / vue-router 4
- oidc-client-ts（OIDC 公开客户端 + PKCE）
- @stomp/stompjs（WebSocket）+ @xterm/xterm（浏览器 SSH 终端）
- codemirror 6 + @codemirror/lang-yaml（配置文件编辑器）
- Vitest（单测）

**本地依赖**（`docker/`）

- 部署目标测试容器：debian + sshd + JRE 17 + nginx + supervisord（见 `docker/Dockerfile`）
- RocketMQ 5.x：NameServer + Broker + Dashboard（`docker/docker-compose-mq.yml`）

## 快速开始

后端与前端启动步骤分别见各子工程 README。

- 后端：[deploy-server/README.md](deploy-server/README.md)
- 前端：[deploy-web/README.md](deploy-web/README.md)
- 端到端部署示例：[samples/README.md](samples/README.md)
- 本地依赖容器：[docker/README.md](docker/README.md)

最小可跑通的链路：

1. 起 MySQL 8（默认 `jdbc:mysql://localhost:3306/deploy_tool`，账号 `root` / `123456`，可用环境变量覆盖）
2. `cd deploy-server && mvn spring-boot:run` → API 起在 `https://localhost:6060`
3. `cd deploy-web && npm install && npm run dev` → 前端起在 `https://localhost:5173/ui`
4. 起部署目标容器 + 把 `samples/` 里的 jar / dist 推过去做端到端验证

## 开发约定

- IDEA 工程根目录开 `/deploy-tool`；VSCode 推荐单独开 `/deploy-tool/deploy-web`
- Git 在仓库根目录提交（commit 用中文 Conventional Commits 风格：`feat:` / `fix:` / `refactor:` / `build:` …）
- 后端、前端、`application.yml` 三处的版本号始终一致（当前 `1.2.4`），改版本要一起改
- 后端遵循阿里巴巴 Java 开发规约；实体不直接暴露，走 DTO/VO 隔离

## 相关文档

- [CLAUDE.md](CLAUDE.md) —— 项目结构、命令与已知坑的权威说明（给协作者和 AI 看）
- [CONTEXT.md](CONTEXT.md) —— 领域术语词典：DeploymentRecord / DeploymentJob / OrderingKey / IdempotencyKey 等定义

## 环境要求

- JDK 21+
- Maven 3.6+
- Node.js 20.19+
- MySQL 8（本地或远程）
- Docker（运行 RocketMQ / 部署目标测试容器时需要）
