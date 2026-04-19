/**
 * Web 聊天会话 Composable
 * 统一管理会话状态、消息流、历史记录
 */

import { ref, onBeforeUnmount } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { openChatSession, closeChatSession, chatStream } from '@/services/chat'
import { queryConversations, queryMessages } from '@/services/message'
import type { ChatMessage } from '@/types/chat'
import type { Conversation, Message } from '@/types/message'

export function useChatSession() {
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

  function toggleThinking(msgId: number) {
    thinkingExpanded.value[msgId] = !thinkingExpanded.value[msgId]
  }

  async function loadConversations() {
    loadingConversations.value = true
    try {
      // 仅加载 Web 来源的会话，避免混入设备对话（每次连接都会产生新 sessionId）
      const res = await queryConversations({ pageNo: 1, pageSize: 50, source: 'web' })
      conversations.value = res.data.list
    } catch (e: unknown) {
      antMessage.error('加载历史会话失败: ' + (e instanceof Error ? e.message : String(e)))
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
      antMessage.warning('当前对话正在进行中，请稍后再试')
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
      antMessage.error('加载消息记录失败: ' + (e instanceof Error ? e.message : String(e)))
      return false
    }
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
        const data = resp as unknown as { sessionId: string }
        sessionId.value = data.sessionId
        activeSessionId.value = data.sessionId
        openedNow = true
      } catch (e: unknown) {
        antMessage.error('建立会话失败: ' + (e instanceof Error ? e.message : String(e)))
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

    try {
      for await (const token of chatStream(sessionId.value, text, currentAbort.signal)) {
        if (token.type === 'thinking') {
          assistantMsg.thinking = (assistantMsg.thinking || '') + token.text
        } else {
          // 思考阶段结束，标记为已完成
          if (assistantMsg.thinking && !assistantMsg.thinkingDone) {
            assistantMsg.thinkingDone = true
          }
          assistantMsg.content += token.text
        }
        onScroll?.()
      }
      // 流结束后确保 thinking 标记为 done
      if (assistantMsg.thinking && !assistantMsg.thinkingDone) {
        assistantMsg.thinkingDone = true
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        // 用户主动取消
      } else {
        assistantMsg.content += '\n\n⚠️ 回复中断: ' + (e instanceof Error ? e.message : String(e))
      }
    } finally {
      assistantMsg.streaming = false
      sending.value = false
      currentAbort = null
      onScroll?.()
    }

    return { openedNow, success: true }
  }

  // 组件卸载时清理
  onBeforeUnmount(() => {
    abortCurrentStream()
    if (activeSessionId.value) {
      closeChatSession(activeSessionId.value).catch(() => {})
    }
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
    startNewChat,
    sendMessage,
    toggleThinking,
  }
}
