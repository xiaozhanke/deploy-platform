package com.xiaozhanke.deploy.messaging.idempotent;

import com.xiaozhanke.deploy.core.ssh.SshOperationExecutor;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.exception.JobFailureException;
import com.xiaozhanke.deploy.exception.SshTransientException;
import com.xiaozhanke.deploy.messaging.config.RocketMQProperties;
import com.xiaozhanke.deploy.messaging.dto.DeadLetterMqMessage;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.mapper.DeploymentJobPoVoMapper;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.service.DeploymentJobExecutionService;
import com.xiaozhanke.deploy.util.ErrorMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 部署作业执行委托——抽取 DeploymentConsumer 与 DeploymentDelayedConsumer 共用的
 * SSH 分发、混合重试、死信投递、状态推送与退避逻辑,消除 ~80 行重复代码。
 *
 * <p>两个 consumer 各自负责自身的特有逻辑(事务消息消费 / 延迟链管理),执行阶段统一
 * 委托给本组件。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutionDelegate {

    private static final int MAX_TRANSIENT_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MILLIS = 500L;

    private final SshOperationExecutor sshOperationExecutor;
    private final DeploymentJobExecutionService executionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DeploymentJobRepository deploymentJobRepository;
    private final DeploymentJobPoVoMapper deploymentJobPoVoMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQProperties properties;
    private final DeploymentRecordRepository deploymentRecordRepository;

    /**
     * 按作业类型分发 SSH 命令(不与数据库交互——状态回写由 {@link DeploymentJobExecutionService} 负责)。
     */
    public void dispatch(String jobId, String deploymentRecordId, JobTypeEnum jobType,
                         DeploymentRecord record) {
        switch (jobType) {
            case START -> {
                String processId = sshOperationExecutor.executeStart(record);
                executionService.markStartSuccess(jobId, deploymentRecordId, processId);
            }
            case STOP -> {
                sshOperationExecutor.executeStop(record);
                executionService.markStopSuccess(jobId, deploymentRecordId);
            }
            case RESTART -> {
                if (Boolean.TRUE.equals(record.getRunning())) {
                    sshOperationExecutor.executeStop(record);
                }
                String processId = sshOperationExecutor.executeStart(record);
                executionService.markStartSuccess(jobId, deploymentRecordId, processId);
            }
            case UPDATE -> throw new JobFailureException("UPDATE 作业类型暂未支持");
            default -> throw new JobFailureException("不支持的作业类型: " + jobType);
        }
        log.info("作业 [{}] 执行成功", jobId);
    }

    /**
     * 混合重试策略(ADR-0003):瞬时故障短重试,业务失败/重试耗尽转死信。
     *
     * @param originalPayload 原始消息体 JSON(用于死信记录,调用方在入参已序列化为字符串)
     */
    public void executeWithRetryAndDeadLetter(String jobId, String deploymentRecordId,
                                              JobTypeEnum jobType, DeploymentRecord record,
                                              String originalPayload) {
        int retryCount = 0;
        DeploymentRecord currentRecord = record;
        while (true) {
            try {
                dispatch(jobId, deploymentRecordId, jobType, currentRecord);
                pushStatus(jobId, deploymentRecordId);
                return;
            } catch (SshTransientException e) {
                retryCount++;
                if (retryCount < MAX_TRANSIENT_ATTEMPTS) {
                    log.warn("作业 [{}] 第 {} 次瞬时故障,退避后重试: {}", jobId, retryCount, e.getMessage());
                    backoff(retryCount);
                    // 重试前重新加载 record,避免前次 dispatch 部分成功导致的状态过时
                    currentRecord = deploymentRecordRepository.findById(deploymentRecordId)
                            .orElse(currentRecord);
                    continue;
                }
                deadLetter(jobId, deploymentRecordId, jobType, originalPayload,
                        retryCount, "瞬时故障重试 " + retryCount + " 次仍失败: " + e.getMessage());
                return;
            } catch (Exception e) {
                deadLetter(jobId, deploymentRecordId, jobType, originalPayload,
                        retryCount, e.getMessage());
                return;
            }
        }
    }

    /**
     * 直接标记死信并投递 DLQ(不经过 SSH 重试循环)。供接力链超限/续发失败等场景使用,
     * 这些场景不需要（也不应该）执行 SSH。
     */
    public void deadLetterDirect(String jobId, String deploymentRecordId, JobTypeEnum jobType,
                                 String originalPayload, String reason) {
        log.error("作业 [{}] 直接进入死信(不经重试): {}", jobId, reason);
        deadLetter(jobId, deploymentRecordId, jobType, originalPayload, 0, reason);
    }

    /**
     * 通过 WebSocket 推送作业最新状态给前端。
     */
    public void pushStatus(String jobId, String recordId) {
        try {
            DeploymentJob job = deploymentJobRepository.findById(jobId).orElse(null);
            if (job != null) {
                messagingTemplate.convertAndSend("/topic/jobs/" + recordId,
                        deploymentJobPoVoMapper.poToVo(job));
            }
        } catch (Exception e) {
            log.warn("WebSocket 推送作业状态失败: jobId=[{}]", jobId, e);
        }
    }

    private void deadLetter(String jobId, String deploymentRecordId, JobTypeEnum jobType,
                            String originalPayload, int retryCount, String reason) {
        log.error("作业 [{}] 进入死信: {}", jobId, reason);
        executionService.markDead(jobId, reason, retryCount);
        try {
            DeadLetterMqMessage deadLetterMqMessage = new DeadLetterMqMessage(jobId,
                    deploymentRecordId, jobType,
                    ErrorMessageUtils.truncate(reason), originalPayload, LocalDateTime.now());
            rocketMQTemplate.syncSend(properties.deadLetterTopic(), deadLetterMqMessage);
        } catch (Exception e) {
            log.error("投递死信队列失败: jobId=[{}]", jobId, e);
        }
        pushStatus(jobId, deploymentRecordId);
        // 清除中断标志,防止 backoff() 中断残留影响 ORDERLY 消费者后续消息处理
        if (Thread.interrupted()) {
            log.debug("已清除线程中断标志");
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
