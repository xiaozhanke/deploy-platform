package com.xiaozhanke.deploy.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiaozhanke.deploy.core.ssh.SshOperationExecutor;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.exception.JobFailureException;
import com.xiaozhanke.deploy.exception.RecordBusyException;
import com.xiaozhanke.deploy.exception.SshTransientException;
import com.xiaozhanke.deploy.messaging.config.RocketMQProperties;
import com.xiaozhanke.deploy.messaging.consumer.DeploymentConsumer;
import com.xiaozhanke.deploy.messaging.dto.DeadLetterMqMessage;
import com.xiaozhanke.deploy.messaging.dto.DeploymentJobMessage;
import com.xiaozhanke.deploy.messaging.idempotent.AcquireResult;
import com.xiaozhanke.deploy.messaging.idempotent.JobAcquisitionService;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.model.mapper.DeploymentJobPoVoMapper;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.service.DeploymentJobExecutionService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 部署作业消费端的混合重试 + 死信编排单测(对应 MQ 方案稿场景 5、ADR-0003)。
 *
 * <p>锁死消费端面对失败时的分级行为,任何一条被后续改动削弱都会让本类变红:
 * <ol>
 *   <li><b>成功路径</b>:占据成功 → SSH 成功 → {@code markStartSuccess},全程不投死信。</li>
 *   <li><b>瞬时故障</b>({@link SshTransientException}):同步短重试,达到上限({@code MAX_TRANSIENT_ATTEMPTS=3})
 *       后才置 {@code DEAD} 并投递死信队列——验证"重试 3 次后转死信",顺序不破(对 MQ 始终 ACK)。</li>
 *   <li><b>业务失败</b>({@link JobFailureException}):重试无益,**立即**置 {@code DEAD} 并投死信,SSH 只执行一次。</li>
 *   <li><b>UPDATE 作业</b>:本批未支持,作为业务失败立即转死信,完全不触达 SSH 执行器。</li>
 *   <li><b>记录串行</b>({@code RECORD_BUSY}):抛 {@link RecordBusyException} 让 ORDERLY 容器稍后重投,不进死信路径。</li>
 *   <li><b>消费幂等</b>({@code ALREADY_HANDLED}):静默 ACK,不触达任何执行/写入/投递。</li>
 * </ol>
 *
 * <p>编排逻辑无需真实 SQL 语义,用纯 Mockito 即可(不连库、不起 Spring 上下文)。
 * {@link RocketMQProperties}(record)与 {@link ObjectMapper} 用真实实例:前者规避 mock final record,
 * 后者注册 {@code JavaTimeModule} 让 {@link DeploymentJobMessage} 里的 {@code LocalDateTime} 能正常序列化,
 * 否则死信投递会在 consumer 的 catch 里被吞掉、{@code syncSend} 永不执行。
 *
 * @author xiaozhanke
 */
@ExtendWith(MockitoExtension.class)
class DeploymentConsumerRetryTest {

    private static final String JOB_ID = "job-1";
    private static final String RECORD_ID = "rec-1";
    private static final String CLIENT_REQUEST_ID = "cr-1";
    private static final String DLQ_TOPIC = "deploy-job-dlq";

    @Mock
    private JobAcquisitionService jobAcquisitionService;

    @Mock
    private DeploymentJobRepository deploymentJobRepository;

    @Mock
    private DeploymentRecordRepository deploymentRecordRepository;

    @Mock
    private SshOperationExecutor sshOperationExecutor;

    @Mock
    private DeploymentJobExecutionService executionService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private DeploymentJobPoVoMapper deploymentJobPoVoMapper;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private DeploymentConsumer consumer;

    private DeploymentJobMessage startMessage;

    private DeploymentRecord deploymentRecord;

    @BeforeEach
    void setUp() {
        RocketMQProperties properties = new RocketMQProperties(
                "deploy-job", "deploy-job-consumer", 8, DLQ_TOPIC, "deploy-job-dlq-consumer");
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        consumer = new DeploymentConsumer(
                jobAcquisitionService, deploymentJobRepository, deploymentRecordRepository,
                sshOperationExecutor, executionService, messagingTemplate, deploymentJobPoVoMapper,
                rocketMQTemplate, properties, objectMapper);

        startMessage = new DeploymentJobMessage(
                JOB_ID, RECORD_ID, JobTypeEnum.START, CLIENT_REQUEST_ID, LocalDateTime.now());
        deploymentRecord = new DeploymentRecord();
    }

    /**
     * 成功路径:占据成功 + SSH 启动成功 → {@code markStartSuccess},不置 DEAD、不投死信。
     */
    @Test
    void startSuccessMarksSuccessWithoutDeadLetter() {
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.ACQUIRED);
        when(deploymentRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(deploymentRecord));
        when(sshOperationExecutor.executeStart(deploymentRecord)).thenReturn("12345");

        consumer.onMessage(startMessage);

        verify(executionService).markStartSuccess(JOB_ID, RECORD_ID, "12345");
        verify(executionService, never()).markDead(anyString(), anyString(), anyInt());
        verifyNoInteractions(rocketMQTemplate);
    }

    /**
     * 瞬时故障:SSH 持续抛 {@link SshTransientException} → 同步重试至上限(3 次)后才置 DEAD 并投死信。
     */
    @Test
    void transientFailureRetriesToLimitThenDeadLetters() {
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.ACQUIRED);
        when(deploymentRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(deploymentRecord));
        when(sshOperationExecutor.executeStart(deploymentRecord))
                .thenThrow(new SshTransientException("connection timeout", new RuntimeException("io")));

        consumer.onMessage(startMessage);

        // 首次 + 2 次重试 = 共 3 次尝试
        verify(sshOperationExecutor, times(3)).executeStart(deploymentRecord);
        verify(executionService).markDead(eq(JOB_ID), contains("瞬时故障重试 3 次仍失败"), eq(3));
        verify(rocketMQTemplate).syncSend(eq(DLQ_TOPIC), any(DeadLetterMqMessage.class));
    }

    /**
     * 业务失败:SSH 抛 {@link JobFailureException} → 立即置 DEAD 并投死信,SSH 只执行一次(不重试)。
     */
    @Test
    void businessFailureDeadLettersImmediatelyWithoutRetry() {
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.ACQUIRED);
        when(deploymentRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(deploymentRecord));
        when(sshOperationExecutor.executeStart(deploymentRecord))
                .thenThrow(new JobFailureException("port in use"));

        consumer.onMessage(startMessage);

        verify(sshOperationExecutor, times(1)).executeStart(deploymentRecord);
        verify(executionService).markDead(eq(JOB_ID), eq("port in use"), eq(0));
        verify(rocketMQTemplate).syncSend(eq(DLQ_TOPIC), any(DeadLetterMqMessage.class));
    }

    /**
     * UPDATE 作业本批未支持:作为业务失败立即转死信,且完全不触达 SSH 执行器。
     */
    @Test
    void updateJobTypeDeadLettersWithoutTouchingSsh() {
        DeploymentJobMessage updateMessage = new DeploymentJobMessage(
                JOB_ID, RECORD_ID, JobTypeEnum.UPDATE, CLIENT_REQUEST_ID, LocalDateTime.now());
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.ACQUIRED);
        when(deploymentRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(deploymentRecord));

        consumer.onMessage(updateMessage);

        verify(executionService).markDead(eq(JOB_ID), eq("UPDATE 作业类型暂未支持"), eq(0));
        verify(rocketMQTemplate).syncSend(eq(DLQ_TOPIC), any(DeadLetterMqMessage.class));
        verify(sshOperationExecutor, never()).executeStart(any());
        verify(sshOperationExecutor, never()).executeStop(any());
    }

    /**
     * 记录串行({@code RECORD_BUSY}):抛 {@link RecordBusyException} 让 ORDERLY 稍后重投,不进死信路径。
     */
    @Test
    void recordBusyThrowsForOrderlyRedelivery() {
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.RECORD_BUSY);

        assertThatThrownBy(() -> consumer.onMessage(startMessage))
                .isInstanceOf(RecordBusyException.class);

        verifyNoInteractions(sshOperationExecutor, executionService, rocketMQTemplate,
                deploymentRecordRepository);
    }

    /**
     * 消费幂等({@code ALREADY_HANDLED}):静默 ACK,不触达执行 / 写入 / 投递。
     */
    @Test
    void alreadyHandledSkipsSilently() {
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.ALREADY_HANDLED);

        consumer.onMessage(startMessage);

        verifyNoInteractions(sshOperationExecutor, executionService, rocketMQTemplate,
                deploymentRecordRepository, deploymentJobRepository, messagingTemplate);
    }
}
