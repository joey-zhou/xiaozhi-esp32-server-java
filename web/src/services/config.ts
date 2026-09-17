import { http } from './request'
import api from './api'
import type { Config, ConfigQueryParams } from '@/types/config'
import type { PlatformConfig } from '@/types/agent'

/**
 * 查询配置列表
 */
export function queryConfigs(params: Partial<ConfigQueryParams>) {
  return http.getPage<Config>(api.config.root, params)
}

/**
 * 添加配置
 * @param confirmStorageSwitch 已确认换掉当前生效的对象存储会让历史文件不可访问
 */
export function addConfig(data: Partial<Config>, confirmStorageSwitch = false) {
  return http.post(api.config.root, data, { params: { confirmStorageSwitch } })
}

/**
 * 更新配置
 * @param confirmStorageSwitch 已确认换掉当前生效的对象存储会让历史文件不可访问
 */
export function updateConfig(data: Partial<Config>, confirmStorageSwitch = false) {
  return http.put(`${api.config.root}/${data.configId}`, data, { params: { confirmStorageSwitch } })
}

/**
 * 删除配置
 *
 * @param confirmStorageSwitch 已确认删掉当前生效的对象存储配置会让历史文件不可访问
 */
export function deleteConfig(configId: number, confirmStorageSwitch = false) {
  return http.delete(`${api.config.root}/${configId}`, { params: { confirmStorageSwitch } })
}

/**
 * 测试配置（使用当前表单值，可能尚未保存）
 */
export function testConfig(data: Partial<Config>) {
  return http.post(api.config.test, data)
}

/**
 * 查询平台配置（智能体 / 音色克隆平台，返回的是 PlatformConfig 形状）
 */
export function queryPlatformConfig(configType: string, provider: string) {
  return http.getPage<PlatformConfig>(api.config.root, {
    configType,
    provider
  })
}

/**
 * 添加平台配置
 */
export function addPlatformConfig(data: Partial<PlatformConfig>) {
  return http.post(api.config.root, data)
}

/**
 * 更新平台配置
 */
export function updatePlatformConfig(data: Partial<PlatformConfig>) {
  return http.put(`${api.config.root}/${data.configId}`, data)
}
