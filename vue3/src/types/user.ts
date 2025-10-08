/**
 * 用户信息接口
 */
export interface User {
  userId: string
  name: string
  username?: string
  email?: string
  tel?: string
  avatar?: string
  state: number // 1-正常 0-禁用
  isAdmin: number // 1-管理员 0-普通用户
  totalDevice?: number // 设备数量
  aliveNumber?: number // 在线设备数
  totalMessage?: number // 对话消息数
  loginTime?: string // 最后登录时间
  loginIp?: string // 最后登录IP
  editable?: boolean // 表格编辑状态
}

/**
 * 用户查询参数
 */
export interface UserQueryParams {
  start: number // 页码
  limit: number // 每页数量
  name?: string // 姓名
  email?: string // 邮箱
  tel?: string // 电话
}

/**
 * 更新用户信息参数
 */
export interface UpdateUserParams {
  username?: string
  name?: string
  email?: string
  tel?: string
  password?: string // 密码字段
  avatar?: string // 头像字段
}

/**
 * 用户列表响应
 */
export interface UserListResponse {
  list: User[]
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

