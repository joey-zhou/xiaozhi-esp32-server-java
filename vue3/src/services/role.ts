import { http } from './request'
import api from './api'
import type { RoleListResponse, RoleQueryParams, RoleFormData, TestVoiceParams } from '@/types/role'
import type { PromptTemplate, TemplateQuery } from '@/types/template'

/**
 * 查询角色列表
 */
export function queryRoles(params: RoleQueryParams = {}) {
  return http.get<RoleListResponse>(
    api.role.query,
    params
  )
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
export function testVoice(data: TestVoiceParams) {
  return http.get<string>(
    api.role.testVoice,
    data
  )
}

/**
 * 查询提示词模板
 */
export function queryTemplates(params: Partial<TemplateQuery> = {}) {
  return http.get<{ list: PromptTemplate[] }>(
    api.template.query,
    params
  )
}

