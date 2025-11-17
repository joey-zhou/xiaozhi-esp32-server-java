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
  return http.post(api.device.update, {
    deviceId: data.deviceId,
    deviceName: data.deviceName,
    roleId: data.roleId,
  })
}

/**
 * 删除设备
 */
export function deleteDevice(deviceId: string) {
  return http.post(api.device.delete, { deviceId })
}

/**
 * 清除设备记忆
 */
export function clearDeviceMemory(deviceId: string) {
  return http.post(api.message.delete, { deviceId })
}
