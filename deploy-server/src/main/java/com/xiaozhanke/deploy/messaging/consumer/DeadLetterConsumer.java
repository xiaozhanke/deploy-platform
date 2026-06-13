package com.xiaozhanke.deploy.messaging.consumer;

import com.xiaozhanke.deploy.messaging.dto.DeadLetterMqMessage;
import com.xiaozhanke.deploy.model.entity.DeadLetterMessage;
import com.xiaozhanke.deploy.repository.DeadLetterMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者。
 *
 * <p>消费自定义死信 Topic {@code deploy-job-dlq}(由 {@code DeploymentConsumer} 在作业进入死信时
 * 显式投递),把死信落到 {@code dead_letter_message} 表供前端查看与人工重试。死信不需要顺序,故用
 * 默认的并发消费(CONCURRENTLY);按 {@code jobId} 去重避免 MQ 重投造成重复死信记录。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${deploy-platform.mq.dead-letter-topic}",
        consumerGroup = "${deploy-platform.mq.dead-letter-consumer-group}",
        messageModel = MessageModel.CLUSTERING
)
@RequiredArgsConstructor
public class DeadLetterConsumer implements RocketMQListener<DeadLetterMqMessage> {

    private final DeadLetterMessageRepository deadLetterMessageRepository;

    @Override
    public void onMessage(DeadLetterMqMessage msg) {
        log.warn("收到死信: jobId=[{}] type=[{}] recordId=[{}] reason=[{}]",
                msg.jobId(), msg.jobType(), msg.deploymentRecordId(), msg.errorMessage());

        if (deadLetterMessageRepository.existsByJobId(msg.jobId())) {
            log.info("死信 jobId=[{}] 已落库,跳过重复投递", msg.jobId());
            return;
        }

        DeadLetterMessage entity = new DeadLetterMessage()
                .setJobId(msg.jobId())
                .setDeploymentRecordId(msg.deploymentRecordId())
                .setJobType(msg.jobType())
                .setErrorMessage(msg.errorMessage())
                .setOriginalPayload(msg.originalPayload())
                .setDeadLetteredAt(msg.failedAt())
                .setRetried(false);
        try {
            deadLetterMessageRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // existsByJobId 与 save 非原子,并发重投下可能撞唯一索引 uk_dead_letter_message_job_id;
            // 视为已落库、幂等跳过(ACK),避免无谓重投
            log.info("死信 jobId=[{}] 并发落库撞唯一索引,幂等跳过", msg.jobId());
        }
    }
}
