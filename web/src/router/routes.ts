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
  SETTING_CONFIG: '/setting/config',
  ERROR_403: '/403',
  ERROR_404: '/404',
  ABOUT: '/about',
} as const
