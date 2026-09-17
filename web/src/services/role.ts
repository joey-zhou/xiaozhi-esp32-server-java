import { http } from './request'
import api from './api'
import type { Role, RoleQueryParams, RoleFormData, TestVoiceParams, TestVoiceResult } from '@/types/role'
import type { SystemGlobalToolSummary } from '@/types/mcpTool'

/**
 * 校验 roleId 是否可用于拼接 URL，非法一律抛错。
 * 允许 0（用于获取全局禁用列表），仅拦截 null/undefined/NaN，
 * 避免把字符串 "undefined" 拼进请求路径导致后端类型转换报错。
 */
function assertRoleId(roleId: number): void {
  if (typeof roleId !== 'number' || Number.isNaN(roleId)) {
    throw new Error(`roleId 无效: ${String(roleId)}`)
  }
}

/**
 * 查询角色列表
 */
export function queryRoles(params: Partial<RoleQueryParams>) {
  return http.getPage<Role>(api.role.root, params)
}

/**
 * 角色提交数据
 */
export type RoleSubmitData = Partial<RoleFormData> & { avatar?: string }

/**
 * 添加角色
 */
export function addRole(data: RoleSubmitData) {
  return http.post<Role>(api.role.root, data)
}

/**
 * 更新角色
 */
export function updateRole(data: RoleSubmitData) {
  const { roleId, ...payload } = data
  return http.put<Role>(`${api.role.root}/${roleId}`, payload)
}

/**
 * 删除角色
 */
export function deleteRole(roleId: number) {
  return http.delete(`${api.role.root}/${roleId}`)
}

/**
 * 测试语音
 */
export function testVoice(data: Partial<TestVoiceParams>) {
  return http.get<TestVoiceResult>(api.role.testVoice, data)
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
  assertRoleId(roleId)
  return http.get<{ roleDisabled: string[]; globalDisabled: string[] }>(
    `${api.mcpTool.disabledTools}/${roleId}/disabled-tools`
  )
}

/**
 * 批量更新工具禁用状态
 */
export function updateToolsStatus(roleId: number, excludeTools: string[]) {
  assertRoleId(roleId)
  return http.post(`${api.mcpTool.batchExclude}/${roleId}/exclude-tools`, { roleId, excludeTools })
}
