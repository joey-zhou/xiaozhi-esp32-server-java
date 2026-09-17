import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

// useRequest 顶层引了 loading store，真实模块会连带初始化 i18n 实例
vi.mock('@/store/loading', () => ({
  useLoadingStore: () => ({ showLoading: vi.fn(), hideLoading: vi.fn() }),
}))

vi.mock('@/services/request', () => ({
  shouldIgnoreRequestError: vi.fn(() => false),
  isForbiddenError: vi.fn(() => false),
}))

vi.mock('@/services/config', () => ({
  queryConfigs: vi.fn(),
  updateConfig: vi.fn(),
  deleteConfig: vi.fn(),
}))

import { message } from 'ant-design-vue'
import { deleteConfig, queryConfigs, updateConfig } from '@/services/config'
import { useConfigManager } from '../useConfigManager'

const queryConfigsMock = vi.mocked(queryConfigs)
const updateConfigMock = vi.mocked(updateConfig)
const deleteConfigMock = vi.mocked(deleteConfig)
const messageMock = vi.mocked(message)

/** 列表接口的成功响应 */
function pageOk() {
  return Promise.resolve({ code: 200, message: 'ok', data: { list: [], total: 0 } })
}

describe('useConfigManager llm factory index', () => {
  it('shares one factory index across instances', () => {
    const first = useConfigManager('llm')
    const second = useConfigManager('llm')

    const models = first.getModelsByProviderAndType('OpenAI', 'chat')
    expect(models.length).toBeGreaterThan(0)
    // 同一份模块级索引，重复实例化不再重建
    expect(second.getModelsByProviderAndType('OpenAI', 'chat')).toBe(models)
    expect(second.typeOptions.value).toBe(first.typeOptions.value)
  })

  it('exposes providers only for llm', () => {
    expect(useConfigManager('llm').typeOptions.value.length).toBeGreaterThan(0)
    // 非 llm 走 providerConfig 的静态 typeOptions，不碰工厂索引
    expect(useConfigManager('oss').typeOptions.value.some((item) => item.value === 'local')).toBe(true)
  })

  it('keeps model type buckets separated', () => {
    const manager = useConfigManager('llm')

    const chat = manager.getModelsByProviderAndType('OpenAI', 'chat')
    expect(chat.every((model) => model.model_type === 'chat')).toBe(true)
    expect(manager.getModelsByProviderAndType('not-a-provider', 'chat')).toEqual([])
  })
})

describe('useConfigManager write actions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    queryConfigsMock.mockImplementation(pageOk as never)
  })

  it('refetches after a successful delete', async () => {
    deleteConfigMock.mockResolvedValue({ code: 200, message: 'ok', data: null } as never)
    const manager = useConfigManager('llm')

    await manager.deleteConfig(7)

    expect(deleteConfigMock).toHaveBeenCalledWith(7)
    expect(messageMock.success).toHaveBeenCalledWith('common.deleteSuccess')
    expect(queryConfigsMock).toHaveBeenCalledTimes(1)
    expect(manager.loading.value).toBe(false)
  })

  it('keeps the list untouched when delete fails', async () => {
    deleteConfigMock.mockResolvedValue({ code: 500, message: '删不掉', data: null } as never)
    const manager = useConfigManager('llm')

    await manager.deleteConfig(7)

    expect(messageMock.error).toHaveBeenCalledWith('删不掉')
    expect(messageMock.success).not.toHaveBeenCalled()
    expect(queryConfigsMock).not.toHaveBeenCalled()
    expect(manager.loading.value).toBe(false)
  })

  it('sends isDefault and refetches on set-as-default', async () => {
    updateConfigMock.mockResolvedValue({ code: 200, message: 'ok', data: null } as never)
    const manager = useConfigManager('llm')

    await manager.setAsDefault({ configId: 3, configName: 'gpt', modelType: 'chat' } as never)

    expect(updateConfigMock).toHaveBeenCalledWith({
      configId: 3,
      configType: 'llm',
      modelType: 'chat',
      isDefault: '1',
    })
    expect(messageMock.success).toHaveBeenCalledWith('common.setDefaultSuccess')
    expect(queryConfigsMock).toHaveBeenCalledTimes(1)
  })

  it('does nothing for tts', async () => {
    const manager = useConfigManager('tts')

    await manager.setAsDefault({ configId: 3, configName: 'v', modelType: 'chat' } as never)

    expect(updateConfigMock).not.toHaveBeenCalled()
    expect(queryConfigsMock).not.toHaveBeenCalled()
  })

  // 业务码失败弹后端返回的 message，只有传输层异常才说服务端维护
  it('shows the maintenance text on transport failure', async () => {
    updateConfigMock.mockRejectedValue(new Error('boom'))
    const manager = useConfigManager('llm')

    await manager.setAsDefault({ configId: 3, configName: 'gpt', modelType: 'chat' } as never)

    expect(messageMock.error).toHaveBeenCalledWith('common.serverMaintenance')
    expect(queryConfigsMock).not.toHaveBeenCalled()
    expect(manager.loading.value).toBe(false)
  })
})
