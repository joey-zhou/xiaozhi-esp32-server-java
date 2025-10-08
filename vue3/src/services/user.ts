import { http } from './request'
import api from './api'
import type { User, UserQueryParams, UserListResponse, UpdateUserParams } from '@/types/user'

/**
 * 用户登录
 */
export function login(data: { username: string; password: string }) {
  return http.postJSON(api.user.login, data)
}

/**
 * 用户注册
 */
export function register(data: {
  name: string
  username: string
  email: string
  tel?: string
  password: string
  verifyCode: string
}) {
  return http.post(api.user.add, data)
}

/**
 * 重置密码
 */
export function resetPassword(data: {
  email: string
  code: string
  password: string
}) {
  return http.post(api.user.update, data)
}

/**
 * 检查用户是否存在
 */
export function checkUser(data: { username?: string; email?: string }) {
  return http.get(api.user.checkUser, data)
}

/**
 * 发送邮箱验证码
 */
export function sendEmailCaptcha(data: { email: string; type: string }) {
  return http.post(api.user.sendEmailCaptcha, data)
}

/**
 * 验证验证码
 */
export function checkCaptcha(data: { email: string; code: string; type: string }) {
  return http.get(api.user.checkCaptcha, data)
}

/**
 * 查询用户列表
 */
export function queryUsers(params: UserQueryParams) {
  return http.get<UserListResponse>(api.user.queryUsers, params)
}

/**
 * 更新用户信息
 */
export function updateUser(data: UpdateUserParams) {
  return http.postJSON(api.user.update, data as Record<string, unknown>)
}

/**
 * 添加用户
 */
export function addUser(data: Partial<User>) {
  return http.post(api.user.add, data)
}


