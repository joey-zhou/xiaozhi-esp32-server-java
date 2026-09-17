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
})
