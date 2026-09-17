import type { PageQueryParams } from './api'

// 角色编辑页用到的模板类型只在模板领域文件里定义一份
export type { PromptTemplate } from './template'

export type RoleModelType = 'llm' | 'agent'
export type VoiceProvider = 'edge' | 'aliyun' | 'aliyun-nls' | 'volcengine' | 'xfyun' | 'minimax' | 'tencent' | 'sherpa-onnx'

// 语音性别
export type VoiceGender = '' | 'male' | 'female'

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
  inactiveTimeoutSeconds?: number
  modelProvider?: string
  ttsProvider?: string
  isDefault?: string | number // 服务器返回字符串 '1' 或 '0'，前端可能转为数字
  totalDevice?: number
  ttsPitch?: number
  ttsSpeed?: number
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
  type: RoleModelType
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

export interface RoleFormData {
  roleId?: number
  roleName: string
  roleDesc?: string
  avatar?: string
  isDefault: boolean | number | string // 支持布尔值、数字和字符串（提交时转为 '1' 或 '0'）
  state?: string
  modelType: RoleModelType
  modelId?: number
  temperature?: number
  topP?: number
  // 语音识别相关
  sttId: number
  vadSpeechTh?: number
  vadSilenceTh?: number
  vadEnergyTh?: number
  vadSilenceMs?: number
  inactiveTimeoutSeconds: number
  // 语音合成相关
  voiceName?: string
  ttsId?: number
  gender?: VoiceGender
  ttsPitch?: number
  ttsSpeed?: number
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

export interface TestVoiceResult {
  audioUrl: string
}
