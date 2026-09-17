import request, { API_BASE_URL, handleAuthExpired } from './request'
import api from './api'
import { useUserStore } from '@/store/user'
import type { ChatToken } from '@/types/chat'

export type { ChatToken }

/** 流式响应两次数据之间的最长间隔，超时中断整条流，避免后端挂起时输入框永久禁用 */
const STREAM_IDLE_TIMEOUT_MS = 60000

/** /chat/open 与 /chat/close 返回裸对象，不走 ApiResponse 信封 */
export interface ChatSessionOpened {
  sessionId: string
}

export interface ChatSessionClosed {
  status: string
}

/**
 * 开启 Web 聊天会话。
 * 不传 sessionId 时创建新会话；传入已有 sessionId 时尝试续接（后端会校验归属）。
 */
export function openChatSession(roleId: number, sessionId?: string): Promise<ChatSessionOpened> {
  return request.post(api.chat.open, null, {
    params: sessionId ? { roleId, sessionId } : { roleId },
  })
}

/**
 * 关闭 Web 聊天会话
 */
export function closeChatSession(sessionId: string): Promise<ChatSessionClosed> {
  return request.post(api.chat.close, null, {
    params: { sessionId },
  })
}

/**
 * 关标签页时关闭会话。
 * pagehide 阶段浏览器会掐掉普通 XHR，必须走 keepalive；sendBeacon 带不了 Authorization 头会 401。
 */
export function closeChatSessionOnUnload(sessionId: string): void {
  const userStore = useUserStore()
  const headers: Record<string, string> = {}
  if (userStore.token) {
    headers.Authorization = `Bearer ${userStore.token}`
  }
  const url = `${API_BASE_URL}${api.chat.close}?sessionId=${encodeURIComponent(sessionId)}`
  void fetch(url, {
    method: 'POST',
    keepalive: true,
    credentials: 'include',
    headers,
  }).catch(() => {})
}

/**
 * 流式聊天（SSE），返回 EventSource 风格的流读取器。
 * 用 POST + 请求体传用户输入：放 query 会被 access log 与反向代理日志留存。
 * SSE 需要原生 fetch（axios 不支持流式读取），因此不走 http 封装。
 */
export async function* chatStream(
  sessionId: string,
  text: string,
  signal?: AbortSignal
): AsyncGenerator<ChatToken> {
  const userStore = useUserStore()

  // 外部中断与空闲超时合并到同一个控制器；空闲计时每收到一段数据就重置
  const controller = new AbortController()
  const forwardAbort = () => controller.abort(signal?.reason)
  if (signal?.aborted) {
    controller.abort(signal.reason)
  } else {
    signal?.addEventListener('abort', forwardAbort, { once: true })
  }
  let idleTimer: ReturnType<typeof setTimeout> | undefined
  const armIdleTimer = () => {
    if (idleTimer) clearTimeout(idleTimer)
    idleTimer = setTimeout(
      () => controller.abort(new Error('chat stream idle timeout')),
      STREAM_IDLE_TIMEOUT_MS
    )
  }

  try {
    armIdleTimer()
    const response = await fetch(`${API_BASE_URL}${api.chat.stream}`, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        Authorization: userStore.token ? `Bearer ${userStore.token}` : '',
      },
      credentials: 'include',
      body: JSON.stringify({ sessionId, text }),
      signal: controller.signal,
    })

    if (!response.ok) {
      if (response.status === 401) {
        handleAuthExpired()
      }
      throw new Error(`chat stream failed: HTTP ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('chat stream failed: empty body')
    }

    const decoder = new TextDecoder()
    let buffer = ''

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        armIdleTimer()

        buffer += decoder.decode(value, { stream: true })

        // 解析 SSE 数据行
        const lines = buffer.split('\n')
        buffer = lines.pop() || '' // 最后一行可能不完整，留到下一次

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const token = parseTokenLine(line.slice(5).trim())
            if (token) yield token
          }
        }
      }
      // 处理剩余 buffer
      if (buffer.startsWith('data:')) {
        const token = parseTokenLine(buffer.slice(5).trim())
        if (token) yield token
      }
    } finally {
      reader.releaseLock()
    }
  } finally {
    if (idleTimer) clearTimeout(idleTimer)
    signal?.removeEventListener('abort', forwardAbort)
    // 调用方提前 break 出 for await 时，响应体还开着，中断掉才会释放连接
    controller.abort()
  }
}

/** SSE data 行解析；非 JSON 时降级为 content 文本 */
function parseTokenLine(data: string): ChatToken | null {
  if (!data) return null
  try {
    return JSON.parse(data) as ChatToken
  } catch {
    return { type: 'content', text: data }
  }
}
