package com.xiaozhanke.deploy.monitor;

/**
 * 资源采样过程中 SSH 连接/命令执行失败时抛出的非受检异常。
 *
 * <p>仅用于监控采样内部链路：由 {@link MonitorSshConnectionPool} 抛出、{@link HostMetricSampler}
 * 按主机捕获并降级为该主机本周期不可用（{@code reachable=false}），<strong>不</strong>映射为 HTTP 响应，
 * 故区别于面向 API 的 {@code BusinessException}。
 *
 * @author xiaozhanke
 */
public class MonitorSampleException extends RuntimeException {

    public MonitorSampleException(String message, Throwable cause) {
        super(message, cause);
    }
}
