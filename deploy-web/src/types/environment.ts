/**
 * JSch Exec 执行结果
 */
export interface ExecResult {
  exitCode: number
  result: string
}

/**
 * 环境配置步骤
 */
export interface SetupStep {
  title: string
  type: 'command' | 'upload' | 'download'
  status: 'wait' | 'process' | 'finish' | 'error' | 'success'
  commands?: string[]
  localPath?: string
  remoteDir?: string
  remotePath?: string
  localDir?: string
  percentage?: number
}

/**
 * Nginx 配置参数
 */
export interface NginxConfigParams {
  configName: string
  frontEndHost?: string
  frontEndPort: number
  frontEndStaticDir: string
  backEndHost?: string
  backEndPort: number
}

/**
 * Redis 配置参数
 */
export interface RedisConfigParams {
  port: number
  password: string
}

/**
 * 文件信息
 */
export interface File {
  name: string
  path: string
  size: number
  updateTime: string
}
