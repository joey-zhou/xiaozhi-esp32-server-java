import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ROUTES } from '@/router/routes'

const userStoreMock = vi.hoisted(() => ({
  userInfo: null as { userId: number } | null,
  isAdmin: true,
}))

const routerMock = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('@/store/user', () => ({ useUserStore: () => userStoreMock }))
// 布局树里的 useMenu 在模块顶层就引了 useRoute，两个都要给
vi.mock('vue-router', () => ({
  useRouter: () => routerMock,
  useRoute: () => ({ path: '/dashboard', name: 'dashboard' }),
}))

import MainLayout from '../MainLayout.vue'
import FloatingChat from '@/components/FloatingChat.vue'

// router-view 的作用域插槽会被当成元素子节点直接调用，必须给一个真实组件占位
const mountOptions = {
  global: {
    components: {
      RouterView: { template: '<div class="router-view-stub" />' },
    },
  },
}

describe('MainLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userStoreMock.userInfo = { userId: 7 }
    userStoreMock.isAdmin = true
  })

  it('挂载浮动聊天入口', () => {
    const wrapper = shallowMount(MainLayout, mountOptions)

    expect(wrapper.findComponent(FloatingChat).exists()).toBe(true)
  })

  it('内容区偏移量按侧边栏宽度', () => {
    const wrapper = shallowMount(MainLayout, mountOptions)

    expect(
      (wrapper.find('.main-layout').element as HTMLElement).style.getPropertyValue('--app-content-offset'),
    ).toBe('200px')
  })

  it('无登录用户信息时跳登录页', () => {
    userStoreMock.userInfo = null

    shallowMount(MainLayout, mountOptions)

    expect(routerMock.push).toHaveBeenCalledWith(ROUTES.LOGIN)
  })
})
