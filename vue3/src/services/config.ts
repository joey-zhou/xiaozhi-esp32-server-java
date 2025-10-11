import { http } from './request'
import api from './api'
import type { Config, ConfigQueryParams, ConfigListResponse } from '@/types/config'

/**
 * 查询配置列表
 */
export function queryConfigs(params: ConfigQueryParams) {
  return http.get<ConfigListResponse>(api.config.query, params)
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
  return http.post(api.config.update, data)
}

/**
 * 获取模型列表（从API）
 */
export function getModels(data: { configName: string; provider: string; apiKey?: string; ak?: string; sk?: string }) {
  return http.post<string[]>(api.config.getModels, data)
}

