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
  batchUpdateDeviceRole: vi.fn(),
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
  selectedRowKeys: string[]
  batchRoleModalOpen: boolean
  batchRoleId: number | undefined
  handleBatchRole: () => void
  handleBatchRoleConfirm: () => Promise<void>
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

  // 选中的设备一次改到同一个智能体，归属与「角色属于设备所属用户」由后端判
  it('批量改智能体：一个请求带上选中的设备，成功后清空勾选并刷新列表', async () => {
    const view = await mountView()
    const listCallsBefore = deviceApiMock.queryDevices.mock.calls.length
    deviceApiMock.batchUpdateDeviceRole.mockResolvedValue({ code: 200, data: { successCount: 2, totalCount: 2 } })
    view.selectedRowKeys = ['d1', 'd2']

    view.handleBatchRole()
    expect(view.batchRoleModalOpen).toBe(true)
    view.batchRoleId = 1
    await view.handleBatchRoleConfirm()
    await flushPromises()

    expect(deviceApiMock.batchUpdateDeviceRole).toHaveBeenCalledWith({ deviceIds: ['d1', 'd2'], roleId: 1 })
    expect(message.success).toHaveBeenCalledWith('device.batchRoleUpdated:{"count":2}')
    expect(view.batchRoleModalOpen).toBe(false)
    expect(view.selectedRowKeys).toEqual([])
    expect(deviceApiMock.queryDevices.mock.calls.length).toBe(listCallsBefore + 1)
  })

  it('批量改智能体：后端拒绝时提示原因、保留弹窗与勾选', async () => {
    const view = await mountView()
    deviceApiMock.batchUpdateDeviceRole.mockResolvedValue({ code: 400, data: null, message: '角色不属于设备所属用户' })
    view.selectedRowKeys = ['d1']
    view.handleBatchRole()
    view.batchRoleId = 1

    await view.handleBatchRoleConfirm()
    await flushPromises()

    expect(message.error).toHaveBeenCalledWith('角色不属于设备所属用户')
    expect(view.batchRoleModalOpen).toBe(true)
    expect(view.selectedRowKeys).toEqual(['d1'])
  })

  it('批量改智能体：没选设备或没选智能体时不发请求', async () => {
    const view = await mountView()

    view.handleBatchRole()
    expect(view.batchRoleModalOpen).toBe(false)

    view.selectedRowKeys = ['d1']
    view.handleBatchRole()
    await view.handleBatchRoleConfirm()
    expect(deviceApiMock.batchUpdateDeviceRole).not.toHaveBeenCalled()
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
