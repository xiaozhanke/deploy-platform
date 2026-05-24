/**
 * 标准化的 API 错误类，继承自 Error。
 */
export class ApiError extends Error {
  /**
   * HTTP 状态码 (e.g., 404, 500)
   */
  public readonly code: number

  /**
   * 业务状态码 (e.g., "NOT_FOUND", "INVALID_ARGUMENT")
   */
  public readonly status: string

  /**
   * @param code HTTP 状态码
   * @param status 业务状态码
   * @param message 对开发者/用户友好的错误消息
   */
  constructor(code: number, status: string, message: string) {
    // 调用父类 Error 的构造函数，将 message 传递上去
    super(message)

    // 设置类的名称，便于调试
    this.name = 'ApiError'

    // 设置自定义属性
    this.code = code
    this.status = status

    // 解决在 TypeScript 中继承内置类时的一些问题
    Object.setPrototypeOf(this, ApiError.prototype)
  }
}
