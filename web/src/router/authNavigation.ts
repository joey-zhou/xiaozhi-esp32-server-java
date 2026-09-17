import type { RouteMeta } from 'vue-router'
import { ROUTES } from './routes'

// 免登录白名单：不持有 token 也能直接访问的页面
const PUBLIC_ROUTE_PATHS: readonly string[] = [ROUTES.LOGIN, ROUTES.REGISTER, ROUTES.FORGET]

// 目标路由信息：字段与 vue-router 的 `to` 以及 `router.resolve()` 的返回值一致，两处调用方都能直接传
export interface AuthNavigationTarget {
  path: string
  fullPath: string
  meta: RouteMeta
}

// 当前登录/权限状态，来自 user store
export interface AuthNavigationState {
  hasToken: boolean
  isAdmin: boolean
  hasPermission: (permissionKey: string) => boolean
  hasAnyPermission: (permissionKeys: string[]) => boolean
}

export type AuthNavigationDecision =
  | { action: 'allow' }
  | { action: 'redirect'; to: string; reason: 'unauthenticated' | 'forbidden' }

/**
 * 权限导航的唯一判断入口：给定目标路径 + 当前登录/权限状态，返回应该放行还是重定向到哪里。
 *
 * 这套判断此前分别在“登录后落地页计算”（composables/useAuth.ts）和“全局路由守卫”
 * （router/guards.ts）里各写一份，两处对同一目标路径可能给出不一致的放行结果——只改一处会
 * 导致登录跳转和路由守卫“打架”。现在两处都改成调用这一份实现。
 *
 * 历史实现在两点上有分歧，这里统一取更严格的一种（宁可多拦一次也不放宽准入）：
 * 1. 无权限时退到哪：useAuth 原来退到登录后的默认落地页（/dashboard 或 /agents），
 *    路由守卫退到 403 页。默认落地页本身也挂着权限（如 /agents 需要 system:agents），
 *    一旦管理员后台把这个权限从角色上收回，退到默认页会在下一跳被守卫二次拦截，
 *    等于又绕了一圈；403 页 requiresAuth 恒为 false、不挂任何权限，是唯一保证可达的兜底，
 *    因此统一退到 403。
 * 2. 权限字段是否只在 requiresAuth 为 true 时才校验：路由守卫原来只在 requiresAuth 为 true
 *    时才检查 isAdmin/permission/permissions，useAuth 原来无条件检查。无条件检查只会多拦、
 *    不会少拦，是二者中更严格的一种，这里统一采用；当前路由表里凡是挂了权限字段的路由都同时
 *    设了 requiresAuth: true，所以这个改动对现有路由表没有实际影响，只是不再依赖这个隐含约定。
 *
 * 权限数据本身不做“是否已加载完成”的等待：hasPermission/hasAnyPermission 读的是 user store
 * 里当前的同步值（本地缓存或刚登录写入的最新值），数据缺失时一律判定为没有权限（fail-closed），
 * 不会因为数据“还没到”而放宽准入；真正的最新权限由路由守卫在放行后异步拉取校正。
 */
export function resolveAuthNavigation(
  target: AuthNavigationTarget,
  state: AuthNavigationState,
): AuthNavigationDecision {
  if (!state.hasToken) {
    if (PUBLIC_ROUTE_PATHS.includes(target.path)) {
      return { action: 'allow' }
    }
    return {
      action: 'redirect',
      to: `${ROUTES.LOGIN}?redirect=${encodeURIComponent(target.fullPath)}`,
      reason: 'unauthenticated',
    }
  }

  if (target.meta.isAdmin && !state.isAdmin) {
    return { action: 'redirect', to: ROUTES.ERROR_403, reason: 'forbidden' }
  }
  if (target.meta.permission && !state.hasPermission(target.meta.permission)) {
    return { action: 'redirect', to: ROUTES.ERROR_403, reason: 'forbidden' }
  }
  if (target.meta.permissions?.length && !state.hasAnyPermission(target.meta.permissions)) {
    return { action: 'redirect', to: ROUTES.ERROR_403, reason: 'forbidden' }
  }

  return { action: 'allow' }
}
