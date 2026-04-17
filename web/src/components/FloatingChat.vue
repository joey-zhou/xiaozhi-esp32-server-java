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
import type { Role } from '@/types/role'
import RobotAvatar from '@/components/RobotAvatar.vue'

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

// 使用滚动管理 composable
const { containerRef: chatContentRef, scrollToBottom, isAtBottom } = useScroll({
  enableScrollListener: true
})

// 角色列表和当前选中的角色
const roleList = ref<Role[]>([])
const selectedRoleId = ref<number | undefined>()

// 头像
const userAvatar = computed(() => getAvatarUrl(userStore.userInfo?.avatar))

// WebSocket 配置（从 store 获取）
const wsConfig = computed(() => ({
  url: userStore.wsConfig.url,
  // 使用 user_chat_ + userId 作为设备ID，与后端自动创建的虚拟设备ID格式一致
  deviceId: `user_chat_${userStore.userInfo?.userId}`,
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
    console.error('获取角色列表失败:', error)
  }
}

// 切换角色
const handleRoleChange = async (roleId: number) => {
  try {
    // 更新虚拟设备的角色ID
    await updateDevice({
      deviceId: wsConfig.value.deviceId,
      roleId: roleId
    })
    
    AMessage.success('角色切换成功')
    
    // 如果已连接，断开连接（下次发送消息时会自动重连，使用新角色）
    if (isConnected.value) {
      disconnect()
    }
  } catch (error) {
    AMessage.error('角色切换失败')
    console.error('角色切换失败:', error)
  }
}

// 组件挂载时获取角色列表
fetchRoles()

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
    nextTick(() => scrollToBottom())
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
              <div class="header-title">AI 助手</div>
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
              placeholder="选择角色"
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
                <MessageOutlined :style="{ fontSize: '48px', color: 'var(--ant-color-text-quaternary)' }" />
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
                  <!-- 用户头像 -->
                  <a-avatar v-if="message.isUser" :src="userAvatar" :size="32" />
                  <!-- AI头像 - SVG -->
                  <RobotAvatar v-else :size="32" />
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

