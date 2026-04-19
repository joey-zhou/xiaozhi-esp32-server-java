/**
 * 配置类型
 */
export type ConfigType = 'llm' | 'stt' | 'tts' | 'agent' | 'oss'
export type ModelType = 'chat' | 'vision' | 'intent' | 'embedding'

/**
 * 配置信息接口
 */
export interface Config {
  configId?: number
  configType: ConfigType
  provider: string
  configName: string
  configDesc?: string
  modelType?: ModelType
  isDefault?: string | boolean // 1-默认 0-非默认，表单中使用boolean
  state?: string
  createTime?: string
  // API相关字段
  appId?: string
  apiKey?: string
  apiSecret?: string
  ak?: string
  sk?: string
  apiUrl?: string
  enableThinking?: boolean
  // 支持动态字段
  [key: string]: any
}

import type { PageQueryParams } from './api'

/**
 * 配置查询参数
 */
export interface ConfigQueryParams extends PageQueryParams {
  configType: ConfigType
  provider?: string
  configName?: string
  modelType?: string,
  state?: string
}

/**
 * 配置字段定义
 */
export interface ConfigField {
  name: string
  label: string
  required: boolean
  inputType?: string  // 'text' | 'password' | 'select'
  placeholder?: string
  span?: number
  help?: string
  suffix?: string
  defaultUrl?: string
  options?: Array<{ label: string; value: string }>  // 下拉选项（当 inputType 为 'select' 时使用）
}

/**
 * 配置类型信息
 */
export interface ConfigTypeInfo {
  label: string
  permissionPrefix?: string
  typeOptions?: Array<{ value: string; label: string; key?: string; configNameOptions?: string[] }>
  typeFields?: Record<string, ConfigField[]>
}

/**
 * 模型选项
 */
export interface ModelOption {
  value: string
  label: string
}

/**
 * LLM 工厂模型信息
 */
export interface LLMModel {
  llm_name: string
  model_type: string
  max_tokens?: number
  is_tools?: boolean
  tags?: string
}

/**
 * LLM 工厂信息
 */
export interface LLMFactory {
  name: string
  llm: LLMModel[]
  url?: string
  rank?: string
  status?: string
}
