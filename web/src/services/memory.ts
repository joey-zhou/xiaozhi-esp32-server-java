import { http } from './request'
import api from './api'
import type { ChatMemory, SummaryMemory, SummaryQueryParams } from '@/types/memory'
import type { MessageQueryParams } from '@/types/message'

/**
 * 查询设备的对话摘要，不传角色时不限角色
 */
export function querySummaryMemory(params: SummaryQueryParams) {
  return http.getPage<SummaryMemory>(api.memory.summary, params)
}

/**
 * 查询聊天记录（使用现有的message接口），角色与设备不传时不限
 */
export function queryChatMemory(params: {
  roleId?: number
  deviceId?: string
  pageNo?: number
  pageSize?: number
  startTime?: string
  endTime?: string
}) {
  const { roleId, deviceId, pageNo = 1, pageSize = 10, startTime, endTime } = params

  const queryParams: Partial<MessageQueryParams> = {
    pageNo,
    pageSize,
    deviceId,
    roleId,
    messageType: 'NORMAL',
  }

  if (startTime) queryParams.startTime = startTime
  if (endTime) queryParams.endTime = endTime

  return http.getPage<ChatMemory>(api.message.root, queryParams)
}

/**
 * 删除摘要记忆
 * id 允许字符串：后端把 Long 主键序列化成字符串保精度，转 number 会删错行
 */
export function deleteSummaryMemory(roleId: number, deviceId: string, summaryId?: number | string) {
  const url = `${api.memory.summary}/${roleId}/${deviceId}`
  const params = summaryId ? { id: summaryId } : {}
  return http.delete(url, params)
}
