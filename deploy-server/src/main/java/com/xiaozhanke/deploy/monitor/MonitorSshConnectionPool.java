package com.xiaozhanke.deploy.monitor;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.xiaozhanke.deploy.core.ssh.SshSessionFactory;
import com.xiaozhanke.deploy.model.dto.HostRecordDto;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资源采样器独占的 per-host JSch {@link Session} 长连接池。
 *
 * <p>池化的<strong>唯一理由</strong>是 5s 高频采样：同一台主机的物理 Session 跨周期复用，每周期只在其上
 * 开临时 {@link MonitorChannelExec} 跑 {@code /proc} 读取命令，命令结束仅关闭 Channel、Session 保持存活。
 * 本池<strong>仅供</strong> {@link HostMetricSampler} 使用，与 {@code SshService} 的交互式/SFTP 会话池、
 * 以及在线检测的短连接彼此<strong>不共享</strong>，失败域完全隔离。
 *
 * <p>连接超时强制用监控参数（{@code app.monitor.connect-timeout-millis} / {@code command-timeout-millis}）
 * 覆盖 HostRecord 的 per-host 配置——监控路径必须快速失败，避免目标主机离线时线程长期悬挂。
 *
 * <p>失效处理（简化）：因池只有采样器一个使用者、同一 Session 上不存在并发 Channel，命令失败时直接
 * {@link #evict 本周期断开、下周期重建}，无需「标记 INVALID 延迟拆除」。订阅归零时由采样器调用
 * {@link #evictAll()} 整池拆光休眠。
 *
 * <p>线程安全：同一台主机在一个采样周期内至多被一个线程访问（{@code @Scheduled(fixedDelay)} 周期不重叠、
 * 周期内按主机并发），故 {@link #pool} 仅需对「不同 key 并发」安全，用 {@link ConcurrentHashMap} 即可。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
public class MonitorSshConnectionPool {

    private final SshSessionFactory sshSessionFactory;
    private final MonitorProperties monitorProperties;

    /**
     * key = hostId，value = 该主机的物理长连接 Session。
     */
    private final Map<String, Session> pool = new ConcurrentHashMap<>();

    public MonitorSshConnectionPool(SshSessionFactory sshSessionFactory, MonitorProperties monitorProperties) {
        this.sshSessionFactory = sshSessionFactory;
        this.monitorProperties = monitorProperties;
    }

    /**
     * 在该主机的长连接上执行一条命令并返回标准输出。
     *
     * <p>复用池中已连接的 Session（无则新建并连接），开临时 {@code ChannelExec} 执行命令，读完标准输出后
     * 关闭 Channel、保留 Session。任一环节失败即驱逐该主机的 Session（下周期重建）并抛
     * {@link MonitorSampleException}。
     *
     * @param host    主机连接详情（含凭据）
     * @param command 远程命令（只应是只读的 {@code /proc} 解析命令）
     * @return 命令标准输出（UTF-8）
     * @throws MonitorSampleException 连接或命令执行失败
     */
    public String execute(HostRecordDto host, String command) {
        String hostId = host.getId();
        Session session = obtainSession(host);
        try {
            // 命令执行细节（开/关临时 ChannelExec、丢弃标准错误、读尽标准输出）统一委托 MonitorChannelExec
            SshCommandResult result = MonitorChannelExec.run(session, command, monitorProperties.commandTimeoutMillis());
            log.debug("主机 [{}] 监控命令执行完成, exit={}", hostId, result.exitStatus());
            return result.stdout();
        } catch (JSchException | IOException e) {
            // 失效即本周期断开、下周期重建（简化失效处理，见类注释）
            evict(hostId);
            throw new MonitorSampleException(
                    String.format("主机 [%s] 监控命令执行失败: %s", hostId, e.getMessage()), e);
        }
    }

    /**
     * 驱逐并断开指定主机的 Session（下次 {@link #execute} 时重建）。
     */
    public void evict(String hostId) {
        Session session = pool.remove(hostId);
        disconnectQuietly(hostId, session);
    }

    /**
     * 整池拆光：断开所有主机的 Session 并清空。订阅归零时由采样器调用，实现「0 采样开销」休眠。
     */
    public void evictAll() {
        if (pool.isEmpty()) {
            return;
        }
        log.info("资源采样订阅归零, 整池拆光 {} 个监控 SSH 连接", pool.size());
        // 先快照 key 再逐个移除，避免遍历视图与并发移除互相干扰
        for (String hostId : new ArrayList<>(pool.keySet())) {
            evict(hostId);
        }
    }

    /**
     * 当前池中长连接数（仅用于日志/观测）。
     */
    public int size() {
        return pool.size();
    }

    /**
     * 取该主机已连接的 Session；无或已断开则新建并连接（用监控超时覆盖 per-host 配置）。
     */
    private Session obtainSession(HostRecordDto host) {
        String hostId = host.getId();
        Session existing = pool.get(hostId);
        if (existing != null && existing.isConnected()) {
            return existing;
        }
        // 残留的已断开 Session 先清掉
        if (existing != null) {
            evict(hostId);
        }
        try {
            Session session = sshSessionFactory.createSession(host);
            // 监控路径强制快速失败：覆盖 HostRecord 的 per-host 超时
            session.setTimeout(monitorProperties.commandTimeoutMillis());
            session.connect(monitorProperties.connectTimeoutMillis());
            pool.put(hostId, session);
            log.debug("主机 [{}] 监控长连接已建立", hostId);
            return session;
        } catch (JSchException e) {
            throw new MonitorSampleException(
                    String.format("主机 [%s] 监控连接建立失败: %s", hostId, e.getMessage()), e);
        }
    }

    private void disconnectQuietly(String hostId, Session session) {
        if (session != null && session.isConnected()) {
            try {
                session.disconnect();
                log.debug("主机 [{}] 监控长连接已断开", hostId);
            } catch (Exception e) {
                log.debug("断开主机 [{}] 监控长连接时忽略异常: {}", hostId, e.getMessage());
            }
        }
    }

    /**
     * 应用关闭时拆光所有监控连接，避免遗留 SSH 会话。
     */
    @PreDestroy
    public void shutdown() {
        evictAll();
    }
}
