import { http } from './request'
import api from './api'
import type { Message, MessageQueryParams } from '@/types/message'

export type { Message, MessageQueryParams }

/**
 * 查询消息列表
 */
export function queryMessages(params: MessageQueryParams) {
  return http.getPage<Message>(api.message.root, params)
}

/**
 * 删除消息
 */
export function deleteMessage(messageId: number) {
  return http.delete(`${api.message.root}/${messageId}`)
}
