import type { JobStatusEnum, JobTypeEnum } from '@/enums/platform'

/**
 * 控制台顶部 KPI 指标行聚合（对应后端 ConsoleKpiVo）。
 */
export interface ConsoleKpi {
  /** 在线主机数（监控缓存中连通的主机） */
  onlineHostCount: number
  /** 未删除主机总数 */
  totalHostCount: number
  /** 运行中实例数（running 且存活探测命中） */
  runningInstanceCount: number
  /** 未删除部署实例总数（含已停止 / 状态未知） */
  totalInstanceCount: number
  /** 在途作业数（PENDING 或 IN_PROGRESS） */
  inFlightJobCount: number
  /** 未处理死信数（未人工重试） */
  unprocessedDeadLetterCount: number
}

/**
 * 控制台「最新发版动态」时间轴的一条动态（对应后端 ActivityVo）。
 *
 * <p>既来自 HTTP 初次拉取，也来自 /topic/activities 全平台广播的增量推送。
 */
export interface Activity {
  jobId: string
  deploymentRecordId: string
  jobType: keyof typeof JobTypeEnum
  status: keyof typeof JobStatusEnum
  /** 触发人（作业 createUser） */
  triggerUser: string
  hostName: string
  hostAddress: string
  /** 作业创建时刻（时间轴排序与展示基准，ISO 字符串） */
  occurredAt: string
}

/**
 * 主机资源监控的一次采样快照（对应后端 HostMetricVo，经 /topic/monitor/hosts 推送全量快照）。
 *
 * <p>CPU / 内存解析失败或首轮采样时为 null，前端显示 --（不可用）而非误导性的 0%。
 */
export interface HostMetric {
  hostId: string
  hostName: string
  address: string
  /** CPU 利用率百分比（0–100），不可用为 null */
  cpuUsage: number | null
  /** 内存使用率百分比（0–100），不可用为 null */
  memoryUsage: number | null
  /** 本周期采样 SSH 是否连通 */
  reachable: boolean
  /** 采样时刻（ISO 字符串） */
  sampleTime: string
}
