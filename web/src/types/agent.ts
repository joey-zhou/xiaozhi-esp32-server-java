/**
 * 智能体相关类型定义
 */

import type { PageQueryParams } from './api'

/**
 * 智能体查询参数
 */
export interface AgentQueryParams extends PageQueryParams {
  provider: string
  agentName?: string
}

/**
 * 平台配置表单（智能体等按 configType+provider 查询与提交的平台凭据）
 * 新建模式下表单还没提交过，configId 不存在，故为可选
 */
export interface PlatformConfig {
  configId?: number
  deviceId?: string
  roleId?: number
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
  agentId?: number
  agentName?: string
  botId?: string
  agentDesc?: string
  iconUrl?: string
  publishTime?: string
  createTime?: string
  updateTime?: string
}

/**
 * 智能体数据（列表行，来自后端已持久化记录，configId 一定存在）
 * 字段与 PlatformConfig 完全一致，只是把 configId 收紧成必填，故直接复用而非重复声明
 */
export interface Agent extends PlatformConfig {
  configId: number
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
