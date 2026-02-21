<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { message as antMessage, type TablePaginationConfig } from 'ant-design-vue'
import { useTable } from '@/composables/useTable'
import { useExport } from '@/composables/useExport'
import { useSelectLoadMore } from '@/composables/useSelectLoadMore'
import { useLoadingStore } from '@/store/loading'
import { queryRoles } from '@/services/role'
import { queryDevices } from '@/services/device'
import { deleteMessage } from '@/services/message'
import {
  querySummaryMemory,
  queryChatMemory,
  deleteSummaryMemory
} from '@/services/memory'
import AudioPlayer from '@/components/AudioPlayer.vue'
import TableActionButtons from '@/components/TableActionButtons.vue'
import type { Role } from '@/types/role'
import type { Device } from '@/types/device'
import type { SummaryMemory, ChatMemory } from '@/types/memory'
import dayjs, { Dayjs } from 'dayjs'
import { useEventBus } from '@vueuse/core'

const { t } = useI18n()
const route = useRoute()
const loadingStore = useLoadingStore()

// 从路由路径推导记忆类型
const memoryType = computed<'chat' | 'summary' | 'long'>(() => {
  if (route.path.endsWith('/summary')) return 'summary'
  if (route.path.endsWith('/long')) return 'long'
  return 'chat'
})

// 获取路由参数
const roleId = computed(() => parseInt(route.query.roleId as string) || 0)
const routeDeviceId = computed(() => route.query.deviceId as string || '')

// 表格和分页
const {
  loading,
  data,
  pagination,
  handleTableChange,
  loadData,
} = useTable<SummaryMemory | ChatMemory>()

// 使用导出 composable
const { exporting, exportToExcel } = useExport()

// 事件总线
const stopAllAudioBus = useEventBus<void>('stop-all-audio')

// 角色下拉（滚动加载）
const {
  list: roles,
  loading: rolesLoading,
  load: loadRoles,
  onPopupScroll: onRolePopupScroll,
} = useSelectLoadMore<Role>(queryRoles)
const selectedRoleId = ref<number>(0)

// 设备下拉（滚动加载）
const {
  list: devices,
  loading: devicesLoading,
  load: loadDevices,
  onPopupScroll: onDevicePopupScroll,
} = useSelectLoadMore<Device>(queryDevices)
const selectedDeviceId = ref<string>('')

// 时间范围
const timeRange = ref<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs().endOf('month')])

// 日期快捷选项
const rangePresets = computed(() => [
  { label: t('message.today'), value: [dayjs().startOf('day'), dayjs().endOf('day')] },
  { label: t('message.thisMonth'), value: [dayjs().startOf('month'), dayjs().endOf('month')] },
])

// 当前选中的设备名称（long 类型后端不返回 deviceName，前端直接取）
const selectedDeviceName = computed(() => {
  if (!selectedDeviceId.value) return ''
  return devices.value.find((d: Device) => d.deviceId === selectedDeviceId.value)?.deviceName || selectedDeviceId.value
})

// 表格列配置
const columns = computed(() => {

  const baseColumns = [
    {
      title: t('message.conversationTime'),
      dataIndex: 'createTime',
      width: 180,
      align: 'center' as const,
    },
    {
      title: t('device.deviceName'),
      dataIndex: 'deviceName',
      width: 120,
      align: 'center' as const,
    },
  ]

  if (memoryType.value === 'summary') {
    return [
      ...baseColumns,
      {
        title: t('memory.summary'),
        dataIndex: 'summary',
        width: 300,
        align: 'center' as const,
      },
      {
        title: t('table.action'),
        dataIndex: 'operation',
        width: 110,
        fixed: 'right' as const,
        align: 'center' as const,
      },
    ]
  } else {
    // chat tab
    return [
      ...baseColumns,
      {
        title: t('message.messageSender'),
        dataIndex: 'sender',
        width: 100,
        align: 'center' as const,
      },
      {
        title: t('message.messageContent'),
        dataIndex: 'message',
        width: 300,
        align: 'center' as const,
      },
      {
        title: t('message.voice'),
        dataIndex: 'audioPath',
        width: 400,
        align: 'center' as const,
      },
      {
        title: t('table.action'),
        dataIndex: 'operation',
        width: 110,
        fixed: 'right' as const,
        align: 'center' as const,
      },
    ]
  }
})

/**
 * 初始化下拉数据并加载表格
 */
async function initSelects() {
  await Promise.all([loadRoles(), loadDevices()])

  const needDefault = memoryType.value === 'summary'

  // 优先使用路由传参，否则 summary/long 自动选第一个
  if (roleId.value) {
    selectedRoleId.value = roleId.value
  } else if (needDefault && roles.value.length > 0) {
    selectedRoleId.value = roles.value[0]!.roleId
  }

  if (routeDeviceId.value && devices.value.find((d: Device) => d.deviceId === routeDeviceId.value)) {
    selectedDeviceId.value = routeDeviceId.value
  } else if (needDefault && devices.value.length > 0) {
    selectedDeviceId.value = devices.value[0]!.deviceId
  }

  await fetchMemoryData()
}

/**
 * 角色筛选函数
 */
function filterRoleOption(input: string, option: any) {
  return option.children?.[0]?.children?.toLowerCase().includes(input.toLowerCase())
}

/**
 * 处理角色切换
 */
async function handleRoleChange(roleIdValue: number) {
  selectedRoleId.value = roleIdValue
  data.value = []
  await fetchMemoryData()
}

/**
 * 获取记忆数据
 */
async function fetchMemoryData() {
  const params: any = {
    start: pagination.current || 1,
    limit: pagination.pageSize || 10,
  }

  // 只有当选择了角色时才添加 roleId
  if (selectedRoleId.value) {
    params.roleId = selectedRoleId.value
  }

  // 只有当选择了设备时才添加 deviceId
  if (selectedDeviceId.value) {
    params.deviceId = selectedDeviceId.value
  }

  try {
    if (memoryType.value === 'chat') {
      await loadData(() => queryChatMemory({
        ...params,
        startTime: timeRange.value[0].format('YYYY-MM-DD HH:mm:ss'),
        endTime: timeRange.value[1].format('YYYY-MM-DD HH:mm:ss'),
      }))
    } else if (memoryType.value === 'summary') {
      await loadData(() => querySummaryMemory(params))
    }
  } catch (error) {
    console.error('加载记忆数据失败:', error)
    antMessage.error(t('common.loadFailed'))
  }
}

/**
 * 处理删除记忆
 */
async function handleDeleteMemory(record: any) {
  loading.value = true
  try {
    let res
    if (memoryType.value === 'summary') {
      // 对于summary，使用id（createTime的毫秒数）删除指定条
      res = await deleteSummaryMemory(selectedRoleId.value, selectedDeviceId.value, record.id)
    }

    if (res?.code === 200) {
      antMessage.success(t('common.deleteSuccess'))
      await fetchMemoryData()
    } else {
      antMessage.error(res?.message || t('common.deleteFailed'))
    }
  } catch (error) {
    console.error('删除记忆失败:', error)
    antMessage.error(t('common.deleteFailed'))
  } finally {
    loading.value = false
  }
}

/**
 * 处理设备切换
 */
async function handleDeviceChange(deviceId: string) {
  selectedDeviceId.value = deviceId
  await fetchMemoryData()
}

/**
 * 处理分页变化
 */
const onTableChange = (pag: TablePaginationConfig) => {
  handleTableChange(pag)
  fetchMemoryData()
}

/**
 * 获取发送方显示文本
 */
function getSenderText(sender: string) {
  return sender === 'user' ? t('message.user') : t('message.assistant')
}

/**
 * 检查音频路径是否有效
 */
function hasValidAudio(audioPath: string | undefined | null): boolean {
  if (!audioPath || !audioPath.trim()) return false
  return true
}

/**
 * 删除聊天消息
 */
async function handleDeleteMessage(record: any) {
  loading.value = true
  try {
    const res = await deleteMessage(record.messageId)
    if (res.code === 200) {
      antMessage.success(t('common.deleteSuccess'))
      await fetchMemoryData()
    }
  } catch (error) {
    console.error('删除消息失败:', error)
    antMessage.error(t('common.deleteFailed'))
  } finally {
    loading.value = false
  }
}

/**
 * 导出当前数据
 */
async function handleExport() {
  if (!data.value || data.value.length === 0) {
    antMessage.warning(t('export.noData'))
    return
  }

  loadingStore.showLoading(t('common.exporting'))
  try {
    let columns: any[] = []
    let filename = ''

    if (memoryType.value === 'chat') {
      filename = `chat_memory_${dayjs().format('YYYY-MM-DD_HH-mm-ss')}`
      columns = [
        { key: 'deviceName', title: t('device.deviceName') },
        {
          key: 'sender',
          title: t('message.messageSender'),
          format: (val: string) => val === 'user' ? t('message.user') : t('message.assistant')
        },
        { key: 'message', title: t('message.messageContent') },
        { key: 'createTime', title: t('message.conversationTime') }
      ]
    } else if (memoryType.value === 'summary') {
      filename = `summary_memory_${dayjs().format('YYYY-MM-DD_HH-mm-ss')}`
      columns = [
        { key: 'deviceName', title: t('device.deviceName') },
        { key: 'summary', title: t('memory.summary') },
        { key: 'createTime', title: t('message.conversationTime') }
      ]
    }

    await exportToExcel(data.value, {
      filename,
      showLoading: false,
      columns
    })
    antMessage.success(t('common.exportSuccess'))
  } catch (error) {
    console.error('导出失败:', error)
    antMessage.error(t('common.exportFailed'))
  } finally {
    loadingStore.hideLoading()
  }
}

// 路由离开前停止所有音频
onBeforeRouteLeave(() => {
  stopAllAudioBus.emit()
})

// 组件销毁前停止所有音频
onBeforeUnmount(() => {
  stopAllAudioBus.emit()
})

// 初始化
onMounted(async () => {
  await initSelects()
})
</script>

<template>
  <div class="memory-management-view">
    <!-- 筛选栏 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item :label="t('role.roleName')">
            <a-select
              v-model:value="selectedRoleId"
              show-search
              :filter-option="filterRoleOption"
              :loading="rolesLoading"
              @change="handleRoleChange"
              @popup-scroll="onRolePopupScroll"
            >
              <a-select-option v-if="memoryType === 'chat'" :value="0">
                {{ t('common.all') }}
              </a-select-option>
              <a-select-option
                v-for="role in roles"
                :key="role.roleId"
                :value="role.roleId"
              >
                {{ role.roleName }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item :label="t('device.deviceName')">
            <a-select
              v-model:value="selectedDeviceId"
              :loading="devicesLoading"
              @change="handleDeviceChange"
              @popup-scroll="onDevicePopupScroll"
            >
              <a-select-option v-if="memoryType === 'chat'" value="">
                {{ t('common.all') }}
              </a-select-option>
              <a-select-option
                v-for="device in devices"
                :key="device.deviceId"
                :value="device.deviceId"
              >
                {{ device.deviceName }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-col>

        <a-col v-if="memoryType === 'chat'" :span="8">
          <a-form-item :label="t('message.conversationDate')">
            <a-range-picker
              v-model:value="timeRange"
              :presets="rangePresets"
              :allow-clear="false"
              format="MM-DD"
              @change="fetchMemoryData"
            />
          </a-form-item>
        </a-col>
      </a-row>
    </a-card>

    <!-- 记忆数据表格 -->
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>{{ t(`router.title.${memoryType === 'chat' ? 'shortTermMemory' : 'summaryMemory'}`) }}</span>
        </a-space>
      </template>
      <template #extra>
        <a-button type="primary" @click="handleExport" :loading="exporting">
          {{ t('common.export') }}
        </a-button>
      </template>

      <!-- 短期记忆表格 -->
      <a-table
        v-if="memoryType === 'chat'"
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
          <!-- 发送方列 -->
          <template v-if="column.dataIndex === 'sender'">
            {{ getSenderText(record.sender) }}
          </template>

          <!-- 消息内容列 -->
          <template v-else-if="column.dataIndex === 'message'">
            <a-tooltip :title="record.message" :mouse-enter-delay="0.5" placement="topLeft">
              <span v-if="record.message" class="ellipsis-text">{{ record.message }}</span>
              <span v-else>-</span>
            </a-tooltip>
          </template>

          <!-- 音频列 -->
          <template v-else-if="column.dataIndex === 'audioPath'">
            <div v-if="hasValidAudio(record.audioPath)" class="audio-player-container">
              <AudioPlayer :audio-url="record.audioPath" />
            </div>
            <span v-else>{{ t('message.noAudio') }}</span>
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.dataIndex === 'operation'">
            <TableActionButtons
              :record="record"
              show-delete
              :delete-title="t('message.confirmDeleteMessage')"
              @delete="() => handleDeleteMessage(record)"
            />
          </template>
        </template>
      </a-table>

      <!-- 摘要记忆表格 -->
      <a-table
        v-else-if="memoryType === 'summary'"
        row-key="createTime"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 800 }"
        size="middle"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">

          <!-- 设备名列（后端不返回，直接用当前选中设备名） -->
          <template v-if="column.dataIndex === 'deviceName'">
            {{ selectedDeviceName }}
          </template>
          <!-- 摘要内容列 -->
          <template v-if="column.dataIndex === 'summary'">
            <a-tooltip :title="record.summary" :mouse-enter-delay="0.5" placement="topLeft">
              <span v-if="record.summary" class="ellipsis-text">{{ record.summary }}</span>
              <span v-else>-</span>
            </a-tooltip>
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.dataIndex === 'operation'">
            <TableActionButtons
              :record="record"
              show-delete
              :delete-title="t('common.confirmDelete')"
              @delete="() => handleDeleteMemory(record)"
            />
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 回到顶部 -->
    <a-back-top />
  </div>
</template>

<style scoped lang="scss">
.memory-management-view {
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
