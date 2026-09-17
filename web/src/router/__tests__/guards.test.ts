import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Router } from 'vue-router'
import { message } from 'ant-design-vue'

const userStoreMock = vi.hoisted(() => ({
  token: '',
  isAdmin: false,
  hasPermission: vi.fn(() => true),
  hasAnyPermission: vi.fn(() => true),
  setUserInfo: vi.fn(),
  setPermissions: vi.fn(),
  setAuthRole: vi.fn(),
}))

vi.mock('@/store/user', () => ({ useUserStore: () => userStoreMock }))
vi.mock('@/services/request', () => ({
  cancelPendingRequests: vi.fn(),
  isRequestCanceledError: vi.fn(() => false),
}))
vi.mock('@/services/user', () => ({ checkToken: vi.fn(() => Promise.resolve({ code: 200, data: null })) }))
vi.mock('@/locales', async () => {
  const { ref } = await import('vue')
  return { i18n: { global: { t: (key: string) => key, locale: ref('zh-CN') } } }
})
vi.mock('nprogress', () => ({
  default: { configure: vi.fn(), start: vi.fn(), done: vi.fn() },
}))

import { i18n } from '@/locales'
import { setupRouterGuards } from '../guards'

type BeforeEachHook = Parameters<Router['beforeEach']>[0]
type ErrorHook = Parameters<Router['onError']>[0]

function installGuards(currentRoute: ReturnType<typeof route> = route('/')) {
  const hooks: { beforeEach?: BeforeEachHook; afterEach?: () => void; onError?: ErrorHook } = {}
  const router = {
    currentRoute: { value: currentRoute },
    beforeEach: (fn: BeforeEachHook) => { hooks.beforeEach = fn },
    afterEach: (fn: () => void) => { hooks.afterEach = fn },
    onError: (fn: ErrorHook) => { hooks.onError = fn },
  }
  setupRouterGuards(router as unknown as Router)
  return hooks
}

function route(path: string, fullPath = path, meta: Record<string, unknown> = {}) {
  return { path, fullPath, meta } as never
}

describe('router guards', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    userStoreMock.token = ''
    userStoreMock.isAdmin = false
  })

  it('未登录访问深链时把完整路径（含 query）编码进 redirect', () => {
    const hooks = installGuards()
    const next = vi.fn()

    hooks.beforeEach?.call(undefined, route('/user', '/user?id=5'), route('/'), next)

    expect(next).toHaveBeenCalledWith(`/login?redirect=${encodeURIComponent('/user?id=5')}`)
    expect(decodeURIComponent(String(next.mock.calls[0]?.[0]).split('redirect=')[1] ?? '')).toBe('/user?id=5')
  })

  it('已登录的普通用户访问登录页时落到设备列表而不是仪表盘', () => {
    const hooks = installGuards()
    const next = vi.fn()
    userStoreMock.token = 'token-1'
    userStoreMock.isAdmin = false

    hooks.beforeEach?.call(undefined, route('/login'), route('/'), next)

    expect(next).toHaveBeenCalledWith({ path: '/device' })
  })

  it('已登录的管理员访问登录页时落到仪表盘', () => {
    const hooks = installGuards()
    const next = vi.fn()
    userStoreMock.token = 'token-1'
    userStoreMock.isAdmin = true

    hooks.beforeEach?.call(undefined, route('/login'), route('/'), next)

    expect(next).toHaveBeenCalledWith({ path: '/dashboard' })
  })

  it('已登录但无对应权限时兜底到 403（与登录后落地页计算共用同一份判断）', () => {
    const hooks = installGuards()
    const next = vi.fn()
    userStoreMock.token = 'token-1'
    userStoreMock.isAdmin = false
    userStoreMock.hasPermission.mockReturnValue(false)

    hooks.beforeEach?.call(undefined, route('/user', '/user', { permission: 'system:user' }), route('/'), next)

    expect(next).toHaveBeenCalledWith('/403')
  })

  it('已登录且有对应权限时正常放行', () => {
    const hooks = installGuards()
    const next = vi.fn()
    userStoreMock.token = 'token-1'
    userStoreMock.isAdmin = false
    userStoreMock.hasPermission.mockReturnValue(true)

    hooks.beforeEach?.call(undefined, route('/user', '/user', { permission: 'system:user' }), route('/'), next)

    expect(next).toHaveBeenCalledWith()
  })

  it('chunk 加载失败连续发生时不再自动刷新，改为提示用户', () => {
    // 首次失败会挂一个延时刷新，用假定时器挡住，避免 jsdom 真去导航
    vi.useFakeTimers()
    try {
      const hooks = installGuards()
      const chunkError = new Error('Failed to fetch dynamically imported module: /assets/DashboardView.js')

      hooks.onError?.(chunkError, route('/dashboard'), route('/'))
      expect(sessionStorage.getItem('chunk-reload-at')).not.toBeNull()
      expect(message.error).not.toHaveBeenCalled()

      hooks.onError?.(chunkError, route('/dashboard'), route('/'))
      expect(message.error).toHaveBeenCalledWith('error.chunkLoadFailed')
    } finally {
      vi.useRealTimers()
    }
  })

  it('切换语言后按当前路由重写标签页标题', async () => {
    const { nextTick } = await import('vue')
    installGuards(route('/dashboard', '/dashboard', { title: 'router.title.dashboard' }))

    i18n.global.locale.value = 'en-US'
    await nextTick()

    expect(document.title).toContain('router.title.dashboard')
  })

  it('导航成功后清掉 chunk 刷新熔断标记', () => {
    const hooks = installGuards()
    sessionStorage.setItem('chunk-reload-at', String(Date.now()))

    hooks.afterEach?.()

    expect(sessionStorage.getItem('chunk-reload-at')).toBeNull()
  })
})
