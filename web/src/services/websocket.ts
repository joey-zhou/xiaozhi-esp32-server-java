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
  type: 'stt' | 'tts' | 'llm' | 'iot' | 'listen' | 'audio' | 'system'
  state?: 'start' | 'stop' | 'text' | 'sentence_start'
  text?: string
  /** type=llm 时的情绪标签（neutral/happy/sad ...） */
  emotion?: string
  session_id?: string
  [key: string]: unknown
}

export interface ChatMessage {
  id: string
  content: string
  type: 'text' | 'audio' | 'stt' | 'tts' | 'system'
  isUser: boolean
  timestamp: Date
  isLoading?: boolean
  duration?: string
  audioData?: ArrayBuffer | Blob
  /** 服务端下发的情绪标签，随本句 AI 回复一起展示 */
  emotion?: string
}

/**
 * 连接状态码。这里刻意不放中文文案：
 * 状态既要显示给用户，也参与 connectToServer 的成败判定与 stopAutoReconnect 的分支，
 * 存文案会让「换个措辞或翻成英文」把控制流一起改坏。文案由视图层按状态码翻译。
 */
export const CONNECTION_STATUS_KEYS = [
  'idle',
  'connecting',
  'connected',
  'closed',
  'dropped',
  'error',
  'timeout',
  'failed',
  'reconnecting',
  'reconnectFailed',
  'reconnectStopped',
] as const

export type ConnectionStatusKey = (typeof CONNECTION_STATUS_KEYS)[number]

/** 这几种表示这一轮连接已经确定失败，不必再等 */
const TERMINAL_FAILURE_KEYS: ReadonlySet<ConnectionStatusKey> = new Set([
  'error',
  'timeout',
  'failed',
  'reconnectFailed',
])

/** 这几种表示处在重连流程里（含已停止重连，与改造前 includes('重连') 的覆盖范围一致） */
const RECONNECT_KEYS: ReadonlySet<ConnectionStatusKey> = new Set([
  'reconnecting',
  'reconnectFailed',
  'reconnectStopped',
])

export interface ConnectionStatus {
  isConnected: boolean
  connectionStatus: ConnectionStatusKey
  /** 仅在 connectionStatus 为 reconnecting 时有意义，供文案插值用 */
  reconnectSeconds: number
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

// 建连等待上限
const CONNECT_TIMEOUT = 5000

// 心跳：服务端 websocket.max-session-idle-timeout 默认 60 秒，网页端不发音频时会被判空闲踢下线
let heartbeatTimer: number | null = null
const HEARTBEAT_INTERVAL = 30000

// 打字机效果相关
let typewriterTimer: number | null = null
let typewriterQueue: string[] = [] // 待打字的文本队列
let isTyping = false // 是否正在打字
const TYPING_SPEED = 50 // 每个字的显示间隔（毫秒）

// 连接状态
const connectionStatus: ConnectionStatus = {
  isConnected: false,
  connectionStatus: 'idle',
  reconnectSeconds: 0,
  connectionTime: null,
  sessionId: null
}

import { reactive } from 'vue'

// 消息列表 - 使用响应式数组
export const messages: ChatMessage[] = reactive([])

// 当前正在构建的AI回复消息
let currentAIMessage: ChatMessage | null = null
// 情绪消息早于 sentence_start 到达，先暂存，等消息创建时再挂上去
let pendingEmotion: string | null = null

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

// 生产只留 warning 以上：下行音频每帧都会走这里，debug 级会把 console 和内存日志刷爆
let currentLogLevel = import.meta.env.DEV ? LOG_LEVELS.debug : LOG_LEVELS.warning
const logHistory: LogEntry[] = []
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

  // 逐条淘汰，不要整体 slice 重建 500 条数组
  while (logHistory.length > MAX_LOG_HISTORY) {
    logHistory.shift()
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
  logHistory.length = 0
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
  currentAIMessage = null // 重置当前AI消息
  pendingEmotion = null
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
  log('✅ 二进制消息处理函数已注册', 'info')
}

/** 组件卸载时必须调，否则处理函数一直挂着，音频链路跟着不放 */
export function unregisterBinaryHandler(handler: BinaryHandler): boolean {
  if (binaryHandler !== handler) {
    return false
  }
  binaryHandler = null
  return true
}

/** 发送保活报文重置服务端空闲计时；服务端收下即丢弃，不回应答 */
function startHeartbeat(): void {
  stopHeartbeat()
  heartbeatTimer = window.setInterval(() => {
    if (webSocket?.readyState === WebSocket.OPEN) {
      sendJsonMessage({ type: 'ping' })
    }
  }, HEARTBEAT_INTERVAL)
}

function stopHeartbeat(): void {
  if (heartbeatTimer !== null) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
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
    connectionStatus.connectionStatus = 'connecting'
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
      } catch {
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
      connectionStatus.connectionStatus = 'connected'
      connectionStatus.connectionTime = new Date()
      reconnectAttempts = 0
      startHeartbeat()
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
      stopHeartbeat()

      if (event.wasClean) {
        connectionStatus.connectionStatus = 'closed'
        log(`WebSocket连接已关闭: 代码=${event.code}, 原因=${event.reason}`, 'info')
      } else {
        connectionStatus.connectionStatus = 'dropped'
        log('WebSocket连接意外断开', 'error')
        scheduleReconnect(config)
      }

      notifyStatusChange()
    }

    // 连接错误事件
    webSocket.onerror = () => {
      isConnecting = false
      connectionStatus.isConnected = false
      stopHeartbeat()
      connectionStatus.connectionStatus = 'error'
      log('WebSocket连接错误', 'error')
      notifyStatusChange()
    }

    // 等待连接完成或超时。
    // 超时定时器只对本轮的 socket 负责：本轮结束后 webSocket 已经换人，再去 close 会掐掉下一轮刚建好的连接
    const pendingSocket = webSocket
    return new Promise((resolve) => {
      let settled = false
      // 定时器先声明：它的回调必定异步执行，此时 settle 已完成初始化
      const timeoutId = setTimeout(() => {
        if (webSocket !== pendingSocket || connectionStatus.isConnected) {
          settle(false)
          return
        }
        log('WebSocket连接超时', 'error')
        isConnecting = false
        connectionStatus.connectionStatus = 'timeout'

        try {
          pendingSocket.close()
        } catch {
          // 忽略关闭错误
        }

        settle(false)
      }, CONNECT_TIMEOUT)

      const settle = (result: boolean) => {
        if (settled) return
        settled = true
        clearTimeout(timeoutId)
        resolve(result)
      }

      const checkConnected = () => {
        if (settled) return
        if (webSocket !== pendingSocket) {
          settle(false)
        } else if (connectionStatus.isConnected) {
          settle(true)
        } else if (TERMINAL_FAILURE_KEYS.has(connectionStatus.connectionStatus)) {
          settle(false)
        } else {
          setTimeout(checkConnected, 100)
        }
      }

      checkConnected()
    })
  } catch (error) {
    isConnecting = false
    connectionStatus.isConnected = false
    connectionStatus.connectionStatus = 'failed'
    log(`WebSocket连接失败: ${error}`, 'error')
    notifyStatusChange()
    return false
  }
}

// 安排重新连接
function scheduleReconnect(config: WebSocketConfig): void {
  if (reconnectAttempts >= maxReconnectAttempts) {
    log(`已达到最大重连次数(${maxReconnectAttempts})，停止重连`, 'warning')
    connectionStatus.connectionStatus = 'reconnectFailed'
    notifyStatusChange()
    return
  }

  const delay = reconnectDelay * Math.pow(1.5, reconnectAttempts)

  log(
    `计划在${delay / 1000}秒后重新连接(尝试${reconnectAttempts + 1}/${maxReconnectAttempts})`,
    'info'
  )
  connectionStatus.reconnectSeconds = Math.ceil(delay / 1000)
  connectionStatus.connectionStatus = 'reconnecting'
  notifyStatusChange()

  reconnectTimer = window.setTimeout(() => {
    reconnectAttempts++
    connectToServer(config)
  }, delay)
}

// 处理WebSocket消息
function handleWebSocketMessage(event: MessageEvent): void {
  try {
    // 详细检查消息类型
    log(`📨 收到WebSocket消息，类型: ${typeof event.data}, 构造函数: ${event.data.constructor.name}`, 'debug')
    
    // 检查是否是二进制数据
    if (event.data instanceof ArrayBuffer) {
      log(`🔢 收到二进制数据: ${event.data.byteLength}字节`, 'debug')
      if (binaryHandler) {
        log('✅ 调用二进制处理函数', 'debug')
        binaryHandler(event.data)
      } else {
        log('❌ 未注册二进制消息处理函数', 'warning')
      }
      return
    }

    // 检查是否是Blob数据
    if (event.data instanceof Blob) {
      log(`🔢 收到Blob数据: ${event.data.size}字节`, 'debug')
      event.data.arrayBuffer().then(buffer => {
        if (binaryHandler) {
          log('✅ 调用二进制处理函数 (Blob转ArrayBuffer)', 'debug')
          binaryHandler(buffer)
        } else {
          log('❌ 未注册二进制消息处理函数', 'warning')
        }
      })
      return
    }

    // 处理文本数据
    log(`📝 收到文本消息: ${event.data.substring(0, 100)}...`, 'debug')
    const data: WebSocketMessage = JSON.parse(event.data)

    // 记录会话ID：重连后服务端会下发新的，必须覆盖旧值，否则后续消息还带着已失效的会话
    if (data.session_id && data.session_id !== connectionStatus.sessionId) {
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
      case 'llm':
        handleEmotionMessage(data)
        break
      case 'iot':
        // 网页端没有可控 IoT 设备，指令消息直接忽略
        log('忽略 iot 指令消息', 'debug')
        break
      default:
        log(`收到未处理类型的消息: ${data.type}`, 'debug')
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

// 处理情绪消息（每句 TTS 前下发一条）
function handleEmotionMessage(data: WebSocketMessage): void {
  if (!data.emotion) return
  if (currentAIMessage) {
    currentAIMessage.emotion = data.emotion
  } else {
    pendingEmotion = data.emotion
  }
}

// 打字机效果：逐字显示文本
function startTypewriter(text: string): void {
  // 将文本添加到队列
  typewriterQueue.push(text)
  
  // 如果没有在打字，启动打字机
  if (!isTyping) {
    processTypewriterQueue()
  }
}

// 处理打字机队列
function processTypewriterQueue(): void {
  if (typewriterQueue.length === 0) {
    isTyping = false
    return
  }
  
  isTyping = true
  const text = typewriterQueue.shift()!
  const chars = Array.from(text) // 支持 emoji 和多字节字符
  let currentIndex = 0
  
  // 如果是第一次打字，创建消息
  if (!currentAIMessage) {
    messages.push({
      id: `msg_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
      content: '',
      type: 'tts',
      isUser: false,
      timestamp: new Date(),
      isLoading: false,
      emotion: pendingEmotion ?? undefined
    })
    pendingEmotion = null
    // 必须回读 messages 里的响应式代理：直接留着原始对象改 content 不会触发视图更新
    currentAIMessage = messages[messages.length - 1]!
    log(`📝 创建新的AI回复消息 (ID: ${currentAIMessage.id})`, 'info')
  }
  
  // 逐字添加
  const typeNextChar = () => {
    if (currentIndex < chars.length) {
      currentAIMessage!.content += chars[currentIndex]
      currentIndex++
      typewriterTimer = window.setTimeout(typeNextChar, TYPING_SPEED)
    } else {
      // 当前文本打完，处理下一个
      log(`✅ 完成打字: "${text}"`, 'debug')
      processTypewriterQueue()
    }
  }
  
  typeNextChar()
}

// 停止打字机效果
function stopTypewriter(): void {
  if (typewriterTimer) {
    clearTimeout(typewriterTimer)
    typewriterTimer = null
  }
  isTyping = false
  typewriterQueue = []
}

/**
 * TTS 状态回调。音频流的开始/结束只由 tts 的 start/stop 表达，
 * 服务端不会下发空音频帧，注册方需据此复位缓冲与结束播放。
 */
type TtsStateHandler = (state: 'start' | 'stop') => void

let ttsStateHandler: TtsStateHandler | null = null

export function registerTtsStateHandler(handler: TtsStateHandler | null): void {
  ttsStateHandler = handler
}

// 处理TTS消息（文本转语音）
function handleTTSMessage(data: WebSocketMessage): void {
  if (data.state === 'start') {
    log('🎵 TTS开始，准备接收音频', 'info')
    
    // 重置打字机和当前AI消息
    stopTypewriter()
    currentAIMessage = null
    pendingEmotion = null

    ttsStateHandler?.('start')
  } else if (data.state === 'sentence_start' && data.text) {
    // 将新句子加入打字机队列
    log(`📥 收到新句子: "${data.text}"`, 'info')
    startTypewriter(data.text)
  } else if (data.state === 'stop') {
    log('🛑 TTS结束，音频流结束', 'info')
    
    // 等待打字机完成后再清理（最多等待10秒）
    let waitCount = 0
    const maxWait = 100 // 100 * 100ms = 10s
    const waitForTyping = () => {
      if (!isTyping && typewriterQueue.length === 0 || waitCount >= maxWait) {
        if (currentAIMessage) {
          log(`✅ AI回复完成，最终内容: "${currentAIMessage.content}"`, 'info')
          currentAIMessage = null
        }
      } else {
        waitCount++
        setTimeout(waitForTyping, 100)
      }
    }
    waitForTyping()

    ttsStateHandler?.('stop')
  }
}

// =============================
// 消息发送
// =============================

function sendJsonMessage(data: Record<string, unknown>): boolean {
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

/** 发送一帧上行音频，帧内容与设备端一致（Opus） */
export function sendBinaryFrame(frame: Uint8Array): boolean {
  if (!webSocket || webSocket.readyState !== WebSocket.OPEN) {
    return false
  }

  try {
    webSocket.send(frame)
    return true
  } catch (error) {
    log(`发送音频帧失败: ${error}`, 'error')
    return false
  }
}

export async function startDirectRecording(): Promise<boolean> {
  if (!webSocket || webSocket.readyState !== WebSocket.OPEN) {
    throw new Error('WebSocket未连接')
  }

  try {
    const startMessage = {
      type: 'listen',
      state: 'start',
      mode: 'manual'
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
      type: 'listen',
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
    connectionStatus.connectionStatus = 'reconnectFailed'
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

    if (RECONNECT_KEYS.has(connectionStatus.connectionStatus)) {
      connectionStatus.connectionStatus = 'reconnectStopped'
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

  stopHeartbeat()
  
  // 停止打字机效果
  stopTypewriter()
  currentAIMessage = null
  pendingEmotion = null

  if (!webSocket) {
    connectionStatus.sessionId = null
    return true
  }

  try {
    if (webSocket.readyState === WebSocket.OPEN) {
      webSocket.close(1000, '用户主动断开')
    }

    webSocket = null
    connectionStatus.isConnected = false
    connectionStatus.connectionStatus = 'closed'
    connectionStatus.sessionId = null
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

