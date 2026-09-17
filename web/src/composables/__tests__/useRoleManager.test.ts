import { beforeEach, describe, expect, it, vi } from 'vitest'

const serviceMock = vi.hoisted(() => ({
  queryConfigs: vi.fn(),
  queryAgents: vi.fn(),
  querySherpaVoices: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

vi.mock('@/services/config', () => ({ queryConfigs: serviceMock.queryConfigs }))
vi.mock('@/services/agent', () => ({ queryAgents: serviceMock.queryAgents }))
vi.mock('@/services/role', () => ({ querySherpaVoices: serviceMock.querySherpaVoices }))

import { message } from 'ant-design-vue'

import { useRoleManager } from '../useRoleManager'

function page(list: unknown[]) {
  return { code: 200, message: 'ok', data: { list, total: list.length } }
}

const EDGE_JSON = [
  { Locale: 'zh-CN', ShortName: 'zh-CN-XiaoxiaoNeural', Gender: 'Female' },
  { Locale: 'en-US', ShortName: 'en-US-JennyNeural', Gender: 'Female' },
]

const ALIYUN_JSON = [{ label: '知厨', value: 'zhichu', gender: 'male' }]

/** 按 url 返回对应的静态音色清单，并记录每个 url 被请求了几次 */
function stubVoiceJsonFetch() {
  const fetchMock = vi.fn(async (url: string) => ({
    ok: true,
    json: async () => (url.includes('edge') ? EDGE_JSON : ALIYUN_JSON),
  }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('useRoleManager 模型列表降级', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 四个来源以前用 Promise.all 并联，Coze 挂掉会让整个模型下拉全空
  it('单个智能体来源失败时，其余模型照常进下拉并提示一次', async () => {
    serviceMock.queryConfigs.mockResolvedValue(
      page([{ configId: 1, configName: 'gpt', modelType: 'chat', provider: 'openai' }]),
    )
    serviceMock.queryAgents.mockImplementation(async ({ provider }: { provider: string }) => {
      if (provider === 'coze') throw new Error('boom')
      if (provider === 'dify') return page([{ configId: 2, agentName: 'dify-bot', provider: 'dify' }])
      return page([])
    })

    const { allModels, loadAllModels } = useRoleManager()
    await loadAllModels()

    expect(allModels.value.map(m => m.value)).toEqual([1, 2])
    expect(message.error).toHaveBeenCalledTimes(1)
  })

  it('重复加载不会让模型项累加', async () => {
    serviceMock.queryConfigs.mockResolvedValue(page([]))
    serviceMock.queryAgents.mockImplementation(async ({ provider }: { provider: string }) =>
      provider === 'coze' ? page([{ configId: 7, agentName: 'coze-bot', provider: 'coze' }]) : page([]),
    )

    const { allModels, loadAllModels } = useRoleManager()
    await loadAllModels()
    await loadAllModels()

    expect(allModels.value).toHaveLength(1)
    expect(message.error).not.toHaveBeenCalled()
  })
})

describe('useRoleManager 音色清单加载', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    serviceMock.querySherpaVoices.mockResolvedValue({ data: [] })
  })

  // 7 份静态清单约 315KB，没配 TTS 的 provider 拉下来也只会被丢掉
  it('只下载 edge 与已配置 provider 的清单，且清单只解析一次', async () => {
    const fetchMock = stubVoiceJsonFetch()
    serviceMock.queryConfigs.mockResolvedValue(page([{ configId: 9, provider: 'aliyun' }]))

    const { allVoices, loadAllVoices } = useRoleManager()
    await loadAllVoices()

    const requested = fetchMock.mock.calls.map(([url]) => url)
    expect(requested).toEqual([
      '/static/assets/edgeVoicesList.json',
      '/static/assets/aliyunVoicesList.json',
    ])

    // edge 只保留中文音色，阿里云音色关联到 TTS 配置
    expect(allVoices.value).toEqual([
      { label: 'Xiaoxiao (zh-CN)', value: 'zh-CN-XiaoxiaoNeural', gender: 'female', provider: 'edge', ttsId: -1 },
      { label: '知厨', value: 'zhichu', gender: 'male', provider: 'aliyun', ttsId: 9 },
    ])

    // 第二个实例复用模块级缓存，不再重新下载解析
    const second = useRoleManager()
    await second.loadAllVoices()
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(second.allVoices.value).toHaveLength(2)
  })
})

describe('useRoleManager 提供商展示信息', () => {
  it('音色与智能体提供商共用一张表', () => {
    const { formatProviderName, getVoiceTagColor } = useRoleManager()

    expect(formatProviderName('edge')).toBe('微软Edge')
    expect(formatProviderName('aliyun-nls')).toBe('阿里云NLS')
    expect(formatProviderName('coze')).toBe('Coze')
    expect(formatProviderName('unknown')).toBe('Unknown')

    expect(getVoiceTagColor('volcengine')).toBe('blue')
    expect(getVoiceTagColor('tencent')).toBe('green')
    expect(getVoiceTagColor()).toBe('green')
  })
})
