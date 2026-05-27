package com.xiaozhanke.deploy.messaging.dto;

import com.xiaozhanke.deploy.enums.JobTypeEnum;

import java.time.LocalDateTime;

/**
 * 部署作业 MQ 消息体
 *
 * <p>**不**包含 SSH 凭据等敏感信息——消费者按 jobId 重新查 DB 获取必要上下文,
 * 让 broker 上滞留的消息体最小化。
 *
 * @author xiaozhanke
 */
public record DeploymentJobMessage(
        String jobId,
        String deploymentRecordId,
        JobTypeEnum jobType,
        String clientRequestId,
        LocalDateTime createTime
) {
}
