package com.xiaozhanke.deploy.monitor;

import com.jcraft.jsch.Session;
import com.xiaozhanke.deploy.core.ssh.SshSessionFactory;
import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import com.xiaozhanke.deploy.model.dto.RunningInstanceView;
import com.xiaozhanke.deploy.model.mapper.HostPoDtoMapper;
import com.xiaozhanke.deploy.repository.DeploymentRecordRepository;
import com.xiaozhanke.deploy.repository.HostRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 主机在线检测与应用实例存活批处理（每 {@code app.monitor.liveness-interval}，默认 60s）。
 *
 * <p>不同于 5s 资源采样器（订阅门控 + 长连接池），本服务<strong>常驻</strong>运行、用<strong>短连接</strong>
 * （connect→命令→断开），与采样器的连接池彻底隔离失败域——60s 一次握手成本可忽略。
 *
 * <p>每周期遍历全部未删除主机并发探测；对同一台主机上的多个实例<strong>合并成单条 {@code ps -p}</strong>
 * 在一次连接内批量探测，拒绝逐实例连接。连通即记主机在线；ps 命中的 PID 记实例存活、未命中记不存活（崩溃）。
 * 主机连不上则记离线、<strong>不</strong>翻转其上实例存活（保持原值待 {@link LivenessCache} TTL 过期为「未知」，
 * 防止主机抖动把实例误报为已停）。结果只写 {@link LivenessCache} 内存缓存，绝不落库。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
public class LivenessProbeService {

    private final HostRepository hostRepository;
    private final HostPoDtoMapper hostPoDtoMapper;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final SshSessionFactory sshSessionFactory;
    private final MonitorProperties monitorProperties;
    private final LivenessCache livenessCache;
    private final Executor livenessProbeExecutor;

    /**
     * 连续探测失败的退避封顶（周期数）。约 8× liveness-interval ≈ 8min。
     */
    private static final int MAX_BACKOFF_CYCLES = 8;

    /**
     * 每主机连续连接失败次数（key=hostId）。
     */
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();

    /**
     * 每主机剩余跳过周期数（退避窗口，key=hostId）；&gt;0 表示本轮起还要跳过几轮探测。
     */
    private final Map<String, Integer> backoffSkipsRemaining = new ConcurrentHashMap<>();

    public LivenessProbeService(HostRepository hostRepository, HostPoDtoMapper hostPoDtoMapper,
                                DeploymentRecordRepository deploymentRecordRepository,
                                SshSessionFactory sshSessionFactory, MonitorProperties monitorProperties,
                                LivenessCache livenessCache,
                                @Qualifier("livenessProbeExecutor") Executor livenessProbeExecutor) {
        this.hostRepository = hostRepository;
        this.hostPoDtoMapper = hostPoDtoMapper;
        this.deploymentRecordRepository = deploymentRecordRepository;
        this.sshSessionFactory = sshSessionFactory;
        this.monitorProperties = monitorProperties;
        this.livenessCache = livenessCache;
        this.livenessProbeExecutor = livenessProbeExecutor;
    }

    /**
     * 定时入口（由 {@link MonitoringConfig} 注册为 fixedDelay）：常驻探测全部主机，不门控订阅。
     */
    public void scheduledProbe() {
        List<HostRecordDto> hosts;
        Map<String, List<RunningInstanceView>> instancesByHost;
        try {
            hosts = hostRepository.findAllByDeletedIsFalse().stream()
                    .map(hostPoDtoMapper::poToDto)
                    .toList();
            if (hosts.isEmpty()) {
                return;
            }
            // 一次查出所有「运行中且有 PID」的实例，按主机分组，供单连接合并探测
            instancesByHost = deploymentRecordRepository.findRunningInstanceViews().stream()
                    .filter(view -> view.hostRecordId() != null)
                    .collect(Collectors.groupingBy(RunningInstanceView::hostRecordId));
        } catch (Exception e) {
            log.warn("在线检测加载主机/实例清单失败: {}", e.getMessage(), e);
            return;
        }
        // 清理已删除主机的退避状态，避免 map 随主机增删无限增长
        Set<String> currentHostIds = hosts.stream().map(HostRecordDto::getId).collect(Collectors.toSet());
        consecutiveFailures.keySet().retainAll(currentHostIds);
        backoffSkipsRemaining.keySet().retainAll(currentHostIds);

        List<CompletableFuture<Void>> futures = hosts.stream()
                // 退避：连续失败的主机自动降频，本轮跳过仍在退避窗口内者
                .filter(host -> !inBackoff(host.getId()))
                .map(host -> CompletableFuture.runAsync(
                        () -> probeHost(host, instancesByHost.getOrDefault(host.getId(), List.of())),
                        livenessProbeExecutor))
                .toList();
        // probeHost 自身不抛异常，join 仅为等待整轮探测完成
        futures.forEach(CompletableFuture::join);
        log.debug("在线检测完成: 主机 {} 台, 当前在线 {} 台", hosts.size(), livenessCache.countOnlineHosts());
    }

    /**
     * 探测单台主机：短连接连通即在线，在同一连接内合并 {@code ps -p} 探测其上实例存活。
     * 本方法<strong>不抛异常</strong>，任何失败都降级为该主机离线 / 实例本轮不更新。
     */
    private void probeHost(HostRecordDto host, List<RunningInstanceView> instances) {
        String hostId = host.getId();
        // 只保留纯数字 PID（防注入），去重后按插入序拼接命令
        Set<String> numericPids = instances.stream()
                .map(RunningInstanceView::processId)
                .filter(this::isNumeric)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Session session = null;
        try {
            session = sshSessionFactory.createSession(host);
            // 监控路径强制快速失败：连接/会话超时统一用在线检测短超时覆盖 per-host 配置
            session.setTimeout(monitorProperties.livenessTimeoutMillis());
            session.connect(monitorProperties.livenessTimeoutMillis());
            // 连上即主机在线
            livenessCache.recordHostOnline(hostId, true);
            // 连通成功：清除退避状态，恢复每周期探测
            resetBackoff(hostId);
            try {
                SshCommandResult result = MonitorChannelExec.run(session, buildProbeCommand(numericPids),
                        monitorProperties.livenessTimeoutMillis());
                applyInstanceLiveness(instances, result.stdout());
            } catch (Exception e) {
                // 主机在线但实例探测失败：本轮不回填存活，保持原值待过期，不翻转主机在线判定
                log.debug("主机 [{}] 在线但实例存活探测失败, 存活保持原状待过期: {}", hostId, e.getMessage());
            }
        } catch (Exception e) {
            // 连接失败：主机离线；不更新其上实例存活，防主机抖动把实例误报为已停
            livenessCache.recordHostOnline(hostId, false);
            // 登记失败并进入/加深退避，避免对凭据失效/长期离线主机每周期反复连接
            registerProbeFailure(hostId);
            log.debug("主机 [{}] 在线检测连接失败: {}", hostId, e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * 构造探测命令：有目标 PID 时合并成单条 {@code ps -p p1,p2 -o pid=}（去表头、每行一个存活 PID）；
     * 无目标实例时只 {@code echo 1} 验主机在线。
     */
    private String buildProbeCommand(Set<String> numericPids) {
        if (numericPids.isEmpty()) {
            return "echo 1";
        }
        // numericPids 已全为纯数字，逗号拼接不存在注入面
        return "ps -p " + String.join(",", numericPids) + " -o pid=";
    }

    /**
     * 按 ps 输出回填实例存活：输出中出现的 PID 记存活，未出现记不存活（崩溃）。非数字 PID 的实例不探测、
     * 不回填（保持缺失 → 「状态未知」）。
     */
    private void applyInstanceLiveness(List<RunningInstanceView> instances, String psOutput) {
        Set<String> alivePids = parseAlivePids(psOutput);
        for (RunningInstanceView instance : instances) {
            String processId = instance.processId();
            if (!isNumeric(processId)) {
                continue;
            }
            livenessCache.recordInstanceAlive(instance.deploymentRecordId(), alivePids.contains(processId));
        }
    }

    /**
     * 解析 {@code ps -p ... -o pid=} 输出为存活 PID 集合（逐行 trim 取纯数字 token）。
     */
    private Set<String> parseAlivePids(String psOutput) {
        if (!StringUtils.hasText(psOutput)) {
            return Set.of();
        }
        Set<String> pids = new HashSet<>();
        for (String line : psOutput.split("\\R")) {
            String token = line.trim();
            if (isNumeric(token)) {
                pids.add(token);
            }
        }
        return pids;
    }

    private boolean isNumeric(String value) {
        return StringUtils.hasText(value) && value.chars().allMatch(Character::isDigit);
    }

    /**
     * 本轮是否跳过该主机：仍在退避窗口内则消耗一个跳过额度并跳过。连续探测失败的主机据此自动降频，
     * 避免对凭据失效 / 长期离线主机每 {@code liveness-interval} 反复连接触发远端 fail2ban / 账户锁定。
     */
    private boolean inBackoff(String hostId) {
        int remaining = backoffSkipsRemaining.getOrDefault(hostId, 0);
        if (remaining > 0) {
            backoffSkipsRemaining.put(hostId, remaining - 1);
            return true;
        }
        return false;
    }

    /**
     * 登记一次连接失败并设置退避窗口：首次失败不退避（容忍瞬时抖动），其后跳过周期数随连续失败递增、
     * 封顶 {@link #MAX_BACKOFF_CYCLES}（如失败 2 次跳 1 轮、3 次跳 2 轮……）。
     */
    private void registerProbeFailure(String hostId) {
        int streak = consecutiveFailures.merge(hostId, 1, Integer::sum);
        backoffSkipsRemaining.put(hostId, Math.min(streak - 1, MAX_BACKOFF_CYCLES));
    }

    /**
     * 连通成功后清除退避状态，恢复每周期探测。
     */
    private void resetBackoff(String hostId) {
        consecutiveFailures.remove(hostId);
        backoffSkipsRemaining.remove(hostId);
    }
}
