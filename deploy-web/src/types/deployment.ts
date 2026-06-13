import type { ApplicationTypeEnum, DeploymentStatusEnum, JobStatusEnum, JobTypeEnum } from '@/enums/platform'

import type { FileRecord } from './file'
import type { HostRecord } from './host'

/**
 * 部署记录
 */
export interface DeploymentRecord {
  id: string
  hostRecord: HostRecord
  fileRecord: FileRecord
  applicationType: keyof typeof ApplicationTypeEnum
  deploymentPath: string
  deploymentConfigPath: string
  port: number
  programArgs: string
  activeProfiles: string
  status: keyof typeof DeploymentStatusEnum
  errorMessage: string
  deployTime: string
  lastStartTime: string
  lastStopTime: string
  processId: string
  running: boolean
  /** 最近一次作业(按 createTime 取最新);该记录从未执行过作业时为空 */
  latestJob?: DeploymentJob
}

/**
 * 部署参数
 */
export interface DeploymentParams {
  hostRecordId: string
  fileRecordId: string
  applicationType: keyof typeof ApplicationTypeEnum
  deploymentPath: string
  deploymentConfigPath?: string
  port?: number
  programArgs?: string
  activeProfiles?: string
  status: keyof typeof DeploymentStatusEnum
  errorMessage?: string
  deployTime?: string
  lastStartTime?: string
  lastStopTime?: string
  processId?: string
  running?: boolean
}

/**
 * 部署作业(对应后端 DeploymentJobVo)
 */
export interface DeploymentJob {
  id: string
  deploymentRecordId: string
  jobType: keyof typeof JobTypeEnum
  status: keyof typeof JobStatusEnum
  clientRequestId: string
  retryCount: number
  errorMessage: string
  startTime: string
  endTime: string
  createTime: string
  updateTime: string
}

/**
 * 创建部署作业请求
 */
export interface CreateJobRequest {
  jobType: keyof typeof JobTypeEnum
  clientRequestId: string
  /** 期望执行时间(ISO 格式);设置后创建延迟作业,留空则立即执行 */
  executeAt?: string
  /** 目标文件记录 Id(仅 UPDATE 作业必填):要更新到的新应用包 */
  fileRecordId?: string
}

/**
 * 操作审计日志(对应后端 AuditLogVo)
 */
export interface AuditLog {
  id: string
  operator: string
  operationType: string
  target: string
  description: string
  outcome: 'SUCCESS' | 'FAILURE'
  errorMessage: string
  clientIp: string
  operationTime: string
}

/**
 * 审计日志查询参数
 */
export interface AuditLogQueryParams {
  operator?: string
  operationType?: string
  outcome?: string
}

/**
 * 死信消息(对应后端 DeadLetterMessageVo)
 */
export interface DeadLetterMessage {
  id: string
  jobId: string
  deploymentRecordId: string
  jobType: keyof typeof JobTypeEnum
  errorMessage: string
  originalPayload: string
  deadLetteredAt: string
  retried: boolean
  retriedJobId: string
  createTime: string
}
