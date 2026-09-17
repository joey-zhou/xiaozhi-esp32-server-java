/**
 * Web 聊天会话 Composable
 * 统一管理会话状态、消息流、历史记录
 */

import { ref, onBeforeUnmount } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { openChatSession, closeChatSession, chatStream } from '@/services/chat'
import { queryMessages } from '@/services/message'
import {
  queryConversations,
  renameConversation as renameConversationApi,
  deleteConversations as deleteConversationsApi,
} from '@/services/conversation'
import type { ChatMessage, Conversation } from '@/types/chat'
import type { Message } from '@/types/message'

/** 把任意异常转成可展示的文本 */
function errorText(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

export function useChatSession() {
  const { t } = useI18n()

  // 会话状态
  const sessionId = ref<string>('')
  const activeSessionId = ref<string>('') // 通过 openChatSession 打开的活跃会话
  const connecting = ref(false)

  // 历史会话列表
  const conversations = ref<Conversation[]>([])
  const loadingConversations = ref(false)

  // 聊天消息
  const messages = ref<ChatMessage[]>([])
  const sending = ref(false)
  const messageIdCounter = ref(0)

  // 思考区域展开/收起状态
  const thinkingExpanded = ref<Record<number, boolean>>({})

  // 当前流式请求的 AbortController
  let currentAbort: AbortController | null = null
  // 当前流式请求挂起的按帧合并回调，外部中止时要一并取消，避免中止后还有 rAF 补写数据
  let cancelPendingFlush: (() => void) | null = null

  function toggleThinking(msgId: number) {
    thinkingExpanded.value[msgId] = !thinkingExpanded.value[msgId]
  }

  async function loadConversations() {
    loadingConversations.value = true
    try {
      const res = await queryConversations({ pageNo: 1, pageSize: 50 })
      if (res.code !== 200) {
        throw new Error(res.message)
      }
      conversations.value = res.data.list
    } catch (e: unknown) {
      antMessage.error(t('chat.loadConversationsFailed', { error: errorText(e) }))
    } finally {
      loadingConversations.value = false
    }
  }

  /**
   * 选择一个历史会话，加载其消息记录
   * @returns 是否成功切换（sending 中或相同会话返回 false）
   */
  async function selectConversation(
    conv: Conversation,
    onAfterLoad?: () => void
  ): Promise<boolean> {
    if (sending.value) {
      antMessage.warning(t('chat.chatInProgress'))
      return false
    }

    if (sessionId.value === conv.sessionId) return false

    // 关闭当前可能活跃的会话
    abortCurrentStream()
    await closeActiveSessionQuietly()

    sessionId.value = conv.sessionId
    messages.value = []

    // 加载该会话的历史消息
    try {
      const res = await queryMessages({
        pageNo: 1,
        pageSize: 100,
        sessionId: conv.sessionId,
      })

      // 接口返回是倒序的(ORDER BY createTime DESC)，我们需要正序显示
      const historyMsgs: ChatMessage[] = res.data.list.reverse().map((m: Message) => ({
        id: ++messageIdCounter.value,
        role: m.sender === 'user' ? 'user' : 'assistant',
        content: m.message,
        timestamp: m.createTime ? new Date(m.createTime) : new Date(),
      }))

      messages.value = historyMsgs
      onAfterLoad?.()
      return true
    } catch (e: unknown) {
      antMessage.error(t('chat.loadMessagesFailed', { error: errorText(e) }))
      return false
    }
  }

  /**
   * 重命名会话，服务端接受后直接改列表里的这一项
   */
  async function renameConversation(conv: Conversation, title: string): Promise<boolean> {
    try {
      const res = await renameConversationApi(conv.sessionId, title)
      if (res.code !== 200) {
        antMessage.error(res.message || t('common.updateFailed'))
        return false
      }
      conv.title = title
      return true
    } catch (e: unknown) {
      antMessage.error(t('chat.renameFailed', { error: errorText(e) }))
      return false
    }
  }

  /**
   * 删除会话。删掉的正好是当前会话时清空对话区：服务端已经把它移除，不用再调 close
   */
  async function removeConversations(sessionIds: string[]): Promise<boolean> {
    if (sending.value && sessionIds.includes(sessionId.value)) {
      antMessage.warning(t('chat.chatInProgress'))
      return false
    }
    try {
      const res = await deleteConversationsApi(sessionIds)
      if (res.code !== 200) {
        antMessage.error(res.message || t('common.deleteFailed'))
        return false
      }
    } catch (e: unknown) {
      antMessage.error(t('chat.deleteConversationsFailed', { error: errorText(e) }))
      return false
    }

    const removed = new Set(sessionIds)
    conversations.value = conversations.value.filter((conv) => !removed.has(conv.sessionId))
    if (removed.has(sessionId.value)) {
      abortCurrentStream()
      activeSessionId.value = ''
      sessionId.value = ''
      messages.value = []
    }
    return true
  }

  async function startNewChat() {
    abortCurrentStream()
    await closeActiveSessionQuietly()
    sessionId.value = ''
    messages.value = []
    sending.value = false
  }

  function abortCurrentStream() {
    if (currentAbort) {
      currentAbort.abort()
      currentAbort = null
    }
    // 立即取消挂起的 rAF，不等 generator 抛出 AbortError 后再清，避免中止和帧回调之间有空隙
    cancelPendingFlush?.()
    cancelPendingFlush = null
  }

  function stopGeneration() {
    abortCurrentStream()
  }

  async function closeActiveSessionQuietly() {
    if (activeSessionId.value) {
      try {
        await closeChatSession(activeSessionId.value)
      } catch {
        // 忽略关闭错误
      }
      activeSessionId.value = ''
    }
  }

  /**
   * 发送消息并处理流式响应
   * @param text 用户输入文本
   * @param roleId 当前选中的角色
   * @param onScroll 每收到新内容时的回调（通常用于滚动到底部）
   * @returns 本轮是否新开/续接了会话（若是，调用者可刷新历史列表）
   */
  async function sendMessage(
    text: string,
    roleId: number,
    onScroll?: () => void
  ): Promise<{ openedNow: boolean; success: boolean }> {
    if (!text || sending.value) return { openedNow: false, success: false }

    // 若无活跃会话，则打开一次：sessionId 已有值（浏览历史后续聊）→ 传给后端续接；否则创建新会话
    let openedNow = false
    if (!activeSessionId.value) {
      connecting.value = true
      try {
        const resp = await openChatSession(roleId, sessionId.value || undefined)
        sessionId.value = resp.sessionId
        activeSessionId.value = resp.sessionId
        openedNow = true
      } catch (e: unknown) {
        antMessage.error(t('chat.openSessionFailed', { error: errorText(e) }))
        connecting.value = false
        return { openedNow: false, success: false }
      }
      connecting.value = false
    }

    // 添加用户消息
    messages.value.push({
      id: ++messageIdCounter.value,
      role: 'user',
      content: text,
      timestamp: new Date(),
    })
    onScroll?.()

    // 添加 AI 占位消息
    messages.value.push({
      id: ++messageIdCounter.value,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      streaming: true,
    })
    // 从响应式数组中获取代理对象，确保后续修改能触发视图更新
    const assistantMsg = messages.value[messages.value.length - 1]!
    onScroll?.()

    sending.value = true
    currentAbort = new AbortController()
    let streamFailed = false
    let lastError: unknown

    // 按帧合并：token 到达时只写本地缓冲，rAF 每帧最多把缓冲刷进 assistantMsg 一次，
    // 避免每个 token 都触发一次整页 render；thinking 的到达时刻/完成时刻单独记录，
    // 不受刷新延迟影响，保证思考耗时统计准确
    let thinkingBuffer = ''
    let contentBuffer = ''
    let thinkingStartedAt: number | undefined
    let thinkingDone = false
    let thinkingDurationMs: number | undefined
    let rafHandle = 0

    function markThinkingDone() {
      if (thinkingStartedAt !== undefined && !thinkingDone) {
        thinkingDone = true
        thinkingDurationMs = Date.now() - thinkingStartedAt
      }
    }

    // 先落 thinking 再落 content，与到达顺序保持一致
    function flush() {
      if (thinkingStartedAt !== undefined && assistantMsg.thinkingStartedAt === undefined) {
        assistantMsg.thinkingStartedAt = thinkingStartedAt
      }
      if (thinkingBuffer) {
        assistantMsg.thinking = (assistantMsg.thinking || '') + thinkingBuffer
        thinkingBuffer = ''
      }
      if (thinkingDone && !assistantMsg.thinkingDone) {
        assistantMsg.thinkingDone = true
        assistantMsg.thinkingDurationMs = thinkingDurationMs
      }
      if (contentBuffer) {
        assistantMsg.content += contentBuffer
        contentBuffer = ''
      }
      onScroll?.()
    }

    function cancelScheduledFlush() {
      if (rafHandle) {
        cancelAnimationFrame(rafHandle)
        rafHandle = 0
      }
    }

    function scheduleFlush() {
      if (rafHandle) return
      rafHandle = requestAnimationFrame(() => {
        rafHandle = 0
        flush()
      })
    }

    cancelPendingFlush = cancelScheduledFlush

    try {
      for await (const token of chatStream(sessionId.value, text, currentAbort.signal)) {
        if (token.type === 'error') {
          // 后端把模型调用失败的原因推过来后就结束流，按中断处理并展示原因
          throw new Error(token.text)
        }
        if (token.type === 'thinking') {
          if (thinkingStartedAt === undefined) thinkingStartedAt = Date.now()
          thinkingBuffer += token.text
        } else {
          markThinkingDone()
          contentBuffer += token.text
        }
        scheduleFlush()
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        // 用户主动取消
      } else {
        streamFailed = true
        lastError = e
      }
    } finally {
      // 无论正常结束/异常/中止，读写 assistantMsg 前都要先把剩余缓冲同步刷掉，并取消挂起的 rAF
      markThinkingDone()
      cancelScheduledFlush()
      // 切会话时旧流的 finally 可能晚于新流开始才跑，只清自己登记的那份
      if (cancelPendingFlush === cancelScheduledFlush) {
        cancelPendingFlush = null
      }
      flush()
      if (streamFailed) {
        // 中断提示要接在已刷入的正文之后
        assistantMsg.content += '\n\n' + t('chat.replyInterrupted', { error: errorText(lastError) })
      }
      assistantMsg.streaming = false
      sending.value = false
      currentAbort = null
      onScroll?.()
    }

    return { openedNow, success: !streamFailed }
  }

  function releaseActiveSession() {
    if (activeSessionId.value) {
      closeChatSession(activeSessionId.value).catch(() => {})
      activeSessionId.value = ''
    }
  }

  // 关标签页/刷新/前进后退时兜底释放服务端会话，仅靠 onBeforeUnmount 覆盖不到这些路径
  window.addEventListener('pagehide', releaseActiveSession)

  // 组件卸载时清理
  onBeforeUnmount(() => {
    window.removeEventListener('pagehide', releaseActiveSession)
    abortCurrentStream()
    releaseActiveSession()
  })

  return {
    // 状态
    sessionId,
    activeSessionId,
    connecting,
    sending,
    messages,
    conversations,
    loadingConversations,
    thinkingExpanded,
    // 操作
    loadConversations,
    selectConversation,
    renameConversation,
    removeConversations,
    startNewChat,
    sendMessage,
    stopGeneration,
    toggleThinking,
  }
}
