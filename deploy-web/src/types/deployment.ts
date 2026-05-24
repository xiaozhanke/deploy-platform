import type { ApplicationTypeEnum, DeploymentStatusEnum } from '@/enums/platform'
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
