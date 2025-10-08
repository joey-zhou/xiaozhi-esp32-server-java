// useWebSocket composable - WebSocket连接管理

import { ref, onBeforeUnmount } from 'vue'
import {
  connectToServer,
  disconnectFromServer,
  sendTextMessage,
  startDirectRecording,
  stopDirectRecording,
  isWebSocketConnected,
  registerMessageHandler,
  unregisterMessageHandler,
  registerStatusChangeCallback,
  unregisterStatusChangeCallback,
  registerBinaryHandler,
  messages,
  clearMessages,
  type WebSocketConfig,
  type WebSocketMessage,
  type ConnectionStatus
} from '@/services/websocket'
import { initAudio, handleBinaryAudioMessage } from '@/services/audio'

export function useWebSocket() {
  // 连接状态
  const isConnected = ref(false)
  const connectionStatus = ref('未连接')
  const connectionTime = ref<Date | null>(null)
  const sessionId = ref<string | null>(null)

  // 状态变更回调
  const handleStatusChange = (status: ConnectionStatus) => {
    isConnected.value = status.isConnected
    connectionStatus.value = status.connectionStatus
    connectionTime.value = status.connectionTime
    sessionId.value = status.sessionId
  }

  // 注册状态变更回调
  registerStatusChangeCallback(handleStatusChange)

  // 组件卸载时清理
  onBeforeUnmount(() => {
    unregisterStatusChangeCallback(handleStatusChange)
  })

  // 连接到服务器
  const connect = async (config: WebSocketConfig): Promise<boolean> => {
    try {
      // 初始化音频系统
      await initAudio()

      // 注册二进制数据处理函数
      registerBinaryHandler(handleBinaryAudioMessage)

      // 连接 WebSocket
      const success = await connectToServer(config)
      return success
    } catch (error) {
      console.error('连接失败:', error)
      return false
    }
  }

  // 断开连接
  const disconnect = (): boolean => {
    return disconnectFromServer()
  }

  // 发送文本消息
  const sendText = (text: string): boolean => {
    return sendTextMessage(text)
  }

  // 开始录音
  const startRecording = async (): Promise<boolean> => {
    try {
      return await startDirectRecording()
    } catch (error) {
      console.error('开始录音失败:', error)
      throw error
    }
  }

  // 停止录音
  const stopRecording = async (): Promise<boolean> => {
    try {
      return await stopDirectRecording()
    } catch (error) {
      console.error('停止录音失败:', error)
      throw error
    }
  }

  // 检查连接状态
  const checkConnected = (): boolean => {
    return isWebSocketConnected()
  }

  // 注册消息处理函数
  const onMessage = (handler: (data: WebSocketMessage) => void): void => {
    registerMessageHandler(handler)
  }

  // 移除消息处理函数
  const offMessage = (handler: (data: WebSocketMessage) => void): void => {
    unregisterMessageHandler(handler)
  }

  // 清空消息
  const clearAllMessages = (): boolean => {
    return clearMessages()
  }

  return {
    // 状态
    isConnected,
    connectionStatus,
    connectionTime,
    sessionId,
    messages,

    // 方法
    connect,
    disconnect,
    sendText,
    startRecording,
    stopRecording,
    checkConnected,
    onMessage,
    offMessage,
    clearAllMessages
  }
}

