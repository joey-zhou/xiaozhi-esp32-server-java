import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { message } from 'ant-design-vue'

const roleApiMock = vi.hoisted(() => ({
  queryRoles: vi.fn(),
  addRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  updateToolsStatus: vi.fn(),
  testVoice: vi.fn(),
  querySherpaVoices: vi.fn(),
  getDisabledTools: vi.fn(),
  getSystemGlobalTools: vi.fn(),
}))

const listApiMock = vi.hoisted(() => ({
  queryTemplates: vi.fn(),
  queryConfigs: vi.fn(),
}))

vi.mock('@/services/role', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/role')>()),
  ...roleApiMock,
}))

vi.mock('@/services/template', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/template')>()),
  queryTemplates: listApiMock.queryTemplates,
}))

vi.mock('@/services/config', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/config')>()),
  queryConfigs: listApiMock.queryConfigs,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({ hasPermission: () => true }),
}))

import RoleView from '../RoleView.vue'
import type { Role } from '@/types/role'

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface RoleViewState {
  activeTabKey: string
  editingRoleId?: number
  formRef?: Record<string, unknown>
  handleDelete: (record: Role) => Promise<void>
  handleSubmit: () => Promise<void>
}

// a-form 在 shallowMount 下解析不出组件，模板 ref 落到原生节点上；重渲染会重新赋值，
// 所以把校验方法补在节点自身而不是替换整个 ref
function stubFormRef(view: RoleViewState) {
  Object.assign(view.formRef as object, {
    validate: vi.fn().mockResolvedValue(true),
    resetFields: vi.fn(),
  })
}

const rolePage = { code: 200, data: { list: [], total: 0 }, message: '' }

async function mountView() {
  const wrapper = shallowMount(RoleView, {
    global: { directives: { permission: {} } },
  })
  await flushPromises()
  // 初始化时列表已经拉过一次，后续断言只关心刷新那一次
  roleApiMock.queryRoles.mockClear()
  return wrapper.vm as unknown as RoleViewState
}

describe('RoleView 请求迁移', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    roleApiMock.queryRoles.mockResolvedValue(rolePage)
    listApiMock.queryTemplates.mockResolvedValue({ code: 200, data: { list: [], total: 0 }, message: '' })
    listApiMock.queryConfigs.mockResolvedValue({ code: 200, data: { list: [], total: 0 }, message: '' })
  })

  it('删除成功时即便响应 data 为 null 也判成功并重新拉列表', async () => {
    // 写接口的成功响应体是 ApiResponse<Void>，data 恒为 null
    roleApiMock.deleteRole.mockResolvedValue({ code: 200, data: null, message: '' })
    const view = await mountView()

    await view.handleDelete({ roleId: 7, totalDevice: 0 } as Role)

    expect(message.success).toHaveBeenCalledWith('role.deleteRoleSuccess')
    expect(roleApiMock.queryRoles).toHaveBeenCalledTimes(1)
  })

  it('删除业务码失败时弹后端原文且不刷新列表', async () => {
    roleApiMock.deleteRole.mockResolvedValue({ code: 500, data: null, message: '角色不存在' })
    const view = await mountView()

    await view.handleDelete({ roleId: 7, totalDevice: 0 } as Role)

    expect(message.error).toHaveBeenCalledWith('角色不存在')
    expect(message.success).not.toHaveBeenCalled()
    expect(roleApiMock.queryRoles).not.toHaveBeenCalled()
  })

  it('更新角色成功时即便响应 data 为 null 也切回列表并刷新', async () => {
    roleApiMock.updateRole.mockResolvedValue({ code: 200, data: null, message: '' })
    const view = await mountView()
    stubFormRef(view)
    view.editingRoleId = 7
    view.activeTabKey = '2'

    await view.handleSubmit()

    expect(message.success).toHaveBeenCalledWith('role.updateRoleSuccess')
    expect(view.activeTabKey).toBe('1')
    expect(roleApiMock.queryRoles).toHaveBeenCalledTimes(1)
  })

  it('新增角色失败时不切回列表也不刷新', async () => {
    roleApiMock.addRole.mockResolvedValue({ code: 500, data: null, message: '角色名已存在' })
    const view = await mountView()
    stubFormRef(view)
    view.activeTabKey = '2'

    await view.handleSubmit()

    expect(message.error).toHaveBeenCalledWith('角色名已存在')
    expect(message.success).not.toHaveBeenCalled()
    expect(view.activeTabKey).toBe('2')
    expect(roleApiMock.queryRoles).not.toHaveBeenCalled()
  })

})
