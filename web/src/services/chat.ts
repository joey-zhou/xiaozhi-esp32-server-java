import { http } from './request'
import { useUserStore } from '@/store/user'
import type { ChatToken } from '@/types/chat'

export type { ChatToken }

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

/**
 * 开启 Web 聊天会话。
 * 不传 sessionId 时创建新会话；传入已有 sessionId 时尝试续接（后端会校验归属）。
 */
export function openChatSession(roleId: number, sessionId?: string) {
  return http.post<{ sessionId: string }>('/chat/open', null, {
    params: sessionId ? { roleId, sessionId } : { roleId },
  })
}

/**
 * 关闭 Web 聊天会话
 */
export function closeChatSession(sessionId: string) {
  return http.post('/chat/close', null, {
    params: { sessionId },
  })
}

/**
 * 流式聊天（SSE），返回 EventSource 风格的流读取器。
 * 由于 SSE 需要用原生 fetch（axios 不支持流式读取），这里不走 http 封装。
 */
export async function* chatStream(
  sessionId: string,
  text: string,
  signal?: AbortSignal
): AsyncGenerator<ChatToken> {
  const userStore = useUserStore()
  const url = `${BASE_URL}/chat/stream?sessionId=${encodeURIComponent(sessionId)}&text=${encodeURIComponent(text)}`

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'text/event-stream',
      Authorization: userStore.token ? `Bearer ${userStore.token}` : '',
    },
    signal,
  })

  if (!response.ok) {
    throw new Error(`聊天请求失败: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('无法读取响应流')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 解析 SSE 数据行
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 最后一行可能不完整，留到下一次

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data) {
            try {
              yield JSON.parse(data) as ChatToken
            } catch {
              // 兼容纯文本（降级为 content）
              yield { type: 'content', text: data } as ChatToken
            }
          }
        }
      }
    }
    // 处理剩余 buffer
    if (buffer.startsWith('data:')) {
      const data = buffer.slice(5).trim()
      if (data) {
        try {
          yield JSON.parse(data) as ChatToken
        } catch {
          yield { type: 'content', text: data } as ChatToken
        }
      }
    }
  } finally {
    reader.releaseLock()
  }
}
