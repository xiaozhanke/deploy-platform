package com.xiaozhanke.deploy.controller;

import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.aspect.Auditable;
import com.xiaozhanke.deploy.enums.AuditOperationTypeEnum;
import com.xiaozhanke.deploy.model.request.CreateJobRequest;
import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.vo.DeploymentJobVo;
import com.xiaozhanke.deploy.service.DeploymentJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * 部署作业接口(MQ 异步化主入口)
 *
 * <p>对应 MQ 方案稿场景 1 的异步化改造。旧的 {@code /deployments/{id}/actions/*} 同步接口
 * 标记 {@code @Deprecated},Phase 2 清理后由本 controller 完全承接。
 *
 * @author xiaozhanke
 */
@Tag(name = "deployment-jobs", description = "部署作业接口(异步)")
@RestController
@RequiredArgsConstructor
public class DeploymentJobController {

    private final DeploymentJobService deploymentJobService;

    /**
     * 创建部署作业(异步入口)
     */
    @Operation(summary = "创建部署作业", description = "为指定部署记录新建一个作业并通过 MQ 异步执行;"
            + "幂等依据 (deploymentRecordId, jobType, clientRequestId) 唯一索引")
    @Auditable(operationType = AuditOperationTypeEnum.JOB_CREATE,
            target = "#deploymentRecordId", description = "#request.jobType.description + ' 作业'")
    @PostMapping("/deployments/{id}/jobs")
    public ResponseEntity<DeploymentJobVo> createJob(
            @Parameter(description = "部署记录 Id", required = true) @PathVariable("id") String deploymentRecordId,
            @Valid @RequestBody CreateJobRequest request) {
        DeploymentJobVo created = deploymentJobService.createJob(deploymentRecordId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/jobs/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(created);
    }

    /**
     * 分页查询某部署记录下的作业
     */
    @Operation(summary = "分页查询部署作业列表", description = "支持按 status 过滤")
    @GetMapping("/deployments/{id}/jobs")
    public PageResult<DeploymentJobVo> queryJobs(
            @Parameter(description = "部署记录 Id", required = true) @PathVariable("id") String deploymentRecordId,
            @Parameter(description = "作业状态过滤,可选") @RequestParam(required = false) JobStatusEnum status,
            @PageableDefault(size = 20, sort = "createTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return deploymentJobService.queryJobs(deploymentRecordId, status, pageable);
    }

    /**
     * 获取单个作业
     */
    @Operation(summary = "获取部署作业", description = "根据 jobId 获取作业当前状态")
    @GetMapping("/jobs/{jobId}")
    public DeploymentJobVo getJob(
            @Parameter(description = "作业 Id", required = true) @PathVariable String jobId) {
        return deploymentJobService.getJob(jobId);
    }

    /**
     * 取消 PENDING 状态的作业(场景 3 延迟作业取消,ADR-0004)。
     *
     * <p>仅 PENDING 作业可撤销;已开始执行(IN_PROGRESS)的作业撤销会被拒绝。
     * 取消是终态——延迟消息到期后 CAS 不命中,链条自然终止。
     */
    @Operation(summary = "取消部署作业", description = "撤销 PENDING 状态的作业(延迟作业取消);已开始执行的作业不可撤销")
    @Auditable(operationType = AuditOperationTypeEnum.JOB_CANCEL, target = "#jobId")
    @PutMapping("/jobs/{jobId}/cancel")
    public DeploymentJobVo cancelJob(
            @Parameter(description = "作业 Id", required = true) @PathVariable String jobId) {
        return deploymentJobService.cancelJob(jobId);
    }
}
