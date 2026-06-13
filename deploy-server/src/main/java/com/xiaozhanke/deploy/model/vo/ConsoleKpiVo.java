package com.xiaozhanke.deploy.model.vo;

/**
 * 控制台顶部 KPI 指标行的聚合快照（4 组指标）。
 *
 * <p>数据源混合、不统一走缓存：在线主机数 / 运行中实例数读监控内存缓存（{@code LivenessCache}），
 * 主机 / 实例总数与在途作业 / 未处理死信走带索引的实时 DB COUNT。前端 30s 轮询一次本端点。
 *
 * <p>不继承 {@code BaseVo}——它是聚合统计而非实体投影，无审计字段、不落库。
 *
 * @param onlineHostCount            在线主机数（监控缓存中连通的主机）
 * @param totalHostCount             未删除主机总数
 * @param runningInstanceCount       运行中实例数（running=true 且存活探测命中）
 * @param totalInstanceCount         未删除部署实例总数（含已停止 / 状态未知）
 * @param inFlightJobCount           在途作业数（PENDING 或 IN_PROGRESS）
 * @param unprocessedDeadLetterCount 未处理死信数（未人工重试）
 * @author xiaozhanke
 */
public record ConsoleKpiVo(
        long onlineHostCount,
        long totalHostCount,
        long runningInstanceCount,
        long totalInstanceCount,
        long inFlightJobCount,
        long unprocessedDeadLetterCount) {
}
