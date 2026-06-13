package com.xiaozhanke.deploy.monitor;

/**
 * 资源采样订阅激活态变更事件：{@code /topic/monitor/hosts} 的订阅数在 0 与正数之间跨越时由
 * {@link MonitorSubscriptionTracker} 发布，{@link HostMetricSampler} 监听后立即唤醒采样 / 整池拆光休眠。
 *
 * <p>以事件解耦 tracker → sampler 的方向依赖（sampler 已依赖 tracker 读订阅数），避免双向循环依赖。
 *
 * @param active {@code true}=订阅数 0→正（唤醒）；{@code false}=订阅数→0（休眠）
 * @author xiaozhanke
 */
public record MonitorActivationEvent(boolean active) {
}
