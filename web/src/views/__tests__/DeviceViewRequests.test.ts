import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { message } from 'ant-design-vue'

const deviceApiMock = vi.hoisted(() => ({
  queryDevices: vi.fn(),
  addDevice: vi.fn(),
  updateDevice: vi.fn(),
  deleteDevice: vi.fn(),
  clearDeviceMemory: vi.fn(),
}))

const roleApiMock = vi.hoisted(() => ({
  queryRoles: vi.fn(),
}))

vi.mock('@/services/device', () => deviceApiMock)
vi.mock('@/services/role', () => roleApiMock)
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))

import DeviceView from '../DeviceView.vue'
import type { Device } from '@/types/device'
import type { Role } from '@/types/role'

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface DeviceViewState {
  addDeviceCode: string
  editVisible: boolean
  roleItems: Role[]
  handleAddDevice: (code: string) => Promise<void>
  handleClearMemory: (device: Device) => Promise<void>
}

const device = { deviceId: 'd1', deviceName: '测试设备' } as Device

/** 写接口的成功响应体是 ApiResponse<Void>，data 恒为 null */
const writeOk = { code: 200, data: null, message: 'success' }

async function mountView() {
  const wrapper = shallowMount(DeviceView)
  await flushPromises()
  return wrapper.vm as unknown as DeviceViewState
}

describe('DeviceView 请求迁移', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    deviceApiMock.queryDevices.mockResolvedValue({ code: 200, data: { list: [], total: 0 } })
    roleApiMock.queryRoles.mockResolvedValue({
      code: 200,
      data: { list: [{ roleId: 1, roleName: '默认角色' }], total: 1 },
    })
  })

  it('添加设备：响应 data 为 null 也判成功，清空输入并刷新列表', async () => {
    const view = await mountView()
    const listCallsBefore = deviceApiMock.queryDevices.mock.calls.length
    deviceApiMock.addDevice.mockResolvedValue(writeOk)
    view.addDeviceCode = 'CODE-1'

    await view.handleAddDevice('CODE-1')
    await flushPromises()

    expect(deviceApiMock.addDevice).toHaveBeenCalledWith('CODE-1')
    expect(message.success).toHaveBeenCalledWith('common.addSuccess')
    expect(view.addDeviceCode).toBe('')
    expect(deviceApiMock.queryDevices.mock.calls.length).toBe(listCallsBefore + 1)
  })

  it('添加设备失败：弹后端原文，不清空输入也不刷新列表', async () => {
    const view = await mountView()
    const listCallsBefore = deviceApiMock.queryDevices.mock.calls.length
    deviceApiMock.addDevice.mockResolvedValue({ code: 500, data: null, message: '设备码无效' })
    view.addDeviceCode = 'CODE-1'

    await view.handleAddDevice('CODE-1')
    await flushPromises()

    expect(message.error).toHaveBeenCalledWith('设备码无效')
    expect(message.success).not.toHaveBeenCalled()
    expect(view.addDeviceCode).toBe('CODE-1')
    expect(deviceApiMock.queryDevices.mock.calls.length).toBe(listCallsBefore)
  })

  it('清除记忆：成功后关闭弹窗并刷新，失败时弹窗保持打开且不刷新', async () => {
    const view = await mountView()
    const listCallsBefore = deviceApiMock.queryDevices.mock.calls.length

    deviceApiMock.clearDeviceMemory.mockResolvedValue(writeOk)
    view.editVisible = true
    await view.handleClearMemory(device)
    await flushPromises()

    expect(message.success).toHaveBeenCalledWith('common.deleteSuccess')
    expect(view.editVisible).toBe(false)
    expect(deviceApiMock.queryDevices.mock.calls.length).toBe(listCallsBefore + 1)

    deviceApiMock.clearDeviceMemory.mockRejectedValue(new Error('network down'))
    view.editVisible = true
    await view.handleClearMemory(device)
    await flushPromises()

    expect(view.editVisible).toBe(true)
    expect(deviceApiMock.queryDevices.mock.calls.length).toBe(listCallsBefore + 1)
  })


  it('角色列表拉取失败：静默降级，不弹错误提示', async () => {
    roleApiMock.queryRoles.mockRejectedValue(new Error('network down'))

    const view = await mountView()

    expect(view.roleItems).toEqual([])
    expect(message.error).not.toHaveBeenCalled()
  })
})
