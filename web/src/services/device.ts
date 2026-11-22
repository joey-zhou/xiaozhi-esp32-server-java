import { http } from './request'
import api from './api'
import type { Device, DeviceBatchUpdateResult, DeviceQueryParams } from '@/types/device'

/**
 * 查询设备列表
 */
export function queryDevices(params: Partial<DeviceQueryParams>) {
  return http.getPage<Device>(api.device, params)
}

/**
 * 添加设备。给了 roleId 就在同一个请求里绑上该智能体，不传用默认智能体
 */
export function addDevice(code: string, roleId?: number) {
  return http.post<Partial<Device>>(api.device, { code, roleId })
}

/**
 * 把选中的设备一次改到同一个智能体；设备与智能体的归属、智能体须属于设备所属用户由后端判
 */
export function batchUpdateDeviceRole(data: { deviceIds: string[]; roleId: number }) {
  return http.post<DeviceBatchUpdateResult>(`${api.device}/batchUpdate`, {
    deviceIds: data.deviceIds.join(','),
    roleId: data.roleId,
  })
}

/**
 * 更新设备信息
 */
export function updateDevice(data: Partial<Device>) {
  return http.put(`${api.device}/${data.deviceId}`, data)
}

/**
 * 删除设备
 */
export function deleteDevice(deviceId: string) {
  return http.delete(`${api.device}/${deviceId}`)
}

/**
 * 清除设备记忆
 */
export function clearDeviceMemory(deviceId: string) {
  return http.delete(api.message.root, { deviceId })
}
