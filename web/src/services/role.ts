import { http } from './request'
import api from './api'
import type { Role, RoleQueryParams, RoleFormData, TestVoiceParams } from '@/types/role'
import type { McpToolItem, SystemGlobalToolSummary } from '@/types/mcpTool'

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
  return http.post<Role>(api.role.add, data)
}

/**
 * 更新角色
 */
export function updateRole(data: Partial<RoleFormData>) {
  const { roleId, ...payload } = data
  return http.put<Role>(`${api.role.update}/${roleId}`, payload)
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
 * 获取本地 sherpa-onnx 音色列表（动态扫描 models/tts 目录）
 */
export function querySherpaVoices() {
  return http.getList<Record<string, string>>(api.role.sherpaVoices, {})
}

/**
 * 获取系统全局工具列表
 */
export function getSystemGlobalTools() {
  return http.getList<SystemGlobalToolSummary>(api.mcpTool.systemGlobalTools, {})
}

/**
 * 获取角色禁用的工具列表
 */
export function getDisabledTools(roleId: number) {
  return http.get<{ roleDisabled: string[]; globalDisabled: string[] }>(
    `${api.mcpTool.disabledTools}/${roleId}/disabled-tools`
  )
}

/**
 * 批量更新工具禁用状态
 */
export function updateToolsStatus(roleId: number, excludeTools: string[]) {
  return http.post(`${api.mcpTool.batchExclude}/${roleId}/exclude-tools`, { excludeTools })
}
