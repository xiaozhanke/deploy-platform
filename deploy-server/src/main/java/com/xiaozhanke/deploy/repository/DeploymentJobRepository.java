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
     * 幂等 + 串行 CAS UPDATE:消费者首行占据作业(ADR-0002 幂等 + ADR-0006 记录串行)。
     *
     * <p>一条 SQL 同时满足两个条件:本作业仍为 {@code PENDING}(消费幂等),且所属记录此刻无其他
     * {@code IN_PROGRESS} 作业(记录串行)。受影响行数为 1 即占据成功。派生表 {@code busy} 绕开
     * MySQL「UPDATE 时 WHERE 子查询不能引用被改表」的限制;native 写法下枚举按
     * {@code @Enumerated(STRING)} 以字符串字面量比较。{@code clearAutomatically} 确保占据后
     * 同事务内重查能读到最新状态而非一级缓存旧值。
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE deployment_job SET status = 'IN_PROGRESS', start_time = :now " +
            "WHERE id = :jobId AND status = 'PENDING' " +
            "AND NOT EXISTS (SELECT 1 FROM (" +
            "  SELECT id FROM deployment_job WHERE deployment_record_id = :recordId AND status = 'IN_PROGRESS'" +
            ") busy)", nativeQuery = true)
    int acquireJobIfRecordIdle(@Param("jobId") String jobId, @Param("recordId") String recordId,
                               @Param("now") LocalDateTime now);
}
