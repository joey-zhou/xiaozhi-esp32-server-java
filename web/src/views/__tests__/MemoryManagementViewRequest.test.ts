import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { message } from 'ant-design-vue'

const memoryApiMock = vi.hoisted(() => ({
  querySummaryMemory: vi.fn(),
  queryChatMemory: vi.fn(),
  deleteSummaryMemory: vi.fn(),
}))

const roleApiMock = vi.hoisted(() => ({ queryRoles: vi.fn() }))
const deviceApiMock = vi.hoisted(() => ({ queryDevices: vi.fn() }))
const messageApiMock = vi.hoisted(() => ({ deleteMessage: vi.fn() }))

vi.mock('@/services/memory', () => memoryApiMock)
vi.mock('@/services/role', () => roleApiMock)
vi.mock('@/services/device', () => deviceApiMock)
vi.mock('@/services/message', () => messageApiMock)
vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/memory/summary', query: { roleId: '3', deviceId: 'd1' } }),
  useRouter: () => ({ push: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
}))

import MemoryManagementView from '../MemoryManagementView.vue'

// <script setup> 的绑定不能经 vm 写回，改值要直接落到 setupState
interface ViewState {
  handleDeleteMemory: (record: { id: number }) => Promise<void>
}

async function mountView() {
  const wrapper = shallowMount(MemoryManagementView, {
    global: {
      directives: { permission: {} },
    },
  })
  await flushPromises()
  const instance = wrapper.vm.$ as unknown as { setupState: ViewState }
  return instance.setupState
}

describe('MemoryManagementView 请求迁移', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    roleApiMock.queryRoles.mockResolvedValue({
      code: 200,
      data: { list: [{ roleId: 3, roleName: '默认角色' }], total: 1 },
      message: '',
    })
    deviceApiMock.queryDevices.mockResolvedValue({
      code: 200,
      data: { list: [{ deviceId: 'd1', deviceName: 'D1' }], total: 1 },
      message: '',
    })
    memoryApiMock.querySummaryMemory.mockResolvedValue({
      code: 200,
      data: { list: [{ id: 123, summary: 'hi' }], total: 1 },
      message: '',
    })
  })

  it('删除摘要记忆：响应 data 为 null 也判成功并刷新列表', async () => {
    const view = await mountView()
    const queriesBefore = memoryApiMock.querySummaryMemory.mock.calls.length
    // 写接口的成功响应体是 ApiResponse<Void>，data 恒为 null
    memoryApiMock.deleteSummaryMemory.mockResolvedValue({ code: 200, data: null, message: '' })

    await view.handleDeleteMemory({ id: 123 })
    await flushPromises()

    expect(memoryApiMock.deleteSummaryMemory).toHaveBeenCalledWith(3, 'd1', 123)
    expect(message.success).toHaveBeenCalledWith('common.deleteSuccess')
    expect(memoryApiMock.querySummaryMemory.mock.calls.length).toBe(queriesBefore + 1)
  })

  it('删除摘要记忆失败：不刷新列表，弹后端原文', async () => {
    const view = await mountView()
    const queriesBefore = memoryApiMock.querySummaryMemory.mock.calls.length
    memoryApiMock.deleteSummaryMemory.mockResolvedValue({ code: 500, data: null, message: '记忆不存在' })

    await view.handleDeleteMemory({ id: 123 })
    await flushPromises()

    expect(message.success).not.toHaveBeenCalledWith('common.deleteSuccess')
    expect(message.error).toHaveBeenCalledWith('记忆不存在')
    expect(memoryApiMock.querySummaryMemory.mock.calls.length).toBe(queriesBefore)
  })
})
