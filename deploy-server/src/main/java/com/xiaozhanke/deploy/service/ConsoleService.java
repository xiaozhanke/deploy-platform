package com.xiaozhanke.deploy.service;

import com.xiaozhanke.deploy.enums.InstanceLivenessStateEnum;
import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.model.vo.ActivityVo;
import com.xiaozhanke.deploy.model.vo.ConsoleKpiVo;
import com.xiaozhanke.deploy.monitor.LivenessCache;
import com.xiaozhanke.deploy.repository.DeadLetterMessageRepository;
import com.xiaozhanke.deploy.repository.DeploymentJobRepository;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.repository.HostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 控制台聚合服务：拼装顶部 KPI 指标行。
 *
 * <p>混合数据源——主机 / 实例总数、在途作业、未处理死信走带索引的实时 DB COUNT（亚毫秒级、不阻塞 Tomcat 线程）；
 * 在线主机数读监控内存缓存（{@link LivenessCache}）；运行中实例数则先以带索引的轻量投影查出「运行中且有 PID」
 * 的候选集，再逐个读 {@link LivenessCache} 派生三态、计 {@code RUNNING}——存活性只在内存、无法落到 SQL COUNT，
 * 候选集随运行中实例数线性增长（与监控设计同样以中小规模机群为前提）。
 *
 * @author xiaozhanke
 */
@Service
@RequiredArgsConstructor
public class ConsoleService {

    /**
     * 在途作业的状态集合：尚未到达终态，合并计为「在途」。
     */
    private static final List<JobStatusEnum> IN_FLIGHT_JOB_STATUSES =
            List.of(JobStatusEnum.PENDING, JobStatusEnum.IN_PROGRESS);

    /**
     * 时间轴初次加载的动态条数。
     */
    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final HostRepository hostRepository;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final DeploymentJobRepository deploymentJobRepository;
    private final DeadLetterMessageRepository deadLetterMessageRepository;
    private final LivenessCache livenessCache;

    /**
     * 聚合控制台顶部 KPI 指标行。
     */
    public ConsoleKpiVo getKpi() {
        long onlineHostCount = livenessCache.countOnlineHosts();
        long totalHostCount = hostRepository.countByDeletedIsFalse();

        long totalInstanceCount = deploymentRecordRepository.countByDeletedIsFalse();
        // 运行中实例：在「运行中且有 PID」的实例里，存活探测派生为 RUNNING 的数量
        // （running=true 已由查询条件保证、processId 非空；崩溃 / 状态未知的不计入运行中）
        long runningInstanceCount = deploymentRecordRepository.findRunningInstanceViews().stream()
                .filter(view -> livenessCache.resolveInstanceState(
                        Boolean.TRUE, view.processId(), view.deploymentRecordId())
                        == InstanceLivenessStateEnum.RUNNING)
                .count();

        long inFlightJobCount = deploymentJobRepository.countByStatusInAndDeletedIsFalse(IN_FLIGHT_JOB_STATUSES);
        long unprocessedDeadLetterCount = deadLetterMessageRepository.countByRetriedIsFalseAndDeletedIsFalse();

        return new ConsoleKpiVo(onlineHostCount, totalHostCount,
                runningInstanceCount, totalInstanceCount,
                inFlightJobCount, unprocessedDeadLetterCount);
    }

    /**
     * 全平台最近的部署动态（按作业创建时间倒序取前 {@value #RECENT_ACTIVITY_LIMIT} 条），
     * 供控制台时间轴初次加载；后续增量由 {@code /topic/activities} 广播驱动。
     */
    public List<ActivityVo> getRecentActivities() {
        return deploymentJobRepository.findRecentActivities(PageRequest.of(0, RECENT_ACTIVITY_LIMIT));
    }

    /**
     * 当前所有在途作业（PENDING 或 IN_PROGRESS，按创建时间倒序），供在途抽屉只读列表展示。
     */
    public List<ActivityVo> getInFlightActivities() {
        return deploymentJobRepository.findInFlightActivities(IN_FLIGHT_JOB_STATUSES);
    }
}
