import { describe, expect, it, vi } from 'vitest'
import type { RouteMeta } from 'vue-router'
import { resolveAuthNavigation, type AuthNavigationState, type AuthNavigationTarget } from '../authNavigation'

// 构造目标路由，meta 缺省为空对象（等价于不挂任何权限要求）
function target(path: string, meta: RouteMeta = {}, fullPath = path): AuthNavigationTarget {
  return { path, fullPath, meta }
}

// 构造权限状态，默认已登录、非管理员、没有任何权限（模拟权限数据尚未加载/为空的场景）
function state(overrides: Partial<AuthNavigationState> = {}): AuthNavigationState {
  return {
    hasToken: true,
    isAdmin: false,
    hasPermission: vi.fn(() => false),
    hasAnyPermission: vi.fn(() => false),
    ...overrides,
  }
}

describe('resolveAuthNavigation', () => {
  describe('未登录', () => {
    it('命中白名单（登录/注册/找回密码）直接放行', () => {
      for (const path of ['/login', '/register', '/forget']) {
        expect(resolveAuthNavigation(target(path), state({ hasToken: false }))).toEqual({ action: 'allow' })
      }
    })

    it('访问非白名单页面时跳登录页，并把完整路径（含 query）编码进 redirect', () => {
      const decision = resolveAuthNavigation(
        target('/user', {}, '/user?id=5'),
        state({ hasToken: false }),
      )

      expect(decision).toEqual({
        action: 'redirect',
        to: `/login?redirect=${encodeURIComponent('/user?id=5')}`,
        reason: 'unauthenticated',
      })
    })
  })

  describe('已登录：无权限要求', () => {
    it('meta 不挂任何权限字段时直接放行', () => {
      expect(resolveAuthNavigation(target('/dashboard'), state())).toEqual({ action: 'allow' })
    })
  })

  describe('已登录：管理员权限', () => {
    it('需要管理员但当前不是管理员时兜底到 403', () => {
      const decision = resolveAuthNavigation(target('/role', { isAdmin: true }), state({ isAdmin: false }))
      expect(decision).toEqual({ action: 'redirect', to: '/403', reason: 'forbidden' })
    })

    it('需要管理员且当前就是管理员时放行', () => {
      const decision = resolveAuthNavigation(target('/role', { isAdmin: true }), state({ isAdmin: true }))
      expect(decision).toEqual({ action: 'allow' })
    })
  })

  describe('已登录：单个权限', () => {
    it('没有对应权限时兜底到 403', () => {
      const hasPermission = vi.fn(() => false)
      const decision = resolveAuthNavigation(
        target('/user', { permission: 'system:user' }),
        state({ hasPermission }),
      )

      expect(decision).toEqual({ action: 'redirect', to: '/403', reason: 'forbidden' })
      expect(hasPermission).toHaveBeenCalledWith('system:user')
    })

    it('拥有对应权限时放行', () => {
      const decision = resolveAuthNavigation(
        target('/user', { permission: 'system:user' }),
        state({ hasPermission: vi.fn(() => true) }),
      )

      expect(decision).toEqual({ action: 'allow' })
    })

    it('管理员豁免所有单个权限检查', () => {
      // hasPermission 由 store 保证管理员恒真，这里模拟同样的约定
      const decision = resolveAuthNavigation(
        target('/user', { permission: 'system:user' }),
        state({ isAdmin: true, hasPermission: vi.fn(() => true) }),
      )

      expect(decision).toEqual({ action: 'allow' })
    })
  })

  describe('已登录：多选一权限', () => {
    it('权限列表为空数组时不做限制，直接放行', () => {
      const decision = resolveAuthNavigation(target('/x', { permissions: [] }), state())
      expect(decision).toEqual({ action: 'allow' })
    })

    it('一个都不满足时兜底到 403', () => {
      const decision = resolveAuthNavigation(
        target('/x', { permissions: ['a', 'b'] }),
        state({ hasAnyPermission: vi.fn(() => false) }),
      )

      expect(decision).toEqual({ action: 'redirect', to: '/403', reason: 'forbidden' })
    })

    it('满足其中一个即可放行', () => {
      const decision = resolveAuthNavigation(
        target('/x', { permissions: ['a', 'b'] }),
        state({ hasAnyPermission: vi.fn(() => true) }),
      )

      expect(decision).toEqual({ action: 'allow' })
    })
  })

  describe('权限数据尚未就绪（本地缓存为空/还没同步到最新值）', () => {
    it('按无权限处理，宁可多拦一次也不放宽准入（fail-closed）', () => {
      // hasPermission/hasAnyPermission 返回 false 模拟权限树还没同步到本地时的状态，
      // 此时必须兜底到 403，不能因为“数据还没到”就放行
      const decision = resolveAuthNavigation(
        target('/config/model', { permission: 'system:config' }),
        state({ hasPermission: vi.fn(() => false), hasAnyPermission: vi.fn(() => false) }),
      )

      expect(decision).toEqual({ action: 'redirect', to: '/403', reason: 'forbidden' })
    })
  })
})
