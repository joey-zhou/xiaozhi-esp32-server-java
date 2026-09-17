/**
 * 路由路径常量
 * 集中管理所有路由路径，避免硬编码字符串分散在各处
 */
export const ROUTES = {
  LOGIN: '/login',
  REGISTER: '/register',
  FORGET: '/forget',
  DASHBOARD: '/dashboard',
  DEVICE: '/device',
  ROLE: '/role',
  TEMPLATE: '/template',
  MEMORY_CHAT: '/memory/chat',
  MEMORY_SUMMARY: '/memory/summary',
  AUTH_ROLE: '/auth-role',
  SETTING_ACCOUNT: '/setting/account',
  ERROR_403: '/403',
  ERROR_404: '/404',
} as const

/**
 * 登录后的默认落地路由
 * 管理员进仪表盘，普通用户进智能体列表
 */
export function defaultRouteFor(isAdmin: boolean): string {
  return isAdmin ? ROUTES.DASHBOARD : ROUTES.DEVICE
}
