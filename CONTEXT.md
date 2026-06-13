# deploy-platform

通过 Web 界面对远端主机上的 Java 后端 / 静态前端进行发布、启停、重启、版本更新的运维工具。本文件是项目术语词典，只定义概念，不写实现。

## Language

**部署记录（DeploymentRecord）**：
描述"某台主机上长期存在一个被托管的应用"的配置 + 当前运行态（部署路径、端口、PID、Running、最后启动/停止时间等）。一份记录的生命周期跨越多次启停与版本更新。
_Avoid_: 部署、部署任务（这两个词指向 [[部署作业]]，含义不同）
_UI 呈现_: 前端左栏「应用实例」页即管理本对象、「部署发布」是创建本对象的动作入口。面向用户用「实例 / 发布」、内部模型仍叫「记录」，有意不同名。

**主机（Host）**：
提供计算资源、拥有 IP 或域名网络地址、可通过 SSH 登录的底层操作系统环境（如物理机、虚拟机）。
_Avoid_: 服务器（Server）。"Server" 容易与上层运行的软件进程（如 Web Server、应用进程）混淆，故全系统统一改用 Host，其网络连接字段统一称为 `address`（而非 `host`）。

**部署作业（DeploymentJob）**：
对一份 [[部署记录]] 执行一次具体动作的可重试单元，类型为 START / STOP / RESTART / UPDATE 之一。作业有自己的 id、状态（PENDING / IN_PROGRESS / SUCCESS / FAILED / DEAD / CANCELLED）、重试次数、错误信息。**MQ 消息的主语始终是作业，不是记录。**
_Avoid_: 部署任务、部署消息、deploy task（统一叫"部署作业"）

**作业类型（JobType）**：
枚举值，目前为 `START | STOP | RESTART | UPDATE`。RESTART 在执行层等价于 STOP→START，但作为一个独立作业类型存在，以便外部（用户/调度器）表达意图、以及让幂等与重试以"一次重启"为单位。
_UPDATE 的落地_（已实现）：UPDATE 作业额外携带 `targetFileRecordId`（目标新包的文件记录 Id），与其它三种作业在 MQ 层走**完全一致**的流程——同样的事务消息、同样的消费端 CAS 串行（见 [[顺序键]]）、同样的 ADR-0003 重试/DLQ 策略。**新包的 SFTP 上传由前端在提交作业前单独完成**（`ApplicationUpdatePackage` 向导第一步经 WebSocket SFTP 推到主机），MQ 作业本身**不传大文件**，只负责"把部署记录的 fileRecord 指针换成新包 + 重启（后端 `java -jar`）/ 解压（前端 `unzip`）"——因此 MQ 不感知"半传文件"中间态。fileRecord 指针的持久化**推迟到 SSH 成功之后**：失败则作业进死信、指针保持旧值不被污染。手动 retry 同样是新建一份新 jobId 的 UPDATE 作业。

**顺序键（OrderingKey）**：
[[部署作业]]串行的维度，取值为 `deploymentRecordId`。**原设计**以它哈希选 MQ 队列（顺序消息）；**实际落地**因事务消息与自定义 `MessageQueueSelector` 互斥，改为事务消息走默认队列、消费者 `ConsumeMode.ORDERLY` + 一条 CAS UPDATE 以 `deploymentRecordId` 为互斥维度兜底（详见 [ADR-0006](docs/adr/0006-serialization-via-db-not-transactional-ordering.md)）。同一份 [[部署记录]] 的多个作业（如连续触发"停止 → 更新 → 启动"）串行执行；不同记录之间并发。**不**以 `hostId` 为顺序键 —— 一台物理机上承载多个独立应用是常态，让它们彼此排队没有业务意义。
_Why 不是 (hostId, port)_：端口属于配置，会被 UPDATE 改写；in-flight 作业的 hash 漂移会导致顺序丢失。
_兜底_：串行最终靠消费端那条 CAS UPDATE（同一 `deploymentRecordId` 同时只允许一个作业处于 IN_PROGRESS），占不到执行权即 `RECORD_BUSY` 重投；MQ 队列顺序只是尽力而为，重试 / rebalance 期间不保证严格"消费"顺序。

**本地事务（事务消息语境）**：
RocketMQ 事务消息的 `executeLocalTransaction` 回调内**仅**执行 `INSERT INTO deployment_job(id, type, deployment_record_id, status=PENDING, ...)` 一行数据库操作，随后 commit 半消息。回查（`checkLocalTransaction`）逻辑为 `SELECT FROM deployment_job WHERE id = ?` 存在则 COMMIT、不存在则 ROLLBACK。**SSH 远程命令不属于本地事务**——它由消费者在拿到消息之后执行，本地事务必须在秒级可判定 commit/rollback，否则破坏回查机制。[[部署记录]] 上的运行态投影（`running` / `processId` / `lastStartTime`）由消费者在作业成功后顺手刷新，不在本地事务内。

**幂等键 / 消费者首行（IdempotencyKey）**：
消费者收到消息后，使用 `jobId` 作为业务唯一键，通过一条 CAS UPDATE 占据作业：

```sql
UPDATE deployment_job SET status = 'IN_PROGRESS' WHERE id = :jobId AND status = 'PENDING'
```

受影响行数为 0 即代表"已被前一次处理（或正在处理）"，直接 ACK 返回成功；为 1 才继续执行 SSH。**不**使用 `messageId` 做去重——事务消息半提交/commit、消费者重投、producer 重试等场景下 messageId 会变化，Caffeine/Redis 去重表会漏过同一业务的重复消息。

**客户端请求 ID（ClientRequestId）**：
HTTP 入口的"操作意图"标识，由前端在每次按钮点击时生成（UUID v4），随请求传到后端。后端在创建 [[部署作业]] 时把 `(deployment_record_id, job_type, client_request_id)` 作为唯一索引：第 2、3 次请求触发 `DataIntegrityViolationException`，由 Controller 转译为"返回已存在 jobId"，**不**生成新作业、**不**发新消息。前端按钮防抖是第一道墙、后端唯一索引是第二道墙。

**取消语义（CancellationSemantics）**：
仅对**延迟作业**（场景 3:定时部署 / 5 分钟后重启）有意义。RocketMQ 延迟消息发出后**不支持撤回**——本项目把"取消"下沉到业务状态机:用户在 UI 撤销时,HTTP 接口把 `deployment_job.status` 从 PENDING 直接转入 CANCELLED 终态;延迟消息到期照常触达消费端,但消费首行的 CAS UPDATE `WHERE status='PENDING'` 不命中,直接 ACK 不执行 SSH。**转换路径**:PENDING → CANCELLED(只允许这一条);IN_PROGRESS 之后不可撤,撤销 HTTP 请求 reject。**长延迟接力链**(详见 [ADR-0004](docs/adr/0004-delayed-message-cancellation.md))每次续发前都查 status,CANCELLED 则链条终止。

**主机在线性（HostLiveness）**：
对一台 Host 执行 `echo 1` 的 SSH 连通性检测结果。检测成功（连通且在超时 `1500ms` 内响应）则该主机视为**在线**；与该主机上所有 DeploymentRecord 的 `running` 状态无关。控制台 KPI「在线主机数」展示的是本指标。
_Avoid_: 用应用进程存活率代替主机在线性（两者分属不同 KPI，职责不重叠）。
_调度独立性_: HostLiveness 检测（60s 常驻）与资源监控采样（5s 惰性）是**两个独立定时任务**，且**不共享连接**：HostLiveness 用短连接（每周期 connect→执行→断开，与部署作业同款，60s 一次握手成本可忽略），常驻运行；资源采样独占一个 per-host JSch Session Pool（池化的理由仅在于 5s 高频），订阅者为 0 时**整池拆光**休眠，实现「0 采样开销」。两者职责与生命周期完全解耦，互不影响。

**应用实例存活性（InstanceLiveness）**：
对一份 [[部署记录]] 的 `processId` 执行 `ps -p PID` 探测得到的**瞬时观测态**（ALIVE / DEAD / 未知）。必须与 `running` 投影严格区分：`running` 是「最后一次成功作业留下的**意图投影**」（消费者在作业成功后写库），InstanceLiveness 是「此刻进程是否真的活着」（定时探测得出）。**探测结果只进内存缓存、绝不持久化**——它每分钟变、无历史价值，写库会与消费者抢 `running` 列、并刷脏 `updateUser` 等审计字段。
_三态派生_: UI 的 ✅运行中 / 🔴已停止 / ⚠️状态未知 在**读取时**由 `(running, processId, 探测缓存)` 派生，不落任何新字段：`running=false`→已停止；`running=true & processId=null`→状态未知；`running=true & 探测=ALIVE`→运行中；`running=true & 探测=DEAD`→已停止（崩溃）；`running=true & 探测缺失/首轮`→状态未知。KPI「运行中实例」仅计 `running=true 且 探测=ALIVE`。
_Avoid_: 把 InstanceLiveness 与 `running` 混为一谈；把探测结果写回 DB。

**文件资源（FileResource）**：
在控制台托管和发布的前端静态包（`.zip`）或后端应用包（`.jar`）等具体制品。在 UI 层表现为「文件资源」管理列表。

**死信队列（DeadLetterQueue / DLQ）**：
在执行过程中连续失败并耗尽重试次数后被隔离的[[部署作业]]。进入死信队列的作业已属于终态，不再自动重试，必须由运维人员人工点击重试。

**审计日志（AuditLog）**：
以合规和审计为目的所记录的用户操作行为流水。记录每次操作的操作人、操作类型、目标资源以及最终的成功与失败状态。

## Flagged ambiguities

（全部已 resolve:~~UPDATE 类作业的重试语义~~ → 见 [[作业类型]];~~顺序消息与失败重试的冲突~~ → [ADR-0003](docs/adr/0003-retry-strategy-with-ordered-messages.md);~~死信处理流程~~ → [ADR-0003](docs/adr/0003-retry-strategy-with-ordered-messages.md);~~场景 3 延迟消息的"取消"语义~~ → [ADR-0004](docs/adr/0004-delayed-message-cancellation.md) / 见 [[取消语义]];~~场景 4 Kafka 审计是否需要事务一致性~~ → [ADR-0005](docs/adr/0005-audit-log-non-transactional.md)。）

## Example dialogue

> **开发**：用户点了"重启"按钮，我是更新部署记录还是新建一条？
> **领域**：都不是。你**新建一个部署作业**，type=RESTART，关联到那份部署记录的 id。作业落库后发 MQ，消费者拿到作业去跑 SSH，跑完回写**作业**的状态。部署记录本身的 `running / processId / lastStartTime` 是在作业成功后由消费者顺手更新的"当前态投影"，而不是作业的主表。
>
> **开发**：那一台机器上有 3 个应用，同时被点重启，会怎样？
> **领域**：那是 3 个独立的部署记录，各自生成一个 RESTART 作业。它们之间会不会被串行化、还是并发跑，取决于我们怎么选顺序消息的顺序键 —— 这个还没拍板，见上面 Flagged ambiguities。
