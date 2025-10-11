<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import { useAvatar } from '@/composables/useAvatar'
import { queryDevices } from '@/services/device'
import { queryMessages } from '@/services/message'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
// @ts-ignore
import jsonp from 'jsonp'
import type { Device } from '@/types/device'
import type { Message } from '@/types/message'

const { t } = useI18n()

// 类型定义
interface DailySentence {
  content: string
  note: string
}

interface FormattedMessage {
  id: string
  content: string
  type: 'text'
  isUser: boolean
  timestamp: Date
}

const userStore = useUserStore()
const { getAvatarUrl } = useAvatar()

// 状态
const loading = ref(true)
const userLoading = ref(true)
const sentenceShow = ref(true)
const sentence = ref<DailySentence>({
  content: '每一天都是新的开始',
  note: 'Every day is a new beginning'
})

// 设备列表
const devices = ref<Device[]>([])

// 消息列表
const messages = ref<Message[]>([])
const page = ref(1)
const isLastData = ref(false)

// 表格列定义
const columns = computed(() => [
  {
    title: t('device.deviceName'),
    dataIndex: 'deviceName',
    key: 'deviceName',
    width: 120
  },
  {
    title: t('device.roleName'),
    dataIndex: 'roleName',
    key: 'roleName',
    align: 'center' as const,
    width: 120
  },
  {
    title: t('device.onlineStatus'),
    dataIndex: 'state',
    key: 'state',
    align: 'center' as const,
    width: 100
  },
  {
    title: t('device.lastOnlineTime'),
    dataIndex: 'lastLogin',
    key: 'lastLogin',
    align: 'center' as const,
    width: 180,
    sorter: (a: Device, b: Device) => {
      if (!a.lastLogin || !b.lastLogin) return 0
      return dayjs(a.lastLogin).unix() - dayjs(b.lastLogin).unix()
    }
  }
])

// 计算属性
const timeFix = computed(() => {
  const hour = dayjs().hour()
  if (hour < 9) return t('dashboard.greeting.morning')
  if (hour < 12) return t('dashboard.greeting.forenoon')
  if (hour < 14) return t('dashboard.greeting.noon')
  if (hour < 18) return t('dashboard.greeting.afternoon')
  if (hour < 22) return t('dashboard.greeting.evening')
  return t('dashboard.greeting.night')
})

const welcomeText = computed(() => {
  const arr = [
    t('dashboard.welcome.rest'),
    t('dashboard.welcome.eat'),
    t('dashboard.welcome.game'),
    t('dashboard.welcome.tired')
  ]
  const index = Math.floor(Math.random() * arr.length)
  return arr[index]
})

const userInfo = computed(() => userStore.userInfo)

const userAvatar = computed(() => {
  if (userInfo.value?.avatar) {
    return getAvatarUrl(userInfo.value.avatar)
  }
  return '/user-avatar.png'
})

// 格式化聊天消息
const formattedChatMessages = computed<FormattedMessage[]>(() => {
  return messages.value.map(item => {
    const content = item.sender === 'user' 
      ? `${item.deviceName || '用户'} 于 ${item.createTime} 发送: ${item.message}`
      : `${item.roleName || 'AI'} 于 ${item.createTime} 回复: ${item.message}`

    return {
      id: String(item.messageId),
      content,
      type: 'text' as const,
      isUser: item.sender === 'user',
      timestamp: new Date(item.createTime || new Date().toISOString())
    }
  })
})

// 获取每日一句
const getSentence = () => {
  const day = dayjs().format('YYYY-MM-DD')
  jsonp(`https://sentence.iciba.com/index.php?c=dailysentence&m=getdetail&title=${day}`, {
    param: 'callback'
  }, (err: Error | null, data: { content?: string; note?: string } | null) => {
    if (err) {
      console.log(t('dashboard.getSentenceFailed'))
    } else {
      sentence.value = {
        content: data?.content || sentence.value.content,
        note: data?.note || sentence.value.note
      }
    }
  })
}

// 获取设备列表
const fetchDevices = async () => {
  try {
    userLoading.value = true
    const res = await queryDevices({
      start: 1,
      limit: 10
    })
    if (res.code === 200) {
      devices.value = res.data.list || []
    } else {
      message.error(res.message || '获取设备列表失败')
    }
  } catch (error) {
    message.error('获取设备列表失败')
  } finally {
    userLoading.value = false
  }
}

// 获取消息列表
const fetchMessages = async () => {
  if (isLastData.value) return

  try {
    const res = await queryMessages({
      start: page.value,
      limit: 10
    })

    if (res.code === 200) {
      const messageList = res.data.list || []
      if (messageList.length === 0) {
        message.warning('已到最后一条数据')
        isLastData.value = true
        return
      }

      messages.value = [...messages.value, ...messageList]
      page.value++
    } else {
      message.error(res.message || '获取消息列表失败')
    }
  } catch (error) {
    message.error('获取消息列表失败')
  } finally {
    loading.value = false
  }
}

// 使用 async setup 进行初始化数据加载（配合 Suspense）
await Promise.all([
  getSentence(),
  fetchDevices(),
  fetchMessages()
])
</script>

<template>
  <div class="dashboard-view">
    <!-- 用户信息卡片 -->
    <a-card :bordered="false" class="user-info-card" :loading="loading">
      <div class="user-info-content">
        <a-avatar :src="userAvatar" :size="72" class="user-avatar" />
        <div class="user-greeting">
          <h2>{{ timeFix }}，{{ userInfo?.name || userInfo?.username }}，{{ welcomeText }}</h2>
          <a-tooltip :title="t('dashboard.clickToTranslate')" placement="bottomLeft">
            <p class="daily-sentence" @click="sentenceShow = !sentenceShow">
              {{ sentenceShow ? sentence.content : sentence.note }}
            </p>
          </a-tooltip>
        </div>
        <div class="user-statistics">
          <a-statistic
            :title="t('dashboard.conversationCount')"
            :value="userInfo?.totalMessage || 0"
            class="statistic-item"
          />
          <a-statistic
            :title="t('dashboard.activeDevices')"
            :value="userInfo?.aliveNumber || 0"
            class="statistic-item"
          />
          <a-statistic
            :title="t('dashboard.totalDevices')"
            :value="userInfo?.totalDevice || 0"
            class="statistic-item"
          />
        </div>
      </div>
    </a-card>

    <!-- 内容区域 -->
    <a-row :gutter="[20, 20]" class="content-row">
      <!-- 聊天记录 -->
      <a-col :xl="14" :lg="12" :xs="24">
        <a-card :title="t('menu.message')" :bordered="false" :loading="loading">
          <div class="chat-messages">
            <div v-if="formattedChatMessages.length === 0" class="empty-messages">
              <a-empty :description="t('dashboard.noData')" />
            </div>
            <div v-else class="message-list">
              <div
                v-for="msg in formattedChatMessages"
                :key="msg.id"
                class="message-item"
                :class="{ 'user-message': msg.isUser }"
              >
                <div class="message-content">
                  <div class="message-text">{{ msg.content }}</div>
                  <div class="message-time">
                    {{ dayjs(msg.timestamp).format('YYYY-MM-DD HH:mm:ss') }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </a-card>
      </a-col>

      <!-- 设备列表 -->
      <a-col :xl="10" :lg="12" :xs="24">
        <a-card :title="t('menu.device')" :bordered="false" :loading="userLoading">
          <a-table
            :columns="columns"
            :data-source="devices"
            :pagination="{ pageSize: 5, showSizeChanger: false }"
            :scroll="{ x: 500 }"
            size="small"
            row-key="deviceId"
          />
        </a-card>
      </a-col>
    </a-row>

    <a-back-top />
  </div>
</template>

<style scoped lang="scss">
.dashboard-view {
  padding: 24px;
  max-width: 1600px;
  margin: 0 auto;
}

// 用户信息卡片
.user-info-card {
  margin-bottom: 20px;
  border-radius: 12px;

  :deep(.ant-card-body) {
    padding: 32px;
  }
}

.user-info-content {
  display: flex;
  gap: 24px;
  align-items: center;
}

.user-avatar {
  flex-shrink: 0;
  border: 3px solid var(--primary-color);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.user-greeting {
  flex: 1;
  min-width: 0;

  h2 {
    font-size: 20px;
    font-weight: 600;
    margin: 0 0 12px 0;
    color: var(--text-color);
    line-height: 1.4;
  }

  .daily-sentence {
    font-size: 14px;
    color: var(--text-secondary);
    margin: 0;
    cursor: pointer;
    transition: color 0.3s;
    line-height: 1.6;

    &:hover {
      color: var(--primary-color);
    }
  }
}

.user-statistics {
  display: flex;
  gap: 32px;
  flex-shrink: 0;

  .statistic-item {
    text-align: right;

    :deep(.ant-statistic-title) {
      font-size: 14px;
      color: var(--text-secondary);
    }

    :deep(.ant-statistic-content) {
      font-size: 24px;
      font-weight: 600;
      color: var(--primary-color);
    }
  }
}

// 内容区域
.content-row {
  margin-bottom: 20px;
}

// 聊天消息
.chat-messages {
  min-height: 400px;
  max-height: 600px;
  overflow-y: auto;
  overflow-x: hidden;

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

.empty-messages {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}

.message-item {
  display: flex;
  padding: 12px 16px;
  background: var(--bg-secondary);
  border-radius: 8px;
  border-left: 3px solid var(--primary-color);
  transition: all 0.3s;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    transform: translateX(4px);
  }

  &.user-message {
    border-left-color: #52c41a;
  }
}

.message-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-text {
  font-size: 14px;
  color: var(--text-color);
  line-height: 1.6;
}

.message-time {
  font-size: 12px;
  color: var(--text-secondary);
}

// 响应式
@media (max-width: 768px) {
  .dashboard-view {
    padding: 16px;
  }

  .user-info-card {
    :deep(.ant-card-body) {
      padding: 20px;
    }
  }

  .user-info-content {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .user-greeting {
    h2 {
      font-size: 18px;
    }
  }

  .user-statistics {
    flex-wrap: wrap;
    justify-content: center;
    gap: 24px;
  }

  .chat-messages {
    min-height: 300px;
    max-height: 400px;
  }
}

// 卡片样式统一
:deep(.ant-card) {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  .ant-card-head {
    border-bottom: 1px solid var(--border-color);
    
    .ant-card-head-title {
      font-size: 16px;
      font-weight: 600;
    }
  }
}
</style>
