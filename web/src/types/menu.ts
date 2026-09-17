import type { RouteMeta } from 'vue-router'

/**
 * 菜单项的 meta 就是路由 meta（权威定义在 types/router.d.ts），这里只额外收紧 title：
 * useMenu 在建菜单前已经滤掉了没有 title 的路由，能进菜单的一定有标题。
 */
export type MenuMeta = RouteMeta & { title: string }

export interface MenuItem {
  path: string
  name?: string
  meta: MenuMeta
  children?: MenuItem[]
  component?: unknown
}
