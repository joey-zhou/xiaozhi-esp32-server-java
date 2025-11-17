/**
 * 智能体管理相关服务
 */
import { http } from './request'
import api from './api'
import type { Agent, AgentQueryParams, PlatformConfig } from '@/types/agent'

/**
 * 查询智能体列表
 */
export function queryAgents(params: Partial<AgentQueryParams>) {
  return http.getPage<Agent>(api.agent.query, params)
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


