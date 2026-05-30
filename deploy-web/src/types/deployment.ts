import type {
  ApplicationTypeEnum,
  DeploymentStatusEnum,
  JobStatusEnum,
  JobTypeEnum,
} from '@/enums/platform'
import type { ServerRecord } from './server'
import type { FileRecord } from './file'

/**
 * 部署记录
 */
export interface DeploymentRecord {
  id: string
  serverRecord: ServerRecord
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
}

/**
 * 部署参数
 */
export interface DeploymentParams {
  serverRecordId: string
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
