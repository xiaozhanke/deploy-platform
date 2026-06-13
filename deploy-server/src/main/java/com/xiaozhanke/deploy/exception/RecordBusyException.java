package com.xiaozhanke.deploy.exception;

/**
 * 部署记录串行占用异常。
 *
 * <p>消费者收到某作业消息时,若同一份 {@code deploymentRecord} 上已有另一作业处于
 * {@code IN_PROGRESS},则当前作业暂不可执行。抛出本异常让 RocketMQ {@code ORDERLY} 消费容器
 * 把当前消息 {@code SUSPEND} 后稍后重投,直到前一作业完成 —— 借此实现"同一记录串行、不同记录并发"。
 *
 * <p>它表达的是"稍后再试",**不是**作业失败,因此**不**进入重试/死信路径
 * (consumer 必须在执行逻辑之前、失败捕获之外处理它)。
 *
 * @author xiaozhanke
 */
public class RecordBusyException extends RuntimeException {
    public RecordBusyException(String message) {
        super(message);
    }
}
