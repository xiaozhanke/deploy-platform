package com.xiaozhanke.deploy.messaging.consumer;

import com.xiaozhanke.deploy.messaging.dto.AuditLogMessage;
import com.xiaozhanke.deploy.model.entity.AuditLog;
import com.xiaozhanke.deploy.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 审计日志 Kafka 消费者(对应 MQ 方案稿场景 4)。
 *
 * <p>Consumer Group 并发消费(并发度 = 分区数,见 {@code KafkaConfig}),把审计消息落 {@code audit_log} 表。
 * 容器用 {@code AckMode.RECORD} 在每条处理成功后提交位移——At-Least-Once:崩溃/重平衡可能重放,审计可容忍
 * 少量重复(不做幂等,合规要求是"不漏"而非"不重")。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogConsumer {

    private final AuditLogRepository auditLogRepository;

    @KafkaListener(
            topics = "${deploy-platform.audit.topic}",
            groupId = "${deploy-platform.audit.consumer-group}",
            containerFactory = "auditKafkaListenerContainerFactory"
    )
    public void onMessage(AuditLogMessage message) {
        AuditLog auditLog = new AuditLog()
                .setOperator(message.operator())
                .setOperationType(message.operationType())
                .setTarget(message.target())
                .setDescription(message.description())
                .setOutcome(message.outcome())
                .setErrorMessage(message.errorMessage())
                .setClientIp(message.clientIp())
                .setOperationTime(message.operationTime());
        auditLogRepository.save(auditLog);
        log.debug("审计落库: operator=[{}] type=[{}] outcome=[{}]",
                message.operator(), message.operationType(), message.outcome());
    }
}
