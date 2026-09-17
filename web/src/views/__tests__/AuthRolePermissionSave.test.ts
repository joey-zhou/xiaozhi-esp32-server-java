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
