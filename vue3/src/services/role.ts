import { http } from './request'
import api from './api'
import type { RoleListResponse, RoleQueryParams, RoleFormData, TestVoiceParams } from '@/types/role'

/**
 * 查询角色列表
 */
export function queryRoles(params: RoleQueryParams = {}) {
  return http.get<{ code: number; data?: RoleListResponse; message?: string }>(
    api.role.query,
    params
  )
}

/**
 * 添加角色
 */
export function addRole(data: Partial<RoleFormData> & { avatar?: string }) {
  return http.post<{ code: number; message?: string }>(api.role.add, data)
}

/**
 * 更新角色
 */
export function updateRole(data: Partial<RoleFormData> & { roleId: number; avatar?: string; state?: string }) {
  return http.post<{ code: number; message?: string }>(api.role.update, data)
}

/**
 * 测试语音
 */
export function testVoice(data: TestVoiceParams) {
  return http.get<{ code: number; data?: string; message?: string }>(
    api.role.testVoice,
    data
  )
}

/**
 * 查询提示词模板
 */
export function queryTemplates(params: any = {}) {
  return http.get<{ code: number; data?: { list: any[] }; message?: string }>(
    api.template.query,
    params
  )
}

