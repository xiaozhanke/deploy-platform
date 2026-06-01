package com.xiaozhanke.deploy.messaging.dto;

import com.xiaozhanke.deploy.enums.JobTypeEnum;

import java.time.LocalDateTime;

/**
 * 延迟部署作业 MQ 消息体(对应 MQ 方案稿场景 3、ADR-0004)。
 *
 * <p>与 {@link DeploymentJobMessage} 的区别在于携带了 {@link #executeAt} 和
 * {@link #remainingDelaySeconds},供长延迟接力链使用——consumer 每次收到后根据剩余时长
 * 决定是续发下一段链节还是执行 SSH。
 *
 * @author xiaozhanke
 */
public record DelayedJobMessage(
        String jobId,
        String deploymentRecordId,
        JobTypeEnum jobType,
        String clientRequestId,
        /** 用户指定的期望执行时间(用于日志与审计) */
        LocalDateTime executeAt,
        /** 剩余延迟秒数(接力链每次消费后递减;≤0 表示到期可执行) */
        long remainingDelaySeconds,
        /** 当前是第几段链节(从 0 开始;首段为 0) */
        int relayHop,
        LocalDateTime createTime
) {
}
