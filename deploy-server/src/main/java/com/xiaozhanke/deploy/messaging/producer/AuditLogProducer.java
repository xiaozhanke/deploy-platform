package com.xiaozhanke.deploy.messaging.producer;

import com.xiaozhanke.deploy.audit.AuditFallbackWriter;
import com.xiaozhanke.deploy.messaging.config.KafkaAuditProperties;
import com.xiaozhanke.deploy.messaging.dto.AuditLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 审计日志 Kafka 生产者(对应 MQ 方案稿场景 4、ADR-0005)。
 *
 * <p>同步发送(等 {@code acks=all} 确认,超时 {@code send-timeout-millis})。发送失败<b>不回滚业务</b>、
 * 不阻断主响应,而是落兜底文件 + 告警——审计是旁路,业务 SLA 不被 Kafka 抖动牵连。这正是与 RocketMQ
 * 业务消息(要事务一致性)的核心分工差异。
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogProducer {

    private final KafkaTemplate<String, AuditLogMessage> auditKafkaTemplate;
    private final KafkaAuditProperties properties;
    private final AuditFallbackWriter fallbackWriter;

    /**
     * 同步发送审计消息;失败转兜底文件,绝不抛出影响业务线程。
     */
    public void send(AuditLogMessage message) {
        try {
            auditKafkaTemplate.send(properties.topic(), message)
                    .get(properties.sendTimeoutMillis(), TimeUnit.MILLISECONDS);
            log.debug("审计消息已发送 Kafka: operator=[{}] type=[{}] outcome=[{}]",
                    message.operator(), message.operationType(), message.outcome());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fallbackWriter.write(message);
            log.warn("审计发送被中断,已落兜底文件: type=[{}]", message.operationType());
        } catch (Exception e) {
            // ExecutionException / TimeoutException:Kafka 抖动 → 兜底 + 告警,不影响业务
            fallbackWriter.write(message);
            log.warn("审计发送 Kafka 失败,已落兜底文件: type=[{}] reason=[{}]",
                    message.operationType(), e.getMessage());
        }
    }
}
