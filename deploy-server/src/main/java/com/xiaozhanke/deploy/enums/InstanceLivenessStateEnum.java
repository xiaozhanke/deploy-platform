package com.xiaozhanke.deploy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用实例存活性三态。
 *
 * <p>在<strong>读取时</strong>由 {@code (running, processId, 探测缓存)} 派生，<strong>不落任何新字段</strong>：
 * <ul>
 *   <li>{@code running=false} → {@link #STOPPED}</li>
 *   <li>{@code running=true & processId=null} → {@link #UNKNOWN}</li>
 *   <li>{@code running=true & 探测=存活} → {@link #RUNNING}</li>
 *   <li>{@code running=true & 探测=不存活} → {@link #STOPPED}（崩溃）</li>
 *   <li>{@code running=true & 探测缺失/首轮/主机离线} → {@link #UNKNOWN}（防 PID 数据缺失误报）</li>
 * </ul>
 * KPI「运行中实例」仅计 {@link #RUNNING}。
 *
 * @author xiaozhanke
 */
@Getter
@AllArgsConstructor
public enum InstanceLivenessStateEnum {

    /**
     * 运行中：意图为运行，且探测到进程存活。
     */
    RUNNING("运行中"),

    /**
     * 已停止：意图为停止，或意图为运行但探测到进程已不存在（崩溃）。
     */
    STOPPED("已停止"),

    /**
     * 状态未知：意图为运行但缺少 PID、或尚无有效探测结果（首轮/主机离线/缓存过期）。
     */
    UNKNOWN("状态未知");

    private final String description;
}
