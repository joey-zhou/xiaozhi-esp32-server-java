/**
 * 角色管理 Composable
 * 统一处理模型选择和音色选择逻辑
 */

import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { queryConfigs } from '@/services/config'
import { queryAgents } from '@/services/agent'
import type { ModelOption, VoiceOption, SttOption, VoiceProvider } from '@/types/role'
import type { Config } from '@/types/config'
import type { Agent } from '@/types/agent'

export function useRoleManager() {
  // 加载状态
  const modelLoading = ref(false)
  const voiceLoading = ref(false)
  const sttLoading = ref(false)

  // 模型相关
  const allModels = ref<ModelOption[]>([])
  const selectedModelId = ref<number>()

  // 语音相关 - 所有语音列表（来自各个JSON文件）
  const allVoices = ref<VoiceOption[]>([])
  const selectedVoiceName = ref<string>()

  // 语音识别
  const sttOptions = ref<SttOption[]>([])

  // 原始数据存储
  const llmConfigs = ref<Config[]>([])
  const agentConfigs = ref<Agent[]>([])
  const ttsConfigs = ref<Config[]>([])

  /**
   * 加载所有模型（LLM + Agent）
   */
  async function loadAllModels() {
    modelLoading.value = true
    try {
      // 并行加载LLM和Agent
      const [llmRes, cozeRes, difyRes] = await Promise.all([
        queryConfigs({ configType: 'llm', start: 1, limit: 1000 }),
        queryAgents({ provider: 'coze', configType: 'agent', start: 1, limit: 1000 }),
        queryAgents({ provider: 'dify', configType: 'agent', start: 1, limit: 1000 })
      ])

      const models: ModelOption[] = []

      // 处理LLM配置（只加载对话模型）
      if (llmRes.code === 200 && llmRes.data?.list) {
        llmConfigs.value = llmRes.data.list
        llmRes.data.list.forEach((config: Config) => {
          // 只添加对话模型（chat类型）
          if (config.modelType === 'chat') {
            models.push({
              label: config.configName,
              value: Number(config.configId),
              desc: config.configDesc,
              type: 'llm',
              provider: config.provider || '',
              configName: config.configName,
              configDesc: config.configDesc
            })
          }
        })
      }

      // 处理Coze Agent
      if (cozeRes.code === 200 && cozeRes.data?.list) {
        cozeRes.data.list.forEach((agent: Agent) => {
          agentConfigs.value.push(agent)
          models.push({
            label: `${agent.agentName} (Coze智能体)`,
            value: agent.configId,
            desc: agent.agentDesc,
            type: 'agent',
            provider: 'coze',
            agentName: agent.agentName,
            agentDesc: agent.agentDesc
          })
        })
      }

      // 处理Dify Agent
      if (difyRes.code === 200 && difyRes.data?.list) {
        difyRes.data.list.forEach((agent: Agent) => {
          agentConfigs.value.push(agent)
          models.push({
            label: `${agent.agentName} (Dify智能体)`,
            value: agent.configId,
            desc: agent.agentDesc,
            type: 'agent',
            provider: 'dify',
            agentName: agent.agentName,
            agentDesc: agent.agentDesc
          })
        })
      }

      allModels.value = models
    } catch (error) {
      console.error('加载模型列表失败:', error)
      message.error('加载模型列表失败')
    } finally {
      modelLoading.value = false
    }
  }

  /**
   * 加载所有语音选项（从TTS配置和JSON文件）
   */
  async function loadAllVoices() {
    voiceLoading.value = true
    try {
      // 1. 加载TTS配置
      const ttsRes = await queryConfigs({ configType: 'tts', start: 1, limit: 1000 })
      if (ttsRes.code === 200 && ttsRes.data?.list) {
        ttsConfigs.value = ttsRes.data.list
      }

      // 2. 并行加载所有语音JSON文件
      const [edgeVoices, aliyunVoices, aliyunNlsVoices, volcengineVoices, xfyunVoices, minimaxVoices] = await Promise.all([
        loadVoiceJson('/static/assets/edgeVoicesList.json', 'edge'),
        loadVoiceJson('/static/assets/aliyunVoicesList.json', 'aliyun'),
        loadVoiceJson('/static/assets/aliyunNlsVoicesList.json', 'aliyun-nls'),
        loadVoiceJson('/static/assets/volcengineVoicesList.json', 'volcengine'),
        loadVoiceJson('/static/assets/xfyunVoicesList.json', 'xfyun'),
        loadVoiceJson('/static/assets/minimaxVoicesList.json', 'minimax')
      ])

      // 3. 合并所有语音，并关联TTS配置
      const voices: VoiceOption[] = []

      // Edge语音（不需要TTS配置）
      voices.push(...edgeVoices.map(v => ({
        ...v,
        ttsId: -1 // Edge使用-1作为特殊标记
      })))

      // 其他提供商的语音（需要关联TTS配置）
      const providerVoicesMap = {
        aliyun: aliyunVoices,
        'aliyun-nls': aliyunNlsVoices,
        volcengine: volcengineVoices,
        xfyun: xfyunVoices,
        minimax: minimaxVoices
      }

      Object.entries(providerVoicesMap).forEach(([provider, providerVoices]) => {
        // 找到该提供商的TTS配置
        const ttsConfig = ttsConfigs.value.find(c => c.provider === provider)
        if (ttsConfig) {
          // 只添加有TTS配置的提供商的语音
          providerVoices.forEach((v: VoiceOption) => {
            voices.push({
              ...v,
              ttsId: ttsConfig.configId
            })
          })
        }
      })

      allVoices.value = voices
    } catch (error) {
      console.error('加载语音列表失败:', error)
      message.error('加载语音列表失败')
    } finally {
      voiceLoading.value = false
    }
  }

  /**
   * 加载单个语音JSON文件
   */
  async function loadVoiceJson(url: string, provider: VoiceProvider): Promise<VoiceOption[]> {
    try {
      const response = await fetch(url)
      if (!response.ok) {
        throw new Error(`加载${provider}语音列表失败`)
      }
      const data = await response.json()

      // 处理Edge特殊格式
      if (provider === 'edge') {
        interface EdgeVoice {
          Locale: string
          ShortName: string
          Gender: string
        }
        return (data as EdgeVoice[])
          .filter((voice) => voice.Locale && voice.Locale.includes('zh'))
          .sort((a, b) => a.Locale.localeCompare(b.Locale))
          .map((voice) => {
            const nameParts = voice.ShortName.split('-')
            let name = nameParts[2] || ''
            if (name.endsWith('Neural')) {
              name = name.substring(0, name.length - 6)
            }
            return {
              label: `${name} (${voice.Locale})`,
              value: voice.ShortName,
              gender: voice.Gender.toLowerCase() as 'male' | 'female' | '',
              provider: 'edge'
            }
          })
      }

      // 其他提供商直接返回原始label，不添加提供商标识
      return (data as Omit<VoiceOption, 'provider'>[]).map((voice) => ({
        ...voice,
        provider
      }))
    } catch (error) {
      console.warn(`加载${provider}语音列表失败:`, error)
      return []
    }
  }

  /**
   * 加载语音识别选项
   */
  async function loadSttOptions() {
    sttLoading.value = true
    try {
      const res = await queryConfigs({ configType: 'stt', start: 1, limit: 1000 })
      const options: SttOption[] = [
        {
          label: 'Vosk本地识别',
          value: -1,
          desc: '默认Vosk本地语音识别模型'
        }
      ]

      if (res.code === 200 && res.data?.list) {
        res.data.list.forEach((config: Config) => {
          options.push({
            label: config.configName,
            value: Number(config.configId),
            desc: config.configDesc
          })
        })
      }

      sttOptions.value = options
    } catch (error) {
      console.error('加载语音识别配置失败:', error)
      message.error('加载语音识别配置失败')
    } finally {
      sttLoading.value = false
    }
  }

  /**
   * 根据模型ID获取模型信息
   */
  function getModelInfo(modelId?: number) {
    if (!modelId) return null
    return allModels.value.find(m => m.value === modelId)
  }

  /**
   * 根据语音名称获取语音信息
   */
  function getVoiceInfo(voiceName?: string) {
    if (!voiceName) return null
    return allVoices.value.find(v => v.value === voiceName)
  }

  /**
   * 格式化提供商名称
   */
  function formatProviderName(provider: string): string {
    const names: Record<string, string> = {
      edge: '微软Edge',
      aliyun: '阿里云',
      'aliyun-nls': '阿里云NLS',
      volcengine: '火山引擎',
      xfyun: '讯飞云',
      minimax: 'Minimax',
      coze: 'Coze',
      dify: 'Dify'
    }
    return names[provider] || provider.charAt(0).toUpperCase() + provider.slice(1)
  }

  /**
   * 获取语音Tag颜色
   */
  function getVoiceTagColor(provider?: string): string {
    const colors: Record<string, string> = {
      edge: 'green',
      aliyun: 'orange',
      'aliyun-nls': 'orange',
      volcengine: 'blue',
      xfyun: 'cyan',
      minimax: 'red'
    }
    return colors[provider || 'edge'] || 'green'
  }

  return {
    // 状态
    modelLoading,
    voiceLoading,
    sttLoading,
    // 数据
    allModels,
    allVoices,
    sttOptions,
    llmConfigs,
    agentConfigs,
    ttsConfigs,
    // 选择
    selectedModelId,
    selectedVoiceName,
    // 方法
    loadAllModels,
    loadAllVoices,
    loadSttOptions,
    getModelInfo,
    getVoiceInfo,
    formatProviderName,
    getVoiceTagColor
  }
}

