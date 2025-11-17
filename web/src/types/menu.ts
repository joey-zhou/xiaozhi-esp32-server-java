export interface MenuMeta {
  title: string
  icon?: string
  isAdmin?: boolean
  hideInMenu?: boolean
  breadcrumb?: Array<{ breadcrumbName: string }>
  parent?: string
}

export interface MenuItem {
  path: string
  name?: string
  meta: MenuMeta
  children?: MenuItem[]
  component?: unknown
}


