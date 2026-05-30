package com.xiaozhanke.deploy.messaging.dto;

import com.xiaozhanke.deploy.enums.JobTypeEnum;

import java.time.LocalDateTime;

/**
 * 死信队列消息体(投递到自定义死信 Topic {@code deploy-job-dlq})。
 *
 * <p>由 {@code DeploymentConsumer} 在作业进入死信时构造,{@code DeadLetterConsumer} 消费后落
 * {@code dead_letter_message} 表。{@code originalPayload} 为原始 {@link DeploymentJobMessage}
 * 的 JSON 文本,保留死信的原始上下文。
 *
 * @author xiaozhanke
 */
public record DeadLetterMqMessage(
        String jobId,
        String deploymentRecordId,
        JobTypeEnum jobType,
        String errorMessage,
        String originalPayload,
        LocalDateTime failedAt
) {
}
