/**
 * 配置类型
 */
export type ConfigType = 'llm' | 'stt' | 'tts' | 'agent'
export type ModelType = 'chat' | 'vision' | 'intent' | 'embedding'

/**
 * 配置信息接口
 */
export interface Config {
  configId: number
  configType: ConfigType
  provider: string
  configName: string
  configDesc?: string
  modelType?: ModelType
  isDefault?: string // 1-默认 0-非默认
  state?: string
  createTime?: string
  // 动态参数
  [key: string]: any
}

/**
 * 配置查询参数
 */
export interface ConfigQueryParams {
  start: number
  limit: number
  configType: ConfigType
  provider?: string
  configName?: string
  modelType?: string
}

/**
 * 配置列表响应
 */
export interface ConfigListResponse {
  list: Config[]
  total: number
}

/**
 * 配置字段定义
 */
export interface ConfigField {
  name: string
  label: string
  required: boolean
  inputType?: string
  placeholder?: string
  span?: number
  help?: string
  suffix?: string
  defaultUrl?: string
}

/**
 * 配置类型信息
 */
export interface ConfigTypeInfo {
  label: string
  typeOptions?: Array<{ value: string; label: string; key?: string }>
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
}

