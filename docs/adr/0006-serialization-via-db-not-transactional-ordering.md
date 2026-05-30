# 同一记录的串行靠消费端 DB CAS 兜底,不依赖事务消息的队列顺序

场景 2 要求:同一份[[部署记录]]的连续作业(STOP/START/RESTART)必须串行,不同记录之间并发(见 [ADR-0001](0001-ordering-key-deployment-record.md) 的顺序键)。直觉做法是用 RocketMQ 顺序消息,producer 端按 `deploymentRecordId` 哈希选队列(`MessageQueueSelector`)。但场景 1 已落地的**事务消息**与之冲突:`RocketMQTemplate.sendMessageInTransaction(...)` **不支持**传入自定义 `MessageQueueSelector`,事务消息只能走默认队列选择。两者二选一。

## Considered Options

- **放弃事务消息、改用顺序消息(`syncSendOrderly` + 选择器)**:能拿到 producer 端的队列顺序,但牺牲了场景 1「作业入库 + 消息发送」的原子性(事务消息的核心卖点),需要回退已完成的 Phase 1 并引入本地消息表补偿,代价过大。
- **两段式(事务消息入库 + 再发一条顺序消息驱动执行)**:链路凭空多一跳、多一个 topic,作品集项目不值得。
- **保留事务消息 + 消费端 DB 串行兜底(选定)**:事务消息继续走默认队列分布;消费者维持 `ConsumeMode.ORDERLY`,并在占据作业的那条 CAS UPDATE 里**附加一个 record 维度的互斥条件** —— 同一 `deploymentRecordId` 同时只允许一个作业处于 `IN_PROGRESS`。占不到执行权(`RECORD_BUSY`)时抛 `RecordBusyException`,由 ORDERLY 容器 `SUSPEND` 后稍后重投,直到前一作业完成。

## Consequences

- 占据 SQL 用一条 native CAS,在 `WHERE` 里以**派生表**自查 `deployment_job` 是否存在同记录的 `IN_PROGRESS` 行:

  ```sql
  UPDATE deployment_job SET status='IN_PROGRESS', start_time=:now
  WHERE id=:jobId AND status='PENDING'
    AND NOT EXISTS (SELECT 1 FROM (
        SELECT id FROM deployment_job
        WHERE deployment_record_id=:recordId AND status='IN_PROGRESS') busy)
  ```

  派生表 `(SELECT ...) busy` 绕开 MySQL「UPDATE 时 WHERE 子查询不能直接引用被改表」的限制。这条 SQL 同时承载 ADR-0002 的消费幂等(`WHERE status='PENDING'`)与本 ADR 的记录串行(`NOT EXISTS ...`)。

- 保证的是「同一记录不并发」这一**底线**;投递顺序由 MQ 尽力而为,rebalance / 重投期间**不**硬保证严格消费顺序 —— 与 [ADR-0001](0001-ordering-key-deployment-record.md) 末尾「顺序消息只保证投递顺序,消费者必须用 `deploymentRecordId` 做行锁/乐观版本号兜底」的声明一致。前端按钮在作业 `IN_PROGRESS` 期间应禁用,进一步降低乱序概率。

- **不**在 `deployment_record` 上引入 `active_job_id` 之类的显式锁字段:那会带来「consumer 崩溃后锁悬挂、需要超时清理」的运维负担。CAS 以作业自身状态为锚,执行结束(SUCCESS/DEAD)即自然释放记录,无悬挂风险。

- 代价:前一作业长时间执行时,后一作业的消息会被 ORDERLY 反复 `SUSPEND` 重投,刷一些日志。开发环境可接受,不额外引入限流。

- 面试叙事:能讲清「事务消息与顺序消息为何互斥」「为什么把顺序保证下沉到数据库而非依赖 MQ 队列」「一条 CAS 如何同时做幂等与串行、派生表怎么绕开 MySQL 自引用限制」。
