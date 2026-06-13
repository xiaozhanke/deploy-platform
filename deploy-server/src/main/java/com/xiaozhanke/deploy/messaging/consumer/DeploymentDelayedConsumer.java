package com.xiaozhanke.deploy.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.exception.RecordBusyException;
import com.xiaozhanke.deploy.messaging.dto.DelayedJobMessage;
import com.xiaozhanke.deploy.messaging.idempotent.AcquireResult;
import com.xiaozhanke.deploy.messaging.idempotent.JobAcquisitionService;
import com.xiaozhanke.deploy.messaging.idempotent.JobExecutionDelegate;
import com.xiaozhanke.deploy.messaging.producer.DeploymentDelayedProducer;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 延迟部署作业消费者。
 *
 * <p>本类只负责长延迟接力链管理(status 检查/CANCELLED 短路/续发链节);
 * 到期执行(SSH 分发+混合重试+死信投递)委托给 {@link JobExecutionDelegate},
 * 消除与 {@link DeploymentConsumer} 之间 ~80 行重复代码。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${deploy-platform.mq.delayed-topic:deploy-job-delayed}",
        consumerGroup = "${deploy-platform.mq.delayed-consumer-group:deploy-job-delayed-consumer}",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING
)
@RequiredArgsConstructor
public class DeploymentDelayedConsumer implements RocketMQListener<DelayedJobMessage> {

    /**
     * 接力链最大跳数(防御性保护,24h/2h=12 跳,上限 200 足够覆盖 16 天延迟)
     */
    private static final int MAX_RELAY_HOPS = 200;

    private final JobAcquisitionService jobAcquisitionService;
    private final JobExecutionDelegate executionDelegate;
    private final DeploymentJobRepository deploymentJobRepository;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final DeploymentDelayedProducer delayedProducer;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(DelayedJobMessage msg) {
        log.info("收到延迟作业消息: jobId=[{}] hop=[{}] 剩余=[{}s] executeAt=[{}]",
                msg.jobId(), msg.relayHop(), msg.remainingDelaySeconds(), msg.executeAt());

        // 第一步:检查是否已取消(消息不可靠撤回,业务状态机做可靠仲裁)
        JobStatusEnum status = deploymentJobRepository.findById(msg.jobId())
                .map(DeploymentJob::getStatus)
                .orElse(null);
        if (status == JobStatusEnum.CANCELLED) {
            log.info("延迟作业 [{}] 已被用户撤销,链条终止", msg.jobId());
            return;
        }
        if (status != JobStatusEnum.PENDING) {
            log.info("延迟作业 [{}] 状态为 [{}],非 PENDING,跳过", msg.jobId(), status);
            return;
        }

        // 第二步:接力链续发(延迟未到期)
        if (msg.remainingDelaySeconds() > 0L) {
            relayOrDeadLetter(msg);
            return;
        }

        // 第三步:到期执行——委托给共享执行组件
        AcquireResult acquireResult = jobAcquisitionService.acquire(msg.jobId(), msg.deploymentRecordId());
        switch (acquireResult) {
            case ALREADY_HANDLED -> {
                return;
            }
            case RECORD_BUSY -> throw new RecordBusyException(String.format(
                    "记录 [%s] 有在途作业,延迟作业 [%s] 稍后重试",
                    msg.deploymentRecordId(), msg.jobId()));
            case ACQUIRED -> { /* 占据成功,继续执行 */ }
        }

        DeploymentRecord record = deploymentRecordRepository.findById(msg.deploymentRecordId())
                .orElseThrow(() -> new IllegalStateException("部署记录不存在: " + msg.deploymentRecordId()));

        String payload;
        try {
            payload = objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            throw new IllegalStateException("序列化延迟作业消息失败: " + msg.jobId(), e);
        }
        executionDelegate.executeWithRetryAndDeadLetter(
                msg.jobId(), msg.deploymentRecordId(), msg.jobType(), record, payload);
    }

    /**
     * 接力链续发或超限转死信。
     */
    private void relayOrDeadLetter(DelayedJobMessage msg) {
        if (msg.relayHop() >= MAX_RELAY_HOPS) {
            log.error("延迟作业 [{}] 接力跳数 [{}] 超过上限 [{}],转入死信",
                    msg.jobId(), msg.relayHop(), MAX_RELAY_HOPS);
            deadLetterViaDelegate(msg, "接力链超过最大跳数 " + MAX_RELAY_HOPS);
            return;
        }
        long nextHop = Math.min(msg.remainingDelaySeconds(),
                DeploymentDelayedProducer.MAX_SINGLE_HOP_SECONDS);
        log.info("延迟作业 [{}] 剩余 [{}s],续发下段链节 [{}s] hop=[{}]",
                msg.jobId(), msg.remainingDelaySeconds(), nextHop, msg.relayHop() + 1);
        try {
            delayedProducer.sendNextHop(msg, nextHop);
        } catch (Exception e) {
            log.error("延迟作业 [{}] 接力链第 [{}] 跳续发失败,转入死信: {}",
                    msg.jobId(), msg.relayHop(), e.getMessage());
            deadLetterViaDelegate(msg, "接力链第 " + msg.relayHop() + " 跳续发失败: " + e.getMessage());
        }
    }

    /**
     * 接力链阶段的死信处理,委托给共享执行组件的 deadLetterDirect(不经 SSH 重试)。
     */
    private void deadLetterViaDelegate(DelayedJobMessage msg, String reason) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            log.error("序列化延迟作业消息失败,无法投递死信: jobId=[{}]", msg.jobId(), e);
            return;
        }
        executionDelegate.deadLetterDirect(msg.jobId(), msg.deploymentRecordId(),
                msg.jobType(), payload, reason);
    }
}
