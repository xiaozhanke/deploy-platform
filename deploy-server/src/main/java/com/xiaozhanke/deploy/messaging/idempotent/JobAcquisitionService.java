package com.xiaozhanke.deploy.messaging.idempotent;

import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消费者首行幂等 + 记录串行占据服务。
 *
 * <p>用业务键 {@code jobId} 做 CAS UPDATE 占据作业,不使用 {@code messageId} 去重——事务消息
 * 半提交/重试/重投都会让 messageId 漂移,按 messageId 去重会漏过同一业务的重复消息。同一条 CAS
 * 还附带"所属记录无其他在途作业"的互斥条件,实现同一记录串行、不同记录并发。
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobAcquisitionService {

    private final DeploymentJobRepository deploymentJobRepository;

    /**
     * 用业务键 {@code jobId} 占据作业,并保证所属记录此刻无其他在途作业。
     *
     * @param jobId              作业 Id
     * @param deploymentRecordId 所属部署记录 Id(记录串行的互斥维度)
     * @return 占据结果:{@link AcquireResult#ACQUIRED} 可执行 SSH;
     * {@link AcquireResult#ALREADY_HANDLED} 重复投递(或已取消),直接 ACK;
     * {@link AcquireResult#RECORD_BUSY} 记录被占,需稍后重投
     */
    @Transactional
    public AcquireResult acquire(String jobId, String deploymentRecordId) {
        int affected = deploymentJobRepository.acquireJobIfRecordIdle(jobId, deploymentRecordId, LocalDateTime.now());
        if (affected > 0) {
            return AcquireResult.ACQUIRED;
        }
        // affected == 0:用作业当前状态区分"已处理/已取消"与"记录被占"
        JobStatusEnum status = deploymentJobRepository.findById(jobId)
                .map(DeploymentJob::getStatus)
                .orElse(null);
        if (status == JobStatusEnum.PENDING) {
            log.info("作业 [{}] 所属记录 [{}] 有在途作业,稍后重试", jobId, deploymentRecordId);
            return AcquireResult.RECORD_BUSY;
        }
        log.info("作业 [{}] 已被前一次处理(status={}),跳过", jobId, status);
        return AcquireResult.ALREADY_HANDLED;
    }
}
