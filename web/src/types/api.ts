/**
 * 统一的 API 类型定义
 */

/**
 * API 响应基础接口
 */
export interface ApiResponse<T = unknown> {
  code: number
  data: T
  message: string
  timestamp?: number
  success?: boolean
}

/**
 * 分页数据接口
 */
export interface PageData<T = unknown> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  size: number
  startRow: number
  endRow: number
  pages: number
  prePage: number
  nextPage: number
  isFirstPage: boolean
  isLastPage: boolean
  hasPreviousPage: boolean
  hasNextPage: boolean
  navigatePages: number
  navigatepageNums: number[]
  navigateFirstPage: number
  navigateLastPage: number
}

/**
 * 分页响应接口
 */
export interface PageResponse<T = unknown> extends ApiResponse<PageData<T>> {
  data: PageData<T>
}

/**
 * 列表响应接口（不带分页）
 */
export interface ListResponse<T = unknown> extends ApiResponse<T[]> {
  data: T[]
}

/**
 * 通用响应接口（无数据）
 */
export interface EmptyResponse extends ApiResponse<null> {
  data: null
}

/**
 * 通用响应接口（任意数据）
 */
export interface DataResponse<T = unknown> extends ApiResponse<T> {
  data: T
}

/**
 * 查询参数基础接口
 */
export interface BaseQueryParams {
  start?: number
  limit?: number
  [key: string]: unknown
}

/**
 * 分页查询参数
 */
export interface PageQueryParams extends BaseQueryParams {
  start?: number
  limit?: number
}

/**
 * 列表查询参数（不分页）
 */
export interface ListQueryParams extends BaseQueryParams {
  // 可以添加其他通用查询参数
}
