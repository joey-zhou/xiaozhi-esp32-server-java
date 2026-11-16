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
  getRoleDefaults: vi.fn(),
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
  formData: Record<string, unknown>
  handleDelete: (record: Role) => Promise<void>
  handleEdit: (record: Role) => void
  handleSubmit: () => Promise<void>
}

const roleDefaults = {
  temperature: 0.7,
  topP: 0.9,
  vadSpeechTh: 0.5,
  vadSilenceTh: 0.3,
  vadEnergyTh: 0.01,
  vadSilenceMs: 800,
  inactiveTimeoutSeconds: 60,
  ttsPitch: 1.0,
  ttsSpeed: 1.0,
}

const TUNED_FIELDS = Object.keys(roleDefaults)

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
    roleApiMock.getRoleDefaults.mockResolvedValue({ code: 200, data: roleDefaults, message: '' })
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

// 默认值由后端给，用户没改过的参数不提交，后端按同一份默认补齐或保留原值
describe('RoleView 只提交改过的参数', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    roleApiMock.queryRoles.mockResolvedValue(rolePage)
    listApiMock.queryTemplates.mockResolvedValue({ code: 200, data: { list: [], total: 0 }, message: '' })
    listApiMock.queryConfigs.mockResolvedValue({ code: 200, data: { list: [], total: 0 }, message: '' })
    roleApiMock.getRoleDefaults.mockResolvedValue({ code: 200, data: roleDefaults, message: '' })
    roleApiMock.addRole.mockResolvedValue({ code: 200, data: { roleId: 9 }, message: '' })
    roleApiMock.updateRole.mockResolvedValue({ code: 200, data: null, message: '' })
  })

  it('新建表单展示后端给的默认值，没改过就不提交', async () => {
    const view = await mountView()
    stubFormRef(view)

    expect(view.formData.vadSilenceMs).toBe(800)
    await view.handleSubmit()

    const submitted = roleApiMock.addRole.mock.calls[0]?.[0] ?? {}
    for (const field of TUNED_FIELDS) {
      expect(submitted[field]).toBeUndefined()
    }
  })

  it('改过的参数照常提交', async () => {
    const view = await mountView()
    stubFormRef(view)
    view.formData.temperature = 0.3

    await view.handleSubmit()

    const submitted = roleApiMock.addRole.mock.calls[0]?.[0] ?? {}
    expect(submitted.temperature).toBe(0.3)
    expect(submitted.topP).toBeUndefined()
  })

  it('编辑时以角色原值为基线，只提交改动的那一项', async () => {
    const view = await mountView()
    stubFormRef(view)
    view.handleEdit({ roleId: 7, roleName: '管家', temperature: 0.2, topP: 0.5, vadSilenceMs: 1200 } as Role)
    await flushPromises()
    view.formData.topP = 0.6

    await view.handleSubmit()

    const submitted = roleApiMock.updateRole.mock.calls[0]?.[0] ?? {}
    expect(submitted.topP).toBe(0.6)
    expect(submitted.temperature).toBeUndefined()
    expect(submitted.vadSilenceMs).toBeUndefined()
  })
})
