import { http } from './request'
import api from './api'
import type { Role, RoleQueryParams, RoleFormData, TestVoiceParams } from '@/types/role'
import type { PromptTemplate, TemplateQuery } from '@/types/template'
import type { PageResponse, DataResponse } from '@/types/api'

/**
 * 查询角色列表
 */
export function queryRoles(params: Partial<RoleQueryParams>) {
  return http.getPage<Role>(api.role.query, params)
}

/**
 * 添加角色
 */
export function addRole(data: Partial<RoleFormData> & { avatar?: string }) {
  return http.post(api.role.add, data)
}

/**
 * 更新角色
 */
export function updateRole(data: Partial<RoleFormData>) {
  return http.post(api.role.update, data)
}

/**
 * 测试语音
 */
export function testVoice(data: Partial<TestVoiceParams>) {
  return http.get<string>(api.role.testVoice, data)
}
