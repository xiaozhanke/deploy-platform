package com.xiaozhanke.deploy.monitor;

/**
 * {@code /proc/stat} 第一行 CPU 累计 Jiffies 的一次快照。
 *
 * <p>只保留计算利用率所需的两个聚合量：{@code idle}（空闲列）与 {@code total}（该行全部数值字段之和）。
 * 利用率由 {@link ProcMetricParser#computeCpuUsagePercent} 用两次快照差值算出。
 *
 * @param idle  空闲 Jiffies（{@code /proc/stat} 第一行第 4 个数值字段，仅 idle 本列）
 * @param total 该行全部数值字段之和（含 user/nice/system/idle/iowait/irq/softirq/steal/...）
 * @author xiaozhanke
 */
public record CpuStat(long idle, long total) {
}
