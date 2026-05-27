package com.xiaozhanke.deploy.repository;

import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 部署作业持久化接口
 *
 * @author xiaozhanke
 */
@Repository
public interface DeploymentJobRepository extends JpaRepository<DeploymentJob, String>,
        JpaSpecificationExecutor<DeploymentJob> {

    /**
     * 按 (recordId, jobType, clientRequestId) 三元组查已有作业,用于 HTTP 入口防重(见 CONTEXT.md「客户端请求 Id」)。
     */
    Optional<DeploymentJob> findByDeploymentRecordIdAndJobTypeAndClientRequestId(
            String deploymentRecordId, JobTypeEnum jobType, String clientRequestId);

    /**
     * 幂等 CAS UPDATE:消费者首行用 jobId 占据作业(详见 ADR-0002)。
     * 受影响行数为 1 即占据成功,为 0 即已被前一次处理(或正在处理)。
     */
    @Modifying
    @Query("UPDATE DeploymentJob j SET j.status = com.xiaozhanke.deploy.enums.JobStatusEnum.IN_PROGRESS, " +
            "j.startTime = :now WHERE j.id = :jobId AND j.status = com.xiaozhanke.deploy.enums.JobStatusEnum.PENDING")
    int acquireJob(@Param("jobId") String jobId, @Param("now") LocalDateTime now);
}
