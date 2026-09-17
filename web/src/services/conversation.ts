import { http } from './request'
import api from './api'
import type { Conversation, ConversationQueryParams } from '@/types/chat'

/**
 * 查询当前用户的网页聊天会话，按最后对话时间倒序
 */
export function queryConversations(params: ConversationQueryParams) {
  return http.getPage<Conversation>(api.conversation, params)
}

/**
 * 重命名会话
 */
export function renameConversation(sessionId: string, title: string) {
  return http.patch(`${api.conversation}/${encodeURIComponent(sessionId)}`, { title })
}

/**
 * 删除会话，连同会话的聊天记录与摘要；返回实际删除的会话数
 */
export function deleteConversations(sessionIds: string[]) {
  return http.deleteBody<number>(api.conversation, { sessionIds })
}
