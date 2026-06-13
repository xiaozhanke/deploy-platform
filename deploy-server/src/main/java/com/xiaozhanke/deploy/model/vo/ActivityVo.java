package com.xiaozhanke.deploy.model.vo;

import com.xiaozhanke.deploy.enums.JobStatusEnum;
import com.xiaozhanke.deploy.enums.JobTypeEnum;

import java.time.LocalDateTime;

/**
 * 控制台「最新发版动态」时间轴的一条紧凑动态（activity）。
 *
 * <p>既是 HTTP 初次拉取最近 N 条的响应项，也是作业状态变更时经 {@code /topic/activities} 全平台广播的
 * 增量推送载荷。用 JPQL JOIN 投影（作业 + 部署记录 + 主机）一次取齐，不暴露实体、不继承 {@code BaseVo}。
 *
 * @param jobId              作业 Id
 * @param deploymentRecordId 关联部署记录 Id（前端点击行深链跳转用）
 * @param jobType            作业类型（时间轴标签）
 * @param status             作业当前状态（时间轴状态徽章）
 * @param triggerUser        触发人（作业 createUser，由 @CreatedBy 在 HTTP 创建线程内写入）
 * @param hostName           关联主机名称
 * @param hostAddress        关联主机地址
 * @param occurredAt         作业创建时刻（时间轴排序与展示基准）
 * @author xiaozhanke
 */
public record ActivityVo(
        String jobId,
        String deploymentRecordId,
        JobTypeEnum jobType,
        JobStatusEnum status,
        String triggerUser,
        String hostName,
        String hostAddress,
        LocalDateTime occurredAt) {
}
