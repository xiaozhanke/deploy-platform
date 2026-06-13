package com.xiaozhanke.deploy.model.vo;

import java.time.LocalDateTime;

/**
 * 单台主机的一次资源采样快照，经 STOMP 推送到 {@code /topic/monitor/hosts}（全量快照里的一项）。
 *
 * <p>不同于其它 JPA 投影 VO，本对象是<strong>瞬时推送载荷</strong>而非实体投影，故为不可变 record、
 * 不继承 {@code BaseVo}（无审计字段）、不落库。CPU/内存解析失败时对应字段为
 * {@code null}，前端显示 {@code --} 而非误导性的 {@code 0%}。
 *
 * @param hostId      主机 Id
 * @param hostName    主机名称
 * @param address     主机地址
 * @param cpuUsage    CPU 利用率百分比（0–100），不可用为 {@code null}（首轮采样 / 采样失败）
 * @param memoryUsage 内存使用率百分比（0–100），不可用为 {@code null}
 * @param reachable   本周期采样 SSH 是否连通成功
 * @param sampleTime  采样时刻
 * @author xiaozhanke
 */
public record HostMetricVo(
        String hostId,
        String hostName,
        String address,
        Double cpuUsage,
        Double memoryUsage,
        boolean reachable,
        LocalDateTime sampleTime) {
}
