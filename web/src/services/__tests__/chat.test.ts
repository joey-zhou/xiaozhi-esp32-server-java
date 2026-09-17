import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const httpMock = vi.hoisted(() => ({
  post: vi.fn(() => Promise.resolve({ code: 200, message: 'success', data: { sessionId: 's-1' } })),
}))

const handleAuthExpiredMock = vi.hoisted(() => vi.fn())

vi.mock('../request', () => ({
  http: httpMock,
  API_BASE_URL: '/api',
  handleAuthExpired: handleAuthExpiredMock,
}))

vi.mock('@/store/user', () => ({ useUserStore: () => ({ token: 'tk' }) }))

import { chatStream, closeChatSession, openChatSession } from '../chat'
import type { ChatToken } from '@/types/chat'

/** 一段 SSE 响应体，读一次就结束 */
function sseResponse(chunk: string, ok = true, status = 200) {
  const encoder = new TextEncoder()
  let sent = false
  return {
    ok,
    status,
    body: {
      getReader: () => ({
        read: () => {
          if (sent) {
            return Promise.resolve({ done: true, value: undefined })
          }
          sent = true
          return Promise.resolve({ done: false, value: encoder.encode(chunk) })
        },
        releaseLock: () => {},
      }),
    },
  }
}

describe('chat service', () => {
  let capturedUrl = ''
  let capturedInit: RequestInit | undefined

  beforeEach(() => {
    vi.clearAllMocks()
    capturedUrl = ''
    capturedInit = undefined
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function stubFetch(response: unknown) {
    vi.stubGlobal('fetch', (input: string, init?: RequestInit) => {
      capturedUrl = input
      capturedInit = init
      return Promise.resolve(response)
    })
  }

  it('开会话与关会话打到 /chat/open 与 /chat/close', async () => {
    await expect(openChatSession(3, 's-1')).resolves.toEqual({ sessionId: 's-1' })
    expect(httpMock.post).toHaveBeenCalledWith('/chat/open', null, {
      params: { roleId: 3, sessionId: 's-1' },
    })

    httpMock.post.mockClear()
    await closeChatSession('s-1')
    expect(httpMock.post).toHaveBeenCalledWith('/chat/close', null, {
      params: { sessionId: 's-1' },
    })
  })

  // 用户输入放 query 会进 access log 与反向代理日志，必须走请求体
  it('SSE 用 POST 发送，用户输入只出现在请求体里', async () => {
    stubFetch(sseResponse('data: {"type":"content","text":"在的"}\n\n'))

    const tokens: ChatToken[] = []
    for await (const token of chatStream('s-1', '我的手机号是 13800138000')) {
      tokens.push(token)
    }

    expect(tokens).toEqual([{ type: 'content', text: '在的' }])
    expect(capturedInit?.method).toBe('POST')
    // 前缀必须来自 request.ts 的 API_BASE_URL，不能在 chat.ts 里另拼一份
    expect(capturedUrl).toBe('/api/chat/stream')
    expect(capturedUrl).not.toContain('13800138000')
    expect(JSON.parse(String(capturedInit?.body))).toEqual({
      sessionId: 's-1',
      text: '我的手机号是 13800138000',
    })
  })

  // SSE 绕开了 axios 拦截器，401 只能在这里自己接
  it('401 触发登录过期处理并抛出不含中文的错误', async () => {
    stubFetch(sseResponse('', false, 401))

    const consume = async () => {
      for await (const token of chatStream('s-1', '你好')) {
        void token
      }
    }

    await expect(consume()).rejects.toThrow('chat stream failed: HTTP 401')

    expect(handleAuthExpiredMock).toHaveBeenCalledOnce()
  })

  // 开流前失败走的是通用异常处理器，返回 ApiResponse JSON；Accept 必须带 application/json 后端才写得出来
  it('开流前失败时把后端 message 原样抛出', async () => {
    stubFetch({
      ok: false,
      status: 400,
      json: () => Promise.resolve({ code: 400, message: '会话不存在或已删除: s-1', data: null }),
    })

    const consume = async () => {
      for await (const token of chatStream('s-1', '你好')) {
        void token
      }
    }

    await expect(consume()).rejects.toThrow('会话不存在或已删除: s-1')
    expect(new Headers(capturedInit?.headers).get('Accept')).toContain('application/json')
    expect(handleAuthExpiredMock).not.toHaveBeenCalled()
  })

  it('开流前失败且响应体不是 JSON 时退回状态码', async () => {
    stubFetch({ ok: false, status: 500, json: () => Promise.reject(new Error('not json')) })

    const consume = async () => {
      for await (const token of chatStream('s-1', '你好')) {
        void token
      }
    }

    await expect(consume()).rejects.toThrow('chat stream failed: HTTP 500')
  })
})
