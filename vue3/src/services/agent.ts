/**
 * 智能体管理相关服务
 */
import { http } from './request'
import api from './api'
import type { AgentQueryParams, AgentListResponse, PlatformConfig } from '@/types/agent'

/**
 * 查询智能体列表
 */
export function queryAgents(params: AgentQueryParams) {
  return http.get<AgentListResponse>(api.agent.query, params)
}

/**
 * 添加智能体
 */
export function addAgent(data: Partial<PlatformConfig>) {
  return http.post(api.agent.add, data)
}

/**
 * 更新智能体
 */
export function updateAgent(data: Partial<PlatformConfig>) {
  return http.post(api.agent.update, data)
}

/**
 * 删除智能体
 */
export function deleteAgent(botId: string) {
  return http.post(api.agent.delete, { bot_id: botId })
}

/**
 * 查询平台配置
 */
export function queryPlatformConfig(configType: string, provider: string) {
  return http.get(api.config.query, {
    configType,
    provider
  })
}

/**
 * 添加平台配置
 */
export function addPlatformConfig(data: PlatformConfig) {
  return http.post(api.config.add, data)
}

/**
 * 更新平台配置
 */
export function updatePlatformConfig(data: PlatformConfig) {
  return http.post(api.config.update, data)
}

