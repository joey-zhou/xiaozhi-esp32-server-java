import { http } from './request'
import api from './api'
import type { Device, DeviceQueryParams } from '@/types/device'

/**
 * 查询设备列表
 */
export function queryDevices(params: Partial<DeviceQueryParams>) {
  return http.getPage<Device>(api.device, params)
}

/**
 * 添加设备
 */
export function addDevice(code: string) {
  return http.post(api.device, { code })
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
