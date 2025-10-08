// WebSocket 服务 - Vue3 TypeScript版本

// =============================
// 类型定义
// =============================

export interface WebSocketConfig {
  url: string
  deviceId?: string
  macAddress?: string
  deviceName?: string
  token?: string
}

export interface WebSocketMessage {
  type: 'stt' | 'tts' | 'listen' | 'audio' | 'system'
  state?: 'start' | 'stop' | 'text' | 'sentence_start'
  text?: string
  session_id?: string
  [key: string]: any
}

export interface ChatMessage {
  id: string
  content: string
  type: 'text' | 'audio' | 'stt' | 'tts' | 'system'
  isUser: boolean
  timestamp: Date
  isLoading?: boolean
  duration?: string
  audioData?: any
}

export interface ConnectionStatus {
  isConnected: boolean
  connectionStatus: string
  connectionTime: Date | null
  sessionId: string | null
}

// =============================
// 状态变量
// =============================

let webSocket: WebSocket | null = null
let isConnecting = false
let reconnectTimer: number | null = null
let reconnectAttempts = 0
const maxReconnectAttempts = 5
const reconnectDelay = 2000

// 连接状态
let connectionStatus: ConnectionStatus = {
  isConnected: false,
  connectionStatus: '未连接',
  connectionTime: null,
  sessionId: null
}

// 消息列表
export const messages: ChatMessage[] = []

// 回调函数
type MessageHandler = (data: WebSocketMessage) => void
type StatusChangeHandler = (status: ConnectionStatus) => void
type BinaryHandler = (data: ArrayBuffer) => void

const messageHandlers: Set<MessageHandler> = new Set()
const statusChangeCallbacks: Set<StatusChangeHandler> = new Set()
let binaryHandler: BinaryHandler | null = null

// =============================
// 日志管理
// =============================

type LogLevel = 'debug' | 'info' | 'success' | 'warning' | 'error'

interface LogEntry {
  message: string
  type: LogLevel
  time: Date
}

const LOG_LEVELS: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  success: 2,
  warning: 3,
  error: 4
}

let currentLogLevel = LOG_LEVELS.debug
let logHistory: LogEntry[] = []
const MAX_LOG_HISTORY = 500

export function log(message: string, type: LogLevel = 'info'): LogEntry {
  if (LOG_LEVELS[type] < currentLogLevel) {
    return { message, type, time: new Date() }
  }

  const entry: LogEntry = {
    message,
    type,
    time: new Date()
  }

  logHistory.push(entry)

  if (logHistory.length > MAX_LOG_HISTORY) {
    logHistory = logHistory.slice(-MAX_LOG_HISTORY)
  }

  switch (type) {
    case 'error':
      console.error(message)
      break
    case 'warning':
      console.warn(message)
      break
    case 'success':
      console.log('%c' + message, 'color: green')
      break
    case 'debug':
      console.debug(message)
      break
    default:
      console.log(message)
  }

  return entry
}

export function getLogs(): LogEntry[] {
  return [...logHistory]
}

export function clearLogs(): boolean {
  logHistory = []
  return true
}

export function setLogLevel(level: LogLevel): boolean {
  if (LOG_LEVELS[level] !== undefined) {
    currentLogLevel = LOG_LEVELS[level]
    return true
  }
  return false
}

// =============================
// 消息管理
// =============================

export function addMessage(message: Partial<ChatMessage>): ChatMessage | null {
  if (!message.content) return null

  const newMessage: ChatMessage = {
    id: message.id || `msg_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
    content: String(message.content).trim(),
    type: message.type || 'text',
    isUser: !!message.isUser,
    timestamp: message.timestamp || new Date(),
    isLoading: !!message.isLoading
  }

  messages.push(newMessage)

  log(
    `添加${newMessage.isUser ? '用户' : 'AI'}消息: ${newMessage.content.substring(0, 50)}${
      newMessage.content.length > 50 ? '...' : ''
    }`,
    'debug'
  )

  return newMessage
}

export function clearMessages(): boolean {
  messages.splice(0, messages.length)
  log('清空所有消息', 'info')
  return true
}

// =============================
// 回调管理
// =============================

export function registerMessageHandler(handler: MessageHandler): boolean {
  if (typeof handler === 'function') {
    messageHandlers.add(handler)
    return true
  }
  return false
}

export function unregisterMessageHandler(handler: MessageHandler): boolean {
  return messageHandlers.delete(handler)
}

export function registerStatusChangeCallback(callback: StatusChangeHandler): boolean {
  if (typeof callback === 'function') {
    statusChangeCallbacks.add(callback)
    return true
  }
  return false
}

export function unregisterStatusChangeCallback(callback: StatusChangeHandler): boolean {
  return statusChangeCallbacks.delete(callback)
}

export function registerBinaryHandler(handler: BinaryHandler): void {
  binaryHandler = handler
}

// 通知状态变更
function notifyStatusChange(): void {
  const status = { ...connectionStatus }

  statusChangeCallbacks.forEach(callback => {
    try {
      callback(status)
    } catch (error) {
      log(`状态变更回调执行错误: ${error}`, 'error')
    }
  })
}

// =============================
// WebSocket 连接
// =============================

export async function connectToServer(config: WebSocketConfig): Promise<boolean> {
  if (webSocket && webSocket.readyState === WebSocket.OPEN) {
    log('WebSocket已连接', 'info')
    return true
  }

  if (isConnecting) {
    log('WebSocket正在连接中...', 'info')
    return false
  }

  try {
    isConnecting = true
    connectionStatus.connectionStatus = '正在连接...'
    connectionStatus.isConnected = false

    // 清除之前的重连计时器
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }

    // 关闭现有连接
    if (webSocket) {
      try {
        webSocket.close()
      } catch (e) {
        // 忽略关闭错误
      }
    }

    // 构建连接URL
    let url = config.url
    if (!url.endsWith('/')) {
      url += '/'
    }

    // 添加查询参数
    const params = new URLSearchParams()
    if (config.deviceId) {
      params.append('device-id', config.deviceId)
    }
    if (config.macAddress || config.deviceName) {
      params.append('mac_address', config.deviceName || config.macAddress || '')
    }
    if (config.token) {
      params.append('token', config.token)
    }

    const queryString = params.toString()
    if (queryString) {
      url += '?' + queryString
    }

    log(`正在连接到: ${url}`, 'info')

    // 创建WebSocket连接
    webSocket = new WebSocket(url)
    webSocket.binaryType = 'arraybuffer'

    // 连接打开事件
    webSocket.onopen = () => {
      isConnecting = false
      connectionStatus.isConnected = true
      connectionStatus.connectionStatus = '已连接'
      connectionStatus.connectionTime = new Date()
      reconnectAttempts = 0
      log('WebSocket连接已建立', 'success')
      notifyStatusChange()
    }

    // 接收消息事件
    webSocket.onmessage = (event) => {
      handleWebSocketMessage(event)
    }

    // 连接关闭事件
    webSocket.onclose = (event) => {
      isConnecting = false
      connectionStatus.isConnected = false

      if (event.wasClean) {
        connectionStatus.connectionStatus = '已断开'
        log(`WebSocket连接已关闭: 代码=${event.code}, 原因=${event.reason}`, 'info')
      } else {
        connectionStatus.connectionStatus = '连接已断开'
        log('WebSocket连接意外断开', 'error')
        scheduleReconnect(config)
      }

      notifyStatusChange()
    }

    // 连接错误事件
    webSocket.onerror = () => {
      isConnecting = false
      connectionStatus.isConnected = false
      connectionStatus.connectionStatus = '连接错误'
      log('WebSocket连接错误', 'error')
      notifyStatusChange()
    }

    // 等待连接完成或超时
    return new Promise((resolve) => {
      const timeoutId = setTimeout(() => {
        if (!connectionStatus.isConnected) {
          log('WebSocket连接超时', 'error')
          isConnecting = false
          connectionStatus.connectionStatus = '连接超时'

          try {
            webSocket?.close()
          } catch (e) {
            // 忽略关闭错误
          }

          resolve(false)
        }
      }, 5000)

      const checkConnected = () => {
        if (connectionStatus.isConnected) {
          clearTimeout(timeoutId)
          resolve(true)
        } else if (
          connectionStatus.connectionStatus.includes('错误') ||
          connectionStatus.connectionStatus.includes('超时')
        ) {
          clearTimeout(timeoutId)
          resolve(false)
        } else {
          setTimeout(checkConnected, 100)
        }
      }

      checkConnected()
    })
  } catch (error) {
    isConnecting = false
    connectionStatus.isConnected = false
    connectionStatus.connectionStatus = '连接失败'
    log(`WebSocket连接失败: ${error}`, 'error')
    notifyStatusChange()
    return false
  }
}

// 安排重新连接
function scheduleReconnect(config: WebSocketConfig): void {
  if (reconnectAttempts >= maxReconnectAttempts) {
    log(`已达到最大重连次数(${maxReconnectAttempts})，停止重连`, 'warning')
    connectionStatus.connectionStatus = '重连失败'
    notifyStatusChange()
    return
  }

  const delay = reconnectDelay * Math.pow(1.5, reconnectAttempts)

  log(
    `计划在${delay / 1000}秒后重新连接(尝试${reconnectAttempts + 1}/${maxReconnectAttempts})`,
    'info'
  )
  connectionStatus.connectionStatus = `${Math.ceil(delay / 1000)}秒后重连...`
  notifyStatusChange()

  reconnectTimer = window.setTimeout(() => {
    reconnectAttempts++
    connectToServer(config)
  }, delay)
}

// 处理WebSocket消息
function handleWebSocketMessage(event: MessageEvent): void {
  try {
    // 检查是否是二进制数据
    if (event.data instanceof ArrayBuffer) {
      if (binaryHandler) {
        binaryHandler(event.data)
      } else {
        log('未注册二进制消息处理函数', 'warning')
      }
      return
    }

    // 处理文本数据
    const data: WebSocketMessage = JSON.parse(event.data)

    // 记录会话ID
    if (data.session_id && !connectionStatus.sessionId) {
      connectionStatus.sessionId = data.session_id
      log(`会话ID: ${connectionStatus.sessionId}`, 'info')
      notifyStatusChange()
    }

    // 根据消息类型处理
    switch (data.type) {
      case 'stt':
        handleSTTMessage(data)
        break
      case 'tts':
        handleTTSMessage(data)
        break
      default:
        log(`收到未知类型的消息: ${data.type}`, 'warning')
    }

    // 调用所有注册的消息处理函数
    messageHandlers.forEach(handler => {
      try {
        handler(data)
      } catch (error) {
        log(`消息处理函数执行错误: ${error}`, 'error')
      }
    })
  } catch (error) {
    log(`处理WebSocket消息出错: ${error}`, 'error')
  }
}

// 处理STT消息（语音识别）
function handleSTTMessage(data: WebSocketMessage): void {
  if (data.text) {
    addMessage({
      content: data.text,
      type: 'stt',
      isUser: true
    })
    log(`语音识别结果: ${data.text}`, 'info')
  }
}

// 处理TTS消息（文本转语音）
function handleTTSMessage(data: WebSocketMessage): void {
  if (data.state === 'start') {
    log('TTS开始', 'info')
  } else if (data.state === 'sentence_start' && data.text) {
    addMessage({
      content: data.text,
      type: 'tts',
      isUser: false
    })
    log(`TTS文本: ${data.text}`, 'info')
  } else if (data.state === 'stop') {
    log('TTS结束', 'info')
  }
}

// =============================
// 消息发送
// =============================

function sendJsonMessage(data: Record<string, any>): boolean {
  if (!webSocket || webSocket.readyState !== WebSocket.OPEN) {
    log('WebSocket未连接，无法发送消息', 'error')
    return false
  }

  try {
    const message = JSON.stringify(data)
    webSocket.send(message)
    return true
  } catch (error) {
    log(`发送JSON消息失败: ${error}`, 'error')
    return false
  }
}

export function sendTextMessage(text: string): boolean {
  if (!text || !webSocket || webSocket.readyState !== WebSocket.OPEN) {
    return false
  }

  try {
    const message = {
      type: 'listen',
      state: 'text',
      text: text
    }

    return sendJsonMessage(message)
  } catch (error) {
    log(`发送文本消息失败: ${error}`, 'error')
    return false
  }
}

export async function startDirectRecording(): Promise<boolean> {
  if (!webSocket || webSocket.readyState !== WebSocket.OPEN) {
    throw new Error('WebSocket未连接')
  }

  try {
    const startMessage = {
      type: 'stt',
      state: 'start'
    }

    sendJsonMessage(startMessage)
    log('已发送开始录音命令', 'info')

    return true
  } catch (error) {
    log(`开始录音失败: ${error}`, 'error')
    throw error
  }
}

export async function stopDirectRecording(): Promise<boolean> {
  if (!webSocket || webSocket.readyState !== WebSocket.OPEN) {
    throw new Error('WebSocket未连接')
  }

  try {
    const stopMessage = {
      type: 'stt',
      state: 'stop'
    }

    sendJsonMessage(stopMessage)
    log('已发送停止录音命令', 'info')

    return true
  } catch (error) {
    log(`停止录音失败: ${error}`, 'error')
    throw error
  }
}

// =============================
// 连接控制
// =============================

export async function reconnectToServer(config: WebSocketConfig): Promise<boolean> {
  try {
    log('手动触发重连...', 'info')

    await disconnectFromServer()

    reconnectAttempts = 0

    return await connectToServer(config)
  } catch (error) {
    log(`手动重连失败: ${error}`, 'error')
    connectionStatus.connectionStatus = '重连失败'
    notifyStatusChange()
    return false
  }
}

export function stopAutoReconnect(): boolean {
  try {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
      log('已停止自动重连', 'info')
    }

    reconnectAttempts = 0

    if (connectionStatus.connectionStatus.includes('重连')) {
      connectionStatus.connectionStatus = '已停止重连'
      notifyStatusChange()
    }

    return true
  } catch (error) {
    log(`停止自动重连失败: ${error}`, 'error')
    return false
  }
}

export function disconnectFromServer(): boolean {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  if (!webSocket) {
    return true
  }

  try {
    if (webSocket.readyState === WebSocket.OPEN) {
      webSocket.close(1000, '用户主动断开')
    }

    webSocket = null
    connectionStatus.isConnected = false
    connectionStatus.connectionStatus = '已断开'
    log('WebSocket连接已断开', 'info')
    notifyStatusChange()

    return true
  } catch (error) {
    log(`断开WebSocket连接失败: ${error}`, 'error')
    return false
  }
}

export function isWebSocketConnected(): boolean {
  return webSocket !== null && webSocket.readyState === WebSocket.OPEN
}

export function getConnectionStatus(): ConnectionStatus {
  return { ...connectionStatus }
}

