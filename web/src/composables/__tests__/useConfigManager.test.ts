import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

vi.mock('@/services/request', () => ({
  shouldIgnoreRequestError: vi.fn(() => false),
}))

vi.mock('@/services/config', () => ({
  queryConfigs: vi.fn(),
  updateConfig: vi.fn(),
  deleteConfig: vi.fn(),
}))

import { useConfigManager } from '../useConfigManager'

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
