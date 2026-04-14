import type { PageQueryParams } from './api'

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
  authRoleId?: number // 后台权限角色ID
  authRoleName?: string // 后台权限角色名称
  editable?: boolean // 表格编辑状态
}

/**
 * 用户查询参数
 */
export interface UserQueryParams extends PageQueryParams {
  name?: string // 姓名
  email?: string // 邮箱
  tel?: string // 电话
  authRoleId?: number // 后台权限角色ID
}

/**
 * 更新用户信息参数
 */
export interface UpdateUserParams {
  userId?: string
  username?: string
  name?: string
  email?: string
  tel?: string
  password?: string // 密码字段
  avatar?: string // 头像字段
}
