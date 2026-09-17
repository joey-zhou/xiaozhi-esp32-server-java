import type { PageQueryParams } from './api'
import type { AuthRole, PermissionTreeNode } from './authRole'

/**
 * 用户信息，字段对应后端 UserResp
 * state / isAdmin 在库表里是 enum('1','0')，JSON 过来是字符串，不要用数字比较
 */
export interface User {
  userId: string
  username?: string
  name?: string
  email?: string
  tel?: string
  avatar?: string
  state?: string // '1'-正常 '0'-禁用
  isAdmin?: string // '1'-管理员 '0'-普通用户
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
 * 登录 / 校验 token / 刷新 token 的响应体
 */
export interface LoginResponse {
  token: string
  refreshToken: string
  expiresIn: number // 过期时间（秒）
  userId: number
  isNewUser: boolean
  user: User
  authRole: AuthRole
  permissions: PermissionTreeNode[]
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
