package com.xiaozhanke.deploy.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * 精确统计 {@code /topic/monitor/hosts} 这一 destination 的订阅数，用于门控资源采样器的休眠/唤醒。
 *
 * <p><strong>只</strong>按该 destination 计数，<strong>不</strong>按全局 WebSocket 订阅总数——否则浏览器
 * SSH 终端、{@code /topic/activities} 时间轴、作业状态频道等订阅会让采样器永不休眠。
 *
 * <p>{@code SessionUnsubscribeEvent} 不携带 destination，故订阅时以 {@code sessionId|subscriptionId} 为键
 * 记下「这是一个监控订阅」，退订/断开时按键移除。订阅数在 0 与正数之间跨越时发布
 * {@link MonitorActivationEvent} 通知采样器。
 *
 * @author xiaozhanke
 */
@Slf4j
@Component
public class MonitorSubscriptionTracker {

    /**
     * 受监控的订阅目标地址（全量主机指标快照推送频道）。
     */
    public static final String MONITOR_HOSTS_DESTINATION = "/topic/monitor/hosts";

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 监控订阅集合，元素为 {@code sessionId|subscriptionId}。所有读写经 {@code synchronized(this)} 守护，
     * 保证订阅数跨越 0 边界的转换判定与事件发布原子化、不重复触发。
     */
    private final Set<String> monitorSubscriptions = new HashSet<>();

    public MonitorSubscriptionTracker(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 当前是否有人正在观看资源监控看板。
     */
    public synchronized boolean hasSubscribers() {
        return !monitorSubscriptions.isEmpty();
    }

    /**
     * 当前监控订阅数（仅用于日志/观测）。
     */
    public synchronized int count() {
        return monitorSubscriptions.size();
    }

    /**
     * 订阅事件：仅当目标为 {@link #MONITOR_HOSTS_DESTINATION} 时记入，并在 0→正 跨越时发布唤醒事件。
     */
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (!MONITOR_HOSTS_DESTINATION.equals(accessor.getDestination())) {
            return;
        }
        String key = subscriptionKey(accessor.getSessionId(), accessor.getSubscriptionId());
        if (key == null) {
            return;
        }
        boolean activated;
        synchronized (this) {
            boolean wasEmpty = monitorSubscriptions.isEmpty();
            monitorSubscriptions.add(key);
            activated = wasEmpty && !monitorSubscriptions.isEmpty();
        }
        log.debug("监控订阅 +1: {} (当前 {})", key, count());
        if (activated) {
            eventPublisher.publishEvent(new MonitorActivationEvent(true));
        }
    }

    /**
     * 退订事件：按 {@code sessionId|subscriptionId} 移除，正→0 跨越时发布休眠事件。
     */
    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String key = subscriptionKey(accessor.getSessionId(), accessor.getSubscriptionId());
        if (key == null) {
            return;
        }
        removeKeysAndMaybeDeactivate(removed -> removed.remove(key), key);
    }

    /**
     * 断开事件：移除该会话名下所有监控订阅（断开不会逐个发 UNSUBSCRIBE），正→0 跨越时发布休眠事件。
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) {
            return;
        }
        String prefix = sessionId + "|";
        removeKeysAndMaybeDeactivate(set -> {
            Iterator<String> iterator = set.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().startsWith(prefix)) {
                    iterator.remove();
                }
            }
        }, sessionId);
    }

    /**
     * 在同步块内执行移除操作，若订阅数由正变 0 则发布休眠事件（移除动作与转换判定原子化）。
     */
    private void removeKeysAndMaybeDeactivate(java.util.function.Consumer<Set<String>> removal, String context) {
        boolean deactivated;
        synchronized (this) {
            boolean wasNonEmpty = !monitorSubscriptions.isEmpty();
            removal.accept(monitorSubscriptions);
            deactivated = wasNonEmpty && monitorSubscriptions.isEmpty();
        }
        log.debug("监控订阅移除: {} (当前 {})", context, count());
        if (deactivated) {
            eventPublisher.publishEvent(new MonitorActivationEvent(false));
        }
    }

    private String subscriptionKey(String sessionId, String subscriptionId) {
        if (sessionId == null || subscriptionId == null) {
            return null;
        }
        return sessionId + "|" + subscriptionId;
    }
}
