/**
 * 智能体相关类型定义
 */

import type { BaseQueryParams } from './api'

/**
 * 智能体查询参数
 */
export interface AgentQueryParams extends BaseQueryParams {
  provider: string
  agentName?: string
  configType: 'agent'
}

/**
 * 智能体数据
 */
export interface Agent {
  configId: number
  deviceId?: string
  roleId?: string
  configName?: string
  configDesc?: string
  configType?: string
  modelType?: string
  provider: string
  appId?: string
  apiKey?: string
  apiSecret?: string
  ak?: string
  sk?: string
  apiUrl?: string
  state?: string
  isDefault?: string
  agentId?: string
  agentName?: string
  botId?: string
  agentDesc?: string
  iconUrl?: string
  publishTime?: string
  createTime?: string
  updateTime?: string
}

/**
 * 平台配置表单
 */
export interface PlatformConfig {
  configId?: number
  deviceId?: string
  roleId?: string
  configName?: string
  configDesc?: string
  configType?: string
  modelType?: string
  provider: string
  appId?: string
  apiKey?: string
  apiSecret?: string
  ak?: string
  sk?: string
  apiUrl?: string
  state?: string
  isDefault?: string
  agentId?: string
  agentName?: string
  botId?: string
  agentDesc?: string
  iconUrl?: string
  publishTime?: string
  createTime?: string
  updateTime?: string
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
  type?: 'input' | 'textarea'
}

/**
 * 平台表单项映射
 */
export type PlatformFormItems = {
  [key: string]: FormItem[]
}

