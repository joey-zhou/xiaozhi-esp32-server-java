import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { message } from 'ant-design-vue'

const configApiMock = vi.hoisted(() => ({
  queryConfigs: vi.fn(),
  addConfig: vi.fn(),
  updateConfig: vi.fn(),
  deleteConfig: vi.fn(),
  testConfig: vi.fn(),
  queryPlatformConfig: vi.fn(),
  addPlatformConfig: vi.fn(),
  updatePlatformConfig: vi.fn(),
}))

vi.mock('@/services/config', () => configApiMock)

import ConfigManager from '../ConfigManager.vue'
import type { Config } from '@/types/config'

// <script setup> 的绑定不能经 vm 写回，改值要直接落到 setupState
interface ConfigManagerState {
  formRef: { validate: () => Promise<void>; resetFields: () => void } | undefined
  formData: Partial<Config>
  editingConfigId: number | undefined
  activeTabKey: string
  handleSubmit: () => Promise<void>
}

async function mountManager() {
  const wrapper = shallowMount(ConfigManager, {
    props: { configType: 'tts' as const },
    global: { directives: { permission: {} } },
  })
  await flushPromises()
  const state = (wrapper.vm.$ as unknown as { setupState: ConfigManagerState }).setupState

  // 表单方法来自 a-form 实例，shallowMount 下的替身没有，这里给一份直接通过的
  state.formRef = { validate: vi.fn().mockResolvedValue(undefined), resetFields: vi.fn() }
  state.formData = { provider: 'aliyun', configName: '默认音色', isDefault: false }
  return state
}

describe('ConfigManager 表单提交', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    configApiMock.queryConfigs.mockResolvedValue({
      code: 200,
      message: '',
      data: { list: [], total: 0 },
    })
  })

  it('新增成功：响应 data 为 null 也判成功，回到列表页并重新拉列表', async () => {
    const state = await mountManager()
    const queriesBefore = configApiMock.queryConfigs.mock.calls.length
    // 写接口的成功响应体是 ApiResponse<Void>，data 恒为 null
    configApiMock.addConfig.mockResolvedValue({ code: 200, data: null, message: '' })

    await state.handleSubmit()
    await flushPromises()

    expect(configApiMock.addConfig).toHaveBeenCalledTimes(1)
    expect(message.success).toHaveBeenCalledWith('config.createSuccess')
    expect(state.activeTabKey).toBe('1')
    expect(configApiMock.queryConfigs.mock.calls.length).toBe(queriesBefore + 1)
  })

  it('提交失败：弹后端原文，不切回列表页也不重新拉列表', async () => {
    const state = await mountManager()
    const queriesBefore = configApiMock.queryConfigs.mock.calls.length
    state.activeTabKey = '2'
    configApiMock.addConfig.mockResolvedValue({ code: 500, data: null, message: '配置名称重复' })

    await state.handleSubmit()
    await flushPromises()

    expect(message.error).toHaveBeenCalledWith('配置名称重复')
    expect(message.success).not.toHaveBeenCalled()
    expect(state.activeTabKey).toBe('2')
    expect(configApiMock.queryConfigs.mock.calls.length).toBe(queriesBefore)
  })
})
