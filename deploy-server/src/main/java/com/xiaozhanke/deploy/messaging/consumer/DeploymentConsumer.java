package com.xiaozhanke.deploy.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhanke.deploy.core.ssh.SshOperationExecutor;
import com.xiaozhanke.deploy.exception.JobFailureException;
import com.xiaozhanke.deploy.exception.RecordBusyException;
import com.xiaozhanke.deploy.exception.SshTransientException;
import com.xiaozhanke.deploy.messaging.config.RocketMQProperties;
import com.xiaozhanke.deploy.messaging.dto.DeadLetterMqMessage;
import com.xiaozhanke.deploy.messaging.dto.DeploymentJobMessage;
import com.xiaozhanke.deploy.messaging.idempotent.AcquireResult;
import com.xiaozhanke.deploy.messaging.idempotent.JobAcquisitionService;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.mapper.DeploymentJobPoVoMapper;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.service.DeploymentJobExecutionService;
import com.xiaozhanke.deploy.util.ErrorMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 部署作业消费者(对应 MQ 方案稿场景 1/2/5)。
 *
 * <p>消费流程:
 * <ol>
 *   <li>{@link JobAcquisitionService#acquire} 一条 CAS 同时做消费幂等(ADR-0002)与记录串行
 *       (ADR-0006):{@code ALREADY_HANDLED} 直接 ACK、{@code RECORD_BUSY} 抛
 *       {@link RecordBusyException} 让 ORDERLY 稍后重投、{@code ACQUIRED} 继续执行。</li>
 *   <li>按 {@code jobType} 分发到 {@link SshOperationExecutor} 执行 SSH(不在事务内)。</li>
 *   <li>混合重试策略(ADR-0003):{@link SshTransientException} 瞬时故障短同步重试 N 次(对 MQ 始终
 *       ACK,顺序不破);业务失败({@link JobFailureException} 等)或重试耗尽 → 作业置 DEAD +
 *       显式投递自定义死信队列 + ACK。</li>
 *   <li>状态写入由 {@link DeploymentJobExecutionService} 在短事务内完成;每个终态通过
 *       {@code SimpMessagingTemplate} 向 {@code /topic/jobs/{recordId}} 推送最新作业状态。</li>
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

    /**
     * 瞬时故障的最大同步尝试次数(含首次)。超过即转死信。
     */
    private static final int MAX_TRANSIENT_ATTEMPTS = 3;

    /**
     * 瞬时故障重试的线性退避基数(毫秒),实际退避 = base * 已重试次数。
     */
    private static final long BACKOFF_BASE_MILLIS = 500L;

    private final JobAcquisitionService jobAcquisitionService;
    private final DeploymentJobRepository deploymentJobRepository;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final SshOperationExecutor sshOperationExecutor;
    private final DeploymentJobExecutionService executionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DeploymentJobPoVoMapper deploymentJobPoVoMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(DeploymentJobMessage msg) {
        log.info("收到部署作业消息: jobId=[{}] jobType=[{}] recordId=[{}]",
                msg.jobId(), msg.jobType(), msg.deploymentRecordId());

        AcquireResult acquireResult = jobAcquisitionService.acquire(msg.jobId(), msg.deploymentRecordId());
        switch (acquireResult) {
            // 重复投递(或已取消):直接 ACK 跳过
            case ALREADY_HANDLED -> {
                return;
            }
            // 同一记录有在途作业:抛出让 ORDERLY 容器 SUSPEND 后稍后重投,实现记录级串行(ADR-0006)
            case RECORD_BUSY -> throw new RecordBusyException(String.format(
                    "记录 [%s] 有在途作业,作业 [%s] 稍后重试", msg.deploymentRecordId(), msg.jobId()));
            case ACQUIRED -> {
                // 占据成功,继续执行
            }
        }

        // onMessage 无事务边界:record 的 server/file 关联是 EAGER,findById 会 JOIN 一次性取全,
        // 游离态下执行 SSH 也能安全读取(状态写入交给 executionService 在各自短事务内做)。
        DeploymentRecord record = deploymentRecordRepository.findById(msg.deploymentRecordId())
                .orElseThrow(() -> new IllegalStateException("部署记录不存在: " + msg.deploymentRecordId()));

        executeWithRetryAndDeadLetter(msg, record);
    }

    /**
     * 混合重试 + 死信(ADR-0003)。瞬时故障短重试,业务失败/重试耗尽转死信;两种终态对 MQ 都 ACK。
     */
    private void executeWithRetryAndDeadLetter(DeploymentJobMessage msg, DeploymentRecord record) {
        int retryCount = 0;
        while (true) {
            try {
                dispatch(msg, record);
                pushStatus(msg.jobId(), msg.deploymentRecordId());
                return;
            } catch (SshTransientException e) {
                retryCount++;
                if (retryCount < MAX_TRANSIENT_ATTEMPTS) {
                    log.warn("作业 [{}] 第 {} 次瞬时故障,退避后重试: {}", msg.jobId(), retryCount, e.getMessage());
                    backoff(retryCount);
                    continue;
                }
                deadLetter(msg, retryCount, "瞬时故障重试 " + retryCount + " 次仍失败: " + e.getMessage());
                return;
            } catch (Exception e) {
                // 业务失败(JobFailureException 等)重试无益,立即转死信
                deadLetter(msg, retryCount, e.getMessage());
                return;
            }
        }
    }

    /**
     * 按作业类型执行 SSH,成功后由 executionService 在短事务内推进状态。SSH 调用本身不在事务内。
     */
    private void dispatch(DeploymentJobMessage msg, DeploymentRecord record) {
        switch (msg.jobType()) {
            case START -> {
                String processId = sshOperationExecutor.executeStart(record);
                executionService.markStartSuccess(msg.jobId(), msg.deploymentRecordId(), processId);
            }
            case STOP -> {
                sshOperationExecutor.executeStop(record);
                executionService.markStopSuccess(msg.jobId(), msg.deploymentRecordId());
            }
            case RESTART -> {
                if (Boolean.TRUE.equals(record.getRunning())) {
                    sshOperationExecutor.executeStop(record);
                }
                String processId = sshOperationExecutor.executeStart(record);
                executionService.markStartSuccess(msg.jobId(), msg.deploymentRecordId(), processId);
            }
            // UPDATE 涉及文件传输与残留清理,本批未支持:作为业务失败直接转死信而非反复重试
            case UPDATE -> throw new JobFailureException("UPDATE 作业类型暂未支持");
        }
        log.info("作业 [{}] 执行成功", msg.jobId());
    }

    /**
     * 作业进入死信:置 DEAD + 显式投递自定义死信队列(由 DeadLetterConsumer 落库)+ 推送状态。
     */
    private void deadLetter(DeploymentJobMessage msg, int retryCount, String reason) {
        log.error("作业 [{}] 进入死信: {}", msg.jobId(), reason);
        executionService.markDead(msg.jobId(), reason, retryCount);
        try {
            String payload = objectMapper.writeValueAsString(msg);
            DeadLetterMqMessage deadLetterMqMessage = new DeadLetterMqMessage(msg.jobId(), msg.deploymentRecordId(),
                    msg.jobType(), ErrorMessageUtils.truncate(reason), payload, LocalDateTime.now());
            rocketMQTemplate.syncSend(properties.deadLetterTopic(), deadLetterMqMessage);
        } catch (Exception e) {
            // 死信投递失败不回滚、不影响 ACK——作业已置 DEAD(主表是真相),死信表可能缺这条
            log.error("投递死信队列失败: jobId=[{}]", msg.jobId(), e);
        }
        pushStatus(msg.jobId(), msg.deploymentRecordId());
    }

    private void pushStatus(String jobId, String recordId) {
        try {
            DeploymentJob job = deploymentJobRepository.findById(jobId).orElse(null);
            if (job != null) {
                messagingTemplate.convertAndSend("/topic/jobs/" + recordId, deploymentJobPoVoMapper.poToVo(job));
            }
        } catch (Exception e) {
            log.warn("WebSocket 推送作业状态失败: jobId=[{}]", jobId, e);
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(BACKOFF_BASE_MILLIS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new JobFailureException("重试退避被中断", ie);
        }
    }
}
