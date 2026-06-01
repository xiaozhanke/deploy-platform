package com.xiaozhanke.deploy.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.exception.RecordBusyException;
import com.xiaozhanke.deploy.messaging.consumer.DeploymentConsumer;
import com.xiaozhanke.deploy.messaging.dto.DeploymentJobMessage;
import com.xiaozhanke.deploy.messaging.idempotent.AcquireResult;
import com.xiaozhanke.deploy.messaging.idempotent.JobAcquisitionService;
import com.xiaozhanke.deploy.messaging.idempotent.JobExecutionDelegate;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 部署作业消费端单测——验证 CAS 占据分流与 JobExecutionDelegate 委托。
 *
 * <p>提取 JobExecutionDelegate 后,DeploymentConsumer 本身不再包含重试/死信/SSH 执行逻辑,
 * 这些由独立的 JobExecutionDelegate 单测覆盖。本类聚焦 consumer 层的编排正确性:
 * <ol>
 *   <li><b>占据成功(ACQUIRED)</b>:委托给 {@link JobExecutionDelegate#executeWithRetryAndDeadLetter}。</li>
 *   <li><b>记录串行(RECORD_BUSY)</b>:抛 {@link RecordBusyException} 让 ORDERLY 稍后重投。</li>
 *   <li><b>消费幂等(ALREADY_HANDLED)</b>:静默 ACK,不触达 delegate。</li>
 *   <li><b>序列化降级</b>:writeValueAsString 失败时使用降级 payload 继续执行,不阻塞 ORDERLY 队列。</li>
 * </ol>
 *
 * @author xiaozhanke
 */
@ExtendWith(MockitoExtension.class)
class DeploymentConsumerRetryTest {

    private static final String JOB_ID = "job-1";
    private static final String RECORD_ID = "rec-1";
    private static final String CLIENT_REQUEST_ID = "cr-1";

    @Mock
    private JobAcquisitionService jobAcquisitionService;

    @Mock
    private DeploymentRecordRepository deploymentRecordRepository;

    @Mock
    private JobExecutionDelegate executionDelegate;

    @Mock
    private ObjectMapper objectMapper;

    private DeploymentConsumer consumer;

    private DeploymentJobMessage startMessage;

    private DeploymentRecord deploymentRecord;

    @BeforeEach
    void setUp() {
        consumer = new DeploymentConsumer(
                jobAcquisitionService, deploymentRecordRepository, executionDelegate, objectMapper);

        startMessage = new DeploymentJobMessage(
                JOB_ID, RECORD_ID, JobTypeEnum.START, CLIENT_REQUEST_ID, LocalDateTime.now());
        deploymentRecord = new DeploymentRecord();
    }

    /**
     * 占据成功 → 委托给 JobExecutionDelegate 执行 SSH + 重试 + 死信。
     */
    @Test
    void acquiredDelegatesToExecutionDelegate() throws Exception {
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.ACQUIRED);
        when(deploymentRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(deploymentRecord));
        when(objectMapper.writeValueAsString(startMessage)).thenReturn("{\"jobId\":\"" + JOB_ID + "\"}");

        consumer.onMessage(startMessage);

        verify(executionDelegate).executeWithRetryAndDeadLetter(
                eq(JOB_ID), eq(RECORD_ID), eq(JobTypeEnum.START), eq(deploymentRecord), anyString());
    }

    /**
     * 记录串行({@code RECORD_BUSY}):抛 {@link RecordBusyException} 让 ORDERLY 稍后重投,不进 delegate。
     */
    @Test
    void recordBusyThrowsForOrderlyRedelivery() {
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.RECORD_BUSY);

        assertThatThrownBy(() -> consumer.onMessage(startMessage))
                .isInstanceOf(RecordBusyException.class);

        verifyNoInteractions(executionDelegate, deploymentRecordRepository);
    }

    /**
     * 消费幂等({@code ALREADY_HANDLED}):静默 ACK,不触达 delegate。
     */
    @Test
    void alreadyHandledSkipsSilently() {
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.ALREADY_HANDLED);

        consumer.onMessage(startMessage);

        verifyNoInteractions(executionDelegate, deploymentRecordRepository);
    }

    /**
     * 序列化失败时使用降级 payload,不抛异常阻塞 ORDERLY 队列。
     */
    @Test
    void serializationFailureUsesFallbackPayload() throws Exception {
        when(jobAcquisitionService.acquire(JOB_ID, RECORD_ID)).thenReturn(AcquireResult.ACQUIRED);
        when(deploymentRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(deploymentRecord));
        when(objectMapper.writeValueAsString(startMessage))
                .thenThrow(new JsonProcessingException("mock serialization error") {});

        assertThatCode(() -> consumer.onMessage(startMessage)).doesNotThrowAnyException();

        verify(executionDelegate).executeWithRetryAndDeadLetter(
                eq(JOB_ID), eq(RECORD_ID), eq(JobTypeEnum.START), eq(deploymentRecord), contains("_serialization_error"));
    }
}
