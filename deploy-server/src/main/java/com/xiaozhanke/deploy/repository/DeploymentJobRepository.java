package com.xiaozhanke.deploy.repository;

import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.enums.JobTypeEnum;
import com.xiaozhanke.deploy.model.entity.DeploymentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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
     * 按 (recordId, jobType, clientRequestId) 三元组查已有作业,用于 HTTP 入口防重。
     */
    Optional<DeploymentJob> findByDeploymentRecordIdAndJobTypeAndClientRequestId(
            String deploymentRecordId, JobTypeEnum jobType, String clientRequestId);

    /**
     * 幂等 + 串行 CAS UPDATE:消费者首行占据作业。
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

    /**
     * 取消作业 CAS:仅当状态为 PENDING 时才允许转入 CANCELLED。
     *
     * <p>受影响行数为 1 表示取消成功;为 0 表示作业已开始执行(IN_PROGRESS)或已终态,不可撤销。
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE deployment_job SET status = 'CANCELLED', end_time = :now " +
            "WHERE id = :jobId AND status = 'PENDING'", nativeQuery = true)
    int cancelIfPending(@Param("jobId") String jobId, @Param("now") LocalDateTime now);

    /**
     * 查指定部署记录下处于 PENDING 状态的所有作业(供"待执行作业"列表使用)。
     */
    List<DeploymentJob> findByDeploymentRecordIdAndStatus(String deploymentRecordId, JobStatusEnum status);

    /**
     * 批量查一组部署记录各自「最近一次作业」(按 createTime 取最新),供部署记录列表的「最近作业」列回填。
     *
     * <p>相关子查询取每条记录的 {@code MAX(createTime)},一次查出整页记录的最新作业,避免逐条 N+1。
     * 同一记录的作业彼此串行,createTime 为 datetime(6),理论上不会并列;若极端并列由
     * 调用方按 recordId 去重兜底。
     *
     * <p>外层与子查询都过滤 {@code deleted = false},与全系统软删约定一致,避免软删作业被当成「最新」。
     */
    @Query("SELECT j FROM DeploymentJob j WHERE j.deploymentRecord.id IN :recordIds " +
            "AND j.deleted = false " +
            "AND j.createTime = (SELECT MAX(j2.createTime) FROM DeploymentJob j2 " +
            "WHERE j2.deploymentRecord.id = j.deploymentRecord.id AND j2.deleted = false)")
    List<DeploymentJob> findLatestByDeploymentRecordIdIn(@Param("recordIds") Collection<String> recordIds);
}
