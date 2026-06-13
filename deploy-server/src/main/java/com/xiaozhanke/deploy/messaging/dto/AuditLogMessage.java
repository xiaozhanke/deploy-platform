package com.xiaozhanke.deploy.messaging.dto;

import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.enums.AuditOutcomeEnum;

import java.time.LocalDateTime;

/**
 * 审计日志 Kafka 消息体。
 *
 * <p>由 {@code AuditAspect} 在请求线程构造(此时 SecurityContext 仍在,能拿到真实
 * {@link #operator} 与 {@link #clientIp}),经 {@code AuditLogProducer} 发往 Kafka;消费端
 * {@code AuditLogConsumer} 据此落 {@code audit_log} 表。Kafka 抖动时由 {@code AuditFallbackWriter}
 * 原样落兜底文件,故本体必须是自包含的不可变快照。
 *
 * @author xiaozhanke
 */
public record AuditLogMessage(
        String operator,
        AuditOperationTypeEnum operationType,
        String target,
        String description,
        AuditOutcomeEnum outcome,
        String errorMessage,
        String clientIp,
        LocalDateTime operationTime
) {
}
