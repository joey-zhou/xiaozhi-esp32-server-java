import type { PageQueryParams } from './api'

/**
 * 配置类型
 */
export type ConfigType = 'llm' | 'stt' | 'tts' | 'agent' | 'oss'
export type ConfigModelType = 'chat' | 'vision' | 'intent' | 'embedding'

/**
 * 配置信息接口
 */
export interface Config {
  configId?: number
  configType: ConfigType
  provider: string
  configName: string
  configDesc?: string
  modelType?: ConfigModelType
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
  projectId?: string
  region?: string
  enableThinking?: boolean
  // 表单字段由 config/providerConfig.ts 的 ConfigField.name 驱动，动态下标取值靠这条索引签名。
  // 类型是上面全部具名字段的并集，TS 要求具名属性必须可赋给索引签名
  [key: string]: string | number | boolean | undefined
}

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
