import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { Modal, message } from 'ant-design-vue'
import type { ModalFuncProps } from 'ant-design-vue'

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
import type { Config, ConfigType } from '@/types/config'

const confirmMock = vi.mocked(Modal.confirm)

/** 让下一次 Modal.confirm 立刻按用户点了「确定」/「取消」返回 */
function answerConfirmOnce(accepted: boolean) {
  confirmMock.mockImplementationOnce(((props: ModalFuncProps) => {
    if (accepted) {
      props.onOk?.()
    } else {
      props.onCancel?.()
    }
    return { destroy: vi.fn(), update: vi.fn() }
  }) as unknown as typeof Modal.confirm)
}

function lastConfirmProps(): ModalFuncProps {
  const calls = confirmMock.mock.calls as unknown as ModalFuncProps[][]
  const last = calls[calls.length - 1]
  if (!last) {
    throw new Error('Modal.confirm 未被调用')
  }
  return last[0] as ModalFuncProps
}

// <script setup> 的绑定不能经 vm 写回，改值要直接落到 setupState
interface ConfigManagerState {
  formRef: { validate: () => Promise<void>; resetFields: () => void } | undefined
  formData: Partial<Config>
  editingConfigId: number | undefined
  activeTabKey: string
  handleSubmit: () => Promise<void>
}

async function mountManager(configType: ConfigType = 'tts') {
  const wrapper = shallowMount(ConfigManager, {
    props: { configType },
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

  it('换对象存储被拦下：弹确认框展示后端原文，确定后带确认参数重发一次', async () => {
    const state = await mountManager('oss')
    const warning = '当前对象存储上还有 1234 条历史音频/文件，切换后这些内容将永久无法访问'
    // 4090 是后端 ResultStatus.CONFIRM_REQUIRED，写死在这里，改动后端码值时这条会先红
    configApiMock.addConfig
      .mockResolvedValueOnce({ code: 4090, data: null, message: warning })
      .mockResolvedValueOnce({ code: 200, data: null, message: '' })
    answerConfirmOnce(true)

    await state.handleSubmit()
    await flushPromises()

    expect(String(lastConfirmProps().content)).toBe(warning)
    expect(configApiMock.addConfig).toHaveBeenCalledTimes(2)
    expect(configApiMock.addConfig.mock.calls[0]![1]).toBe(false)
    expect(configApiMock.addConfig.mock.calls[1]![1]).toBe(true)
    expect(message.success).toHaveBeenCalledWith('config.createSuccess')
    expect(message.error).not.toHaveBeenCalled()
  })

  it('换对象存储被拦下：取消后什么都不做，不重发也不弹错误', async () => {
    const state = await mountManager('oss')
    configApiMock.addConfig.mockResolvedValueOnce({ code: 4090, data: null, message: '还有历史文件' })
    answerConfirmOnce(false)

    await state.handleSubmit()
    await flushPromises()

    expect(configApiMock.addConfig).toHaveBeenCalledTimes(1)
    expect(message.error).not.toHaveBeenCalled()
    expect(message.success).not.toHaveBeenCalled()
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
