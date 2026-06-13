package com.xiaozhanke.deploy.repository;

import com.xiaozhanke.deploy.model.dto.RunningInstanceView;
import com.xiaozhanke.deploy.model.entity.DeploymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 部署记录持久化接口
 *
 * @author xiaozhanke
 */
@Repository
public interface DeploymentRecordRepository extends JpaRepository<DeploymentRecord, String>,
        JpaSpecificationExecutor<DeploymentRecord> {

    /**
     * 投影出所有「运行中且有进程号」的部署实例（{@link RunningInstanceView}），供在线检测按主机分组、
     * 合并 PID 在单连接内批量探测。
     *
     * <p>只取三列、过滤软删，避免加载整实体与触碰 {@code hostRecord} 懒加载关联；{@code processId IS NOT NULL}
     * 在 SQL 侧剔除无 PID 的记录（它们在读取时直接派生为「状态未知」，无需远程探测）。
     */
    @Query("SELECT new com.xiaozhanke.deploy.model.dto.RunningInstanceView(d.id, d.hostRecord.id, d.processId) " +
            "FROM DeploymentRecord d " +
            "WHERE d.deleted = false AND d.running = true AND d.processId IS NOT NULL")
    List<RunningInstanceView> findRunningInstanceViews();

    /**
     * 统计未删除部署实例总数，供控制台 KPI「总实例数」使用。
     */
    long countByDeletedIsFalse();
}
