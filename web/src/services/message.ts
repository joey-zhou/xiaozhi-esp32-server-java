import { http } from './request'
import api from './api'
import type { Message, MessageQueryParams } from '@/types/message'

/**
 * 查询消息列表
 */
export function queryMessages(params: Partial<MessageQueryParams>) {
  return http.getPage<Message>(api.message.query, params)
}

/**
 * 删除消息
 */
export function deleteMessage(messageId: number | string) {
  return http.delete(`${api.message.delete}/${messageId}`)
}

/**
 * 更新消息
 */
export function updateMessage(data: Partial<Message>) {
  return http.putJSON(`${api.message.update}/${data.messageId}`, data)
}

