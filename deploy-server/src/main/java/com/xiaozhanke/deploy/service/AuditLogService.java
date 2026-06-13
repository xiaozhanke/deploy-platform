package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.enums.AuditOutcomeEnum;
import com.xiaozhanke.deploy.model.entity.AuditLog;
import com.xiaozhanke.deploy.model.mapper.AuditLogPoVoMapper;
import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.vo.AuditLogVo;
import com.xiaozhanke.deploy.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作审计日志查询服务。
 *
 * <p>审计落库由 {@code AuditLogConsumer} 异步完成,本服务只负责<b>读</b>——按操作人 / 类型 / 结果
 * 动态过滤分页查询,供 {@code AuditLogController} + 前端审计页使用。
 *
 * @author xiaozhanke
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogPoVoMapper auditLogPoVoMapper;

    /**
     * 分页查询审计日志,支持按操作人 / 操作类型 / 结果过滤。
     */
    public PageResult<AuditLogVo> queryAuditLogs(String operator, AuditOperationTypeEnum operationType,
                                                 AuditOutcomeEnum outcome, Pageable pageable) {
        Specification<AuditLog> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (operator != null && !operator.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("operator"), operator));
            }
            if (operationType != null) {
                predicates.add(criteriaBuilder.equal(root.get("operationType"), operationType));
            }
            if (outcome != null) {
                predicates.add(criteriaBuilder.equal(root.get("outcome"), outcome));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLog> page = auditLogRepository.findAll(specification, pageable);
        List<AuditLogVo> content = auditLogPoVoMapper.poListToVoList(page.getContent());
        return new PageResult<>(content, pageable, page.getTotalElements());
    }
}
