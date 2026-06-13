package com.xiaozhanke.deploy.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * deploy-platform 自有的 MQ 业务配置(topic / consumer group 等),
 * 与 rocketmq-spring-boot-starter 自带的 {@code rocketmq.*} 配置正交。
 *
 * @param deployJobTopic          部署作业事务消息 Topic 名
 * @param deployJobConsumerGroup  部署作业 consumer group 名
 * @param deadLetterTopic         自定义死信 Topic 名(用业务名而非 RocketMQ
 *                                系统保留前缀 {@code %DLQ%},后者默认只读、手动投递会失败)
 * @param deadLetterConsumerGroup 死信 consumer group 名
 * @author xiaozhanke
 */
@ConfigurationProperties(prefix = "deploy-platform.mq")
public record RocketMQProperties(String deployJobTopic, String deployJobConsumerGroup,
                                 String deadLetterTopic, String deadLetterConsumerGroup) {

    public RocketMQProperties {
        if (deployJobTopic == null || deployJobTopic.isBlank()) {
            throw new IllegalArgumentException("deploy-platform.mq.deploy-job-topic must be set");
        }
        if (deployJobConsumerGroup == null || deployJobConsumerGroup.isBlank()) {
            throw new IllegalArgumentException("deploy-platform.mq.deploy-job-consumer-group must be set");
        }
        if (deadLetterTopic == null || deadLetterTopic.isBlank()) {
            throw new IllegalArgumentException("deploy-platform.mq.dead-letter-topic must be set");
        }
        if (deadLetterConsumerGroup == null || deadLetterConsumerGroup.isBlank()) {
            throw new IllegalArgumentException("deploy-platform.mq.dead-letter-consumer-group must be set");
        }
    }
}
