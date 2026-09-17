<script setup lang="ts">
import { ref, nextTick, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  DownOutlined,
  CheckOutlined,
  EditOutlined,
  MoreOutlined,
  MenuFoldOutlined,
} from '@ant-design/icons-vue'
import { useSelectLoadMore } from '@/composables/useSelectLoadMore'
import { useChatSession } from '@/composables/useChatSession'
import { queryRoles } from '@/services/role'
import { formatShortDateTime } from '@/utils/date'
import type { Conversation } from '@/types/chat'
import type { Role } from '@/types/role'
import ChatMessageItem from '@/components/chat/ChatMessageItem.vue'
import ChatComposer from '@/components/chat/ChatComposer.vue'

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
  renameConversation,
  removeConversations,
  startNewChat,
  sendMessage: sendMessageToSession,
  stopGeneration,
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
const HISTORY_WIDTH = 320
const showHistory = ref(true)

const contentPaddingRight = computed(() => (showHistory.value ? `${HISTORY_WIDTH}px` : '0'))

function toggleHistory() {
  showHistory.value = !showHistory.value
  if (showHistory.value && conversations.value.length === 0) {
    loadConversations()
  }
}

// 批量删除：进入选择模式后点会话是勾选，不是切换
const selecting = ref(false)
const selectedSessionIds = ref<string[]>([])

function exitSelecting() {
  selecting.value = false
  selectedSessionIds.value = []
}

function toggleSelecting() {
  if (selecting.value) {
    exitSelecting()
  } else {
    selecting.value = true
  }
}

function toggleSelected(id: string) {
  const index = selectedSessionIds.value.indexOf(id)
  if (index < 0) {
    selectedSessionIds.value.push(id)
  } else {
    selectedSessionIds.value.splice(index, 1)
  }
}

watch(showHistory, (open) => {
  if (!open) exitSelecting()
})

async function deleteSelected() {
  if (selectedSessionIds.value.length === 0) return
  if (await removeConversations([...selectedSessionIds.value])) {
    exitSelecting()
  }
}

function confirmDelete(conv: Conversation) {
  Modal.confirm({
    title: t('chat.deleteConversation'),
    content: t('chat.deleteConversationHint'),
    okText: t('common.delete'),
    okType: 'danger',
    cancelText: t('common.cancel'),
    onOk: () => removeConversations([conv.sessionId]),
  })
}

// 重命名
const renameTarget = ref<Conversation | null>(null)
const renameTitle = ref('')
const renaming = ref(false)

const renameOpen = computed({
  get: () => renameTarget.value !== null,
  set: (open: boolean) => {
    if (!open) renameTarget.value = null
  },
})

function openRename(conv: Conversation) {
  renameTarget.value = conv
  renameTitle.value = conv.title ?? ''
}

async function submitRename() {
  const target = renameTarget.value
  const title = renameTitle.value.trim()
  if (!target || !title || renaming.value) return
  renaming.value = true
  try {
    if (await renameConversation(target, title)) {
      renameTarget.value = null
    }
  } finally {
    renaming.value = false
  }
}

function onConversationAction(conv: Conversation, key: string) {
  if (key === 'rename') {
    openRename(conv)
  } else if (key === 'delete') {
    confirmDelete(conv)
  }
}

// 视图状态：输入框 / 滚动 / 角色弹窗
const inputText = ref('')
const chatContainerRef = ref<HTMLDivElement>()
const composerRef = ref<InstanceType<typeof ChatComposer>>()
const rolePopoverOpen = ref(false)

const selectedRole = computed(() => roles.value.find((r: Role) => r.roleId === selectedRoleId.value))
const selectedRoleName = computed(() => selectedRole.value?.roleName || '')
const selectedRoleAvatar = computed(() => selectedRole.value?.avatar || '')

// 用户向上翻阅历史时不再被流式 token 拽回底部
const stickBottom = ref(true)
let scrollFrame = 0

function onMessagesScroll() {
  const el = chatContainerRef.value
  if (!el) return
  stickBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 40
}

/** 流式追加内容时的贴底滚动：不在底部就不动，同一帧内只滚一次 */
function scrollToBottom() {
  if (!stickBottom.value || scrollFrame) return
  scrollFrame = requestAnimationFrame(() => {
    scrollFrame = 0
    const el = chatContainerRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 发送新消息、载入历史后强制回到底部 */
function forceScrollToBottom(smooth = false) {
  stickBottom.value = true
  nextTick(() => {
    const el = chatContainerRef.value
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  })
}

async function init() {
  try {
    await loadRoles()
  } catch (e) {
    console.error('load roles failed:', e)
  }
  await loadConversations()
}

onMounted(() => {
  void init()
})

onBeforeUnmount(() => {
  if (scrollFrame) cancelAnimationFrame(scrollFrame)
})

function focusInput() {
  nextTick(() => {
    composerRef.value?.focus()
  })
}

async function selectConversation(conv: Conversation) {
  const switched = await selectConversationRaw(conv, () => forceScrollToBottom(true))
  if (switched) {
    selectedRoleId.value = conv.roleId
  }
}

function onHistoryItemClick(conv: Conversation) {
  if (selecting.value) {
    toggleSelected(conv.sessionId)
  } else {
    void selectConversation(conv)
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
  forceScrollToBottom()

  const { openedNow, success } = await sendMessageToSession(text, selectedRoleId.value, scrollToBottom)
  focusInput()
  // 新开/续接会话发送完一轮后，刷新列表（新会话入列表，续接会话上浮）
  if (success && openedNow) {
    loadConversations()
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
              <button
                v-for="role in roles"
                :key="role.roleId"
                type="button"
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
              </button>
            </div>
          </template>
        </a-popover>

        <a-space>
          <a-button type="text" @click="handleNewChat">
            <template #icon><PlusOutlined /></template>
            {{ t('chat.newChat') }}
          </a-button>
          <a-divider type="vertical" />
          <a-button type="text" :title="t('chat.history')" @click="toggleHistory">
            <template #icon><ClockCircleOutlined /></template>
          </a-button>
        </a-space>
      </a-flex>
    </a-layout-header>

    <!-- 主体对话区域 -->
    <div class="chat-content" :style="{ paddingRight: contentPaddingRight }">
      <!-- 消息列表 -->
      <div class="chat-messages" ref="chatContainerRef" @scroll="onMessagesScroll">
        <div class="chat-messages-inner">
          <div v-if="messages.length === 0" :style="{ margin: 'auto', textAlign: 'center', color: 'var(--ant-color-text-tertiary)' }">
            <h2 :style="{ marginBottom: '8px', color: 'var(--ant-color-text)' }">{{ t('chat.greeting', { name: selectedRoleName || t('chat.defaultAssistant') }) }}</h2>
            <span>{{ t('chat.emptyHint') }}</span>
          </div>

          <ChatMessageItem
            v-for="msg in messages"
            :key="msg.id"
            :msg="msg"
            :role-name="selectedRoleName || t('chat.defaultAssistant')"
            :role-avatar="selectedRoleAvatar"
            :thinking-expanded="thinkingExpanded[msg.id]"
            @toggle-thinking="toggleThinking(msg.id)"
          />
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-wrapper">
        <ChatComposer
          ref="composerRef"
          v-model="inputText"
          :disabled="!selectedRoleId"
          :sending="sending"
          :role-name="selectedRoleName"
          @send="sendMessage"
          @stop="stopGeneration"
        />
        <a-typography-text type="secondary" :style="{ display: 'block', textAlign: 'center', marginTop: '12px', fontSize: '12px' }">
          {{ t('chat.disclaimer') }}
        </a-typography-text>
      </div>

      <!-- 历史记录抽屉（渲染在 chat-content 内） -->
      <a-drawer
        v-model:open="showHistory"
        :title="t('chat.history')"
        placement="right"
        :width="HISTORY_WIDTH"
        :mask="false"
        :mask-closable="false"
        :closable="false"
        :get-container="false"
        :content-wrapper-style="{ width: `${HISTORY_WIDTH}px` }"
      >
        <template #extra>
          <a-space>
            <a-button type="text" size="small" @click="showHistory = false">
              <template #icon><MenuFoldOutlined :rotate="180" /></template>
            </a-button>
            <a-divider type="vertical" />
            <a-button
              type="text"
              size="small"
              :title="t('chat.batchDelete')"
              :class="{ 'batch-active': selecting }"
              :disabled="conversations.length === 0"
              @click="toggleSelecting"
            >
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
              <div class="history-row">
                <a-checkbox
                  v-if="selecting"
                  :checked="selectedSessionIds.includes(conv.sessionId)"
                  class="history-check"
                  @change="toggleSelected(conv.sessionId)"
                />
                <button
                  type="button"
                  class="history-item"
                  :class="{ active: !selecting && sessionId === conv.sessionId }"
                  @click="onHistoryItemClick(conv)"
                >
                  <a-typography-paragraph :ellipsis="{ rows: 2 }" :content="conv.title || t('chat.newConversation')" :style="{ marginBottom: '4px' }" />
                  <a-flex justify="space-between" class="history-item-meta">
                    <span>{{ conv.roleName }}</span>
                    <span>{{ formatShortDateTime(conv.updateTime) }}</span>
                  </a-flex>
                </button>
                <a-dropdown v-if="!selecting" :trigger="['click']" placement="bottomRight">
                  <a-button type="text" size="small" class="history-more" :title="t('common.more')">
                    <template #icon><MoreOutlined /></template>
                  </a-button>
                  <template #overlay>
                    <a-menu @click="(info: { key: string | number }) => onConversationAction(conv, String(info.key))">
                      <a-menu-item key="rename">
                        <EditOutlined />
                        {{ t('chat.rename') }}
                      </a-menu-item>
                      <a-menu-item key="delete" danger>
                        <DeleteOutlined />
                        {{ t('common.delete') }}
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else :description="t('chat.noHistory')" />
        </a-spin>

        <template v-if="selecting" #footer>
          <a-flex justify="space-between" align="center">
            <a-button size="small" @click="exitSelecting">{{ t('common.cancel') }}</a-button>
            <a-popconfirm
              :title="t('chat.deleteConversationHint')"
              :disabled="selectedSessionIds.length === 0"
              @confirm="deleteSelected"
            >
              <a-button size="small" type="primary" danger :disabled="selectedSessionIds.length === 0">
                {{ t('chat.deleteSelected', { count: selectedSessionIds.length }) }}
              </a-button>
            </a-popconfirm>
          </a-flex>
        </template>
      </a-drawer>
    </div>

    <a-modal
      v-model:open="renameOpen"
      :title="t('chat.renameConversation')"
      :confirm-loading="renaming"
      :ok-button-props="{ disabled: !renameTitle.trim() }"
      @ok="submitRename"
    >
      <a-input
        v-model:value="renameTitle"
        :maxlength="100"
        :placeholder="t('chat.titlePlaceholder')"
        @press-enter="submitRename"
      />
    </a-modal>
  </a-layout>
</template>

<style scoped>
.chat-layout {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--ant-color-bg-container);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  background: var(--ant-color-bg-container);
  padding: 0 24px;
  height: 60px;
  line-height: normal;
  border-bottom: 1px solid var(--ant-color-border-secondary);
}

.role-selector {
  display: inline-flex !important;
  align-items: center !important;
  font-size: 15px;
  font-weight: 500;
  height: 32px;
  padding: 0 10px;
  border-radius: 8px;
  color: var(--ant-color-text);
}

.role-selector:hover {
  background: var(--ant-color-fill-tertiary);
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

/* 原来是 div，现在是真正的 button，重置原生按钮外观（宽度/字体/边框），视觉保持不变 */
.role-card {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: none;
  border: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s;
}

.role-card:hover {
  background: var(--ant-color-fill-tertiary);
}

.role-card.active {
  background: var(--ant-color-primary-bg);
}

.role-card-info {
  flex: 1;
  min-width: 0;
}

.role-card-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--ant-color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-card-desc {
  font-size: 12px;
  color: var(--ant-color-text-tertiary);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-card-check {
  color: var(--ant-color-primary);
  font-size: 14px;
  flex-shrink: 0;
}

.chat-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: var(--ant-color-fill-quaternary);
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
}

.chat-messages-inner {
  max-width: 880px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

/* 输入区域 */
.chat-input-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 880px;
  margin: 0 auto;
  width: 100%;
  padding: 16px 24px 0;
  box-sizing: border-box;
}

/* 历史记录时间线 */
.history-timeline {
  padding-top: 4px;
}

.history-row {
  display: flex;
  align-items: flex-start;
  gap: 4px;
}

.history-check {
  margin-top: 10px;
}

/* 原来是 div，现在是真正的 button，重置原生按钮外观（宽度/字体/边框/对齐），视觉保持不变 */
.history-item {
  display: block;
  flex: 1;
  min-width: 0;
  background: none;
  border: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
  border-radius: 8px;
  padding: 8px 10px;
  transition: background 0.2s;
}

.history-item:hover {
  background: var(--ant-color-fill-tertiary);
}

.history-item.active {
  background: var(--ant-color-primary-bg);
}

.history-item.active :deep(.ant-typography) {
  color: var(--ant-color-primary);
}

.history-item-meta {
  font-size: 12px;
  color: var(--ant-color-text-tertiary);
}

/* 更多操作只在悬停或键盘聚焦时出现，不挤占标题 */
.history-more {
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}

.history-row:hover .history-more,
.history-more:focus-visible,
.history-more.ant-dropdown-open {
  opacity: 1;
}

.batch-active {
  color: var(--ant-color-primary);
}

@media (max-width: 600px) {
  .chat-header {
    padding: 0 12px;
  }

  .chat-messages {
    padding: 18px 12px;
  }

  .chat-input-wrapper {
    padding: 10px 12px 0;
  }

  .history-more {
    opacity: 1;
  }
}
</style>
