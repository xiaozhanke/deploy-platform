import type { FileScopeEnum } from '@/enums/platform'

/**
 * 文件记录
 */
export interface FileRecord {
  /**
   * 文件 Id
   */
  id: string

  /**
   * 文件名
   */
  fileName: string

  /**
   * 文件相对路径
   */
  relativePath: string

  /**
   * 文件大小（字节）
   */
  fileSize: number

  /**
   * 文件内容类型
   */
  contentType: string

  /**
   * 使用范围
   */
  scope: keyof typeof FileScopeEnum

  /**
   * 文件分组 Id
   */
  groupId: string

  /**
   * 构件 Id
   */
  artifactId: string

  /**
   * 版本
   */
  version: string

  /**
   * 文件描述
   */
  description: string

  /**
   * 创建时间
   */
  createTime: string

  /**
   * 更新时间
   */
  updateTime: string
}

/**
 * 文件参数
 */
export interface FileParams {
  /**
   * 文件名
   */
  fileName?: string

  /**
   * 文件相对路径
   */
  relativePath?: string

  /**
   * 使用范围
   */
  scope?: keyof typeof FileScopeEnum

  /**
   * 文件分组 Id
   */
  groupId?: string

  /**
   * 构件 Id
   */
  artifactId?: string

  /**
   * 版本
   */
  version?: string

  /**
   * 文件描述
   */
  description?: string
}
