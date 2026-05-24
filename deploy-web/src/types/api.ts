/**
 * API 响应
 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 分页结果
 */
export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/**
 * 分页参数
 */
export interface PageParams {
  page?: number
  size?: number
  sort?: string
}
