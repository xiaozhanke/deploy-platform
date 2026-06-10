import type { SshAuthTypeEnum } from '@/enums/platform'

/**
 * 主机信息
 */
export interface HostRecord {
  id: string
  name: string
  description: string
  address: string
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
 * 主机信息参数
 */
export interface HostParams {
  name?: string
  description?: string
  address: string
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
 * 主机查询参数
 *
 * 仅承载列表 / 分页查询的过滤字段，与写入用的 HostParams 解耦
 */
export interface HostQueryParams {
  name?: string
  address?: string
}
