import { http } from './request'
import api from './api'
import type { User, UserQueryParams, UpdateUserParams } from '@/types/user'
import type { LoginResponse } from '@/store/user'

/**
 * 用户登录
 */
export function login(data: { username: string; password: string }) {
  return http.post<LoginResponse>(api.user.login, data)
}

/**
 * 手机号验证码登录
 */
export function telLogin(data: { tel: string; code: string }) {
  return http.post<LoginResponse>(api.user.telLogin, data)
}

/**
 * 检查Token有效性
 * 用于页面刷新时验证登录状态
 */
export function checkToken() {
  return http.get<LoginResponse>(api.user.checkToken)
}

/**
 * 刷新Token
 * 延长登录有效期
 */
export function refreshToken() {
  return http.post<LoginResponse>(api.user.refreshToken)
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
  // 后端期望的参数名是 code，前端使用 verifyCode 更语义化
  const { verifyCode, tel, ...rest } = data
  // 手机号为选填项：后端 @Pattern 对空字符串会校验失败（null 才会跳过），
  // 因此未填写时不要发送该字段，避免误报“手机号格式不正确”
  const payload: Record<string, unknown> = { ...rest, code: verifyCode }
  if (tel) {
    payload.tel = tel
  }
  return http.post(api.user.add, payload)
}

/**
 * 重置密码
 */
export function resetPassword(data: {
  email: string
  code: string
  password: string
}) {
  return http.post(api.user.resetPassword, data)
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
 * 发送短信验证码
 */
export function sendSmsCaptcha(data: { tel: string; type: string }) {
  return http.post(api.user.sendSmsCaptcha, data)
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
export function queryUsers(params: Partial<UserQueryParams>) {
  return http.getPage<User>(api.user.query, params)
}

/**
 * 更新用户信息
 */
export function updateUser(data: Partial<UpdateUserParams>) {
  const { userId, ...updateData } = data
  return http.put(`${api.user.update}/${userId}`, updateData)
}

/**
 * 添加用户
 */
export function addUser(data: Partial<User>) {
  return http.post(api.user.add, data)
}
