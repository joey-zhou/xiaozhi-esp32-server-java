<script setup lang="ts">
import { ref, nextTick, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowUpOutlined,
  UserOutlined,
  PlusOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  DownOutlined,
  CheckOutlined,
  SettingOutlined,
  MenuFoldOutlined,
} from '@ant-design/icons-vue'
import { useSelectLoadMore } from '@/composables/useSelectLoadMore'
import { useChatSession } from '@/composables/useChatSession'
import { queryRoles } from '@/services/role'
import type { Conversation } from '@/types/message'
import type { Role } from '@/types/role'
import RobotAvatar from '@/components/RobotAvatar.vue'
import ThinkingBlock from '@/components/chat/ThinkingBlock.vue'

const { t } = useI18n()

// 会话 / 消息 / 历史
const {
  sessionId,
  sending,
  messages,
  conversations,
  loadingConversations,
  thinkingExpanded,
  loadConversations,
  selectConversation: selectConversationRaw,
  startNewChat,
  sendMessage: sendMessageToSession,
  toggleThinking,
} = useChatSession()

// 角色选择
const {
  list: roles,
  load: loadRoles,
} = useSelectLoadMore<Role>(queryRoles)
const selectedRoleId = ref<number | undefined>(undefined)

// 自动选择默认角色
watch(roles, (newRoles) => {
  if (!selectedRoleId.value && newRoles && newRoles.length > 0) {
    const defaultRole = newRoles.find((r: Role) => String(r.isDefault) === '1' || String(r.isDefault) === 'true')
    if (defaultRole) {
      selectedRoleId.value = defaultRole.roleId
    } else if (newRoles[0]) {
      selectedRoleId.value = newRoles[0].roleId
    }
  }
}, { immediate: true })

// UI 状态：历史记录抽屉
const showHistory = ref(false)

function toggleHistory() {
  showHistory.value = !showHistory.value
  if (showHistory.value && conversations.value.length === 0) {
    loadConversations()
  }
}

function formatTime(timeStr: string) {
  const d = new Date(timeStr)
  return `${d.getMonth() + 1}-${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

// 视图状态：输入框 / 滚动 / 角色弹窗
const inputText = ref('')
const chatContainerRef = ref<HTMLDivElement>()
const textareaRef = ref()
const rolePopoverOpen = ref(false)

const selectedRole = computed(() => roles.value.find((r: Role) => r.roleId === selectedRoleId.value))
const selectedRoleName = computed(() => selectedRole.value?.roleName || '')
const selectedRoleAvatar = computed(() => selectedRole.value?.avatar || '')

// 初始化加载
loadRoles()
loadConversations()

function scrollToBottom() {
  nextTick(() => {
    if (chatContainerRef.value) {
      chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight
    }
  })
}

function focusInput() {
  nextTick(() => {
    const el = textareaRef.value?.$el?.querySelector('textarea') || textareaRef.value?.$el
    el?.focus()
  })
}

async function selectConversation(conv: Conversation) {
  const switched = await selectConversationRaw(conv, scrollToBottom)
  if (switched) {
    selectedRoleId.value = conv.roleId
  }
}

async function handleNewChat() {
  await startNewChat()
  inputText.value = ''
}

function selectRole(role: Role) {
  if (role.roleId !== selectedRoleId.value) {
    selectedRoleId.value = role.roleId
    handleNewChat()
  }
  rolePopoverOpen.value = false
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sending.value || !selectedRoleId.value) return

  // 立即清空输入框并重新聚焦
  inputText.value = ''
  focusInput()

  const { openedNow, success } = await sendMessageToSession(text, selectedRoleId.value, scrollToBottom)
  focusInput()
  // 新开/续接会话发送完一轮后，刷新列表（新会话入列表，续接会话上浮）
  if (success && openedNow) {
    loadConversations()
  }
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}
</script>

<template>
  <a-layout class="chat-layout">
    <!-- 顶部导航栏 -->
    <a-layout-header class="chat-header">
      <a-flex justify="space-between" align="center" :style="{ height: '100%' }">
        <a-popover
          v-model:open="rolePopoverOpen"
          trigger="click"
          placement="bottomLeft"
          :arrow="false"
          :overlay-inner-style="{ padding: 0 }"
        >
          <a-button type="text" class="role-selector">
            <span class="role-selector-text">{{ selectedRoleName || (roles.length > 0 ? t('chat.selectRole') : t('chat.noRole')) }}</span>
            <DownOutlined :style="{ fontSize: '10px', marginLeft: '6px', opacity: 0.5 }" />
          </a-button>
          <template #content>
            <div class="role-panel">
              <div
                v-for="role in roles"
                :key="role.roleId"
                class="role-card"
                :class="{ active: selectedRoleId === role.roleId }"
                @click="selectRole(role)"
              >
                <a-avatar :size="36" :src="role.avatar" :style="{ flexShrink: 0, background: '#722ed1' }">
                  {{ role.roleName?.charAt(0) }}
                </a-avatar>
                <div class="role-card-info">
                  <div class="role-card-name">{{ role.roleName }}</div>
                  <div v-if="role.roleDesc" class="role-card-desc">{{ role.roleDesc }}</div>
                </div>
                <CheckOutlined v-if="selectedRoleId === role.roleId" class="role-card-check" />
              </div>
            </div>
          </template>
        </a-popover>

        <a-space>
          <a-button type="text" @click="handleNewChat">
            <template #icon><PlusOutlined /></template>
            {{ t('chat.newChat') }}
          </a-button>
          <a-divider type="vertical" />
          <a-button type="text" @click="toggleHistory">
            <template #icon><ClockCircleOutlined /></template>
          </a-button>
          <a-divider type="vertical" />
          <a-button type="text">
            <template #icon><SettingOutlined /></template>
          </a-button>
        </a-space>
      </a-flex>
    </a-layout-header>

    <!-- 主体对话区域 -->
    <a-layout-content class="chat-content" :style="{ paddingRight: showHistory ? '320px' : '0' }">
      <!-- 消息列表 -->
      <div class="chat-messages" ref="chatContainerRef">
        <div class="chat-messages-inner">
          <div v-if="messages.length === 0" :style="{ margin: 'auto', textAlign: 'center', color: '#8c8c8c' }">
            <h2 :style="{ marginBottom: '8px', color: '#1f2329' }">{{ t('chat.greeting', { name: selectedRoleName || t('chat.defaultAssistant') }) }}</h2>
            <span>{{ t('chat.emptyHint') }}</span>
          </div>

          <div v-for="msg in messages" :key="msg.id" class="message-row" :class="msg.role">
            <a-avatar v-if="msg.role === 'user'" :size="36" :style="{ background: '#1677ff', flexShrink: 0 }">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <a-avatar v-else-if="selectedRoleAvatar" :size="36" :src="selectedRoleAvatar" :style="{ flexShrink: 0 }" />
            <RobotAvatar v-else :size="36" />
            <div class="message-content" :class="msg.role">
              <a-typography-text type="secondary" :style="{ fontSize: '13px', padding: '0 4px', marginBottom: '4px' }">
                {{ msg.role === 'user' ? t('chat.me') : (selectedRoleName || t('chat.defaultAssistant')) }}
              </a-typography-text>
              <div class="message-bubble" :class="msg.role">
                <div v-if="msg.streaming && !msg.content && !msg.thinking" class="typing-indicator">
                  <span></span><span></span><span></span>
                </div>
                <template v-else>
                  <ThinkingBlock
                    v-if="msg.thinking"
                    :content="msg.thinking"
                    :done="msg.thinkingDone"
                    :expanded="thinkingExpanded[msg.id]"
                    @toggle="toggleThinking(msg.id)"
                  />
                  <template v-if="msg.content">{{ msg.content }}</template>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-wrapper">
        <a-card :bordered="true" class="chat-input-card" :body-style="{ padding: '12px' }">
          <a-textarea
            ref="textareaRef"
            v-model:value="inputText"
            :placeholder="selectedRoleId ? t('chat.inputPlaceholder') : t('chat.connectFirst')"
            :auto-size="{ minRows: 1, maxRows: 8 }"
            :bordered="false"
            @keydown="handleKeyDown"
          />
          <a-flex justify="end" :style="{ paddingTop: '8px' }">
            <a-button
              type="primary"
              :disabled="!selectedRoleId || !inputText.trim() || sending"
              :loading="sending"
              @click="sendMessage"
              shape="circle"
              class="send-btn"
            >
              <template #icon><ArrowUpOutlined /></template>
            </a-button>
          </a-flex>
        </a-card>
        <a-typography-text type="secondary" :style="{ display: 'block', textAlign: 'center', marginTop: '12px', fontSize: '12px' }">
          {{ t('chat.disclaimer') }}
        </a-typography-text>
      </div>

      <!-- 历史记录抽屉（渲染在 chat-content 内） -->
      <a-drawer
        v-model:open="showHistory"
        :title="t('chat.history')"
        placement="right"
        :width="320"
        :mask="false"
        :mask-closable="false"
        :closable="false"
        :get-container="false"
        :content-wrapper-style="{ width: '320px' }"
      >
        <template #extra>
          <a-space>
            <a-button type="text" size="small" @click="showHistory = false">
              <template #icon><MenuFoldOutlined :rotate="180" /></template>
            </a-button>
            <a-divider type="vertical" />
            <a-button type="text" size="small" :title="t('chat.batchDelete')">
              <template #icon><DeleteOutlined /></template>
            </a-button>
          </a-space>
        </template>

        <a-spin :spinning="loadingConversations">
          <a-timeline v-if="conversations.length > 0" class="history-timeline">
            <a-timeline-item
              v-for="conv in conversations"
              :key="conv.sessionId"
              :color="sessionId === conv.sessionId ? '#1677ff' : 'gray'"
            >
              <div
                class="history-item"
                :class="{ active: sessionId === conv.sessionId }"
                @click="selectConversation(conv)"
              >
                <a-typography-paragraph :ellipsis="{ rows: 2 }" :content="conv.title || t('chat.newConversation')" :style="{ marginBottom: '4px' }" />
                <a-flex justify="space-between" class="history-item-meta">
                  <span>{{ conv.roleName }}</span>
                  <span>{{ formatTime(conv.updateTime) }}</span>
                </a-flex>
              </div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else :description="t('chat.noHistory')" />
        </a-spin>
      </a-drawer>
    </a-layout-content>
  </a-layout>
</template>

<style scoped>
.chat-layout {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  overflow: hidden;
}

.chat-header {
  background: #fff;
  padding: 0 24px;
  height: 60px;
  line-height: normal;
  border-bottom: 1px solid #f0f0f0;
}

.role-selector {
  display: inline-flex !important;
  align-items: center !important;
  font-size: 15px;
  font-weight: 500;
  height: 32px;
  padding: 0 10px;
  border-radius: 8px;
  color: #1f2329;
}

.role-selector:hover {
  background: #f5f5f5;
}

.role-selector-text {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-panel {
  width: 320px;
  max-height: 400px;
  overflow-y: auto;
  padding: 6px;
}

.role-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.role-card:hover {
  background: #f5f5f5;
}

.role-card.active {
  background: #e6f4ff;
}

.role-card-info {
  flex: 1;
  min-width: 0;
}

.role-card-name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2329;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-card-desc {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-card-check {
  color: #1677ff;
  font-size: 14px;
  flex-shrink: 0;
}

.chat-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: #f7f8fa;
  overflow: hidden;
  position: relative;
  transition: padding-right 0.3s;
}

/* 消息区域 */
.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px;
  scroll-behavior: smooth;
}

.chat-messages-inner {
  max-width: 880px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

/* 消息气泡（无 AntD 等效组件，保留自定义） */
.message-row {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-content {
  display: flex;
  flex-direction: column;
  max-width: 80%;
}

.message-content.user {
  align-items: flex-end;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.message-bubble.user {
  background: #e6f4ff;
  border-top-right-radius: 4px;
}

.message-bubble.assistant {
  background: #fff;
  border-top-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.02);
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  background: #8c8c8c;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1); }
}

/* 输入区域 */
.chat-input-wrapper {
  max-width: 880px;
  margin: 0 auto;
  width: 100%;
  padding: 16px 0 0 0;
}

.chat-input-card {
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
  transition: all 0.3s;
}

.chat-input-card:focus-within {
  border-color: #1677ff;
  box-shadow: 0 4px 12px rgba(22, 119, 255, 0.08);
}

.send-btn {
  width: 36px;
  height: 36px;
  font-size: 16px;
}

/* 历史记录时间线 */
.history-timeline {
  padding-top: 4px;
}

.history-item {
  cursor: pointer;
  border-radius: 8px;
  padding: 8px 10px;
  transition: background 0.2s;
}

.history-item:hover {
  background: #f7f8fa;
}

.history-item.active {
  background: #f0f7ff;
}

.history-item.active :deep(.ant-typography) {
  color: #1677ff;
}

.history-item-meta {
  font-size: 12px;
  color: #8c8c8c;
}
</style>
