/**
 * 角色管理 Composable
 * 统一处理模型选择和音色选择逻辑
 */

import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { queryConfigs } from '@/services/config'
import { queryAgents } from '@/services/agent'
import { querySherpaVoices } from '@/services/role'
import type { ModelOption, VoiceOption, SttOption, VoiceProvider } from '@/types/role'
import type { Config } from '@/types/config'
import type { Agent } from '@/types/agent'
import type { PageResponse } from '@/types/api'

/**
 * 音色提供商总表：静态清单路径、下拉显示名、标签色
 * 没有 json 的表示音色由接口动态返回（sherpa-onnx）
 */
const VOICE_PROVIDERS: Record<VoiceProvider, { json?: string; label: string; color: string }> = {
  edge: { json: '/static/assets/edgeVoicesList.json', label: '微软Edge', color: 'green' },
  aliyun: { json: '/static/assets/aliyunVoicesList.json', label: '阿里云', color: 'orange' },
  'aliyun-nls': { json: '/static/assets/aliyunNlsVoicesList.json', label: '阿里云NLS', color: 'orange' },
  volcengine: { json: '/static/assets/volcengineVoicesList.json', label: '火山引擎', color: 'blue' },
  xfyun: { json: '/static/assets/xfyunVoicesList.json', label: '讯飞云', color: 'cyan' },
  minimax: { json: '/static/assets/minimaxVoicesList.json', label: 'Minimax', color: 'red' },
  tencent: { json: '/static/assets/tencentVoicesList.json', label: '腾讯云', color: 'green' },
  'sherpa-onnx': { label: 'Sherpa-ONNX', color: 'purple' },
}

/** 智能体提供商：只参与模型下拉的显示名 */
const AGENT_PROVIDERS: Array<{ provider: string; label: string }> = [
  { provider: 'coze', label: 'Coze' },
  { provider: 'dify', label: 'Dify' },
  { provider: 'xingchen', label: 'XingChen' },
]

/** edge 音色不依赖 TTS 配置，固定用这个 ttsId */
const EDGE_TTS_ID = -1

interface EdgeVoice {
  Locale: string
  ShortName: string
  Gender: string
}

/** 把 edge 原始清单裁成中文音色 */
function parseEdgeVoices(data: EdgeVoice[]): VoiceOption[] {
  return data
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
        provider: 'edge' as VoiceProvider,
      }
    })
}

/** 静态音色清单是常量，解析结果按 provider 缓存在模块作用域，页面来回切不重复下载解析 */
const voiceJsonCache = new Map<VoiceProvider, Promise<VoiceOption[]>>()

async function fetchVoiceJson(provider: VoiceProvider): Promise<VoiceOption[]> {
  const url = VOICE_PROVIDERS[provider].json
  if (!url) return []

  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`加载${provider}语音列表失败`)
  }
  const data = await response.json()

  if (provider === 'edge') {
    return parseEdgeVoices(data as EdgeVoice[])
  }

  // 其他提供商直接返回原始 label，不添加提供商标识
  return (data as Omit<VoiceOption, 'provider'>[]).map((voice) => ({ ...voice, provider }))
}

function loadVoiceJson(provider: VoiceProvider): Promise<VoiceOption[]> {
  const cached = voiceJsonCache.get(provider)
  if (cached) return cached

  const task = fetchVoiceJson(provider).catch((error) => {
    // 失败的结果不留缓存，下次进页面还能重试
    voiceJsonCache.delete(provider)
    console.warn(`加载${provider}语音列表失败:`, error)
    return [] as VoiceOption[]
  })
  voiceJsonCache.set(provider, task)
  return task
}

/** 取分页结果里的 list：请求失败或非 200 一律当空列表，不让一个来源挂掉整个下拉 */
function settledList<T>(result: PromiseSettledResult<PageResponse<T>>, source: string): T[] {
  if (result.status === 'rejected') {
    console.error(`加载${source}列表失败:`, result.reason)
    return []
  }
  if (result.value.code !== 200) {
    console.error(`加载${source}列表失败:`, result.value.message)
    return []
  }
  return result.value.data?.list ?? []
}

export function useRoleManager() {
  const { t } = useI18n()

  // 加载状态
  const modelLoading = ref(false)
  const voiceLoading = ref(false)
  const sttLoading = ref(false)

  // 模型相关
  const allModels = ref<ModelOption[]>([])

  // 语音相关 - 所有语音列表（来自各个JSON文件）
  const allVoices = ref<VoiceOption[]>([])

  // 语音识别
  const sttOptions = ref<SttOption[]>([])

  /**
   * 加载所有模型（LLM + Agent）
   * 四个来源互不依赖，用 allSettled 逐个降级，一个挂掉不会让整个下拉全空
   */
  async function loadAllModels() {
    modelLoading.value = true
    try {
      const [llmResults, agentResults] = await Promise.all([
        Promise.allSettled([queryConfigs({ configType: 'llm', pageNo: 1, pageSize: 1000 })]),
        Promise.allSettled(
          AGENT_PROVIDERS.map(({ provider }) => queryAgents({ provider, pageNo: 1, pageSize: 1000 }))
        ),
      ])

      // 部分来源失败时照常展示已拿到的模型，只补一条提示
      const anyFailed = [llmResults[0]!, ...agentResults].some(
        result => result.status === 'rejected' || result.value.code !== 200
      )

      const models: ModelOption[] = []

      // 处理LLM配置（只加载对话模型）
      settledList(llmResults[0]!, 'llm').forEach((config: Config) => {
        if (config.modelType !== 'chat') return
        models.push({
          label: config.configName,
          value: Number(config.configId),
          desc: config.configDesc,
          type: 'llm',
          provider: config.provider || '',
          configName: config.configName,
          configDesc: config.configDesc
        })
      })

      // 处理各家智能体
      AGENT_PROVIDERS.forEach(({ provider, label }, index) => {
        settledList(agentResults[index]!, provider).forEach((agent: Agent) => {
          models.push({
            label: `${agent.agentName} (${label}智能体)`,
            value: agent.configId,
            desc: agent.agentDesc,
            type: 'agent',
            provider,
            agentName: agent.agentName,
            agentDesc: agent.agentDesc
          })
        })
      })

      allModels.value = models
      if (anyFailed) {
        message.error(t('role.loadModelFailed'))
      }
    } catch (error) {
      console.error('加载模型列表失败:', error)
      message.error(t('role.loadModelFailed'))
    } finally {
      modelLoading.value = false
    }
  }

  /**
   * 加载所有语音选项（从TTS配置和JSON文件）
   * 只下载已配置 provider 的静态清单，没配的那几份 JSON 拉下来也会被丢掉
   */
  async function loadAllVoices() {
    voiceLoading.value = true
    try {
      // 1. 加载TTS配置
      const ttsRes = await queryConfigs({ configType: 'tts', pageNo: 1, pageSize: 1000 })
      const ttsConfigs: Config[] = ttsRes.code === 200 ? (ttsRes.data?.list ?? []) : []
      const configOf = (provider: string) => ttsConfigs.find(c => c.provider === provider)

      // 2. 并行加载需要的语音JSON文件和 sherpa-onnx 动态音色
      const jsonProviders = (Object.keys(VOICE_PROVIDERS) as VoiceProvider[])
        .filter(provider => VOICE_PROVIDERS[provider].json && (provider === 'edge' || configOf(provider)))

      const sherpaConfig = configOf('sherpa-onnx')
      const [jsonVoices, sherpaRes] = await Promise.all([
        Promise.all(jsonProviders.map(async provider => ({ provider, list: await loadVoiceJson(provider) }))),
        sherpaConfig ? querySherpaVoices().catch(() => ({ data: [] })) : Promise.resolve({ data: [] })
      ])

      // 3. 合并所有语音，并关联TTS配置
      const voices: VoiceOption[] = []

      jsonVoices.forEach(({ provider, list }) => {
        // 上面按有没有 TTS 配置筛过，非 edge 的 provider 这里一定取得到
        const ttsId = provider === 'edge' ? EDGE_TTS_ID : configOf(provider)!.configId
        list.forEach(voice => voices.push({ ...voice, ttsId }))
      })

      // sherpa-onnx 动态音色
      if (sherpaConfig) {
        const items = (sherpaRes as { data?: Record<string, string>[] }).data ?? []
        items.forEach((item: Record<string, string>) => {
          voices.push({
            label: item.label,
            value: item.value,
            gender: (item.gender as 'male' | 'female' | '') || '',
            provider: 'sherpa-onnx',
            model: item.model,
            ttsId: sherpaConfig.configId
          })
        })
      }

      allVoices.value = voices
    } catch (error) {
      console.error('加载语音列表失败:', error)
      message.error(t('role.loadVoiceFailed'))
    } finally {
      voiceLoading.value = false
    }
  }

  /**
   * 加载语音识别选项
   */
  async function loadSttOptions() {
    sttLoading.value = true
    try {
      const res = await queryConfigs({ configType: 'stt', pageNo: 1, pageSize: 1000 })
      const options: SttOption[] = [
        {
          label: t('role.voskLocalStt'),
          value: -1,
          desc: t('role.voskLocalSttDesc')
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
      message.error(t('role.loadSttFailed'))
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
   * @param voiceName 语音名称/ID
   */
  function getVoiceInfo(voiceName?: string) {
    if (!voiceName) return null
    return allVoices.value.find(v => v.value === voiceName) || null
  }

  /**
   * 格式化提供商名称
   */
  function formatProviderName(provider: string): string {
    const voiceLabel = VOICE_PROVIDERS[provider as VoiceProvider]?.label
    const agentLabel = AGENT_PROVIDERS.find(item => item.provider === provider)?.label
    return voiceLabel || agentLabel || provider.charAt(0).toUpperCase() + provider.slice(1)
  }

  /**
   * 获取语音Tag颜色
   */
  function getVoiceTagColor(provider?: string): string {
    return VOICE_PROVIDERS[provider as VoiceProvider]?.color || 'green'
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
