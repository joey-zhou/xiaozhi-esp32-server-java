import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { message } from 'ant-design-vue'

const { queryAuthRoles, queryUsers } = vi.hoisted(() => ({
  queryAuthRoles: vi.fn(),
  queryUsers: vi.fn(),
}))

vi.mock('@/services/authRole', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/authRole')>()),
  queryAuthRoles,
}))
vi.mock('@/services/user', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/user')>()),
  queryUsers,
}))

import UserView from '../UserView.vue'
import type { AuthRole } from '@/types/authRole'

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface UserViewState {
  authRoleOptions: AuthRole[]
  loadAuthRoleOptions: () => Promise<void>
}

async function mountView() {
  const wrapper = shallowMount(UserView, {
    global: { directives: { permission: {} } },
  })
  await flushPromises()
  return wrapper.vm as unknown as UserViewState
}

describe('UserView 权限角色下拉', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    queryUsers.mockResolvedValue({ code: 200, data: { list: [], total: 0 }, message: '' })
  })

  it('拉到权限角色后填充下拉选项', async () => {
    queryAuthRoles.mockResolvedValue({
      code: 200,
      data: { list: [{ authRoleId: 1, authRoleName: '管理员' }], total: 1 },
      message: '',
    })

    const view = await mountView()

    expect(queryAuthRoles).toHaveBeenCalledWith({ pageNo: 1, pageSize: 100 })
    expect(view.authRoleOptions).toEqual([{ authRoleId: 1, authRoleName: '管理员' }])
  })

  it('业务码失败时保留空选项且不弹提示', async () => {
    queryAuthRoles.mockResolvedValue({ code: 500, data: null, message: '权限角色查询失败' })

    const view = await mountView()

    expect(view.authRoleOptions).toEqual([])
    expect(message.error).not.toHaveBeenCalled()
  })

  it('请求抛错时不外抛、选项保持原样且不额外弹提示', async () => {
    queryAuthRoles.mockResolvedValue({ code: 200, data: { list: [], total: 0 }, message: '' })
    const view = await mountView()

    // setup 里是不带 await 的浮动调用，往上抛会变成 unhandled rejection；
    // 传输层错误已由 axios 拦截器统一提示，这里只吞掉不重复弹
    queryAuthRoles.mockRejectedValue(new Error('network down'))
    await expect(view.loadAuthRoleOptions()).resolves.toBeUndefined()

    expect(view.authRoleOptions).toEqual([])
    expect(message.error).not.toHaveBeenCalled()
  })
})
