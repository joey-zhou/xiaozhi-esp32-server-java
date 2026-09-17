import { beforeEach, describe, expect, it, vi } from 'vitest'

const serviceMock = vi.hoisted(() => ({
  queryConfigs: vi.fn(),
  queryAgents: vi.fn(),
  querySherpaVoices: vi.fn(),
  queryLocalStt: vi.fn(),
}))

// vue-i18n 与 ant-design-vue 由 src/__tests__/setup.ts 统一 mock：
// 这里再局部 mock 会盖掉 setup 里的 createI18n，让 @/services/config -> services/request -> locales 整条链炸掉

vi.mock('@/services/config', () => ({ queryConfigs: serviceMock.queryConfigs }))
vi.mock('@/services/agent', () => ({ queryAgents: serviceMock.queryAgents }))
vi.mock('@/services/role', () => ({
  querySherpaVoices: serviceMock.querySherpaVoices,
  queryLocalStt: serviceMock.queryLocalStt,
}))

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

// 配置查询的两条失败路径处理方式不同，这两组用例就是钉住这个区别：
// 「没配」（非 200）静默退化成可用的最小清单，「请求挂了」（throw）要报错并保留上一次的清单。
// 正因为要区分这两条路，这里没有迁到 useRequest 的 execute——它把两者都变成返回 undefined。
describe('useRoleManager 未配置时静默退化', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    stubVoiceJsonFetch()
    serviceMock.querySherpaVoices.mockResolvedValue({ code: 200, message: 'ok', data: [] })
  })

  it('TTS 配置返回非 200 时只剩 edge 音色，且不弹提示', async () => {
    serviceMock.queryConfigs.mockResolvedValue({ code: 500, message: 'boom', data: null })

    const { allVoices, loadAllVoices } = useRoleManager()
    await loadAllVoices()

    expect(allVoices.value.map(voice => voice.provider)).toEqual(['edge'])
    expect(message.error).not.toHaveBeenCalled()
  })

  it('STT 配置返回非 200 时只留本地识别一项，且不弹提示', async () => {
    serviceMock.queryConfigs.mockResolvedValue({ code: 500, message: 'boom', data: null })
    serviceMock.queryLocalStt.mockResolvedValue({
      code: 200, message: 'ok', data: { provider: 'sherpa-onnx', available: true },
    })

    const { sttOptions, localSttLabel, loadSttOptions } = useRoleManager()
    await loadSttOptions()

    // 本地识别那一项的名字跟着服务端实际加载的模型走
    expect(sttOptions.value).toEqual([
      { label: 'role.localSttSenseVoice', value: -1, desc: 'role.localSttDesc' },
    ])
    expect(localSttLabel.value).toBe('role.localSttSenseVoice')
    expect(message.error).not.toHaveBeenCalled()
  })

  it('本地识别状态取不到时不给本地选项，只留第三方清单', async () => {
    serviceMock.queryConfigs.mockResolvedValue(page([{ configId: 3, configName: '火山', configDesc: 'd' }]))
    serviceMock.queryLocalStt.mockRejectedValue(new Error('down'))

    const { sttOptions, localSttAvailable, loadSttOptions } = useRoleManager()
    await loadSttOptions()

    expect(sttOptions.value.map(o => o.label)).toEqual(['火山'])
    expect(localSttAvailable.value).toBe(false)
    expect(message.error).not.toHaveBeenCalled()
  })

  it('服务端两个本地模型都没有时没有本地识别可选，列表里的老角色显示模型未安装', async () => {
    serviceMock.queryConfigs.mockResolvedValue(page([]))
    serviceMock.queryLocalStt.mockResolvedValue({ code: 200, message: 'ok', data: { provider: null, available: false } })

    const { sttOptions, localSttLabel, localSttAvailable, loadSttOptions } = useRoleManager()
    await loadSttOptions()

    expect(sttOptions.value).toEqual([])
    expect(localSttAvailable.value).toBe(false)
    expect(localSttLabel.value).toBe('role.localSttUnavailable')
  })
})

describe('useRoleManager 请求异常时保留上一次的清单', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    stubVoiceJsonFetch()
    serviceMock.querySherpaVoices.mockResolvedValue({ code: 200, message: 'ok', data: [] })
    serviceMock.queryLocalStt.mockResolvedValue({
      code: 200, message: 'ok', data: { provider: 'vosk', available: true },
    })
  })

  it('TTS 配置请求抛错时报错，且不把音色清单覆写成只有 edge', async () => {
    serviceMock.queryConfigs.mockResolvedValueOnce({
      code: 200,
      message: 'ok',
      data: { list: [{ configId: 7, provider: 'aliyun', configName: 'ali' }], total: 1 },
    })
    const { allVoices, loadAllVoices } = useRoleManager()
    await loadAllVoices()
    const loaded = allVoices.value

    expect(loaded.length).toBeGreaterThan(0)

    serviceMock.queryConfigs.mockRejectedValue(new Error('boom'))
    await loadAllVoices()

    expect(allVoices.value).toBe(loaded)
    expect(message.error).toHaveBeenCalledWith('role.loadVoiceFailed')
  })

  it('STT 配置请求抛错时报错，且不把选项覆写成只有本地识别', async () => {
    serviceMock.queryConfigs.mockResolvedValueOnce({
      code: 200,
      message: 'ok',
      data: { list: [{ configId: 9, configName: 'whisper', configDesc: 'd' }], total: 1 },
    })
    const { sttOptions, loadSttOptions } = useRoleManager()
    await loadSttOptions()
    const loaded = sttOptions.value

    expect(loaded).toHaveLength(2)

    serviceMock.queryConfigs.mockRejectedValue(new Error('boom'))
    await loadSttOptions()

    expect(sttOptions.value).toBe(loaded)
    expect(message.error).toHaveBeenCalledWith('role.loadSttFailed')
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
