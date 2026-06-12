# deploy-server

deploy-platform 的 Spring Boot 后端：通过 REST + WebSocket 暴露主机管理、部署作业、文件传输、SSH 终端、配置生成与操作审计等能力。部署作业以消息驱动异步执行（RocketMQ）、审计日志经 Kafka 投递。Spring Authorization Server 与 Resource Server **同进程内**部署，API 自己签发 OIDC token。

> 项目背景与双子工程关系见根 [README.md](../README.md)；工作细则见 [CLAUDE.md](../CLAUDE.md)；领域术语见 [CONTEXT.md](../CONTEXT.md)。

## 技术栈

- **Spring Boot 3.5.14 / Java 21 / Maven**
- **Spring Data JPA + Hibernate**（构建期字节码增强：lazy init / dirty tracking / association management）+ **MySQL 8**
- **Spring Authorization Server + Resource Server**（OAuth2 / OIDC，PKCE，公开客户端）+ Spring Security + `spring-security-messaging`
- **Spring WebSocket**：浏览器 SSH 终端 / SFTP 进度走 STOMP
- **RocketMQ Spring Boot Starter** 2.3.4：部署作业事务 / 顺序 / 延迟 / 广播消息
- **Spring Kafka**：操作审计日志高吞吐投递
- **JSch** `com.github.mwiede:jsch` 0.2.26（**不是** jcraft fork）：SSH/SFTP
- **MapStruct** 1.6.3 + **Lombok**：entity ↔ DTO/VO 映射
- **Apache FreeMarker**：Nginx 配置文件模板渲染（`templates/nginx.conf.ftl`）
- **SpringDoc OpenAPI** 2.8.13：Swagger UI / OpenAPI 3
- Spring Boot Actuator、Validation

## 包结构（`com.xiaozhanke.deploy`）

```text
deploy-server/src/main/java/com/xiaozhanke/deploy/
├── DeployApplication.java
├── controller/        # REST：Auth/Login/Csrf/Host/File/Deployment/DeploymentJob/Ssh/WebSocketSsh/
│                      #        Config/PlatformRole/PlatformUser/AuditLog/MQMonitor/Test
├── service/           # 业务逻辑：Host/FileStorage/Deployment/DeploymentJob/
│                      #          DeploymentJobExecution/Config/PlatformRole/PlatformUser/
│                      #          AuditLog/DeadLetter/Ssh
├── messaging/         # 消息模块（见下「消息架构」）
│   ├── config/        #   RocketMQConfig/RocketMQProperties/KafkaConfig/KafkaAuditProperties
│   ├── producer/      #   DeploymentMQ/DeploymentDelayed/ConfigChange/AuditLog Producer
│   ├── consumer/      #   Deployment/DeploymentDelayed/DeadLetter/ConfigBroadcast/AuditLog Consumer
│   ├── transaction/   #   DeploymentTransactionListener（事务消息本地事务 + 回查）
│   ├── selector/      #   DeploymentRecordQueueSelector（顺序键分队列）
│   ├── idempotent/    #   JobAcquisitionService / AcquireResult / JobExecutionDelegate
│   └── dto/           #   消息体
├── aspect/            # @Auditable 注解 + AuditAspect（AOP 审计切面）
├── audit/             # AuthenticationAuditListener / AuditFallbackWriter / AuditFallbackReplayJob
├── core/
│   ├── ssh/           # 基于 JSch 的 SSH 命令 / Shell 执行
│   └── websocket/     # 浏览器 SSH 终端与 SFTP 进度的 STOMP 通道
├── model/
│   ├── base/          # BasePo/BaseDto/BaseVo + 基类映射
│   ├── entity/        # JPA 实体（不直接暴露给上层）
│   ├── dto/ mapper/ request/ response/ vo/ validation/
├── repository/        # Spring Data JPA 仓库
├── security/          # config/exception/handler/token/user（OAuth2/OIDC 安全模型）
├── config/            # Security/WebMvc/WebSocket/Jpa/OpenApi/Scheduling/Jackson/Cors/FileStorage
├── exception/         # 业务异常 + GlobalExceptionHandler
└── constant/、enums/、util/、validation/
```

## 消息架构（RocketMQ + Kafka）

部署作业（DeploymentJob）与配置广播、操作审计均以消息流转，共 6 个场景（详见 [MQ模块设计方案.md](../MQ模块设计方案.md) 与 [docs/adr/](../docs/adr/) 0001–0006）：

| 场景 | 中间件 / 模式 | Topic | 关键类 |
| --- | --- | --- | --- |
| 1 部署作业异步化 | RocketMQ 事务消息 | `deploy-job` | `DeploymentMQProducer` + `DeploymentTransactionListener` |
| 2 单记录作业串行化 | RocketMQ 顺序消息 | `deploy-job` | `DeploymentRecordQueueSelector`（顺序键 = `deploymentRecordId`） |
| 3 定时部署 + 取消 | RocketMQ 延迟消息 | `deploy-job-delayed` | `DeploymentDelayedProducer` / `DeploymentDelayedConsumer` |
| 4 操作审计 | Kafka（非事务、3 分区并发） | `audit-log` | `AuditLogProducer` / `AuditLogConsumer` |
| 5 失败重试 + 死信 | 自定义 DLQ | `deploy-job-dlq` | `DeadLetterConsumer` + `DeadLetterService` |
| 6 配置广播 | RocketMQ 广播消费 | `config-broadcast` | `ConfigChangeProducer` / `ConfigBroadcastConsumer` |

- **事务消息**：`executeLocalTransaction` 仅 INSERT 一行 `deployment_job`（PENDING）再 commit 半消息；回查按 `jobId` 存在与否决定 commit/rollback。SSH 远程命令由消费者拿到消息后执行，不在本地事务内。
- **顺序键**：以 `deploymentRecordId` 分队列（`deploy-job-queue-count: 8`，与 broker 队列数对齐），同一记录的作业串行、不同记录并发。
- **幂等**：消费者用 `JobAcquisitionService` 的 CAS `UPDATE deployment_job SET status='IN_PROGRESS' WHERE id=? AND status='PENDING'` 占据作业，受影响 0 行即重复消息直接 ACK。
- **取消语义**：延迟消息无法撤回，取消下沉到状态机（PENDING→CANCELLED）；到期消息消费首行 CAS 不命中即 ACK 不执行。
- **审计兜底**：Kafka 不可用时 `AuditFallbackWriter` 落 `logs/audit-fallback.jsonl`，`AuditFallbackReplayJob` 定时回放（默认 60s）。

## 本地启动

### 1. MySQL 8

后端默认连接 `jdbc:mysql://localhost:3306/deploy_platform`，账号 `root` / 密码 `123456`，可经环境变量覆盖：

| 变量 | 默认值 |
| --- | --- |
| `MYSQL_HOST` | `localhost` |
| `MYSQL_PORT` | `3306` |
| `MYSQL_DB` | `deploy_platform` |
| `MYSQL_USER` | `root` |
| `MYSQL_PASSWORD` | `123456` |

dev profile `ddl-auto=update` 首次启动自动建表；`classpath:/sql/schema.sql`、`data.sql` 提供建表/种子（`application-dev.yml` 里 `sql.init` 默认注释）。

### 2. RocketMQ

```bash
cd ../docker
docker compose -f docker-compose-mq.yml up -d
```

NameServer `127.0.0.1:9876`、Dashboard <http://localhost:8180>。详见 [docker/README.md](../docker/README.md)。

### 3. Kafka（审计日志，可选）

审计经 Kafka（`127.0.0.1:9092`，topic `audit-log`）投递。未就绪时审计自动落本地兜底文件并稍后回放，不阻塞主流程，因此本地调试可不起 Kafka。

### 4. 启动后端

```bash
mvn spring-boot:run                # dev profile（pom.xml 中 activeByDefault=true）
mvn clean package                  # 打 dev jar
mvn clean package -Ppro            # 打 pro jar（凭据须经环境变量注入）
mvn test                           # JUnit / spring-boot-starter-test
mvn -Dtest=ClassName#method test   # 跑单个测试方法
```

| 端点 | URL |
| --- | --- |
| API（HTTP，TLS 由前置 nginx 终止） | `http://localhost:6060` |
| Swagger UI | `http://localhost:6060/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:6060/v3/api-docs` |
| WebSocket（STOMP） | `ws://localhost:6060/websocket` |
| OIDC discovery | `http://localhost:6060/.well-known/openid-configuration` |

> 开发时浏览器经 Vite 反代以同源 `https://localhost:5173` 访问，issuer 会被重建为 `https://localhost:5173/...`（`forward-headers-strategy: framework` + Vite 注入的 `X-Forwarded-*`）。

### 5. 生产部署

```bash
mvn clean package -Ppro
java -jar target/deploy-server-1.3.0.jar
```

pro profile 与 dev 的差异：`ddl-auto=none`、`show-sql=false`；OIDC 回调用绝对 URL（`${APP_BASE_URL}/ui/login/callback` 等，须与前端 `.env.production` 精确匹配）；access token 10m / refresh token 4h、`reuse-refresh-tokens=false`；MySQL 凭据与 JWT 密钥（`secrets/jwt-key.json`，`auto-create-on-missing=false`）须预置。

## 安全模型

- **同进程内** Authorization Server + Resource Server，无外部 IdP
- OIDC 客户端 `oidc-client`，**公开客户端**（PKCE，`client-authentication-methods: none`，`authorization_code` + `refresh_token`）
- AS 自托管登录页（`static/login.html` + `LoginController`/`CsrfController`），失败/成功/登出由 `security/handler/` 处理
- 登录失败锁定（`max-failed-logins=5`、`lockout-cooldown=15m`）、密码有效期（`password-validity-days=365`）见 `app.security`
- JWT 签名密钥持久化到 `secrets/jwt-key.json`（dev 允许自动生成，pro 须预置），跨重启稳定
- 文件上传扩展名白名单见 `app.file.allowed-extensions`（`.jar/.war/.zip/.tar.gz/...`）
- WebSocket 通道走 `spring-security-messaging` 鉴权

## 持久化要点

- **MySQL 8**；实体：HostRecord、FileRecord、DeploymentRecord、DeploymentJob、DeadLetterMessage、AuditLog、PlatformUser、PlatformRole
- JPA：dev `ddl-auto=update`、pro `none`，`open-in-view=false`
- **构建期 Hibernate 字节码增强不要随意关**——`hibernate-enhance-maven-plugin` 启用了 lazy init / dirty tracking / association management，关闭会影响 JPA 审计字段写入

## SSH 与 WebSocket 终端

- `core/ssh/`（`SshOperationExecutor` / `ShellCommandTask` / `CommandResultCallback`）封装 JSch 会话与命令执行；`SshService` 维护会话生命周期，主机连通性检测对 Host 执行 `echo 1`（超时 1500ms）
- 浏览器终端：前端经 REST 建立会话 → 订阅 STOMP topic（`/topic/ssh/sessions/{sessionId}/...`）→ `core/websocket/` 把 JSch Shell/SFTP 输出与进度推回；`WebSocketSftpProgressMonitor` 上报上传进度

## 注解处理器（容易踩坑）

`pom.xml` 在 `annotationProcessorPaths` 串联了 MapStruct + Lombok + `lombok-mapstruct-binding`。若 IDE 报找不到 `*Impl.java`：IntelliJ → *Build, Execution, Deployment → Compiler → Annotation Processors* → 启用注解处理、选 "Obtain processors from project classpath"，然后 `mvn clean` 再 rebuild。

## 测试

- `mvn test` 跑 JUnit；`mvn -Dtest=ClassName#method test` 跑单个方法
- 覆盖消息重试/幂等（`messaging/`）、安全边界（`security/`）、路径安全与 Shell 转义（`util/PathSafetyUtils`、`ShellArgEscaper`）、文件名净化与白名单等
- 端到端部署测试用 [samples/](../samples/) 的 sample-app + `docker/Dockerfile` 部署目标容器

## 版本号

后端 `pom.xml`、前端 `package.json`、`application.yml` 的 `spring.application.version` 始终一致（当前 `1.3.0`），变更时三处一起改。
