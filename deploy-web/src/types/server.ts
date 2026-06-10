import type { SshAuthTypeEnum } from '@/enums/platform'

/**
 * 服务器信息
 */
export interface ServerRecord {
  id: string
  name: string
  description: string
  host: string
  port: number
  username: string
  homeDir: string
  authType: keyof typeof SshAuthTypeEnum
  password: string
  privateKeyPath: string
  privateKeyPassword: string
  kexAlgorithms: string
  cipherAlgorithms: string
  macAlgorithms: string
  serverHostKeyAlgorithms: string
  connectionTimeout: number
  compressionEnabled: boolean
  strictHostKeyChecking: boolean
  x11ForwardingEnabled: boolean
  portForwardingEnabled: boolean
}

/**
 * 服务器信息参数
 */
export interface ServerParams {
  name?: string
  description?: string
  host: string
  port: number
  username: string
  homeDir?: string
  authType: keyof typeof SshAuthTypeEnum
  password?: string
  privateKeyPath?: string
  privateKeyPassword?: string
  kexAlgorithms?: string
  cipherAlgorithms?: string
  macAlgorithms?: string
  serverHostKeyAlgorithms?: string
  connectionTimeout?: number
  compressionEnabled?: boolean
  strictHostKeyChecking?: boolean
  x11ForwardingEnabled?: boolean
  portForwardingEnabled?: boolean
}

/**
 * 服务器查询参数
 *
 * 仅承载列表 / 分页查询的过滤字段，与写入用的 ServerParams 解耦
 */
export interface ServerQueryParams {
  name?: string
  host?: string
}
