package com.xiaozhanke.deploy.messaging.idempotent;

import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消费者首行幂等服务(详见 ADR-0002)。
 *
 * <p>用业务键 {@code jobId} 做 CAS UPDATE 占据作业,不使用 {@code messageId} 去重——事务消息
 * 半提交/重试/重投都会让 messageId 漂移,按 messageId 去重会漏过同一业务的重复消息。
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobAcquisitionService {

    private final DeploymentJobRepository deploymentJobRepository;

    /**
     * 用业务键 {@code jobId} 占据作业。
     *
     * @param jobId 作业 Id
     * @return true 表示占据成功(可以开始执行 SSH);false 表示已被前一次处理(直接 ACK)
     */
    @Transactional
    public boolean acquire(String jobId) {
        int affected = deploymentJobRepository.acquireJob(jobId, LocalDateTime.now());
        if (affected == 0) {
            log.info("作业 [{}] 已被前一次处理(或正在处理),跳过", jobId);
            return false;
        }
        return true;
    }
}
