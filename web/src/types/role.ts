import type { PageQueryParams } from './api'

// 模型类型
export type ModelType = 'llm' | 'agent'

// 语音提供商类型
export type VoiceProvider = 'edge' | 'aliyun' | 'aliyun-nls' | 'volcengine' | 'xfyun' | 'minimax' | 'tencent' | 'sherpa-onnx'

// 语音性别
export type VoiceGender = '' | 'male' | 'female'

// 记忆类型
export type MemoryType = 'window' | 'summary'

// 角色数据
export interface Role {
  createTime?: string
  updateTime?: string
  userId?: number
  startTime?: string
  endTime?: string
  roleId: number
  avatar?: string
  roleName: string
  roleDesc?: string
  voiceName?: string
  state?: string
  ttsId?: number
  modelId?: number
  modelName?: string
  sttId?: number
  temperature?: number
  topP?: number
  vadEnergyTh?: number
  vadSpeechTh?: number
  vadSilenceTh?: number
  vadSilenceMs?: number
  modelProvider?: string
  ttsProvider?: string
  isDefault?: string | number // 服务器返回字符串 '1' 或 '0'，前端可能转为数字
  totalDevice?: number
  ttsPitch?: number // 语音音调(0.5-2.0)
  ttsSpeed?: number // 语音语速(0.5-2.0)
  memoryType?: MemoryType // 记忆类型
}

export interface RoleQueryParams extends PageQueryParams {
  roleName?: string
  isDefault?: number
}

export interface VoiceOption {
  label?: string
  value?: string
  gender: VoiceGender
  provider: VoiceProvider
  ttsId?: number
  model?: string
}

export interface ModelOption {
  label: string
  value: number
  desc?: string
  type: ModelType
  provider: string
  configName?: string
  configDesc?: string
  agentName?: string
  agentDesc?: string
}

export interface SttOption {
  label: string
  value: number
  desc?: string
}

export interface PromptTemplate {
  templateId: number
  templateName: string
  templateContent: string
  isDefault?: boolean | number
}

export interface RoleFormData {
  roleId?: number
  roleName: string
  roleDesc?: string
  avatar?: string
  isDefault: boolean | number | string // 支持布尔值、数字和字符串（提交时转为 '1' 或 '0'）
  state?: string
  // 模型相关
  modelType: ModelType
  modelId?: number
  temperature?: number
  topP?: number
  // 语音识别相关
  sttId: number
  vadSpeechTh?: number
  vadSilenceTh?: number
  vadEnergyTh?: number
  vadSilenceMs?: number
  // 语音合成相关
  voiceName?: string
  ttsId?: number
  gender?: VoiceGender
  ttsPitch?: number
  ttsSpeed?: number
  // 记忆类型
  memoryType?: MemoryType
}

// 测试语音参数
export interface TestVoiceParams {
  voiceName: string
  ttsId: number
  message: string
  provider: string
  ttsPitch?: number
  ttsSpeed?: number
}

