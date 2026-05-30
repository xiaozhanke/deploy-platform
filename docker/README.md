# Docker 环境

本目录托管 deploy-tool 开发期的容器化依赖。

## RocketMQ 单机开发环境

deploy-tool MQ 模块(详见 [MQ模块设计方案.md](../MQ模块设计方案.md))依赖 RocketMQ 5.x。本目录提供一键起 NameServer + Broker + Dashboard 的 Compose 文件。

### 启动

```bash
cd docker
docker compose -f docker-compose-mq.yml up -d
```

### 端口约定

| 服务 | 端口 | 用途 |
|---|---|---|
| NameServer | 9876 | RocketMQ 路由发现 |
| Broker | 10909 / 10911 / 10912 | VIP 通道 / 消息收发 / HA |
| Dashboard | 8180 | Web 控制台,浏览器访问 <http://localhost:8180> |

### 验证

启动后访问 dashboard,在「集群」页能看到 broker-a 注册即代表健康。

应用层(deploy-server)通过 `application-dev.yml` 里的 `rocketmq.name-server: 127.0.0.1:9876` 连过来,topic 在首次发送消息时自动创建(`autoCreateTopicEnable=true`)。

### 停止与清理

```bash
docker compose -f docker-compose-mq.yml down          # 停止容器
docker compose -f docker-compose-mq.yml down -v       # 顺带删卷(磁盘上的消息也清掉)
```

### 注意

- [broker.conf](rocketmq/broker.conf) 里 `brokerIP1` 填**主机名 `rmqbroker`**(compose 的 service 名),不要填具体 IP。`brokerIP1` 是 broker 注册到 NameServer 的地址,所有客户端共用;填主机名后让两类客户端各自解析到可达目标,**切换网络后无需再改**:
  - 容器视角(如 dashboard):docker 内置 DNS 把 `rmqbroker` 解析为 broker 的桥网络 IP,走 docker 桥直连 10911(不经 vpnkit)
  - 宿主机视角(IDE 里的 deploy-server):靠宿主机 `/etc/hosts` 的 `127.0.0.1 rmqbroker`,经 docker 端口映射 10911 直连(走 loopback)
  - **前置(本机一次性)**:宿主机 `/etc/hosts` 加一行 `127.0.0.1 rmqbroker`;改完 broker.conf 后 `docker restart deploy-tool-rmqbroker` 让其用新地址重新注册
  - **不要填 LAN IP**(如 `192.168.x.x`):Wi-Fi 切网后 IP 变,producer 拿旧地址报 `send message Exception`——这正是改用主机名要根治的问题
  - **不要填 `127.0.0.1`**:dashboard 容器视角下它是容器自己,会报 `RemotingConnectException: connect to 127.0.0.1:10911 failed`
  - **不要填 `host.docker.internal`**:macOS Docker Desktop 的 vpnkit 转发 RocketMQ 协议字节时会立刻关闭 channel(broker.remoting.log 表现为 channelActive 后立刻 channelInactive,producer 报 `RemotingSendRequestException`)。注意 `nc -zv` 能通是因为只测三次握手不发应用数据,**别用 nc 误判连通性**
  - **Linux 宿主**:容器与宿主机网络互通更好,`brokerIP1` 用 `127.0.0.1` + 给依赖容器加 `extra_hosts: ["host.docker.internal:host-gateway"]` 也可,不存在 vpnkit 问题
  - 生产部署:改成 broker 节点的真实 DNS 主机名(比裸 IP 更抗迁移)
- `autoCreateTopicEnable = true` 也只用于开发,生产环境要禁用,topic 由 admin 命令显式创建
- `defaultTopicQueueNums = 8` 给顺序消息按 `deploymentRecordId` 分队列(见 [ADR-0001](../docs/adr/0001-ordering-key-deployment-record.md))预留足够空间
