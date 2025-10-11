<script setup lang="ts">
import { ref, reactive, onBeforeUnmount, computed } from 'vue'
import { message as antMessage, type TablePaginationConfig } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { useTable } from '@/composables/useTable'
import { useLoadingStore } from '@/store/loading'
import { queryMessages, deleteMessage } from '@/services/message'
import AudioPlayer from '@/components/AudioPlayer.vue'
import type { Message, MessageQueryParams } from '@/types/message'
import dayjs, { Dayjs } from 'dayjs'
import { useEventBus } from '@vueuse/core'
import { useRouter, onBeforeRouteLeave } from 'vue-router'

const { t } = useI18n()

const router = useRouter()

// 表格和分页
const {
  loading,
  data,
  pagination,
  handleTableChange,
  resetPagination,
  loadData,
  createDebouncedSearch
} = useTable<Message>()

// 全局 Loading
const loadingStore = useLoadingStore()

// 查询表单
const queryForm = reactive({
  deviceId: '',
  deviceName: '',
  sender: '',
})

// 时间范围
const timeRange = ref<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs().endOf('month')])

// 查询过滤器配置
const queryFilters = [
  { label: t('device.deviceId'), key: 'deviceId' as const, placeholder: t('device.deviceId') },
  { label: t('device.deviceName'), key: 'deviceName' as const, placeholder: t('device.deviceName') },
]

// 消息发送方选项
const senderOptions = [
  { label: t('common.all'), value: '' },
  { label: t('message.user'), value: 'user' },
  { label: t('message.assistant'), value: 'assistant' },
]

// 日期快捷选项
const rangePresets = computed(() => ({
  [t('message.today')]: [dayjs().startOf('day'), dayjs().endOf('day')],
  [t('message.thisMonth')]: [dayjs().startOf('month'), dayjs().endOf('month')],
}))

// 事件总线
const stopAllAudioBus = useEventBus<void>('stop-all-audio')

// 表格列配置
const columns = computed(() => [
  {
    title: t('device.deviceId'),
    dataIndex: 'deviceId',
    width: 160,
    align: 'center'
  },
  {
    title: t('device.deviceName'),
    dataIndex: 'deviceName',
    width: 100,
    align: 'center'
  },
  {
    title: t('device.roleName'),
    dataIndex: 'roleName',
    width: 100,
    align: 'center'
  },
  {
    title: t('message.messageSender'),
    dataIndex: 'sender',
    width: 100,
    align: 'center',
  },
  {
    title: t('message.messageContent'),
    dataIndex: 'message',
    width: 200,
    align: 'center'
  },
  {
    title: t('message.voice'),
    dataIndex: 'audioPath',
    width: 400,
    align: 'center',
  },
  {
    title: t('message.conversationTime'),
    dataIndex: 'createTime',
    width: 150,
    align: 'center',
  },
  {
    title: t('table.action'),
    dataIndex: 'operation',
    width: 110,
    fixed: 'right',
    align: 'center',
  },
])

// 获取消息数据
async function fetchData() {
  await loadData((params) => {
    const queryParams: MessageQueryParams = {
      start: params.start,
      limit: params.limit,
      startTime: timeRange.value[0].format('YYYY-MM-DD HH:mm:ss'),
      endTime: timeRange.value[1].format('YYYY-MM-DD HH:mm:ss'),
    }

    if (queryForm.deviceId) queryParams.deviceId = queryForm.deviceId
    if (queryForm.deviceName) queryParams.deviceName = queryForm.deviceName
    if (queryForm.sender) queryParams.sender = queryForm.sender

    return queryMessages(queryParams)
  })
}

// 防抖搜索
const debouncedSearch = createDebouncedSearch(fetchData, 500)

// 删除消息
async function handleDeleteMessage(record: Message) {
  loading.value = true
  try {
    const res = await deleteMessage(record.messageId)
    if (res.code === 200) {
      antMessage.success(t('message.prompt.deleteSuccess'))
      fetchData()
    }
  } catch (error) {
    console.error('删除消息失败:', error)
    antMessage.error(t('message.prompt.deleteFailed'))
  } finally {
    loading.value = false
  }
}

/**
 * 检查音频路径是否有效
 */
function hasValidAudio(audioPath: string | undefined | null): boolean {
  if (!audioPath || !audioPath.trim()) return false
  // 可以在这里添加更多的音频路径验证逻辑
  return true
}

/**
 * 处理音频加载错误
 */
function handleAudioLoadError(record: Message) {
  // 标记该记录的音频加载失败
  record.audioLoadError = true
  console.debug('音频文件加载失败:', record.audioPath)
}

// 获取发送方显示文本
function getSenderText(sender: string) {
  return sender === 'user' ? t('message.user') : t('message.assistant')
}

// 导出消息数据
async function handleExport() {
  try {
    loadingStore.showLoading(t('message.prompt.exporting'))
    
    // TODO: 调用实际的导出接口
    await new Promise(resolve => setTimeout(resolve, 2000))
    
    antMessage.success(t('message.prompt.exportSuccess'))
  } catch (error) {
    console.error('导出失败:', error)
    antMessage.error(t('message.prompt.exportFailed'))
  } finally {
    loadingStore.hideLoading()
  }
}

// 处理分页变化
const onTableChange = (pag: TablePaginationConfig) => {
  handleTableChange(pag)
  fetchData()
}

// 路由离开前停止所有音频
onBeforeRouteLeave(() => {
  stopAllAudioBus.emit()
})

// 组件销毁前停止所有音频
onBeforeUnmount(() => {
  stopAllAudioBus.emit()
})

await fetchData()
</script>

<template>
  <div class="message-view">
    <!-- 查询表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col
            v-for="filter in queryFilters"
            :key="filter.key"
            :xxl="6"
            :xl="6"
            :lg="12"
            :md="12"
            :xs="24"
          >
            <a-form-item :label="filter.label">
              <a-input
                v-model:value="queryForm[filter.key]"
                :placeholder="filter.placeholder"
                allow-clear
                @input="debouncedSearch"
              />
            </a-form-item>
          </a-col>

          <a-col :xxl="6" :xl="6" :lg="12" :md="12" :xs="24">
            <a-form-item :label="t('message.messageSender')">
              <a-select v-model:value="queryForm.sender" @change="debouncedSearch">
                <a-select-option
                  v-for="item in senderOptions"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>

          <a-col :xxl="6" :xl="6" :lg="12" :md="12" :xs="24">
            <a-form-item :label="t('message.conversationDate')">
              <a-range-picker
                v-model:value="timeRange"
                :presets="rangePresets"
                format="MM-DD"
                @change="debouncedSearch"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 数据表格 -->
    <a-card :title="t('message.queryTable')" :bordered="false">
      <template #extra>
        <a-button type="primary" @click="handleExport">
          {{ t('common.export') }}
        </a-button>
      </template>
      
      <a-table
        row-key="messageId"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 800 }"
        size="middle"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <!-- 设备编号列 -->
          <template v-if="column.dataIndex === 'deviceId'">
            <a-tooltip :title="record.deviceId" placement="topLeft">
              <span class="ellipsis-text">{{ record.deviceId }}</span>
            </a-tooltip>
          </template>

          <!-- 设备名称列 -->
          <template v-else-if="column.dataIndex === 'deviceName'">
            <a-tooltip :title="record.deviceName" placement="top">
              <span class="ellipsis-text">{{ record.deviceName }}</span>
            </a-tooltip>
          </template>

          <!-- 角色列 -->
          <template v-else-if="column.dataIndex === 'roleName'">
            <a-tooltip
              :title="record.roleDesc || record.roleName"
              :mouse-enter-delay="500"
              placement="top"
            >
              <span v-if="record.roleName" class="ellipsis-text">{{ record.roleName }}</span>
              <span v-else>-</span>
            </a-tooltip>
          </template>

          <!-- 发送方列 -->
          <template v-else-if="column.dataIndex === 'sender'">
            {{ getSenderText(record.sender) }}
          </template>

          <!-- 消息内容列 -->
          <template v-else-if="column.dataIndex === 'message'">
            <a-tooltip :title="record.message" :mouse-enter-delay="500" placement="topLeft">
              <span v-if="record.message" class="ellipsis-text">{{ record.message }}</span>
              <span v-else>-</span>
            </a-tooltip>
          </template>

          <!-- 音频列 -->
          <template v-else-if="column.dataIndex === 'audioPath'">
            <div v-if="hasValidAudio(record.audioPath)" class="audio-player-container">
              <AudioPlayer 
                :audio-url="record.audioPath" 
                @audio-load-error="() => handleAudioLoadError(record)"
              />
            </div>
            <span v-else>{{ t('message.noAudio') }}</span>
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.dataIndex === 'operation'">
            <a-space>
              <a-popconfirm
                :title="t('message.confirmDeleteMessage')"
                :ok-text="t('common.confirm')"
                :cancel-text="t('common.cancel')"
                @confirm="() => handleDeleteMessage(record)"
              >
                <a style="color: #ff4d4f">{{ t('common.delete') }}</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 回到顶部 -->
    <a-back-top />
  </div>
</template>

<style scoped lang="scss">
.message-view {
  padding: 24px;
}

.search-card :deep(.ant-form-item) {
  margin-bottom: 0;
}

.audio-player-container {
  position: relative;
  width: 100%;
  overflow: hidden;
  z-index: 1;
}

// 表格文字省略样式
.ellipsis-text {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

// 表格单元格样式
:deep(.ant-table) {
  .ant-table-tbody > tr > td {
    max-width: 0;
  }
}
</style>


