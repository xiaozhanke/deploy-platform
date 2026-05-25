/**
 * 把 catch 到的任意 error 提取为面向用户展示的简短消息。
 *
 * <p>request.ts 的响应拦截器已经把 axios 错误统一包装成 ApiError(message = 中文友好文案)，
 * 此处只读 `.message`，避免 `String(error)` 弹出 "ApiError: xxx" 这种带类名前缀的串；
 * 其他不可控异常（SyntaxError、第三方库的非 Error 抛出）走 String() 兜底防二次错。
 */
export function extractErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  if (typeof error === 'string') {
    return error
  }
  return String(error)
}
