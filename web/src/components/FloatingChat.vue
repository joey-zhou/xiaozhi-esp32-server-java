<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { MessageOutlined, SendOutlined, AudioOutlined, CloseOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message as AMessage } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import { useAvatar } from '@/composables/useAvatar'
import { useWebSocket } from '@/composables/useWebSocket'
import { useScroll } from '@/composables/useScroll'
import { queryRoles } from '@/services/role'
import { updateDevice } from '@/services/device'
import { getRelativeTime } from '@/utils/date'
import type { Role } from '@/types/role'
import RobotAvatar from '@/components/RobotAvatar.vue'

const { t } = useI18n()
const userStore = useUserStore()
const { getAvatarUrl } = useAvatar()

// WebSocket 连接
const {
  isConnected,
  connectionStatus,
  reconnectSeconds,
  messages: wsMessages,
  connect,
  disconnect,
  sendText,
  startRecording: wsStartRecording,
  stopRecording: wsStopRecording,
  clearAllMessages
} = useWebSocket()

// 聊天窗口状态
const chatVisible = ref(false)
const inputMessage = ref('')
const isVoiceMode = ref(false)
const isRecording = ref(false)

// 使用滚动管理 composable
const { containerRef: chatContentRef, scrollToBottom, isAtBottom } = useScroll({
  enableScrollListener: true
})

// 角色列表和当前选中的角色
const roleList = ref<Role[]>([])
const selectedRoleId = ref<number | undefined>()

// 头像
const userAvatar = computed(() => getAvatarUrl(userStore.userInfo?.avatar))

// 虚拟设备ID，与后端自动创建的格式一致；未登录时为空，此时不允许连接与切换角色
const virtualDeviceId = computed(() =>
  userStore.userInfo?.userId ? `user_chat_${userStore.userInfo.userId}` : ''
)

// WebSocket 配置（从 store 获取）
const wsConfig = computed(() => ({
  url: userStore.wsConfig.url,
  deviceId: virtualDeviceId.value,
  token: userStore.token
}))

// 获取角色列表
const fetchRoles = async () => {
  try {
    const res = await queryRoles({})
    if (res.data?.list) {
      roleList.value = res.data.list
      // 设置默认选中的角色（第一个默认角色或第一个角色）
      const defaultRole = roleList.value.find(r => r.isDefault === '1')
      selectedRoleId.value = defaultRole?.roleId || roleList.value[0]?.roleId
    }
  } catch (error) {
    console.error('load roles failed:', error)
  }
}

// 切换角色。虚拟设备要等首条对话消息到达服务端才建好，之前切换必然查不到设备
const handleRoleChange = async (roleId: number) => {
  if (!virtualDeviceId.value) {
    AMessage.warning(t('chat.floating.notLoggedIn'))
    return
  }

  try {
    // 更新虚拟设备的角色ID
    await updateDevice({
      deviceId: virtualDeviceId.value,
      roleId: roleId
    })

    AMessage.success(t('chat.floating.roleChanged'))

    // 如果已连接，断开连接（下次发送消息时会自动重连，使用新角色）
    if (isConnected.value) {
      disconnect()
    }
  } catch (error) {
    AMessage.error(t('chat.floating.roleChangeFailed'))
    console.error('switch role failed:', error)
  }
}

// 组件挂载时获取角色列表
void fetchRoles()

// 监听消息变化并滚动到底部
watch(() => wsMessages.length, () => {
  // 只有当用户在底部时，才自动滚动（避免打断用户查看历史消息）
  if (isAtBottom.value || wsMessages.length === 1) {
    scrollToBottom()
  }
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

// 确保WebSocket连接
const ensureConnection = async (): Promise<boolean> => {
  if (!virtualDeviceId.value) {
    AMessage.error(t('chat.floating.notLoggedIn'))
    return false
  }

  if (!isConnected.value) {
    try {
      const success = await connect(wsConfig.value)
      if (!success) {
        AMessage.error(t('chat.floating.connectFailed'))
        return false
      }
      await new Promise(resolve => setTimeout(resolve, 300))
    } catch (error) {
      AMessage.error(t('chat.floating.connectError', { error: String(error) }))
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
    nextTick(() => scrollToBottom())
  } else {
    AMessage.error(t('chat.floating.sendFailed'))
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
  } catch {
    isRecording.value = false
    AMessage.error(t('chat.floating.micDenied'))
  }
}

// 停止录音
const stopRecording = async () => {
  if (!isRecording.value) return

  try {
    isRecording.value = false
    await wsStopRecording()
  } catch {
    AMessage.error(t('chat.floating.stopRecordFailed'))
  }
}

// 清空消息（同时停掉仍在缓冲/播放的 TTS 音频）
const clearMessages = () => {
  clearAllMessages()
}

// 情绪标签到表情，取值与后端 EmojiUtils.EMOTION_TO_EMOJIS 的首项一致
const EMOTION_EMOJI: Record<string, string> = {
  neutral: '\u{1F610}', happy: '\u{1F60A}', laughing: '\u{1F600}', funny: '\u{1F602}',
  sad: '\u{1F622}', angry: '\u{1F620}', crying: '\u{1F62D}', loving: '\u{1F60D}',
  embarrassed: '\u{1F633}', surprised: '\u{1F62E}', shocked: '\u{1F631}', thinking: '\u{1F914}',
  winking: '\u{1F609}', cool: '\u{1F60E}', relaxed: '\u{1F60C}', delicious: '\u{1F60B}',
  kissy: '\u{1F618}', confident: '\u{1F4AA}', sleepy: '\u{1F634}', silly: '\u{1F61B}',
  confused: '\u{1F615}',
}

const emotionEmoji = (emotion: string) => EMOTION_EMOJI[emotion] ?? ''

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
    return t('chat.floating.online')
  }
  // websocket 层只报状态码，文案在这里翻译；重连倒计时要把秒数插进去
  return t(`chat.floating.status.${connectionStatus.value}`, {
    seconds: reconnectSeconds.value,
  })
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
      :style="{ right: '84px', bottom: '48px' }"
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
            <!-- AI头像 -->
            <RobotAvatar :size="36" fill="#ffffff" background="rgba(255, 255, 255, 0.2)" />
            <div class="header-text">
              <div class="header-title">{{ t('chat.defaultAssistant') }}</div>
              <div class="header-status">
                <span class="status-dot" :class="connectionStatusDot"></span>
                {{ connectionStatusText }}
              </div>
            </div>
          </div>
          <div class="header-actions">
            <!-- 角色切换下拉框 -->
            <a-select
              v-model:value="selectedRoleId"
              :placeholder="t('chat.selectRole')"
              :style="{ width: '120px' }"
              size="small"
              @change="handleRoleChange"
              :dropdown-style="{ zIndex: 2001 }"
            >
              <a-select-option
                v-for="role in roleList"
                :key="role.roleId"
                :value="role.roleId"
              >
                {{ role.roleName }}
              </a-select-option>
            </a-select>
            <a-button
              type="text"
              size="small"
              @click="clearMessages"
              :title="t('chat.floating.clearMessages')"
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
            <a-empty :description="t('chat.floating.emptyMessages')">
              <template #image>
                <MessageOutlined :style="{ fontSize: '48px', color: 'var(--ant-color-text-quaternary)' }" />
              </template>
            </a-empty>
          </div>
          <div v-else class="chat-messages">
            <div v-for="(message, index) in wsMessages" :key="message.id">
              <!-- 时间戳 -->
              <div v-if="showTimestamp(index)" class="message-timestamp">
                {{ getRelativeTime(message.timestamp) }}
              </div>

              <!-- 消息内容 -->
              <div class="message-wrapper" :class="{ 'user-message': message.isUser, 'ai-message': !message.isUser }">
                <!-- 头像 -->
                <div class="message-avatar">
                  <!-- 用户头像 -->
                  <a-avatar v-if="message.isUser" :src="userAvatar" :size="32" />
                  <!-- AI头像 - SVG -->
                  <RobotAvatar v-else :size="32" />
                </div>

                <!-- 消息气泡 -->
                <div class="message-content">
                  <div class="message-bubble">
                    <span v-if="message.emotion" class="message-emotion">{{ emotionEmoji(message.emotion) }}</span>
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
              :placeholder="t('chat.floating.inputPlaceholder')"
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
              {{ isRecording ? t('chat.floating.releaseToSend') : t('chat.floating.holdToTalk') }}
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
  background: var(--ant-color-bg-container);
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
        background: var(--ant-color-success);
        animation: pulse-dot 2s infinite;
      }

      &.offline {
        background: var(--ant-color-text-quaternary);
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
  gap: 8px;

  // 角色选择器样式
  :deep(.ant-select) {
    .ant-select-selector {
      background: rgba(255, 255, 255, 0.2) !important;
      border-color: rgba(255, 255, 255, 0.3) !important;
      color: var(--ant-color-text-inverse) !important;

      &:hover {
        background: rgba(255, 255, 255, 0.3) !important;
        border-color: rgba(255, 255, 255, 0.5) !important;
      }
    }

    .ant-select-selection-item {
      color: var(--ant-color-text-inverse) !important;
    }

    .ant-select-arrow {
      color: var(--ant-color-text-inverse) !important;
    }
  }

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
  background: var(--ant-color-bg-base);
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
  gap: 16px;
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
  align-items: flex-start;

  &.user-message {
    flex-direction: row-reverse;
  }
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  max-width: 75%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 8px;
  word-break: break-word;
  line-height: 1.6;
  font-size: 15px;
  position: relative;
  max-width: 100%;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  
  // 微信风格的小三角
  &::before {
    content: '';
    position: absolute;
    top: 10px;
    width: 0;
    height: 0;
    border-style: solid;
  }
}

.user-message .message-bubble {
  background: #95ec69;
  color: #000;
  
  // 右侧小三角
  &::before {
    right: -8px;
    border-width: 6px 0 6px 8px;
    border-color: transparent transparent transparent #95ec69;
  }
}

.ai-message .message-bubble {
  background: var(--ant-color-bg-container);
  color: var(--ant-color-text);
  
  // 左侧小三角
  &::before {
    left: -7px;
    border-width: 6px 7px 6px 0;
    border-color: transparent var(--ant-color-bg-container) transparent transparent;
  }
}

.message-emotion {
  float: left;
  margin-right: 6px;
  font-size: 16px;
  line-height: 1.6;
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
  background: var(--ant-color-bg-container);
  border-top: 1px solid var(--ant-color-border);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: var(--ant-color-fill-tertiary);
  border-radius: 10px;
  border: 1px solid var(--ant-color-border);
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
    background: var(--ant-color-fill-quaternary);
    border-color: var(--ant-color-border);
  }
}

.record-button {
  flex: 1;
  height: 40px;
  border-radius: 20px;
  font-weight: 500;

  &.recording {
    background: var(--ant-color-error);
    border-color: var(--ant-color-error);
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
</style>

