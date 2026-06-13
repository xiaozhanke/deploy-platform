import type { PageParams, PageResult } from '@/types/api'
import type { PasswordForm, UserProfile } from '@/types/auth'
import type {
  AuditLog,
  AuditLogQueryParams,
  CreateJobRequest,
  DeadLetterMessage,
  DeploymentJob,
  DeploymentParams,
  DeploymentRecord,
} from '@/types/deployment'
import type { ExecResult, NginxConfigParams } from '@/types/environment'
import type { FileParams, FileRecord } from '@/types/file'
import type { HostParams, HostQueryParams, HostRecord } from '@/types/host'

import request from './request'

/**
 * 测试 API 可用性
 * @returns
 */
export const testPing = (): Promise<string> => {
  return request.get('/test/ping')
}

/**
 * 获取主机列表
 */
export const hostQueryList = (): Promise<Array<HostRecord>> => {
  return request.get('/hosts/list')
}

/**
 * 分页查询主机列表
 * @param queryParams 查询参数
 * @param pageParams 分页参数
 */
export const hostQueryPage = (
  queryParams: HostQueryParams,
  pageParams?: PageParams,
): Promise<PageResult<HostRecord>> => {
  return request.get('/hosts/page', { params: { ...queryParams, ...pageParams } })
}

/**
 * 添加主机
 * @param host 主机信息
 */
export const hostAdd = (host: HostParams): Promise<HostRecord> => {
  return request.post('/hosts', host)
}

/**
 * 更新主机信息
 * @param id 主机 Id
 * @param host 主机信息
 */
export const hostUpdate = (id: string, host: HostParams): Promise<HostRecord> => {
  return request.put(`/hosts/${id}`, host)
}

/**
 * 删除主机
 * @param id 主机 Id
 */
export const hostDelete = (id: string): Promise<void> => {
  return request.delete(`/hosts/${id}`)
}

/**
 * 获取主机信息
 * @param id 主机 Id
 */
export const hostQueryById = (id: string): Promise<HostRecord> => {
  return request.get(`/hosts/${id}`)
}

/**
 * 测试主机连接（请求体携带连接信息，供新增等尚未保存的场景使用）
 * @param host 主机信息
 */
export const hostTestConnection = (host: HostParams): Promise<boolean> => {
  return request.post('/hosts/test-connection', host)
}

/**
 * 测试已保存主机连接（按 Id，凭据由后端取出，分页数据不含密码）
 * @param id 主机 Id
 */
export const hostTestConnectionById = (id: string): Promise<boolean> => {
  return request.post(`/hosts/${id}/test-connection`)
}

/**
 * SSH 连接
 * @param hostId 主机 Id
 */
export const sshConnect = (hostId: string): Promise<string> => {
  return request.post('/ssh/sessions', null, { params: { hostId } })
}

/**
 * SSH 断开连接
 * @param sessionId 会话 Id
 */
export const sshDisconnect = (sessionId: string): Promise<void> => {
  return request.delete(`/ssh/sessions/${sessionId}`)
}

/**
 * SSH 创建 Shell 通道
 * @param sessionId  会话 Id
 */
export const sshShellAdd = (sessionId: string): Promise<string> => {
  return request.post(`/ssh/sessions/${sessionId}/shell`)
}

/**
 * SSH 断开 Shell 通道
 * @param sessionId 会话 Id
 * @param channelId 通道 Id
 */
export const sshShellClose = (sessionId: string, channelId: string): Promise<void> => {
  return request.delete(`/ssh/sessions/${sessionId}/shell/${channelId}`)
}

/**
 * SSH 连接 Exec 通道并执行命令
 * @param sessionId 会话 Id
 * @param command 要执行的命令
 */
export const sshExecCommand = (sessionId: string, command: string): Promise<ExecResult> => {
  return request.post(`/ssh/sessions/${sessionId}/exec`, { command })
}

/**
 * 通过 SFTP 把文本内容覆盖写入远程文件，避免 path 与内容被 shell 解释。
 *
 * useSudo=true 时后端走「SFTP 写 /tmp 临时文件 → exec sudo -n mv 到目标」两步，
 * 用于 /etc/nginx/conf.d 这类 root 目录；要求远端登录用户配置 NOPASSWD sudo。
 *
 * @param sessionId 会话 Id
 * @param remotePath 远程文件绝对路径（POSIX 分隔符 /）
 * @param content 文件内容（UTF-8）
 * @param useSudo 是否提权落盘，默认 false
 */
export const sshWriteFile = (
  sessionId: string,
  remotePath: string,
  content: string,
  useSudo: boolean = false,
): Promise<void> => {
  return request.post(`/ssh/sessions/${sessionId}/file`, { remotePath, content, useSudo })
}

/**
 * 新建 Nginx 配置文件
 * @param params 配置文件参数
 */
export const configNginxAdd = (params: NginxConfigParams): Promise<string> => {
  return request.post('/config/nginx', params)
}

/**
 * 查询文件记录列表
 * @param params 查询参数
 * @param sort 排序参数，格式为 "字段名,排序方式"（如：updateTime,desc）
 */
export const fileQueryList = (params: FileParams, sort?: string): Promise<Array<FileRecord>> => {
  return request.get('/files/list', {
    params: { ...params, sort },
  })
}

/**
 * 分页查询文件记录列表
 * @param queryParams 查询参数
 * @param pageParams 分页参数
 * @returns 分页列表
 */
export const fileQueryPage = (
  queryParams: Partial<FileParams>,
  pageParams?: PageParams,
): Promise<PageResult<FileRecord>> => {
  return request.get('/files/page', { params: { ...queryParams, ...pageParams } })
}

/**
 * 上传文件并保存文件记录
 * @param file 文件对象
 * @param fileParams 文件参数
 */
export const fileUpload = (file: File, fileParams?: FileParams): Promise<FileRecord> => {
  const formData = new FormData()
  formData.append('file', file)
  if (fileParams) {
    Object.entries(fileParams).forEach(([key, value]) => {
      if (value !== undefined) {
        formData.append(key, value as string)
      }
    })
  }

  return request.post('/files', formData)
}

/**
 * 在目录上创建子文件夹
 * @param relativePath 相对路径
 * @param directoryName 文件夹名称
 */
export const fileCreateDirectory = (relativePath: string, directoryName: string): Promise<FileRecord> => {
  return request.post('/files/directories', { relativePath, directoryName })
}

/**
 * 根据文件 Id 删除文件和记录
 * @param id 文件 Id
 */
export const fileDelete = (id: string): Promise<void> => {
  return request.delete(`/files/${id}`)
}

/**
 * 根据文件 Id 下载文件资源
 * @param id 文件 Id
 */
export const fileDownload = (id: string) => {
  return request.get<Blob>(`/files/${id}`, {
    responseType: 'blob',
  })
}

/**
 * 更新文件记录元数据
 * @param id 文件 Id
 * @param params 文件参数
 */
export const fileUpdateMetadata = (id: string, params: FileParams): Promise<FileRecord> => {
  return request.put(`/files/${id}/metadata`, params)
}

/**
 * 更新文件记录原始文件
 * @param id 文件 Id
 * @param file 原始文件
 */
export const fileUpdateRaw = (id: string, file: File): Promise<FileRecord> => {
  const formData = new FormData()
  formData.append('file', file)
  return request.put(`/files/${id}/raw`, formData)
}

/**
 * 查询文件绝对路径
 * @param params 文件参数
 * @returns 文件绝对路径
 */
export const fileQueryPath = (params: FileParams): Promise<string> => {
  return request.get('/files/path', { params })
}

/**
 * 根据文件 Id 查询文件绝对路径
 * @param id 文件 Id
 * @returns 文件绝对路径
 */
export const fileQueryPathById = (id: string): Promise<string> => {
  return request.get(`/files/${id}/path`)
}

/**
 * 获取当前用户
 */
export const authUserCurrent = (): Promise<UserProfile> => {
  return request.get('/auth/me')
}

/**
 * 更新当前用户信息
 * @param user 用户信息
 */
export const userProfileUpdate = (user: UserProfile) => {
  return request.put('/users/me/profiles', user)
}

/**
 * 更新当前用户密码
 * @param params 密码参数
 */
export const userPasswordUpdate = (params: PasswordForm) => {
  return request.put('/users/me/password', params)
}

/**
 * 查询部署记录列表
 * @param queryParams 查询参数
 * @param sort 排序参数
 * @returns 部署记录列表
 */
export const deploymentRecordQueryList = (
  queryParams: Partial<DeploymentParams>,
  sort?: string,
): Promise<Array<DeploymentRecord>> => {
  return request.get('/deployments/list', { params: { ...queryParams, sort } })
}

/**
 * 分页查询部署记录列表
 * @param queryParams 查询参数
 * @param pageParams 分页参数
 * @returns 分页列表
 */
export const deploymentRecordQueryPage = (
  queryParams: Partial<DeploymentParams>,
  pageParams?: PageParams,
): Promise<PageResult<DeploymentRecord>> => {
  return request.get('/deployments/page', { params: { ...queryParams, ...pageParams } })
}

/**
 * 获取部署记录
 * @param id 部署 Id
 * @returns 部署记录
 */
export const deploymentRecordQueryById = (id: string): Promise<DeploymentRecord> => {
  return request.get(`/deployments/${id}`)
}

/**
 * 创建部署记录
 * @param params 部署记录
 * @returns 部署记录
 */
export const deploymentRecordAdd = (params: DeploymentParams): Promise<DeploymentRecord> => {
  return request.post('/deployments', params)
}

/**
 * 更新部署记录
 * @param id 部署 Id
 * @param params 部署参数
 * @returns 部署记录
 */
export const deploymentRecordUpdate = (id: string, params: DeploymentParams): Promise<DeploymentRecord> => {
  return request.put(`/deployments/${id}`, params)
}

/**
 * 删除部署记录
 * @param id 部署记录
 * @returns
 */
export const deploymentRecordDelete = (id: string): Promise<void> => {
  return request.delete(`/deployments/${id}`)
}

/**
 * 获取后端应用运行状态
 * @param id 部署 Id
 * @returns
 */
export const deploymentRecordStatus = (id: string): Promise<DeploymentRecord> => {
  return request.get(`/deployments/${id}/status`)
}

/**
 * 创建部署作业(异步入口,经 MQ 执行)
 * @param deploymentRecordId 部署记录 Id
 * @param params 作业类型与客户端请求 Id（用于幂等）
 * @returns 新建的部署作业
 */
export const deploymentJobCreate = (deploymentRecordId: string, params: CreateJobRequest): Promise<DeploymentJob> => {
  return request.post(`/deployments/${deploymentRecordId}/jobs`, params)
}

/**
 * 分页查询某部署记录下的作业
 * @param deploymentRecordId 部署记录 Id
 * @param queryParams 查询参数（可按作业状态过滤）
 * @param pageParams 分页参数
 * @returns 作业分页列表
 */
export const deploymentJobQueryPage = (
  deploymentRecordId: string,
  queryParams: { status?: DeploymentJob['status'] },
  pageParams?: PageParams,
): Promise<PageResult<DeploymentJob>> => {
  return request.get(`/deployments/${deploymentRecordId}/jobs`, {
    params: { ...queryParams, ...pageParams },
  })
}

/**
 * 根据作业 Id 获取作业当前状态
 * @param jobId 作业 Id
 * @returns 部署作业
 */
export const deploymentJobQueryById = (jobId: string): Promise<DeploymentJob> => {
  return request.get(`/jobs/${jobId}`)
}

/**
 * 取消 PENDING 状态的部署作业(延迟作业取消)
 * @param jobId 作业 Id
 * @returns 更新后的部署作业
 */
export const deploymentJobCancel = (jobId: string): Promise<DeploymentJob> => {
  return request.put(`/jobs/${jobId}/cancel`)
}

/**
 * 分页查询死信列表
 * @param queryParams 查询参数（可按是否已重试过滤）
 * @param pageParams 分页参数
 * @returns 死信分页列表
 */
export const deadLetterQueryPage = (
  queryParams: { retried?: boolean },
  pageParams?: PageParams,
): Promise<PageResult<DeadLetterMessage>> => {
  return request.get('/mq/dead-letters', { params: { ...queryParams, ...pageParams } })
}

/**
 * 人工重试死信（用死信里的 recordId + jobType 新建一份新作业）
 * @param id 死信记录 Id
 * @returns 新建的部署作业
 */
export const deadLetterRetry = (id: string): Promise<DeploymentJob> => {
  return request.post(`/mq/dead-letters/${id}/retry`)
}

/**
 * 分页查询操作审计日志(Kafka 审计)
 * @param queryParams 查询参数（操作人、操作类型、操作结果）
 * @param pageParams 分页参数
 * @returns 审计日志分页列表
 */
export const auditLogQueryPage = (
  queryParams: AuditLogQueryParams,
  pageParams?: PageParams,
): Promise<PageResult<AuditLog>> => {
  return request.get('/audit-logs', { params: { ...queryParams, ...pageParams } })
}
