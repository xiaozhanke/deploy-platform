package com.xiaozhanke.deploy.controller;

import com.xiaozhanke.deploy.model.response.PageResult;
import com.xiaozhanke.deploy.model.vo.DeadLetterMessageVo;
import com.xiaozhanke.deploy.model.vo.DeploymentJobVo;
import com.xiaozhanke.deploy.service.DeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MQ 监控接口(死信查看与人工重试)。
 *
 * @author xiaozhanke
 */
@Tag(name = "mq-monitor", description = "MQ 监控接口(死信队列)")
@RestController
@RequiredArgsConstructor
public class MQMonitorController {

    private final DeadLetterService deadLetterService;

    /**
     * 分页查询死信列表。
     */
    @Operation(summary = "分页查询死信列表", description = "支持按是否已重试过滤")
    @GetMapping("/mq/dead-letters")
    public PageResult<DeadLetterMessageVo> queryDeadLetters(
            @Parameter(description = "是否已重试过滤,可选") @RequestParam(required = false) Boolean retried,
            @PageableDefault(size = 20, sort = "deadLetteredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return deadLetterService.queryDeadLetters(retried, pageable);
    }

    /**
     * 获取单条死信。
     */
    @Operation(summary = "获取死信详情", description = "根据死信 Id 获取详情")
    @GetMapping("/mq/dead-letters/{id}")
    public DeadLetterMessageVo getDeadLetter(
            @Parameter(description = "死信记录 Id", required = true) @PathVariable String id) {
        return deadLetterService.getDeadLetter(id);
    }

    /**
     * 人工重试死信(新建一份新 jobId 的作业)。
     */
    @Operation(summary = "重试死信", description = "用死信里的 (recordId, jobType) 新建一份新作业,不复投原消息")
    @PostMapping("/mq/dead-letters/{id}/retry")
    public DeploymentJobVo retry(
            @Parameter(description = "死信记录 Id", required = true) @PathVariable String id) {
        return deadLetterService.retry(id);
    }
}
