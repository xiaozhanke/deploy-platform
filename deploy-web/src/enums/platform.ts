import { createEnum } from '@/enums/common'

/**
 * 应用类型枚举
 */
export const ApplicationTypeEnum = createEnum({
  FRONTEND: { value: 'FRONTEND', label: '前端应用' },
  BACKEND: { value: 'BACKEND', label: '后端应用' },
} as const)

/**
 * 芯片架构枚举
 */
export const ArchitectureEnum = createEnum({
  X86: { value: 'X86', label: 'X86 架构' },
  X64: { value: 'X64', label: 'x64 架构' },
  ARM: { value: 'ARM', label: 'ARM 架构' },
  AARCH64: { value: 'AARCH64', label: 'AARCH64 架构' },
  UNKNOWN: { value: 'UNKNOWN', label: '未知架构' },
} as const)

/**
 * 部署状态枚举
 */
export const DeploymentStatusEnum = createEnum({
  DEPLOYING: { value: 'DEPLOYING', label: '部署中' },
  SUCCESS: { value: 'SUCCESS', label: '部署成功' },
  FAILED: { value: 'FAILED', label: '部署失败' },
} as const)

/**
 * 文件操作类型枚举
 */
export const FileOperationEnum = createEnum({
  UPLOAD: { value: 'UPLOAD', label: '上传' },
  DOWNLOAD: { value: 'DOWNLOAD', label: '下载' },
} as const)

/**
 * 文件适用范围枚举
 */
export const FileScopeEnum = createEnum({
  ENVIRONMENT: { value: 'ENVIRONMENT', label: '环境安装' },
  CONFIGURATION: { value: 'CONFIGURATION', label: '配置文件' },
  APPLICATION_BACKEND: { value: 'APPLICATION_BACKEND', label: '后端应用' },
  APPLICATION_FRONTEND: { value: 'APPLICATION_FRONTEND', label: '前端应用' },
} as const)

/**
 * SSH 认证方式枚举
 */
export const SshAuthTypeEnum = createEnum({
  PASSWORD: { value: 'PASSWORD', label: '密码认证' },
  KEY: { value: 'KEY', label: '密钥认证' },
  KEY_WITH_PASS: { value: 'KEY_WITH_PASS', label: '带密码的密钥认证' },
} as const)

/**
 * 用户状态枚举
 */
export const UserStatusEnum = createEnum({
  INITIALIZED: { value: 'INITIALIZED', label: '初始' },
  ACTIVE: { value: 'ACTIVE', label: '正常' },
  LOCKED: { value: 'LOCKED', label: '锁定' },
  DISABLED: { value: 'DISABLED', label: '停用' },
} as const)

/**
 * 部署作业状态枚举(对应后端 JobStatusEnum)
 */
export const JobStatusEnum = createEnum({
  PENDING: { value: 'PENDING', label: '待执行' },
  IN_PROGRESS: { value: 'IN_PROGRESS', label: '执行中' },
  SUCCESS: { value: 'SUCCESS', label: '成功' },
  FAILED: { value: 'FAILED', label: '失败' },
  DEAD: { value: 'DEAD', label: '死信' },
  CANCELLED: { value: 'CANCELLED', label: '已取消' },
} as const)

/**
 * 部署作业类型枚举(对应后端 JobTypeEnum)
 */
export const JobTypeEnum = createEnum({
  START: { value: 'START', label: '启动' },
  STOP: { value: 'STOP', label: '停止' },
  RESTART: { value: 'RESTART', label: '重启' },
  UPDATE: { value: 'UPDATE', label: '更新' },
} as const)

/**
 * 部署作业状态 → el-tag 类型映射(应用管理「最近作业」列、作业历史抽屉共用)
 */
const JOB_STATUS_TAG_TYPE: Record<string, 'success' | 'warning' | 'info' | 'primary' | 'danger'> = {
  [JobStatusEnum.PENDING.value]: 'info',
  [JobStatusEnum.IN_PROGRESS.value]: 'primary',
  [JobStatusEnum.SUCCESS.value]: 'success',
  [JobStatusEnum.FAILED.value]: 'warning',
  [JobStatusEnum.DEAD.value]: 'danger',
  [JobStatusEnum.CANCELLED.value]: 'info',
}

/**
 * 取作业状态对应的 el-tag 类型,未知状态回退到 info。
 */
export const jobStatusTagType = (status?: string): 'success' | 'warning' | 'info' | 'primary' | 'danger' =>
  (status && JOB_STATUS_TAG_TYPE[status]) || 'info'

/**
 * 审计操作类型枚举(对应后端 AuditOperationTypeEnum,场景 4)
 */
export const AuditOperationTypeEnum = createEnum({
  SSH_EXEC: { value: 'SSH_EXEC', label: 'SSH 命令执行' },
  FILE_UPLOAD: { value: 'FILE_UPLOAD', label: '文件上传' },
  FILE_DOWNLOAD: { value: 'FILE_DOWNLOAD', label: '文件下载' },
  FILE_DELETE: { value: 'FILE_DELETE', label: '文件删除' },
  LOGIN: { value: 'LOGIN', label: '登录' },
  LOGOUT: { value: 'LOGOUT', label: '登出' },
  DEPLOYMENT_CREATE: { value: 'DEPLOYMENT_CREATE', label: '创建部署记录' },
  DEPLOYMENT_UPDATE: { value: 'DEPLOYMENT_UPDATE', label: '更新部署记录' },
  DEPLOYMENT_DELETE: { value: 'DEPLOYMENT_DELETE', label: '删除部署记录' },
  JOB_CREATE: { value: 'JOB_CREATE', label: '创建部署作业' },
  JOB_CANCEL: { value: 'JOB_CANCEL', label: '取消部署作业' },
  SERVER_CREATE: { value: 'SERVER_CREATE', label: '创建服务器' },
  SERVER_UPDATE: { value: 'SERVER_UPDATE', label: '更新服务器' },
  SERVER_DELETE: { value: 'SERVER_DELETE', label: '删除服务器' },
  USER_CREATE: { value: 'USER_CREATE', label: '创建用户' },
  USER_UPDATE: { value: 'USER_UPDATE', label: '更新用户' },
  USER_DELETE: { value: 'USER_DELETE', label: '删除用户' },
  ROLE_CREATE: { value: 'ROLE_CREATE', label: '创建角色' },
  ROLE_UPDATE: { value: 'ROLE_UPDATE', label: '更新角色' },
  ROLE_DELETE: { value: 'ROLE_DELETE', label: '删除角色' },
} as const)

/**
 * 审计操作结果枚举(对应后端 AuditOutcomeEnum,场景 4)
 */
export const AuditOutcomeEnum = createEnum({
  SUCCESS: { value: 'SUCCESS', label: '成功' },
  FAILURE: { value: 'FAILURE', label: '失败' },
} as const)

/**
 * 审计操作结果标签类型映射(场景 4)
 */
const AUDIT_OUTCOME_TAG_TYPE: Record<string, 'success' | 'danger'> = {
  SUCCESS: 'success',
  FAILURE: 'danger',
}

export const auditOutcomeTagType = (outcome?: string): 'success' | 'danger' | 'info' =>
  (outcome && AUDIT_OUTCOME_TAG_TYPE[outcome]) || 'info'
