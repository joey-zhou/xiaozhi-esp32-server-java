import { http } from './request'
import api from './api'
import type { DataResponse } from '@/types/api'
import type { Role, RoleQueryParams, RoleFormData, TestVoiceParams, TestVoiceResult } from '@/types/role'
import type { McpToolItem, SystemGlobalToolSummary } from '@/types/mcpTool'

/**
 * 判断 roleId 是否为可用于拼接 URL 的有效值。
 * 允许 0（用于获取全局禁用列表），仅拦截 null/undefined/NaN，
 * 避免把字符串 "undefined" 拼进请求路径导致后端类型转换报错。
 */
function isValidRoleId(roleId: unknown): roleId is number {
  return typeof roleId === 'number' && !Number.isNaN(roleId)
}

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
  if (!isValidRoleId(roleId)) {
    return Promise.resolve<DataResponse<{ roleDisabled: string[]; globalDisabled: string[] }>>({
      code: 200,
      data: { roleDisabled: [], globalDisabled: [] },
      message: '',
    })
  }
  return http.get<{ roleDisabled: string[]; globalDisabled: string[] }>(
    `${api.mcpTool.disabledTools}/${roleId}/disabled-tools`
  )
}

/**
 * 批量更新工具禁用状态
 */
export function updateToolsStatus(roleId: number, excludeTools: string[]) {
  if (!isValidRoleId(roleId)) {
    return Promise.reject(new Error('roleId 无效，无法更新工具禁用状态'))
  }
  return http.post(`${api.mcpTool.batchExclude}/${roleId}/exclude-tools`, { excludeTools })
}
