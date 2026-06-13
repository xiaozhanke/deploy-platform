package com.xiaozhanke.deploy.messaging.producer;

import com.xiaozhanke.deploy.messaging.config.RocketMQProperties;
import com.xiaozhanke.deploy.messaging.dto.DeploymentJobMessage;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 部署作业 MQ 生产者
 *
 * <p>发**事务消息**——RocketMQ Spring Starter 2.x 的 {@code sendMessageInTransaction} 不支持自定义
 * {@link org.apache.rocketmq.client.producer.MessageQueueSelector},事务消息只能走默认队列选择。
 * 「同一部署记录串行」并不依赖 producer 端的队列选择,而是下沉到消费端的一条 DB CAS UPDATE 兜底,
 * 与事务消息互不冲突。
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentMQProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQProperties properties;

    /**
     * 发送部署作业事务消息。
     *
     * <p>本地事务(单行 INSERT)在
     * {@link com.xiaozhanke.deploy.messaging.transaction.DeploymentTransactionListener#executeLocalTransaction}
     * 内完成;commit 后由 consumer 拿到消息再执行 SSH。
     *
     * @param pendingJob 待入库作业(由 listener 在本地事务内 save)
     * @return 发送结果(用于调用方判定半消息是否成功投递)
     */
    public SendResult sendDeploymentJob(DeploymentJob pendingJob) {
        DeploymentJobMessage payload = new DeploymentJobMessage(
                pendingJob.getId(),
                pendingJob.getDeploymentRecord().getId(),
                pendingJob.getJobType(),
                pendingJob.getClientRequestId(),
                LocalDateTime.now());
        Message<DeploymentJobMessage> message = MessageBuilder.withPayload(payload)
                .setHeader("jobId", pendingJob.getId())
                .setHeader(RocketMQHeaders.KEYS, pendingJob.getId())
                .build();
        SendResult result = rocketMQTemplate.sendMessageInTransaction(
                properties.deployJobTopic(), message, pendingJob);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            log.warn("事务消息发送状态非 SEND_OK: jobId=[{}] result=[{}]", pendingJob.getId(), result);
        } else {
            log.info("事务消息发送成功: jobId=[{}] msgId=[{}]", pendingJob.getId(), result.getMsgId());
        }
        return result;
    }
}
