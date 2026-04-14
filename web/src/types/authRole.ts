import type { PageQueryParams } from './api'

export interface AuthRole {
  authRoleId: number
  authRoleName: string
  roleKey: string
  description?: string
  status?: string
  createTime?: string
  updateTime?: string
}

export interface AuthRoleQueryParams extends PageQueryParams {
  authRoleName?: string
  roleKey?: string
  status?: string
}

export interface PermissionTreeNode {
  permissionId: number
  parentId?: number
  name: string
  permissionKey: string
  permissionType: 'menu' | 'button' | 'api'
  path?: string
  component?: string
  icon?: string
  sort?: number
  visible?: string
  status?: string
  children?: PermissionTreeNode[]
}

export interface AuthRolePermissionConfig extends AuthRole {
  permissionTree: PermissionTreeNode[]
  checkedPermissionIds: number[]
}
