# deploy-server

deploy-platform 的 Spring Boot 后端：通过 REST + WebSocket 暴露主机管理、部署作业、文件传输、SSH 终端、配置生成等能力。Spring Authorization Server 与 Resource Server **同进程内**部署，API 自己签发 OIDC token。

> 项目背景、双子工程关系与开发约定见仓库根 [README.md](../README.md) 与 [CLAUDE.md](../CLAUDE.md)；领域术语见 [CONTEXT.md](../CONTEXT.md)。

## 技术栈

- **Spring Boot 3.5.14 / Java 21 / Maven**
- **Spring Data JPA + Hibernate** + **MySQL 8**（构建期开启 Hibernate 字节码增强：lazy init / dirty tracking / association management）
- **Spring Authorization Server + Resource Server**（OAuth2 / OIDC，PKCE，公开客户端）
- **Spring WebSocket** + `spring-security-messaging`（浏览器 SSH 终端走 STOMP）
- **JSch**：`com.github.mwiede:jsch` 0.2.26（**不是**原始 jcraft fork），SSH/SFTP 实现
- **MapStruct** 1.6.3 + **Lombok**：entity ↔ DTO/VO 映射
- **Apache FreeMarker**：Nginx 配置文件模板渲染
- **SpringDoc OpenAPI** 2.8.13：Swagger UI / OpenAPI 3
- **RocketMQ Spring Boot Starter** 2.3.4：部署作业事务消息 + 顺序消息

## 包结构（`com.xiaozhanke.deploy`）

```text
deploy-server/src/main/java/com/xiaozhanke/deploy/
├── DeployApplication.java
├── controller/        # REST 接口：Auth/Config/Deployment/DeploymentJob/File/PlatformRole/PlatformUser/Host/Ssh/WebSocketSsh/Test
├── service/           # 业务逻辑：ConfigService/DeploymentService/FileStorageService/PlatformRoleService/PlatformUserService/HostService/SshService
├── core/
│   ├── ssh/           # 基于 JSch 的 SSH 会话管理
│   └── websocket/     # 浏览器端 SSH 终端的 STOMP / WebSocket 通道
├── messaging/         # RocketMQ 集成：config/producer/consumer/transaction/selector/idempotent/dto
├── model/
│   ├── dto/           # 服务层数据传输对象
│   ├── entity/        # JPA 实体（不直接暴露给上层）
│   ├── mapper/        # MapStruct 映射器
│   ├── request/       # 请求参数
│   └── vo/            # 视图对象
├── repository/        # Spring Data JPA 仓库接口
├── security/          # OAuth2 / OIDC 安全模型：config/exception/token/user
├── config/、filter/、interceptor/、validation/、exception/
├── constant/、enums/、util/
```

## 本地启动

### 1. 准备 MySQL 8

后端默认连接 `jdbc:mysql://localhost:3306/deploy_platform`，账号 `root` / 密码 `123456`，可通过环境变量覆盖：

| 变量 | 默认值 |
| --- | --- |
| `MYSQL_HOST` | `localhost` |
| `MYSQL_PORT` | `3306` |
| `MYSQL_DB` | `deploy_platform` |
| `MYSQL_USER` | `root` |
| `MYSQL_PASSWORD` | `123456` |

dev profile 配 `spring.jpa.hibernate.ddl-auto=update`，首次启动会自动建表。可选的种子数据在 `classpath:/sql/data.sql`（默认注释掉）。

### 2.（可选）准备 RocketMQ

如果要跑 MQ 模块相关功能：

```bash
cd ../docker
docker compose -f docker-compose-mq.yml up -d
```

NameServer 起在 `127.0.0.1:9876`、Dashboard 在 <http://localhost:8180>。详见 [docker/README.md](../docker/README.md)。

### 3. 启动后端

```bash
cd deploy-server
mvn spring-boot:run            # dev profile（pom.xml 中 activeByDefault=true）
mvn clean package              # 打 dev jar
mvn clean package -Ppro        # 打 pro jar（生产环境必须通过环境变量提供 MySQL 凭据）
mvn test                       # JUnit / spring-boot-starter-test
mvn -Dtest=ClassName#method test   # 跑单个测试方法
```

启动成功后：

| 端点 | URL |
| --- | --- |
| API（HTTPS，keystore.p12 内置自签证书，密码 `changeit`，alias `deploy`） | `https://localhost:6060` |
| Swagger UI | `https://localhost:6060/swagger-ui.html` |
| OpenAPI JSON | `https://localhost:6060/v3/api-docs` |
| WebSocket（STOMP） | `wss://localhost:6060/websocket` |
| OIDC discovery | `https://localhost:6060/.well-known/openid-configuration` |

### 4. 生产部署

```bash
mvn clean package -Ppro
java -jar target/deploy-server-1.2.4.jar
```

Maven 中 `dev` profile 标了 `activeByDefault=true`，会设置 `spring.profiles.active=dev`；`-Ppro` 切到生产 profile。

## 安全模型

- **同进程内** Authorization Server + Resource Server，没有外部 IdP 依赖
- OIDC 客户端 ID `oidc-client`，**公开客户端**（PKCE，`client-authentication-methods: none`）
- 回调地址：`https://localhost:5173/ui/login/callback`（开发）
- JWT 签名密钥从仓库根目录的 `secrets/jwt-key.json` 读取（仅本地开发，生产请用 env / Vault 注入）
- WebSocket 通道走 `spring-security-messaging` 鉴权
- `security/` 包内分 `config/`、`exception/`、`token/`、`user/` 四个子目录

## 持久化要点

- **MySQL 8** 是当前真实使用的数据库（已从 H2 全量迁移完成）
- JPA：dev 用 `ddl-auto: update`、pro 用 `none`；`open-in-view: false`
- **构建期 Hibernate 字节码增强不要随意关**——`hibernate-enhance-maven-plugin` 启用了 lazy init / dirty tracking / association management，关闭会导致 JPA 审计字段写入异常（参考 commit `2047dfd`）

## 注解处理器（容易踩坑）

`pom.xml` 在 `annotationProcessorPaths` 里串联了 MapStruct + Lombok + `lombok-mapstruct-binding`。若 IDE 报找不到 `*Impl.java`：

1. IntelliJ → *Build, Execution, Deployment → Compiler → Annotation Processors*
2. 勾选 `Enable annotation processing`
3. 选 `Obtain processors from project classpath`
4. `mvn clean` 后 rebuild

## 测试

- `mvn test` 跑 JUnit（基于 `spring-boot-starter-test`）
- `mvn -Dtest=ClassName#method test` 跑单个方法
- 端到端部署测试用 [samples/](../samples/) 下的 sample-app-backend / sample-app-frontend + `docker/Dockerfile` 部署目标容器，详见 [samples/README.md](../samples/README.md)

## 版本号

后端 `pom.xml`、前端 `package.json` 和 `application.yml` 里的 `spring.application.version` 始终保持一致（当前 `1.2.4`），变更时三处一起改。
