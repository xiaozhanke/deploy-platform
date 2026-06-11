package com.xiaozhanke.deploy.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka 审计日志业务配置(对应 MQ 方案稿场景 4、ADR-0005)。
 *
 * <p>与 RocketMQ 业务消息(场景 1/2/3/5)分工对照:审计是高吞吐数据流,走 Kafka;不要事务,
 * 发送失败落兜底文件而非回滚业务。
 *
 * @param bootstrapServers  Kafka broker 地址(单机开发为 127.0.0.1:9092)
 * @param consumerGroup     审计消费组
 * @param topic             审计 Topic 名
 * @param partitions        Topic 分区数(演示 Consumer Group 并发消费,与 listener concurrency 对齐)
 * @param sendTimeoutMillis 同步发送超时(ms);超时即视为 Kafka 抖动,转兜底文件
 * @param fallbackFile      Kafka 不可用时的本地兜底文件路径(恢复后由 replay job 回放)
 * @author xiaozhanke
 */
@ConfigurationProperties(prefix = "deploy-platform.audit")
public record KafkaAuditProperties(String bootstrapServers, String consumerGroup, String topic, int partitions,
                                   long sendTimeoutMillis, String fallbackFile) {

    public KafkaAuditProperties {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("deploy-platform.audit.bootstrap-servers must be set");
        }
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new IllegalArgumentException("deploy-platform.audit.consumer-group must be set");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("deploy-platform.audit.topic must be set");
        }
        if (partitions <= 0) {
            throw new IllegalArgumentException("deploy-platform.audit.partitions must be positive");
        }
        if (sendTimeoutMillis <= 0) {
            throw new IllegalArgumentException("deploy-platform.audit.send-timeout-millis must be positive");
        }
        if (fallbackFile == null || fallbackFile.isBlank()) {
            throw new IllegalArgumentException("deploy-platform.audit.fallback-file must be set");
        }
    }
}
