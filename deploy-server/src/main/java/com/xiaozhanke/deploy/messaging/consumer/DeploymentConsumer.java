package com.xiaozhanke.deploy.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhanke.deploy.exception.RecordBusyException;
import com.xiaozhanke.deploy.messaging.dto.DeploymentJobMessage;
import com.xiaozhanke.deploy.messaging.idempotent.AcquireResult;
import com.xiaozhanke.deploy.messaging.idempotent.JobAcquisitionService;
import com.xiaozhanke.deploy.messaging.idempotent.JobExecutionDelegate;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 部署作业消费者(对应 MQ 方案稿场景 1/2/5)。
 *
 * <p>消费流程:
 * <ol>
 *   <li>{@link JobAcquisitionService#acquire} 一条 CAS 同时做消费幂等(ADR-0002)与记录串行
 *       (ADR-0006)</li>
 *   <li>占据成功后委托 {@link JobExecutionDelegate} 执行 SSH 分发 + 混合重试 + 死信投递</li>
 * </ol>
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${deploy-tool.mq.deploy-job-topic}",
        consumerGroup = "${deploy-tool.mq.deploy-job-consumer-group}",
        consumeMode = ConsumeMode.ORDERLY,
        messageModel = MessageModel.CLUSTERING
)
@RequiredArgsConstructor
public class DeploymentConsumer implements RocketMQListener<DeploymentJobMessage> {

    private final JobAcquisitionService jobAcquisitionService;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final JobExecutionDelegate executionDelegate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(DeploymentJobMessage msg) {
        log.info("收到部署作业消息: jobId=[{}] jobType=[{}] recordId=[{}]",
                msg.jobId(), msg.jobType(), msg.deploymentRecordId());

        AcquireResult acquireResult = jobAcquisitionService.acquire(msg.jobId(), msg.deploymentRecordId());
        switch (acquireResult) {
            case ALREADY_HANDLED -> {
                return;
            }
            case RECORD_BUSY -> throw new RecordBusyException(String.format(
                    "记录 [%s] 有在途作业,作业 [%s] 稍后重试", msg.deploymentRecordId(), msg.jobId()));
            case ACQUIRED -> { /* 占据成功,继续执行 */ }
        }

        DeploymentRecord record = deploymentRecordRepository.findById(msg.deploymentRecordId())
                .orElseThrow(() -> new IllegalStateException("部署记录不存在: " + msg.deploymentRecordId()));

        String payload;
        try {
            payload = objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            log.error("序列化部署作业消息失败,使用降级 payload: jobId=[{}]", msg.jobId(), e);
            payload = "{\"jobId\":\"" + msg.jobId() + "\",\"_serialization_error\":true}";
            // jobId 为系统生成的 UUID,不含 JSON 特殊字符,字符串拼接安全
        }
        executionDelegate.executeWithRetryAndDeadLetter(
                msg.jobId(), msg.deploymentRecordId(), msg.jobType(), record, payload);
    }
}
