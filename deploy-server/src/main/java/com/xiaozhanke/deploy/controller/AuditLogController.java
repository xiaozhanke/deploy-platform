package com.xiaozhanke.deploy.controller;

import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.enums.AuditOutcomeEnum;
import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.vo.AuditLogVo;
import com.xiaozhanke.deploy.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计日志接口(对应 MQ 方案稿场景 4)。
 *
 * @author xiaozhanke
 */
@Tag(name = "audit-log", description = "操作审计日志接口")
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询审计日志。
     */
    @Operation(summary = "分页查询审计日志", description = "支持按操作人、操作类型、结果过滤")
    @GetMapping
    public PageResult<AuditLogVo> queryAuditLogs(
            @Parameter(description = "操作人,可选") @RequestParam(required = false) String operator,
            @Parameter(description = "操作类型,可选") @RequestParam(required = false) AuditOperationTypeEnum operationType,
            @Parameter(description = "操作结果,可选") @RequestParam(required = false) AuditOutcomeEnum outcome,
            @PageableDefault(size = 20, sort = "operationTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditLogService.queryAuditLogs(operator, operationType, outcome, pageable);
    }
}
