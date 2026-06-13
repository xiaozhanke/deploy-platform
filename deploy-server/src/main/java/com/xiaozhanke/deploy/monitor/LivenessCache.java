package com.xiaozhanke.deploy.monitor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xiaozhanke.deploy.enums.InstanceLivenessStateEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 主机在线性与应用实例存活性的本地内存缓存（Caffeine，<strong>绝不持久化</strong>）。
 *
 * <p>由 {@link LivenessProbeService} 每 {@code app.monitor.liveness-interval}（默认 60s）写入，控制台
 * 加载与 KPI 聚合直接读这里——它每分钟变、无历史价值，写库会与 MQ 消费者抢 {@code DeploymentRecord.running}
 * 列、刷脏 {@code updateUser} 审计字段。
 *
 * <p>两张缓存均 {@code expireAfterWrite(cacheTtl)}（默认 180s ≈ 3× 探测周期）：探测任务停摆时陈旧条目
 * 自动过期，主机在线读不到即视为「不在线」，实例存活读不到即派生为「状态未知」，不会一直停留在最后一次
 * 探测的旧值。
 *
 * @author xiaozhanke
 */
@Component
public class LivenessCache {

    /**
     * 主机在线性：key=hostId，value=本轮短连接是否连通。
     */
    private final Cache<String, Boolean> hostOnlineCache;

    /**
     * 实例存活性：key=deploymentRecordId，value=进程是否存活（{@code ps -p} 命中）。
     */
    private final Cache<String, Boolean> instanceAliveCache;

    public LivenessCache(MonitorProperties monitorProperties) {
        this.hostOnlineCache = Caffeine.newBuilder()
                .expireAfterWrite(monitorProperties.cacheTtl())
                .build();
        this.instanceAliveCache = Caffeine.newBuilder()
                .expireAfterWrite(monitorProperties.cacheTtl())
                .build();
    }

    /**
     * 记录一台主机本轮在线检测结果。
     */
    public void recordHostOnline(String hostId, boolean online) {
        hostOnlineCache.put(hostId, online);
    }

    /**
     * 记录一个实例本轮存活探测结果。
     */
    public void recordInstanceAlive(String deploymentRecordId, boolean alive) {
        instanceAliveCache.put(deploymentRecordId, alive);
    }

    /**
     * 指定主机当前是否在线（缺失/过期视为不在线）。
     */
    public boolean isHostOnline(String hostId) {
        return Boolean.TRUE.equals(hostOnlineCache.getIfPresent(hostId));
    }

    /**
     * 当前在线主机数（缓存中在线条目计数），供 KPI「在线主机数」直接读取。
     */
    public long countOnlineHosts() {
        return hostOnlineCache.asMap().values().stream().filter(Boolean::booleanValue).count();
    }

    /**
     * 在<strong>读取时</strong>由 {@code (running, processId, 实例存活缓存)} 派生应用实例三态，<strong>不落任何字段</strong>：
     * <ul>
     *   <li>{@code running != true} → {@link InstanceLivenessStateEnum#STOPPED}；</li>
     *   <li>{@code running=true & processId 为空} → {@link InstanceLivenessStateEnum#UNKNOWN}（无 PID 不探测，不误判为停止）；</li>
     *   <li>{@code running=true & 探测命中存活} → {@link InstanceLivenessStateEnum#RUNNING}；</li>
     *   <li>{@code running=true & 探测命中不存活} → {@link InstanceLivenessStateEnum#STOPPED}（崩溃）；</li>
     *   <li>{@code running=true & 探测缓存缺失}（首轮/主机离线/过期）→ {@link InstanceLivenessStateEnum#UNKNOWN}。</li>
     * </ul>
     *
     * @param running            部署记录的运行意图
     * @param processId          记录的进程号
     * @param deploymentRecordId 部署记录 Id（实例存活缓存 key）
     * @return 派生出的三态
     */
    public InstanceLivenessStateEnum resolveInstanceState(Boolean running, String processId,
                                                          String deploymentRecordId) {
        if (!Boolean.TRUE.equals(running)) {
            return InstanceLivenessStateEnum.STOPPED;
        }
        if (!StringUtils.hasText(processId)) {
            return InstanceLivenessStateEnum.UNKNOWN;
        }
        Boolean alive = instanceAliveCache.getIfPresent(deploymentRecordId);
        if (alive == null) {
            return InstanceLivenessStateEnum.UNKNOWN;
        }
        return alive ? InstanceLivenessStateEnum.RUNNING : InstanceLivenessStateEnum.STOPPED;
    }
}
