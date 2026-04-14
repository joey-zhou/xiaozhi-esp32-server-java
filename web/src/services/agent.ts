/**
 * 智能体管理相关服务
 */
import { http } from './request'
import api from './api'
import type { Agent, AgentQueryParams } from '@/types/agent'

/**
 * 查询智能体列表
 */
export function queryAgents(params: Partial<AgentQueryParams>) {
  return http.getPage<Agent>(api.agent.query, params)
}
