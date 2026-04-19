/**
 * Web 聊天相关类型定义
 */

/**
 * LLM 流式输出的 Token 单元，区分思考过程和正式回复
 */
export interface ChatToken {
  type: 'thinking' | 'content'
  text: string
}

/**
 * 聊天消息（前端视图模型）
 */
export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  /** 思考过程内容（仅 assistant 且开启深度思考时有值） */
  thinking?: string
  /** 思考是否已完成（切换 UI：思考中... → 已完成思考） */
  thinkingDone?: boolean
  timestamp: Date
  /** 流式接收中 */
  streaming?: boolean
}
