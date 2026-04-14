import { http } from './request'
import api from './api'
import type { AuthRole, AuthRolePermissionConfig, AuthRoleQueryParams } from '@/types/authRole'

export function queryAuthRoles(params: AuthRoleQueryParams = {}) {
  return http.getPage<AuthRole>(api.authRole.query, params)
}

export function getAuthRolePermissionConfig(authRoleId: number) {
  return http.get<AuthRolePermissionConfig>(`${api.authRole.permissions}/${authRoleId}/permissions`)
}

export function updateAuthRolePermissions(authRoleId: number, permissionIds: number[]) {
  return http.put<AuthRolePermissionConfig>(`${api.authRole.permissions}/${authRoleId}/permissions`, permissionIds)
}
