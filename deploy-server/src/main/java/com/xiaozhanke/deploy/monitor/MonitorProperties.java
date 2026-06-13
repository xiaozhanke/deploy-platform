package com.xiaozhanke.deploy.monitor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 免 Agent 资源监控的运行期可调参数。
 *
 * <p>全部带默认值：不配置 {@code app.monitor.*} 也能按下列默认运行。生产可在对应 profile 覆盖。
 *
 * @param sampleInterval        资源采样周期（CPU/内存），默认 5s
 * @param livenessInterval      主机/实例在线检测周期，默认 60s
 * @param connectTimeoutMillis  采样 SSH 会话连接超时（覆盖 per-host 配置，监控路径必须快速失败），默认 3000ms
 * @param commandTimeoutMillis  采样 SSH 通道/命令超时，默认 5000ms
 * @param livenessTimeoutMillis 在线检测短连接超时，默认 1500ms
 * @param samplingConcurrency   单周期内并发采样/探测的最大主机数，默认 8
 * @param cacheTtl              在线检测结果缓存的兜底过期时长（探测任务停摆时让陈旧条目自动过期为「未知」），
 *                              默认 180s（约 3× livenessInterval）
 * @author xiaozhanke
 */
@ConfigurationProperties(prefix = "app.monitor")
public record MonitorProperties(
        @DefaultValue("5s") Duration sampleInterval,
        @DefaultValue("60s") Duration livenessInterval,
        @DefaultValue("3000") int connectTimeoutMillis,
        @DefaultValue("5000") int commandTimeoutMillis,
        @DefaultValue("1500") int livenessTimeoutMillis,
        @DefaultValue("8") int samplingConcurrency,
        @DefaultValue("180s") Duration cacheTtl) {

    public MonitorProperties {
        if (samplingConcurrency <= 0) {
            throw new IllegalArgumentException("app.monitor.sampling-concurrency 必须大于 0");
        }
        if (connectTimeoutMillis <= 0 || commandTimeoutMillis <= 0 || livenessTimeoutMillis <= 0) {
            throw new IllegalArgumentException("app.monitor.*-timeout-millis 必须大于 0");
        }
    }
}
