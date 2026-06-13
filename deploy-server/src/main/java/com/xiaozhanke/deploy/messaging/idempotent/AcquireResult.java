package com.xiaozhanke.deploy.messaging.idempotent;

/**
 * 消费者占据作业的结果。
 *
 * <p>一条 CAS UPDATE 同时承载两层语义,affected rows 与作业当前状态共同决定落到哪个分支:
 *
 * @author xiaozhanke
 */
public enum AcquireResult {
    /**
     * 占据成功:本作业由 PENDING 经 CAS 推进到 IN_PROGRESS,且所属记录此刻无其他在途作业,可执行 SSH。
     */
    ACQUIRED,

    /**
     * 已被前一次处理(或已取消):作业状态已不是 PENDING,重复投递直接 ACK 跳过(消费幂等)。
     */
    ALREADY_HANDLED,

    /**
     * 记录被占:本作业仍为 PENDING,但同一 {@code deploymentRecord} 上已有作业处于 IN_PROGRESS。
     * 需让消息稍后重投以保证同一记录串行,**不**视为失败。
     */
    RECORD_BUSY
}
