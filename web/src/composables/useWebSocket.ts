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
  sendBinaryFrame,
  messages,
  clearMessages,
  type WebSocketConfig,
  type WebSocketMessage,
  type ConnectionStatus
} from '@/services/websocket'
import { initAudio, handleBinaryAudioMessage } from '@/services/audio'
import { startMicrophoneCapture, stopMicrophoneCapture } from '@/services/audioRecorder'

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
    // 录音中卸载时释放麦克风，否则浏览器录音标识会一直亮着
    void stopMicrophoneCapture()
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

  // 是否已通知服务端进入聆听状态。未进入时采集到的帧直接丢弃，
  // 避免音频早于 listen/start 到达导致服务端 VAD 尚未初始化而丢包。
  let listening = false
  // 用于作废「启动尚未完成就已松手」的录音请求
  let startToken = 0

  // 开始录音
  const startRecording = async (): Promise<boolean> => {
    const token = ++startToken

    try {
      // 先取得麦克风权限再通知服务端，避免用户拒绝授权后服务端空等
      await startMicrophoneCapture(frame => {
        if (listening) {
          sendBinaryFrame(frame)
        }
      })

      // 授权期间用户已松手，直接回收麦克风
      if (token !== startToken) {
        await stopMicrophoneCapture()
        return false
      }

      const started = await startDirectRecording()
      if (!started) {
        await stopMicrophoneCapture()
        return false
      }

      listening = true
      return true
    } catch (error) {
      listening = false
      await stopMicrophoneCapture()
      console.error('开始录音失败:', error)
      throw error
    }
  }

  // 停止录音
  const stopRecording = async (): Promise<boolean> => {
    // 作废可能仍在进行中的启动流程
    startToken++
    const wasListening = listening
    listening = false

    try {
      await stopMicrophoneCapture()
      // 从未通知过服务端进入聆听，就不必发结束消息
      return wasListening ? await stopDirectRecording() : true
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

