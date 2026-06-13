package com.xiaozhanke.deploy.monitor;

import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import com.xiaozhanke.deploy.model.mapper.HostPoDtoMapper;
import com.xiaozhanke.deploy.model.vo.HostMetricVo;
import com.xiaozhanke.deploy.repository.HostRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 免 Agent 主机资源采样器：订阅期内每 {@code app.monitor.sample-interval}（默认 5s）对<strong>全部</strong>主机
 * 采集 CPU/内存，经 STOMP 推送<strong>全量快照</strong>到 {@link MonitorSubscriptionTracker#MONITOR_HOSTS_DESTINATION}，
 * 前端排序取 top-5（异常优先）。指标取自 /proc 原始数据并在服务端做 CPU 差值。
 *
 * <p>调度由 {@code MonitoringConfig} 以 {@link MonitorProperties#sampleInterval()} 编程式注册（fixedDelay，
 * 周期不重叠）。采样范围为全部主机以支撑「异常优先上浮」的正确排序——<strong>前提假设中小规模机群</strong>。
 *
 * <p>休眠/唤醒：{@link MonitorSubscriptionTracker} 在订阅数跨越 0 边界时发布 {@link MonitorActivationEvent}，
 * 本类立即唤醒采样或整池拆光休眠（{@link MonitorSshConnectionPool#evictAll()} + 清空 CPU 快照），实现「0 采样开销」。
 * {@link #sampleLock} 保证采样与拆池互斥，不在采样中途拆连接。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
public class HostMetricSampler {

    private final HostRepository hostRepository;
    private final HostPoDtoMapper hostPoDtoMapper;
    private final MonitorSshConnectionPool connectionPool;
    private final ProcMetricParser procMetricParser;
    private final MonitorSubscriptionTracker subscriptionTracker;
    private final SimpMessagingTemplate messagingTemplate;
    private final Executor samplingExecutor;
    private final Executor wakeupExecutor;

    /**
     * 每台主机「上一次」{@code /proc/stat} 读数，用于服务端差值算 CPU 利用率（key=hostId）。
     * 采样失败时移除该主机条目、整池拆光时清空——唤醒后首轮 CPU 返回 {@code null}。
     */
    private final Map<String, CpuStat> previousCpuStat = new ConcurrentHashMap<>();

    /**
     * 互斥采样周期与整池拆光：避免在采样进行中拆掉连接。
     */
    private final ReentrantLock sampleLock = new ReentrantLock();

    public HostMetricSampler(HostRepository hostRepository, HostPoDtoMapper hostPoDtoMapper,
                             MonitorSshConnectionPool connectionPool, ProcMetricParser procMetricParser,
                             MonitorSubscriptionTracker subscriptionTracker,
                             SimpMessagingTemplate messagingTemplate,
                             @Qualifier("monitorSamplingExecutor") Executor samplingExecutor,
                             @Qualifier("monitorWakeupExecutor") Executor wakeupExecutor) {
        this.hostRepository = hostRepository;
        this.hostPoDtoMapper = hostPoDtoMapper;
        this.connectionPool = connectionPool;
        this.procMetricParser = procMetricParser;
        this.subscriptionTracker = subscriptionTracker;
        this.messagingTemplate = messagingTemplate;
        this.samplingExecutor = samplingExecutor;
        this.wakeupExecutor = wakeupExecutor;
    }

    /**
     * 定时入口（由 MonitoringConfig 编程式注册为 fixedDelay 任务）：有订阅则跑一轮采样，无订阅则确保已休眠。
     */
    public void scheduledSample() {
        if (subscriptionTracker.hasSubscribers()) {
            runSampleCycle();
        } else {
            ensureAsleep();
        }
    }

    /**
     * 订阅激活态变更：0→正 立即唤醒采样（消除最多一个周期的首屏延迟）；正→0 立即整池拆光休眠。
     *
     * <p>事件由 STOMP inbound 线程<strong>同步</strong>发布，而采样与整池拆光都含阻塞 SSH，必须挪到独立的
     * {@code monitorWakeupExecutor} 执行——否则会卡住整条 WebSocket inbound 通道（终端按键、作业状态等所有
     * 实时消息）。该池与采样的 per-host 任务池分离，亦避免 {@code samplingConcurrency=1} 时编排线程 join
     * 自身池任务的自锁。
     */
    @EventListener
    public void onActivation(MonitorActivationEvent event) {
        if (event.active()) {
            log.info("监控看板出现订阅, 立即唤醒资源采样");
            wakeupExecutor.execute(this::runSampleCycle);
        } else {
            log.info("监控看板订阅归零, 准备休眠");
            wakeupExecutor.execute(this::ensureAsleep);
        }
    }

    /**
     * 跑一轮采样并推送全量快照。{@code sampleLock.tryLock()} 避免与正在进行的采样/拆池重入。
     */
    private void runSampleCycle() {
        if (!sampleLock.tryLock()) {
            log.debug("已有采样/拆池在进行, 跳过本次触发");
            return;
        }
        try {
            List<HostRecordDto> hosts = hostRepository.findAllByDeletedIsFalse().stream()
                    .map(hostPoDtoMapper::poToDto)
                    .toList();
            if (hosts.isEmpty()) {
                messagingTemplate.convertAndSend(MonitorSubscriptionTracker.MONITOR_HOSTS_DESTINATION, List.of());
                return;
            }
            LocalDateTime sampleTime = LocalDateTime.now();
            // 按主机并发采样（并发上限由 monitorSamplingExecutor 控制），各任务自身不抛异常
            List<CompletableFuture<HostMetricVo>> futures = hosts.stream()
                    .map(host -> CompletableFuture.supplyAsync(() -> sampleHost(host, sampleTime), samplingExecutor))
                    .toList();
            List<HostMetricVo> snapshot = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            messagingTemplate.convertAndSend(MonitorSubscriptionTracker.MONITOR_HOSTS_DESTINATION, snapshot);
            log.debug("已推送 {} 台主机资源快照", snapshot.size());
        } catch (Exception e) {
            log.warn("资源采样周期异常: {}", e.getMessage(), e);
        } finally {
            sampleLock.unlock();
        }
    }

    /**
     * 采集单台主机的 CPU/内存。本方法<strong>不抛异常</strong>：任何失败都降级为该主机本周期不可用
     * （{@code reachable=false}、指标为 {@code null}），以免拖垮整轮采样。
     */
    private HostMetricVo sampleHost(HostRecordDto host, LocalDateTime sampleTime) {
        String hostId = host.getId();
        try {
            // 先取两条命令的原始输出（任一失败即抛出，连接已被池驱逐）
            String cpuRaw = connectionPool.execute(host, ProcMetricParser.CPU_STAT_COMMAND);
            String memRaw = connectionPool.execute(host, ProcMetricParser.MEMORY_USAGE_COMMAND);

            CpuStat current = procMetricParser.parseCpuStat(cpuRaw);
            Double cpuUsage = procMetricParser.computeCpuUsagePercent(previousCpuStat.get(hostId), current);
            // 维护 per-host 上一次读数：解析成功才留存，失败则清掉使下轮回到 null
            if (current != null) {
                previousCpuStat.put(hostId, current);
            } else {
                previousCpuStat.remove(hostId);
            }
            Double memoryUsage = procMetricParser.parseMemoryUsagePercent(memRaw);

            return new HostMetricVo(hostId, host.getName(), host.getAddress(),
                    cpuUsage, memoryUsage, true, sampleTime);
        } catch (Exception e) {
            // 连接/命令失败：丢弃 CPU 快照（重建后首轮回 null），标记本周期不可用
            previousCpuStat.remove(hostId);
            log.debug("主机 [{}] 本周期采样不可用: {}", hostId, e.getMessage());
            return new HostMetricVo(hostId, host.getName(), host.getAddress(),
                    null, null, false, sampleTime);
        }
    }

    /**
     * 确保进入休眠态：整池拆光 + 清空 CPU 快照。已休眠则跳过，避免无谓加锁。
     */
    private void ensureAsleep() {
        if (connectionPool.size() == 0 && previousCpuStat.isEmpty()) {
            return;
        }
        sampleLock.lock();
        try {
            connectionPool.evictAll();
            previousCpuStat.clear();
        } finally {
            sampleLock.unlock();
        }
    }
}
