import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'

const authRoleApiMock = vi.hoisted(() => ({
  queryAuthRoles: vi.fn(),
  getAuthRolePermissionConfig: vi.fn(),
  updateAuthRolePermissions: vi.fn(),
}))

vi.mock('@/services/authRole', () => authRoleApiMock)

import AuthRoleView from '../AuthRoleView.vue'

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface AuthRoleViewState {
  checkedPermissionIds: number[]
  authRoles: { authRoleId: number; authRoleName: string; roleKey: string }[]
  permissionConfig: { authRoleId: number } | null
  handleSavePermissions: () => Promise<void>
}

// 两级权限树：设备管理(1) 下挂列表(11)、删除(12)
const permissionTree = [
  {
    permissionId: 1,
    name: '设备管理',
    permissionKey: 'system:device',
    permissionType: 'menu',
    children: [
      { permissionId: 11, name: '列表', permissionKey: 'system:device:list', permissionType: 'api' },
      { permissionId: 12, name: '删除', permissionKey: 'system:device:delete', permissionType: 'api' },
    ],
  },
]

function permissionConfig(checkedPermissionIds: number[]) {
  return {
    code: 200,
    data: {
      authRoleId: 1,
      authRoleName: '普通用户',
      roleKey: 'user',
      permissionTree,
      checkedPermissionIds,
    },
  }
}

async function mountView() {
  const wrapper = shallowMount(AuthRoleView, {
    global: { directives: { permission: {} } },
  })
  await flushPromises()
  return wrapper.vm as unknown as AuthRoleViewState
}

describe('AuthRoleView 权限保存', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authRoleApiMock.queryAuthRoles.mockResolvedValue({
      code: 200,
      data: { list: [{ authRoleId: 1, authRoleName: '普通用户', roleKey: 'user' }] },
    })
    authRoleApiMock.getAuthRolePermissionConfig.mockResolvedValue(permissionConfig([1, 11, 12]))
    authRoleApiMock.updateAuthRolePermissions.mockResolvedValue(permissionConfig([]))
  })

  it('只勾中子权限时把半选的父菜单一并提交，避免父菜单权限被静默丢弃', async () => {
    const view = await mountView()

    // checkStrictly:false 下父节点半选，onChange 只会给到子节点 id
    view.checkedPermissionIds = [11]
    await view.handleSavePermissions()

    expect(authRoleApiMock.updateAuthRolePermissions).toHaveBeenCalledWith(1, [1, 11])
  })

  it('整棵子树都勾中时按原样提交，不会重复补父节点', async () => {
    const view = await mountView()

    view.checkedPermissionIds = [1, 11, 12]
    await view.handleSavePermissions()

    expect(authRoleApiMock.updateAuthRolePermissions).toHaveBeenCalledWith(1, [1, 11, 12])
  })
})

// 这三处走 useRequest 的 execute，失败时它只回 undefined 不抛，
// 调用方必须自己守住「不要拿空数据覆写已有状态」
describe('AuthRoleView 加载失败时不覆写已有状态', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authRoleApiMock.queryAuthRoles.mockResolvedValue({
      code: 200,
      data: { list: [{ authRoleId: 1, authRoleName: '普通用户', roleKey: 'user' }] },
    })
    authRoleApiMock.getAuthRolePermissionConfig.mockResolvedValue(permissionConfig([1, 11, 12]))
    authRoleApiMock.updateAuthRolePermissions.mockResolvedValue(permissionConfig([]))
  })

  it('角色列表请求失败时不去拉权限配置，列表留空', async () => {
    authRoleApiMock.queryAuthRoles.mockRejectedValue(new Error('boom'))

    const view = await mountView()

    expect(view.authRoles).toEqual([])
    expect(authRoleApiMock.getAuthRolePermissionConfig).not.toHaveBeenCalled()
  })

  it('权限配置业务码失败时 permissionConfig 保持为空，不写进半截数据', async () => {
    authRoleApiMock.getAuthRolePermissionConfig.mockResolvedValue({
      code: 500,
      message: '没权限',
      data: null,
    })

    const view = await mountView()

    expect(view.permissionConfig).toBeNull()
    expect(view.checkedPermissionIds).toEqual([])
  })

  it('保存失败时不把已有的勾选状态清掉', async () => {
    const view = await mountView()
    view.checkedPermissionIds = [1, 11]

    authRoleApiMock.updateAuthRolePermissions.mockResolvedValue({
      code: 500,
      message: '保存失败',
      data: null,
    })
    await view.handleSavePermissions()

    expect(view.checkedPermissionIds).toEqual([1, 11])
  })
})
