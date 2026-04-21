import { http } from './request'
import api from './api'
import type { Device, DeviceQueryParams } from '@/types/device'

/**
 * 查询设备列表
 */
export function queryDevices(params: Partial<DeviceQueryParams>) {
  return http.getPage<Device>(api.device.query, params)
}

/**
 * 添加设备
 */
export function addDevice(code: string) {
  return http.post(api.device.add, { code })
}

/**
 * 更新设备信息
 */
export function updateDevice(data: Partial<Device>) {
  return http.put(`${api.device.update}/${data.deviceId}`, data)
}

/**
 * 删除设备
 */
export function deleteDevice(deviceId: string) {
  return http.delete(`${api.device.delete}/${deviceId}`)
}

/**
 * 清除设备记忆
 */
export function clearDeviceMemory(deviceId: string) {
  return http.delete(api.message.delete, { deviceId })
}
