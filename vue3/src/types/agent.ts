/**
 * 智能体相关类型定义
 */

/**
 * 智能体查询参数
 */
export interface AgentQueryParams {
  provider: string
  agentName?: string
  configType: 'agent'
  start?: number
  limit?: number
}

/**
 * 智能体数据
 */
export interface Agent {
  configId: number
  deviceId?: string | null
  roleId?: string | null
  configName?: string | null
  configDesc?: string | null
  configType?: string | null
  modelType?: string | null
  provider: string
  appId?: string | null
  apiKey?: string | null
  apiSecret?: string | null
  ak?: string | null
  sk?: string | null
  apiUrl?: string | null
  state?: string | null
  isDefault?: string | null
  agentId?: string | null
  agentName?: string | null
  botId?: string | null
  agentDesc?: string | null
  iconUrl?: string | null
  publishTime?: string | null
  createTime?: string | null
  updateTime?: string | null
}

/**
 * 智能体列表响应
 */
export interface AgentListResponse {
  list: Agent[]
  total: number
  pageNum: number
  pageSize: number
  size: number
  startRow: number
  endRow: number
  pages: number
  prePage: number
  nextPage: number
  isFirstPage: boolean
  isLastPage: boolean
  hasPreviousPage: boolean
  hasNextPage: boolean
  navigatePages: number
  navigatepageNums: number[]
  navigateFirstPage: number
  navigateLastPage: number
}

/**
 * 平台配置表单
 */
export interface PlatformConfig {
  configId?: number
  deviceId?: string | null
  roleId?: string | null
  configName?: string | null
  configDesc?: string | null
  configType?: string | null
  modelType?: string | null
  provider: string
  appId?: string | null
  apiKey?: string | null
  apiSecret?: string | null
  ak?: string | null
  sk?: string | null
  apiUrl?: string | null
  state?: string | null
  isDefault?: string | null
  agentId?: string | null
  agentName?: string | null
  botId?: string | null
  agentDesc?: string | null
  iconUrl?: string | null
  publishTime?: string | null
  createTime?: string | null
  updateTime?: string | null
}

/**
 * 平台选项
 */
export interface ProviderOption {
  label: string
  value: string
}

/**
 * 表单项配置
 */
export interface FormItem {
  field: string
  label: string
  placeholder: string
  suffix?: string
}

/**
 * 平台表单项映射
 */
export type PlatformFormItems = {
  [key: string]: FormItem[]
}

