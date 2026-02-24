import { http } from './request'
import api from './api'
import type { Role, RoleQueryParams, RoleFormData, TestVoiceParams } from '@/types/role'
import type { PromptTemplate, TemplateQuery } from '@/types/template'
import type { McpToolItem } from '@/types/mcpServer'
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
  return http.postJSON(api.role.add, data)
}

/**
 * 更新角色
 */
export function updateRole(data: Partial<RoleFormData>) {
  return http.putJSON(`${api.role.update}/${data.roleId}`, data)
}

/**
 * 删除角色
 */
export function deleteRole(roleId: number) {
  return http.delete(`${api.role.delete}/${roleId}`)
}

/**
 * 测试语音
 */
export function testVoice(data: Partial<TestVoiceParams>) {
  return http.get<string>(api.role.testVoice, data)
}

/**
 * 获取系统全局工具列表
 */
export function getSystemGlobalTools() {
  return http.getList<string>(api.mcpTool.getSystemGlobalTools, {})
}

/**
 * 获取角色禁用的工具列表
 */
export function getDisabledTools(roleId: number) {
  return http.get<{ roleDisabled: string[]; globalDisabled: string[] }>(
    api.mcpTool.getDisabledTools,
    { roleId }
  )
}

/**
 * 批量更新工具禁用状态
 */
export function updateToolsStatus(roleId: number, excludeTools: string[]) {
  return http.postJSON(api.mcpTool.batchSetExcludeTools, { roleId, excludeTools })
}

