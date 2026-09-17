import { defineComponent, h } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const chatMock = vi.hoisted(() => ({
  openChatSession: vi.fn(),
  closeChatSession: vi.fn(),
  chatStream: vi.fn(),
}))

const messageServiceMock = vi.hoisted(() => ({
  queryConversations: vi.fn(),
  queryMessages: vi.fn(),
}))

const antMessageMock = vi.hoisted(() => ({
  error: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
  info: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('ant-design-vue', () => ({ message: antMessageMock }))
vi.mock('@/services/chat', () => chatMock)
vi.mock('@/services/message', () => messageServiceMock)

import { useChatSession } from '../useChatSession'
import type { Conversation } from '@/types/message'

const otherConversation: Conversation = {
  sessionId: 'other',
  roleId: 2,
  roleName: '另一个角色',
  title: '历史会话',
  updateTime: '2026-01-01 00:00:00',
}

type Session = ReturnType<typeof useChatSession>

let wrappers: VueWrapper[] = []

/** 在真实组件实例里跑，保证 onBeforeUnmount 与 pagehide 注销路径可测 */
function mountSession(): Session {
  let session!: Session
  wrappers.push(
    mount(
      defineComponent({
        setup() {
          session = useChatSession()
          return () => h('div')
        },
      })
    )
  )
  return session
}

function streamOf(...tokens: { type: string; text: string }[]) {
  return async function* () {
    for (const token of tokens) yield token
  }
}

describe('useChatSession', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chatMock.openChatSession.mockResolvedValue({ sessionId: 's-1' })
    chatMock.closeChatSession.mockResolvedValue({ status: 'ok' })
  })

  afterEach(() => {
    wrappers.forEach((wrapper) => wrapper.unmount())
    wrappers = []
  })

  it('reports success when the whole stream is consumed', async () => {
    chatMock.chatStream.mockImplementation(streamOf({ type: 'content', text: '你好' }))

    const session = mountSession()
    const result = await session.sendMessage('hi', 1)

    expect(result).toEqual({ openedNow: true, success: true })
    expect(session.messages.value[1]?.content).toBe('你好')
    expect(session.sending.value).toBe(false)
  })

  it('reports failure and appends a translated notice when the stream breaks', async () => {
    chatMock.chatStream.mockImplementation(async function* () {
      yield { type: 'content', text: '半句' }
      throw new Error('boom')
    })

    const session = mountSession()
    const result = await session.sendMessage('hi', 1)

    expect(result.success).toBe(false)
    expect(session.messages.value[1]?.content).toContain('chat.replyInterrupted')
    expect(session.messages.value[1]?.content).not.toContain('回复中断')
  })

  it('treats an error token as an interruption instead of reply content', async () => {
    chatMock.chatStream.mockImplementation(
      streamOf({ type: 'content', text: '半句' }, { type: 'error', text: 'HTTP 401 invalid api key' })
    )

    const session = mountSession()
    const result = await session.sendMessage('hi', 1)

    expect(result.success).toBe(false)
    // 失败原因只经中断提示的 {error} 参数展示，不会作为正文直接拼进回复（测试里的 t 不展开参数）
    expect(session.messages.value[1]?.content).toBe('半句\n\nchat.replyInterrupted')
  })

  it('does not count a user abort as a failure', async () => {
    chatMock.chatStream.mockImplementation(async function* () {
      yield { type: 'content', text: '半句' }
      throw new DOMException('aborted', 'AbortError')
    })

    const session = mountSession()
    const result = await session.sendMessage('hi', 1)

    expect(result.success).toBe(true)
    expect(session.messages.value[1]?.content).toBe('半句')
  })

  it('surfaces the open failure through an i18n key instead of hardcoded Chinese', async () => {
    chatMock.openChatSession.mockRejectedValue(new Error('nope'))

    const session = mountSession()
    const result = await session.sendMessage('hi', 1)

    expect(result).toEqual({ openedNow: false, success: false })
    expect(antMessageMock.error).toHaveBeenCalledWith('chat.openSessionFailed')
    expect(session.connecting.value).toBe(false)
  })

  it('closes the server session on pagehide so a closed tab does not leak it', async () => {
    chatMock.chatStream.mockImplementation(streamOf({ type: 'content', text: 'ok' }))

    const session = mountSession()
    await session.sendMessage('hi', 1)
    expect(session.activeSessionId.value).toBe('s-1')

    window.dispatchEvent(new Event('pagehide'))

    expect(chatMock.closeChatSession).toHaveBeenCalledWith('s-1')
    expect(session.activeSessionId.value).toBe('')
  })

  it('stops listening to pagehide once the view is unmounted', async () => {
    chatMock.chatStream.mockImplementation(streamOf({ type: 'content', text: 'ok' }))

    const session = mountSession()
    await session.sendMessage('hi', 1)

    wrappers.forEach((wrapper) => wrapper.unmount())
    wrappers = []
    chatMock.closeChatSession.mockClear()

    window.dispatchEvent(new Event('pagehide'))

    expect(chatMock.closeChatSession).not.toHaveBeenCalled()
    expect(session.activeSessionId.value).toBe('')
  })

  it('refuses to switch conversations while a reply is streaming', async () => {
    let release: (() => void) | undefined
    chatMock.chatStream.mockImplementation(async function* () {
      await new Promise<void>((resolve) => {
        release = resolve
      })
      yield { type: 'content', text: 'done' }
    })

    const session = mountSession()
    const pending = session.sendMessage('hi', 1)
    await vi.waitUntil(() => session.sending.value)

    const switched = await session.selectConversation(otherConversation)

    expect(switched).toBe(false)
    expect(antMessageMock.warning).toHaveBeenCalledWith('chat.chatInProgress')

    release?.()
    await pending
  })

  describe('按帧合并 token', () => {
    /** 接管 rAF：只记录回调，不自动触发，测试里手动决定何时“进入下一帧” */
    function stubRaf() {
      const callbacks: FrameRequestCallback[] = []
      const rafSpy = vi
        .spyOn(globalThis, 'requestAnimationFrame')
        .mockImplementation((cb) => {
          callbacks.push(cb)
          return callbacks.length
        })
      const cafSpy = vi.spyOn(globalThis, 'cancelAnimationFrame').mockImplementation(() => {})
      return {
        callbacks,
        /** 触发最早排队的那一帧 */
        runNextFrame() {
          const cb = callbacks.shift()
          cb?.(0)
        },
        restore() {
          rafSpy.mockRestore()
          cafSpy.mockRestore()
        },
      }
    }

    it('同一帧内到达的多个 token 只落一次盘，刷新前 assistantMsg 保持不变', async () => {
      const raf = stubRaf()
      let release: (() => void) | undefined
      chatMock.chatStream.mockImplementation(async function* () {
        yield { type: 'content', text: 'A' }
        yield { type: 'content', text: 'B' }
        await new Promise<void>((resolve) => {
          release = resolve
        })
        yield { type: 'content', text: 'C' }
      })

      const session = mountSession()
      const pending = session.sendMessage('hi', 1)
      await vi.waitUntil(() => raf.callbacks.length > 0)

      // 两个 token 都到了，但同一帧只调度了一次 rAF，且还没刷进 assistantMsg
      expect(raf.callbacks).toHaveLength(1)
      expect(session.messages.value[1]?.content).toBe('')

      raf.runNextFrame()
      expect(session.messages.value[1]?.content).toBe('AB')

      release?.()
      await pending
      // 结束时剩余缓冲（C）已经被同步刷净，不需要再等一帧
      expect(session.messages.value[1]?.content).toBe('ABC')

      raf.restore()
    })

    it('中止时把已到达但未刷新的缓冲同步刷净，再追加中断提示', async () => {
      const raf = stubRaf()
      let throwError: (() => void) | undefined
      chatMock.chatStream.mockImplementation(async function* () {
        yield { type: 'content', text: '半句' }
        await new Promise<void>((_, reject) => {
          throwError = () => reject(new Error('网络断了'))
        })
        yield { type: 'content', text: '不会到达' }
      })

      const session = mountSession()
      const pending = session.sendMessage('hi', 1)
      await vi.waitUntil(() => raf.callbacks.length > 0)

      // 这一帧还没刷，assistantMsg 里还看不到已到达的 token
      expect(session.messages.value[1]?.content).toBe('')

      throwError?.()
      const result = await pending

      expect(result.success).toBe(false)
      const content = session.messages.value[1]?.content ?? ''
      // 缓冲先被刷净，中断提示追加在已刷入内容之后
      expect(content.startsWith('半句')).toBe(true)
      expect(content).toContain('chat.replyInterrupted')

      raf.restore()
    })

    it('思考耗时按 token 实际到达时刻计算，不受 rAF 延迟刷新影响', async () => {
      const raf = stubRaf()
      let now = 1_000
      const nowSpy = vi.spyOn(Date, 'now').mockImplementation(() => now)

      chatMock.chatStream.mockImplementation(async function* () {
        yield { type: 'thinking', text: '思考中' } // 到达时刻 1000
        now = 1_300
        yield { type: 'content', text: '正文' } // 到达时刻 1300 -> 思考耗时应为 300ms
      })

      const session = mountSession()
      const pending = session.sendMessage('hi', 1)
      await vi.waitUntil(() => raf.callbacks.length > 0)

      // 模拟这一帧被推迟很久才真正刷新，flush 时刻不应影响耗时计算
      now = 5_000
      raf.runNextFrame()

      expect(session.messages.value[1]?.thinkingDurationMs).toBe(300)

      await pending
      nowSpy.mockRestore()
      raf.restore()
    })
  })
})
