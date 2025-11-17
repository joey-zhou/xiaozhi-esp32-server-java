import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Device } from '@/types/device'

/**
 * 设备状态 Store
 * 管理设备列表、在线状态等
 */
export const useDeviceStore = defineStore('device', () => {
  // ========== 设备列表 ==========
  const devices = ref<Device[]>([])
  
  // 在线设备列表
  const onlineDevices = computed(() => {
    return devices.value.filter(device => device.state == '1')
  })

  // 离线设备列表
  const offlineDevices = computed(() => {
    return devices.value.filter(device => device.state == '0')
  })

  // 设备总数
  const totalDevices = computed(() => devices.value.length)

  // 在线设备数
  const onlineCount = computed(() => onlineDevices.value.length)

  // ========== 当前选中设备 ==========
  const currentDevice = ref<Device | null>(null)

  // ========== 操作方法 ==========
  
  /**
   * 设置设备列表
   */
  const setDevices = (list: Device[]) => {
    devices.value = list
  }

  /**
   * 添加设备
   */
  const addDevice = (device: Device) => {
    devices.value.push(device)
  }

  /**
   * 更新设备
   */
  const updateDevice = (deviceId: string, updates: Partial<Device>) => {
    const index = devices.value.findIndex(d => d.deviceId === deviceId)
    if (index !== -1) {
      const current = devices.value[index]
      if (current) {
        devices.value[index] = {
          ...current,
          ...updates,
          deviceId: current.deviceId,
          state: updates.state !== undefined ? updates.state : current.state,
        }
      }
    }
  }

  /**
   * 删除设备
   */
  const removeDevice = (deviceId: string) => {
    const index = devices.value.findIndex(d => d.deviceId === deviceId)
    if (index !== -1) {
      devices.value.splice(index, 1)
    }
  }

  /**
   * 更新设备在线状态
   */
  const updateDeviceStatus = (deviceId: string, online: boolean) => {
    updateDevice(deviceId, { state: online ? '1' : '0' })
  }

  /**
   * 设置当前选中设备
   */
  const setCurrentDevice = (device: Device | null) => {
    currentDevice.value = device
  }

  /**
   * 根据 ID 获取设备
   */
  const getDeviceById = (deviceId: string): Device | undefined => {
    return devices.value.find(d => d.deviceId === deviceId)
  }

  /**
   * 清空设备列表
   */
  const clearDevices = () => {
    devices.value = []
    currentDevice.value = null
  }

  return {
    // 状态
    devices,
    onlineDevices,
    offlineDevices,
    totalDevices,
    onlineCount,
    currentDevice,
    
    // 方法
    setDevices,
    addDevice,
    updateDevice,
    removeDevice,
    updateDeviceStatus,
    setCurrentDevice,
    getDeviceById,
    clearDevices,
  }
})

