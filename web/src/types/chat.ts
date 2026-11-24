/**
 * Web 聊天相关类型定义
 */

import type { PageQueryParams } from './api'
import type { MessageAttachment } from './message'

/**
 * LLM 流式输出的 Token 单元，区分思考过程和正式回复；error 表示模型调用失败，text 是失败原因；
 * activity 表示出正文前的准备工作，text 取值见后端 ChatToken：vision 在看图
 */
export interface ChatToken {
  type: 'thinking' | 'content' | 'error' | 'activity'
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
  /** 首个思考片段到达的时间戳，用于计算本轮思考耗时 */
  thinkingStartedAt?: number
  /** 思考阶段耗时（毫秒） */
  thinkingDurationMs?: number
  timestamp: Date
  /** 流式接收中 */
  streaming?: boolean
  /** 正在进行的准备工作（如看图），正文到达后清掉 */
  activity?: string
  /** 用户随消息发的附件 */
  attachments?: MessageAttachment[]
}

/**
 * 上传聊天附件的结果（对齐后端 ChatAttachmentResp）
 */
export interface ChatAttachment {
  attachmentId: string
  kind: 'image'
  name: string
}

/**
 * 输入框里待发的附件（前端视图模型）
 */
export interface ChatDraftAttachment {
  localId: number
  name: string
  /** 本地预览地址（blob:） */
  previewUrl: string
  status: 'uploading' | 'ready'
  attachmentId?: string
}

/**
 * 网页聊天会话（对齐后端 ConversationResp）
 */
export interface Conversation {
  sessionId: string
  roleId: number
  roleName: string
  /** 默认取第一句话的开头，可重命名；为空时显示「新对话」 */
  title: string | null
  /** 最后一次对话的时间 */
  updateTime: string
}

/**
 * 会话查询参数（对齐后端 ConversationPageReq）
 */
export interface ConversationQueryParams extends PageQueryParams {
  roleId?: number
}
