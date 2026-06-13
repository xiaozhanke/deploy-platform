package com.xiaozhanke.deploy.controller;

import com.xiaozhanke.deploy.model.vo.ActivityVo;
import com.xiaozhanke.deploy.model.vo.ConsoleKpiVo;
import com.xiaozhanke.deploy.service.ConsoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 控制台（首页）聚合接口。
 *
 * @author xiaozhanke
 */
@Tag(name = "console", description = "控制台聚合接口")
@RestController
@RequestMapping("/console")
@RequiredArgsConstructor
public class ConsoleController {

    private final ConsoleService consoleService;

    /**
     * 控制台顶部 KPI 指标行聚合：在线主机 / 总数、运行中实例 / 总数、在途作业、未处理死信。
     * 前端 30s 轮询，提交作业成功后手动触发一次即时刷新以消除轮询延迟。
     */
    @Operation(summary = "控制台 KPI 聚合", description = "在线主机/总数、运行中实例/总数、在途作业、未处理死信，供首页 30s 轮询")
    @GetMapping("/kpi")
    public ConsoleKpiVo getKpi() {
        return consoleService.getKpi();
    }

    /**
     * 全平台最近 10 条部署动态，供「最新发版动态」时间轴初次加载；后续增量走 /topic/activities 广播。
     */
    @Operation(summary = "最近部署动态", description = "全平台最近 10 条部署作业动态，供控制台时间轴初次加载")
    @GetMapping("/activities")
    public List<ActivityVo> getRecentActivities() {
        return consoleService.getRecentActivities();
    }

    /**
     * 当前所有在途作业（PENDING / IN_PROGRESS），供点击在途 KPI 卡片滑出的抽屉只读列表展示。
     */
    @Operation(summary = "在途作业列表", description = "当前所有 PENDING / IN_PROGRESS 作业，供在途抽屉展示")
    @GetMapping("/in-flight-jobs")
    public List<ActivityVo> getInFlightJobs() {
        return consoleService.getInFlightActivities();
    }
}
