/**
 * 消息信息接口
 */
export interface Message {
  messageId: number
  deviceId: string
  deviceName?: string
  roleId?: number
  roleName?: string
  sender: 'user' | 'assistant'
  message: string
  audioPath?: string
  state: string
  messageType: string
  sessionId?: string
  createTime?: string
  updateTime?: string
  audioLoadError?: boolean
}

/**
 * 消息查询参数
 */
export interface MessageQueryParams {
  start: number
  limit: number
  deviceId?: string
  deviceName?: string
  sender?: string
  startTime?: string
  endTime?: string
}

/**
 * 消息列表响应
 */
export interface MessageListResponse {
  list: Message[]
  total: number
  pageNum: number
  pageSize: number
  size: number
  startRow: number
  endRow: number
  pages: number
  prePage: number
  nextPage: number
  isFirstPage: boolean
  isLastPage: boolean
  hasPreviousPage: boolean
  hasNextPage: boolean
  navigatePages: number
  navigatepageNums: number[]
  navigateFirstPage: number
  navigateLastPage: number
}

