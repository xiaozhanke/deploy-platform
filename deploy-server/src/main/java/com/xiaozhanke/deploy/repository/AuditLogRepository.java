package com.xiaozhanke.deploy.repository;

import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.enums.AuditOutcomeEnum;
import com.xiaozhanke.deploy.model.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * 操作审计日志持久化接口(对应 MQ 方案稿场景 4)。
 *
 * <p>{@link JpaSpecificationExecutor} 支撑按操作人 / 类型 / 结果的动态过滤查询。
 *
 * @author xiaozhanke
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String>,
        JpaSpecificationExecutor<AuditLog> {

    /**
     * 统计某操作人 + 类型 + 结果的审计条数(供集成测试断言异步落库完成,生产查询走 Specification)。
     */
    long countByOperatorAndOperationTypeAndOutcome(String operator, AuditOperationTypeEnum operationType,
                                                   AuditOutcomeEnum outcome);
}
