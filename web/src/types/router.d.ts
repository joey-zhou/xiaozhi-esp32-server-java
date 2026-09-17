import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    requiresAuth?: boolean
    isAdmin?: boolean
    parent?: string
    hideInMenu?: boolean
    permission?: string // 单个权限
    permissions?: string[] // 多个权限（任一即可）
    showInUserHeader?: boolean // 是否在普通用户的横向导航菜单中显示
    showInSidebar?: boolean // 是否在管理员侧边栏显示（默认true，除非明确设为false）
  }
}
