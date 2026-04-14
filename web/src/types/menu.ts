export interface MenuMeta {
  title: string
  icon?: string
  isAdmin?: boolean
  hideInMenu?: boolean
  breadcrumb?: Array<{ breadcrumbName: string }>
  parent?: string
  permission?: string
  permissions?: string[]
  showInUserHeader?: boolean
}

export interface MenuItem {
  path: string
  name?: string
  meta: MenuMeta
  children?: MenuItem[]
  component?: unknown
}

