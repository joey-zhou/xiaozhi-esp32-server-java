<script setup lang="ts">
import { ref, computed, nextTick, watch, onMounted } from 'vue'
import { MessageOutlined, SendOutlined, AudioOutlined, CloseOutlined, DeleteOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { message as AMessage } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import { useAvatar } from '@/composables/useAvatar'
import { useWebSocket } from '@/composables/useWebSocket'
import type { WebSocketConfig } from '@/store/user'
import type { Rule } from 'ant-design-vue/es/form'

const { t } = useI18n()
const userStore = useUserStore()
const { getAvatarUrl } = useAvatar()

// WebSocket 连接
const {
  isConnected,
  connectionStatus,
  messages: wsMessages,
  connect,
  disconnect,
  sendText,
  startRecording: wsStartRecording,
  stopRecording: wsStopRecording
} = useWebSocket()

// 聊天窗口状态
const chatVisible = ref(false)
const inputMessage = ref('')
const isVoiceMode = ref(false)
const isRecording = ref(false)
const chatContentRef = ref<HTMLElement>()

// 设置抽屉状态
const settingsVisible = ref(false)
const settingsForm = ref<WebSocketConfig>({
  url: userStore.wsConfig.url,
  deviceId: userStore.wsConfig.deviceId || 'web_test',
  deviceName: userStore.wsConfig.deviceName || 'Web用户',
  autoConnect: userStore.wsConfig.autoConnect
})

// 表单验证规则
const rules: Record<string, Rule[]> = {
  url: [
    { required: true, message: t('validation.enterWebSocketUrl'), trigger: 'blur' },
    {
      pattern: /^wss?:\/\/.+/,
      message: t('validation.enterValidWebSocketUrl'),
      trigger: 'blur'
    }
  ],
  deviceId: [
    { required: true, message: t('validation.enterDeviceId'), trigger: 'blur' }
  ]
}

// 头像
const userAvatar = computed(() => getAvatarUrl(userStore.userInfo?.avatar))
const aiAvatar = computed(() => '/ai-avatar.png') // 默认AI头像

// WebSocket 配置（从 store 获取）
const wsConfig = computed(() => ({
  url: userStore.wsConfig.url,
  deviceId: userStore.wsConfig.deviceId || userStore.userInfo?.userId?.toString() || '1',
  deviceName: userStore.wsConfig.deviceName || userStore.userInfo?.name || 'Web用户',
  token: userStore.token
}))

// 初始化 - 自动连接WebSocket
// 注意：onMounted 支持 async 函数是 Vue 3 的正确用法
// 与页面级的 async setup 不同，组件内的异步初始化使用 onMounted
onMounted(async () => {
  // 只有在启用自动连接时才连接
  if (!userStore.wsConfig.autoConnect) {
    return
  }

  try {
    const success = await connect(wsConfig.value)
    if (!success) {
      AMessage.warning('WebSocket连接失败，部分功能可能不可用')
    }
  } catch (error) {
    console.error('连接WebSocket失败:', error)
  }
})

// 监听消息变化并滚动到底部
watch(() => wsMessages.length, () => {
  scrollToBottom()
})

// 切换聊天窗口
const toggleChat = () => {
  chatVisible.value = !chatVisible.value
  if (chatVisible.value) {
    nextTick(() => {
      scrollToBottom()
    })
  }
}

// 关闭聊天窗口
const closeChat = () => {
  chatVisible.value = false
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatContentRef.value) {
      chatContentRef.value.scrollTop = chatContentRef.value.scrollHeight
    }
  })
}

// 确保WebSocket连接
const ensureConnection = async (): Promise<boolean> => {
  if (!isConnected.value) {
    try {
      const success = await connect(wsConfig.value)
      if (!success) {
        AMessage.error('未连接到服务器，请检查聊天配置')
        return false
      }
      await new Promise(resolve => setTimeout(resolve, 300))
    } catch (error) {
      AMessage.error('连接失败: ' + error)
      return false
    }
  }
  return true
}

// 发送文本消息
const sendTextMessage = async () => {
  const text = inputMessage.value.trim()
  if (!text) return

  // 确保连接
  const connected = await ensureConnection()
  if (!connected) return

  // 发送到服务器
  const success = sendText(text)

  if (success) {
    inputMessage.value = ''
    scrollToBottom()
  } else {
    AMessage.error('发送失败，请检查连接状态')
  }
}

// 处理回车键
const handleEnterKey = (e: KeyboardEvent) => {
  if (!e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey) {
    e.preventDefault()
    sendTextMessage()
  }
}

// 切换输入模式
const toggleInputMode = () => {
  isVoiceMode.value = !isVoiceMode.value
}

// 开始录音
const startRecording = async () => {
  if (isRecording.value) return

  // 确保连接
  const connected = await ensureConnection()
  if (!connected) return

  try {
    isRecording.value = true
    await wsStartRecording()
  } catch (error) {
    isRecording.value = false
    AMessage.error('无法启动录音，请检查麦克风权限')
  }
}

// 停止录音
const stopRecording = async () => {
  if (!isRecording.value) return

  try {
    isRecording.value = false
    await wsStopRecording()
  } catch (error) {
    AMessage.error('停止录音失败')
  }
}

// 清空消息
const clearMessages = () => {
  wsMessages.splice(0, wsMessages.length)
}

// 打开设置抽屉
const openSettings = () => {
  console.log('打开设置抽屉')
  settingsForm.value = {
    url: userStore.wsConfig.url,
    deviceId: userStore.wsConfig.deviceId || 'web_test',
    deviceName: userStore.wsConfig.deviceName || 'Web用户',
    autoConnect: userStore.wsConfig.autoConnect
  }
  settingsVisible.value = true
  console.log('settingsVisible:', settingsVisible.value)
}

// 保存设置
const saveSettings = async () => {
  try {
    // 更新 store
    userStore.updateWsConfig(settingsForm.value)
    
    AMessage.success('配置已保存')
    
    // 如果已连接，断开重连
    if (isConnected.value) {
      disconnect()
      await new Promise(resolve => setTimeout(resolve, 500))
      
      if (settingsForm.value.autoConnect) {
        const success = await connect(wsConfig.value)
        if (success) {
          AMessage.success('已重新连接')
        }
      }
    }
    
    settingsVisible.value = false
  } catch (error) {
    AMessage.error('保存失败: ' + error)
  }
}

// 取消设置
const cancelSettings = () => {
  settingsVisible.value = false
}

// 测试连接
const testConnection = async () => {
  try {
    AMessage.loading({ content: '测试连接中...', key: 'testConn', duration: 0 })
    
    // 临时使用表单中的配置测试
    const testConfig = {
      url: settingsForm.value.url,
      deviceId: settingsForm.value.deviceId,
      deviceName: settingsForm.value.deviceName,
      token: userStore.token
    }
    
    // 断开当前连接
    if (isConnected.value) {
      disconnect()
      await new Promise(resolve => setTimeout(resolve, 300))
    }
    
    // 测试新配置
    const success = await connect(testConfig)
    
    if (success) {
      AMessage.success({ content: '连接测试成功！', key: 'testConn' })
    } else {
      AMessage.error({ content: '连接测试失败', key: 'testConn' })
    }
  } catch (error) {
    AMessage.error({ content: '连接测试失败: ' + error, key: 'testConn' })
  }
}

// 格式化时间
const formatTime = (date: Date) => {
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  
  return date.toLocaleString()
}

// 是否显示时间戳
const showTimestamp = (index: number) => {
  if (index === 0) return true
  const prevMsg = wsMessages[index - 1]
  const currMsg = wsMessages[index]
  if (!prevMsg || !currMsg) return false
  const timeDiff = currMsg.timestamp.getTime() - prevMsg.timestamp.getTime()
  return timeDiff > 5 * 60 * 1000 // 超过5分钟显示时间
}

// 连接状态文本
const connectionStatusText = computed(() => {
  if (isConnected.value) {
    return '在线'
  }
  return connectionStatus.value
})

// 连接状态类型
const connectionStatusDot = computed(() => {
  return isConnected.value ? 'online' : 'offline'
})
</script>

<template>
  <div class="floating-chat">
    <!-- 浮动按钮 -->
    <a-float-button
      :type="chatVisible ? 'default' : 'primary'"
      @click="toggleChat"
      :style="{ right: '24px', bottom: '24px' }"
    >
      <template #icon>
        <MessageOutlined v-if="!chatVisible" />
        <CloseOutlined v-else />
      </template>
    </a-float-button>

    <!-- 聊天窗口 -->
    <transition name="chat-slide">
      <div v-if="chatVisible" class="chat-window">
        <!-- 头部 -->
        <div class="chat-header">
          <div class="header-info">
            <a-avatar :src="aiAvatar" :size="36" />
            <div class="header-text">
              <div class="header-title">AI 助手</div>
              <div class="header-status">
                <span class="status-dot" :class="connectionStatusDot"></span>
                {{ connectionStatusText }}
              </div>
            </div>
          </div>
          <div class="header-actions">
            <a-button
              type="text"
              size="small"
              @click="openSettings"
              title="设置"
            >
              <template #icon>
                <SettingOutlined />
              </template>
            </a-button>
            <a-button
              type="text"
              size="small"
              @click="clearMessages"
              title="清空消息"
            >
              <template #icon>
                <DeleteOutlined />
              </template>
            </a-button>
            <a-button
              type="text"
              size="small"
              @click="closeChat"
            >
              <template #icon>
                <CloseOutlined />
              </template>
            </a-button>
          </div>
        </div>

        <!-- 消息区域 -->
        <div ref="chatContentRef" class="chat-content">
          <div v-if="wsMessages.length === 0" class="empty-chat">
            <a-empty description="暂无对话记录">
              <template #image>
                <MessageOutlined :style="{ fontSize: '48px', color: '#d9d9d9' }" />
              </template>
            </a-empty>
          </div>
          <div v-else class="chat-messages">
            <div v-for="(message, index) in wsMessages" :key="message.id">
              <!-- 时间戳 -->
              <div v-if="showTimestamp(index)" class="message-timestamp">
                {{ formatTime(message.timestamp) }}
              </div>

              <!-- 消息内容 -->
              <div class="message-wrapper" :class="{ 'user-message': message.isUser, 'ai-message': !message.isUser }">
                <!-- 头像 -->
                <div class="message-avatar">
                  <a-avatar :src="message.isUser ? userAvatar : aiAvatar" :size="32" />
                </div>

                <!-- 消息气泡 -->
                <div class="message-content">
                  <div class="message-bubble">
                    <div class="message-text">{{ message.content }}</div>
                  </div>
                  <div v-if="message.isLoading" class="loading-indicator">
                    <a-spin size="small" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="chat-input">
          <div class="input-wrapper">
            <!-- 模式切换按钮 -->
            <a-button
              type="text"
              class="mode-toggle"
              :class="{ active: isVoiceMode }"
              @click="toggleInputMode"
            >
              <template #icon>
                <AudioOutlined v-if="isVoiceMode" />
                <MessageOutlined v-else />
              </template>
            </a-button>

            <!-- 文本输入 -->
            <a-textarea
              v-if="!isVoiceMode"
              v-model:value="inputMessage"
              placeholder="输入消息..."
              :auto-size="{ minRows: 1, maxRows: 3 }"
              :bordered="false"
              @keypress.enter="handleEnterKey"
            />

            <!-- 语音输入按钮 -->
            <a-button
              v-else
              class="record-button"
              :class="{ recording: isRecording }"
              type="primary"
              @mousedown="startRecording"
              @mouseup="stopRecording"
              @mouseleave="isRecording && stopRecording()"
              @touchstart="startRecording"
              @touchend="stopRecording"
            >
              {{ isRecording ? '松开结束' : '按住说话' }}
            </a-button>

            <!-- 发送按钮 -->
            <a-button
              v-if="!isVoiceMode"
              type="primary"
              class="send-button"
              :disabled="!inputMessage.trim()"
              @click="sendTextMessage"
            >
              <template #icon>
                <SendOutlined />
              </template>
            </a-button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 设置抽屉 -->
    <a-drawer
      v-model:open="settingsVisible"
      title="聊天设置"
      placement="right"
      :width="360"
      :z-index="2000"
    >
      <a-form
        :model="settingsForm"
        :rules="rules"
        layout="vertical"
      >
        <!-- WebSocket 地址 -->
        <a-form-item label="服务器地址" name="url">
          <a-input
            v-model:value="settingsForm.url"
            placeholder="ws://127.0.0.1:8091/ws/xiaozhi/v1/"
          />
          <template #extra>
            <div class="form-tip">
              WebSocket 服务器地址
            </div>
          </template>
        </a-form-item>

        <!-- 设备 ID -->
        <a-form-item label="设备 ID" name="deviceId">
          <a-input
            v-model:value="settingsForm.deviceId"
            placeholder="web_test"
          />
          <template #extra>
            <div class="form-tip">
              设备的唯一标识符
            </div>
          </template>
        </a-form-item>

        <!-- 设备名称 -->
        <a-form-item label="设备名称">
          <a-input
            v-model:value="settingsForm.deviceName"
            placeholder="Web用户"
          />
          <template #extra>
            <div class="form-tip">
              用于显示的设备名称
            </div>
          </template>
        </a-form-item>

        <!-- 自动连接 -->
        <a-form-item label="自动连接">
          <a-switch v-model:checked="settingsForm.autoConnect">
            <template #checkedChildren>开启</template>
            <template #unCheckedChildren>关闭</template>
          </a-switch>
          <div class="form-tip" style="margin-top: 8px;">
            打开聊天窗口时自动连接
          </div>
        </a-form-item>

        <!-- 连接状态 -->
        <a-form-item label="当前状态">
          <a-tag :color="isConnected ? 'success' : 'default'">
            {{ isConnected ? '已连接' : '未连接' }}
          </a-tag>
        </a-form-item>
      </a-form>

      <template #footer>
        <div class="drawer-footer">
          <a-space>
            <a-button @click="cancelSettings">
              取消
            </a-button>
            <a-button type="dashed" @click="testConnection">
              测试连接
            </a-button>
            <a-button type="primary" @click="saveSettings">
              保存
            </a-button>
          </a-space>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped lang="scss">
.floating-chat {
  position: fixed;
  z-index: 1000;
}

// 聊天窗口动画
.chat-slide-enter-active,
.chat-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.chat-slide-enter-from,
.chat-slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

// 聊天窗口
.chat-window {
  position: fixed;
  right: 24px;
  bottom: 88px;
  width: 380px;
  height: 600px;
  background: var(--card-bg);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

// 头部
.chat-header {
  padding: 16px;
  background: var(--ant-color-primary);
  color: var(--ant-color-text-inverse);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-text {
  .header-title {
    font-size: 16px;
    font-weight: 600;
    line-height: 1.4;
  }

  .header-status {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    opacity: 0.9;
    margin-top: 2px;

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      display: inline-block;

      &.online {
        background: #52c41a;
        animation: pulse-dot 2s infinite;
      }

      &.offline {
        background: #d9d9d9;
      }
    }
  }
}

@keyframes pulse-dot {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;

  :deep(.ant-btn) {
    color: var(--ant-color-text-inverse);
    opacity: 0.85;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      color: var(--ant-color-text-inverse);
      opacity: 1;
      background: var(--ant-color-primary-hover);
    }

    .anticon {
      font-size: 16px;
    }
  }
}

// 消息区域
.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--ant-color-bg-layout);
  scroll-behavior: smooth;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 3px;

    &:hover {
      background: rgba(0, 0, 0, 0.2);
    }
  }
}

.empty-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 300px;
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-timestamp {
  text-align: center;
  margin: 16px 0;
  color: var(--ant-color-text-secondary);
  font-size: 12px;
  position: relative;

  &::before,
  &::after {
    content: '';
    position: absolute;
    top: 50%;
    width: 60px;
    height: 1px;
    background: var(--ant-color-border);
  }

  &::before {
    right: calc(50% + 70px);
  }

  &::after {
    left: calc(50% + 70px);
  }
}

.message-wrapper {
  display: flex;
  gap: 8px;
  align-items: flex-end;

  &.user-message {
    flex-direction: row-reverse;
  }
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  max-width: 70%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 16px;
  word-break: break-word;
  line-height: 1.5;
  font-size: 14px;
  transition: all 0.2s;
}

.user-message .message-bubble {
  background: linear-gradient(135deg, var(--ant-color-primary) 0%, var(--ant-color-primary-hover) 100%);
  color: var(--ant-color-text-inverse);
  border-bottom-right-radius: 4px;
}

.ai-message .message-bubble {
  background: var(--ant-color-bg-container);
  color: var(--ant-color-text);
  border-bottom-left-radius: 4px;
  border: 1px solid var(--ant-color-border);
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.loading-indicator {
  align-self: flex-start;
}

.user-message .loading-indicator {
  align-self: flex-end;
}

// 输入区域
.chat-input {
  padding: 16px;
  background: var(--card-bg);
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: var(--bg-secondary);
  border-radius: 10px;
  border: 1px solid var(--border-color);
  transition: all 0.3s;

  &:focus-within {
    border-color: var(--ant-color-primary);
    box-shadow: 0 0 0 2px var(--ant-color-primary-bg);
  }
}

.mode-toggle {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ant-color-text-secondary);

  &.active {
    color: var(--ant-color-primary);
    background: var(--ant-color-primary-bg);
  }

  &:hover {
    background: var(--ant-color-fill-quaternary);
  }
}

:deep(.ant-input) {
  flex: 1;
  border: none;
  background: transparent;
  padding: 6px 8px;
  font-size: 14px;
  resize: none;

  &:focus {
    box-shadow: none;
  }

  &::placeholder {
    color: var(--ant-color-text-placeholder);
  }
}

.send-button {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  transform: scale(0.8);
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &:disabled {
    background: var(--bg-disabled);
    border-color: var(--border-color);
  }
}

.record-button {
  flex: 1;
  height: 40px;
  border-radius: 20px;
  font-weight: 500;

  &.recording {
    background: #ff4d4f;
    border-color: #ff4d4f;
    animation: recording-pulse 1.5s infinite;
  }
}

@keyframes recording-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 77, 79, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(255, 77, 79, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 77, 79, 0);
  }
}

// 响应式
@media (max-width: 768px) {
  .chat-window {
    right: 16px;
    bottom: 80px;
    width: calc(100vw - 32px);
    max-width: 380px;
    height: 500px;
  }
}

@media (max-width: 480px) {
  .chat-window {
    right: 8px;
    bottom: 72px;
    width: calc(100vw - 16px);
    height: calc(100vh - 100px);
  }
}

// 设置抽屉样式
.form-tip {
  color: var(--ant-color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  border-top: 1px solid var(--ant-color-border);
}

:deep(.ant-drawer) {
  .ant-drawer-header {
    border-bottom: 1px solid var(--ant-color-border);
  }

  .ant-drawer-body {
    padding: 24px;
  }

  .ant-drawer-footer {
    padding: 0;
    border-top: none;
  }
}

// 深色模式适配
html.dark,
html[data-theme='dark'] {
  .chat-content {
    &::-webkit-scrollbar-thumb {
      background: var(--ant-color-fill-quaternary);

      &:hover {
        background: var(--ant-color-fill-tertiary);
      }
    }
  }
}
</style>

