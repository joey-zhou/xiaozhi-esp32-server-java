import { http } from './request'
import api from './api'
import type { Config, ConfigQueryParams } from '@/types/config'
import type { PlatformConfig } from '@/types/agent'

/**
 * 查询配置列表
 */
export function queryConfigs(params: Partial<ConfigQueryParams>) {
  return http.getPage<Config>(api.config.query, params)
}

/**
 * 添加配置
 */
export function addConfig(data: Partial<Config>) {
  return http.post(api.config.add, data)
}

/**
 * 更新配置
 */
export function updateConfig(data: Partial<Config>) {
  return http.put(`${api.config.update}/${data.configId}`, data)
}

/**
 * 删除配置
 */
export function deleteConfig(configId: number) {
  return http.delete(`${api.config.delete}/${configId}`)
}

/**
 * 查询平台配置
 */
export function queryPlatformConfig(configType: string, provider: string) {
  return http.getPage<Config>(api.config.query, {
    configType,
    provider
  })
}

/**
 * 添加平台配置
 */
export function addPlatformConfig(data: Partial<PlatformConfig>) {
  return http.post(api.config.add, data)
}

/**
 * 更新平台配置
 */
export function updatePlatformConfig(data: Partial<PlatformConfig>) {
  return http.put(`${api.config.update}/${data.configId}`, data)
}
