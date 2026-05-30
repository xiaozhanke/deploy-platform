package com.xiaozhanke.deploy.messaging.consumer;

import com.xiaozhanke.deploy.core.ssh.SshOperationExecutor;
import com.xiaozhanke.deploy.enums.DeploymentStatusEnum;
import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.messaging.dto.DeploymentJobMessage;
import com.xiaozhanke.deploy.messaging.idempotent.JobAcquisitionService;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.mapper.DeploymentJobPoVoMapper;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 部署作业消费者(对应 MQ 方案稿场景 1)
 *
 * <p>消费流程:
 * <ol>
 *   <li>{@link JobAcquisitionService#acquire CAS UPDATE} 占据作业(ADR-0002)
 *   <li>按 {@code jobType} 分发到 {@link SshOperationExecutor}
 *   <li>成功:{@code status=SUCCESS} + 顺手刷新 {@link DeploymentRecord} 的运行态投影
 *   <li>失败(Phase 1 简化版):{@code status=FAILED} + ACK + 日志;完整的 ADR-0003 混合策略
 *       (短同步重试 + 自定义 DLQ)留给 Phase 2
 *   <li>无论成功失败,通过 {@code SimpMessagingTemplate} 向 {@code /topic/jobs/{recordId}} 推送状态
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
    private final DeploymentJobRepository deploymentJobRepository;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final SshOperationExecutor sshOperationExecutor;
    private final SimpMessagingTemplate messagingTemplate;
    private final DeploymentJobPoVoMapper deploymentJobPoVoMapper;

    @Override
    public void onMessage(DeploymentJobMessage msg) {
        log.info("收到部署作业消息: jobId=[{}] jobType=[{}] recordId=[{}]",
                msg.jobId(), msg.jobType(), msg.deploymentRecordId());

        if (!jobAcquisitionService.acquire(msg.jobId())) {
            return;
        }

        DeploymentJob job = deploymentJobRepository.findById(msg.jobId())
                .orElseThrow(() -> new IllegalStateException("作业不存在: " + msg.jobId()));
        // onMessage 无事务边界:acquire() 的事务已提交关闭,job 查出即为游离态(detached),
        // 其 deploymentRecord 是 @ManyToOne(LAZY) 代理,执行 SSH 时访问字段会因 Session 已关
        // 抛 LazyInitializationException。改为按 recordId 直接加载——DeploymentRecord 的
        // server/file 关联是 EAGER,findById 会 JOIN 一次性取全,游离态下也能安全读取。
        DeploymentRecord record = deploymentRecordRepository.findById(msg.deploymentRecordId())
                .orElseThrow(() -> new IllegalStateException("部署记录不存在: " + msg.deploymentRecordId()));

        try {
            switch (msg.jobType()) {
                case START -> doStart(record);
                case STOP -> doStop(record);
                case RESTART -> doRestart(record);
                case UPDATE -> doUpdate(record);
            }
            markSuccess(job);
            log.info("作业 [{}] 执行成功", job.getId());
        } catch (Exception e) {
            log.error("作业 [{}] 执行失败", job.getId(), e);
            markFailed(job, e);
        } finally {
            pushStatus(job, record.getId());
        }
    }

    @Transactional
    protected void doStart(DeploymentRecord record) {
        String processId = sshOperationExecutor.executeStart(record);
        record.setStatus(DeploymentStatusEnum.SUCCESS)
                .setRunning(true)
                .setProcessId(processId)
                .setLastStartTime(LocalDateTime.now())
                .setErrorMessage(null);
        deploymentRecordRepository.save(record);
    }

    @Transactional
    protected void doStop(DeploymentRecord record) {
        sshOperationExecutor.executeStop(record);
        record.setRunning(false)
                .setLastStopTime(LocalDateTime.now())
                .setProcessId(null);
        deploymentRecordRepository.save(record);
    }

    @Transactional
    protected void doRestart(DeploymentRecord record) {
        if (Boolean.TRUE.equals(record.getRunning())) {
            doStop(record);
        }
        doStart(record);
    }

    protected void doUpdate(DeploymentRecord record) {
        // Phase 1 暂未实现 UPDATE 的完整路径(涉及文件传输 + 残留清理);Phase 2 在 SshOperationExecutor 里补
        throw new UnsupportedOperationException("UPDATE 作业类型在 Phase 1 尚未支持");
    }

    @Transactional
    protected void markSuccess(DeploymentJob job) {
        job.setStatus(JobStatusEnum.SUCCESS)
                .setEndTime(LocalDateTime.now())
                .setErrorMessage(null);
        deploymentJobRepository.save(job);
    }

    @Transactional
    protected void markFailed(DeploymentJob job, Exception e) {
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (reason.length() > 1024) {
            reason = reason.substring(0, 1024);
        }
        job.setStatus(JobStatusEnum.FAILED)
                .setEndTime(LocalDateTime.now())
                .setErrorMessage(reason);
        deploymentJobRepository.save(job);
    }

    private void pushStatus(DeploymentJob job, String recordId) {
        try {
            messagingTemplate.convertAndSend("/topic/jobs/" + recordId, deploymentJobPoVoMapper.poToVo(job));
        } catch (Exception e) {
            log.warn("WebSocket 推送作业状态失败: jobId=[{}]", job.getId(), e);
        }
    }
}
