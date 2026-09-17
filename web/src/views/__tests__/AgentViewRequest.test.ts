import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { message } from 'ant-design-vue'
import type { Ref } from 'vue'

const agentApiMock = vi.hoisted(() => ({
  queryAgents: vi.fn(),
}))

const configApiMock = vi.hoisted(() => ({
  updateConfig: vi.fn(),
  queryPlatformConfig: vi.fn(),
  addPlatformConfig: vi.fn(),
  updatePlatformConfig: vi.fn(),
}))

vi.mock('@/services/agent', () => agentApiMock)
vi.mock('@/services/config', () => configApiMock)

import AgentView from '../config/AgentView.vue'
import type { Agent, PlatformConfig } from '@/types/agent'

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface AgentViewState {
  handleSetDefault: (record: Agent) => Promise<void>
  handleConfigPlatform: () => Promise<void>
  platformModal: {
    visible: Ref<boolean>
    submit: (data: PlatformConfig) => Promise<boolean>
  }
}

const agent = { configId: 7, agentName: '小智' } as Agent

const platformForm = {
  configType: 'agent',
  provider: 'coze',
  configName: 'coze',
  configDesc: '',
  appId: 'app-1',
  apiKey: 'key',
  apiSecret: 'secret',
  ak: 'ak',
  sk: 'sk',
} as PlatformConfig

async function mountView() {
  const wrapper = shallowMount(AgentView, {
    global: {
      directives: { permission: {} },
      // 弹窗关闭时会调 formRef.resetFields，默认的空壳 stub 上没有这个方法
      stubs: {
        'a-form': {
          template: '<form><slot /></form>',
          methods: {
            resetFields: () => undefined,
            validate: () => Promise.resolve(),
          },
        },
      },
    },
  })
  await flushPromises()
  // 初始化时的列表请求不参与断言
  agentApiMock.queryAgents.mockClear()
  return wrapper.vm as unknown as AgentViewState
}

describe('AgentView 请求处理', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    agentApiMock.queryAgents.mockResolvedValue({ code: 200, data: { list: [], total: 0 }, message: '' })
  })

  it('设为默认：响应 data 为 null 也判成功，并刷新列表', async () => {
    // 写接口的成功响应体是 ApiResponse<Void>，data 恒为 null
    configApiMock.updateConfig.mockResolvedValue({ code: 200, data: null, message: '' })
    const view = await mountView()

    await view.handleSetDefault(agent)

    expect(configApiMock.updateConfig).toHaveBeenCalledWith({ configId: 7, isDefault: '1' })
    expect(message.success).toHaveBeenCalledWith('common.setDefaultSuccess:{"name":"小智"}')
    expect(agentApiMock.queryAgents).toHaveBeenCalled()
  })

  it('设为默认失败：不刷新列表，错误提示用后端原文', async () => {
    configApiMock.updateConfig.mockResolvedValue({ code: 500, data: null, message: '该配置已被删除' })
    const view = await mountView()

    await view.handleSetDefault(agent)

    expect(message.error).toHaveBeenCalledWith('该配置已被删除')
    expect(agentApiMock.queryAgents).not.toHaveBeenCalled()
  })

  it('平台配置保存：响应 data 为 null 也判成功，并刷新列表', async () => {
    configApiMock.addPlatformConfig.mockResolvedValue({ code: 200, data: null, message: '' })
    const view = await mountView()

    const ok = await view.platformModal.submit({ ...platformForm })

    expect(ok).toBe(true)
    expect(message.success).toHaveBeenCalledWith('common.addPlatformConfigSuccess')
    expect(agentApiMock.queryAgents).toHaveBeenCalled()
  })

  it('平台配置保存失败：不刷新列表，弹窗不关闭', async () => {
    configApiMock.addPlatformConfig.mockResolvedValue({ code: 500, data: null, message: '凭据校验不通过' })
    const view = await mountView()
    view.platformModal.visible.value = true

    const ok = await view.platformModal.submit({ ...platformForm })

    expect(ok).toBe(false)
    expect(message.error).toHaveBeenCalledWith('凭据校验不通过')
    expect(agentApiMock.queryAgents).not.toHaveBeenCalled()
    expect(view.platformModal.visible.value).toBe(true)
  })

  it('查询平台配置失败：不打开弹窗', async () => {
    configApiMock.queryPlatformConfig.mockResolvedValue({ code: 500, data: null, message: '查询失败' })
    const view = await mountView()

    await view.handleConfigPlatform()

    expect(view.platformModal.visible.value).toBe(false)
    expect(message.error).toHaveBeenCalledWith('查询失败')
  })
})
