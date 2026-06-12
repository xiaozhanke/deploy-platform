# Docker 开发环境

本目录托管 deploy-platform 开发期的两类容器化资产：

1. **消息中间件**：RocketMQ + Kafka 单机一键 Compose（`docker-compose-mq.yml`）
2. **部署目标测试容器**：SSH/SFTP + nginx 的部署落地目标（`Dockerfile`）

---

## 一、消息中间件（RocketMQ + Kafka）

deploy-platform 的消息模块（详见 [MQ模块设计方案.md](../MQ模块设计方案.md)）依赖 RocketMQ 5.x（部署作业事务/顺序/延迟/广播消息）与 Kafka 3.x（操作审计日志）。Compose 一键起齐 NameServer + Broker + Dashboard 与 Kafka（KRaft 模式，无需 Zookeeper）+ Kafka UI。

### 启动

```bash
cd docker
docker compose -f docker-compose-mq.yml up -d
```

### 端口约定

| 服务 | 镜像 | 端口 | 用途 |
|---|---|---|---|
| RocketMQ NameServer | `apache/rocketmq:5.3.2` | 9876 | 路由发现 |
| RocketMQ Broker | `apache/rocketmq:5.3.2` | 10909 / 10911 / 10912 | VIP 通道 / 消息收发 / HA |
| RocketMQ Dashboard | `apacherocketmq/rocketmq-dashboard:1.0.0` | 8180 | <http://localhost:8180> |
| Kafka（KRaft） | `apache/kafka:3.9.0` | 9092 | 审计日志 broker，对外通告 `127.0.0.1:9092` |
| Kafka UI | `provectuslabs/kafka-ui` | 8080 | <http://localhost:8080> |

### 验证

- RocketMQ Dashboard「集群」页能看到 `broker-a` 注册即健康
- Kafka UI 能看到 `deploy-platform-local` 集群即健康

应用层（deploy-server）通过 `application-dev.yml` 连过来：`rocketmq.name-server: 127.0.0.1:9876`、`deploy-platform.audit.bootstrap-servers: 127.0.0.1:9092`。topic 在首次发送消息时自动创建（`autoCreateTopicEnable=true`）。

### 停止与清理

```bash
docker compose -f docker-compose-mq.yml down          # 停止容器
docker compose -f docker-compose-mq.yml down -v       # 顺带删卷（磁盘上的消息也清掉）
```

### 注意

- [broker.conf](rocketmq/broker.conf) 里 `brokerIP1` 填**主机名 `rmqbroker`**（compose 的 service 名），不要填具体 IP。`brokerIP1` 是 broker 注册到 NameServer 的地址、所有客户端共用；填主机名后让两类客户端各自解析到可达目标，**切换网络后无需再改**：
  - 容器视角（如 dashboard）：docker 内置 DNS 把 `rmqbroker` 解析为 broker 的桥网络 IP，走 docker 桥直连 10911（不经 vpnkit）
  - 宿主机视角（IDE 里的 deploy-server）：靠宿主机 `/etc/hosts` 的 `127.0.0.1 rmqbroker`，经 docker 端口映射 10911 直连（走 loopback）
  - **前置（本机一次性）**：宿主机 `/etc/hosts` 加一行 `127.0.0.1 rmqbroker`；改完 broker.conf 后 `docker restart deploy-platform-rmqbroker` 让其用新地址重新注册
  - **不要填 LAN IP**（如 `192.168.x.x`）：Wi-Fi 切网后 IP 变，producer 拿旧地址报 `send message Exception`
  - **不要填 `127.0.0.1`**：dashboard 容器视角下它是容器自己，会报 `RemotingConnectException: connect to 127.0.0.1:10911 failed`
  - **不要填 `host.docker.internal`**：macOS Docker Desktop 的 vpnkit 转发 RocketMQ 协议字节时会立刻关闭 channel（producer 报 `RemotingSendRequestException`）。注意 `nc -zv` 能通是因为只测三次握手不发应用数据，**别用 nc 误判连通性**
  - **Linux 宿主**：容器与宿主机网络互通更好，`brokerIP1` 用 `127.0.0.1` + 给依赖容器加 `extra_hosts: ["host.docker.internal:host-gateway"]` 也可，不存在 vpnkit 问题
  - 生产部署：改成 broker 节点的真实 DNS 主机名（比裸 IP 更抗迁移）
- Kafka 走 KRaft 模式（单节点同时是 Controller + Broker），`KAFKA_ADVERTISED_LISTENERS` 通告 `127.0.0.1:9092`（强制 IPv4，避免 `localhost` 解析为 `::1`）；副本因子均为 1，仅适用于开发
- `autoCreateTopicEnable = true` 只用于开发，生产环境要禁用、topic 由 admin 命令显式创建
- `defaultTopicQueueNums = 8` 给顺序消息按 `deploymentRecordId` 分队列（见 [ADR-0001](../docs/adr/0001-ordering-key-deployment-record.md)）预留空间

---

## 二、部署目标测试容器（`Dockerfile`）

端到端测试时，deploy-platform 需要一台可 SSH 登录、跑 nginx 的远程主机作为部署落地目标。本目录的 `Dockerfile`（debian 12-slim + openssh-server + JRE 17 + nginx + unzip + supervisord）就是这台「主机」，supervisord 同时拉起 sshd 与 nginx。

### 构建与启动

```bash
# 在仓库根目录执行
docker build -t deploy-target docker/
docker run -d --name deploy-target -p 2222:22 -p 8080:8080 -p 80:80 deploy-target
```

### 容器约定

| 项 | 值 |
|---|---|
| 账号 | `root` / `root`，`deploy` / `deploy`（`deploy` 在 sudo 组、已配 NOPASSWD） |
| SSH / SFTP | 容器 22 → 宿主 2222 |
| nginx 站点 | 容器 80 → 宿主 80，默认站点目录 `/var/www/html`（前端 zip 解压落点） |
| 应用端口 | 容器 8080 → 宿主 8080（被部署 jar 的常用默认端口） |

### 注意

- 该容器**仅供测试**：放开了密码登录、`PermitRootLogin`、`deploy` 的 NOPASSWD sudo，并移除了 `pam_loginuid`（容器无 audit 子系统，否则密码正确也会被踢）。**勿用于生产**。
- 宿主 8080 端口与上面 Kafka UI 冲突：若消息中间件 Compose 在跑（kafka-ui 占用 8080），请把部署目标的应用端口换个映射，如 `-p 8081:8080`。
- 端到端关联示例见 [samples/README.md](../samples/README.md)。
