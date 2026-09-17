<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { message as antMessage, type TableColumnsType } from 'ant-design-vue'
import { useTable, type TablePageParams } from '@/composables/useTable'
import { useExport, type ExportColumn } from '@/composables/useExport'
import { useSelectLoadMore } from '@/composables/useSelectLoadMore'
import { useRequest } from '@/composables/useRequest'
import { useLoadingStore } from '@/store/loading'
import { queryRoles } from '@/services/role'
import { queryDevices } from '@/services/device'
import { deleteMessage } from '@/services/message'
import { shouldIgnoreRequestError } from '@/services/request'
import {
  querySummaryMemory,
  queryChatMemory,
  deleteSummaryMemory,
} from '@/services/memory'
import AudioPlayer from '@/components/AudioPlayer.vue'
import TableActionButtons from '@/components/TableActionButtons.vue'
import TableEmptyState from '@/components/TableEmptyState.vue'
import type { PageResponse } from '@/types/api'
import type { Role } from '@/types/role'
import type { Device } from '@/types/device'
import type { SummaryMemory, ChatMemory } from '@/types/memory'
import dayjs, { Dayjs } from 'dayjs'
import { useEventBus } from '@vueuse/core'

const { t } = useI18n()
const route = useRoute()
const loadingStore = useLoadingStore()

// 从路由路径推导记忆类型
const memoryType = computed<'chat' | 'summary'>(() => {
  if (route.path.endsWith('/summary')) return 'summary'
  return 'chat'
})

// 获取路由参数
const roleId = computed(() => parseInt(route.query.roleId as string) || 0)
const routeDeviceId = computed(() => route.query.deviceId as string || '')

// 表格和分页：三种记忆共用一张表，请求按 memoryType 在 fetchMemoryPage 里分流
const {
  loading,
  data,
  pagination,
  resetPagination,
  loadError,
  retryLoad,
  fetchData: fetchMemoryData,
  onTableChange,
} = useTable<SummaryMemory | ChatMemory>(fetchMemoryPage)

type MemoryRecord = SummaryMemory | ChatMemory

/** 短期记忆行走消息接口，比 ChatMemory 多一个工具调用字段 */
type ChatMemoryRow = ChatMemory & { toolCalls?: string }

// 使用导出 composable
const { exporting, exportToExcel } = useExport()

const { execute: executeRouteDevice } = useRequest()
// 行内删除：每个请求一个实例，失败文案互不干扰
const { executeOk: executeDeleteMemory } = useRequest()
const { executeOk: executeDeleteMessage } = useRequest()

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

// 路由带来的设备未必落在下拉已加载的那几页里，单查回来的那台挂在这里补进选项
const routeDeviceOption = ref<Device | null>(null)

// 补进来的设备一旦随翻页进了 devices，就不再单独占一项，避免出现重复 key
const deviceOptions = computed<Device[]>(() => {
  const extra = routeDeviceOption.value
  const loaded = devices.value
  if (!extra || loaded.some((d) => d.deviceId === extra.deviceId)) {
    return loaded
  }
  return [extra, ...loaded]
})

// 时间范围
const timeRange = ref<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs().endOf('month')])

// 日期快捷选项
const rangePresets = computed(() => [
  { label: t('message.today'), value: [dayjs().startOf('day'), dayjs().endOf('day')] },
  { label: t('message.thisMonth'), value: [dayjs().startOf('month'), dayjs().endOf('month')] },
])

// 当前选中的设备名称
const selectedDeviceName = computed(() => {
  if (!selectedDeviceId.value) return ''
  return deviceOptions.value.find((d) => d.deviceId === selectedDeviceId.value)?.deviceName || selectedDeviceId.value
})

// 表格列配置
const columns = computed<TableColumnsType>(() => {

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
        title: t('message.toolCalls'),
        dataIndex: 'messageType',
        width: 250,
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
        width: 160,
        fixed: 'right' as const,
        align: 'center' as const,
      },
    ]
  }
})

/**
 * 按 deviceId 单查一台设备，补进下拉选项
 * 下拉按页拉取，路由传来的设备可能不在已加载的页里，不补就会被静默换成第一台
 */
async function loadRouteDeviceOption(deviceId: string) {
  if (devices.value.some((d) => d.deviceId === deviceId)) {
    return
  }
  // 补不到就保持不补，不打扰用户
  await executeRouteDevice(() => queryDevices({ pageNo: 1, pageSize: 1, deviceId }), {
    showError: false,
    onSuccess: (data) => {
      routeDeviceOption.value = data?.list?.[0] ?? null
    },
  })
}

/**
 * 初始化下拉数据并加载表格
 */
async function initSelects() {
  await Promise.all([loadRoles(), loadDevices()])

  const needDefault = memoryType.value === 'summary'

  // 优先使用路由传参，否则 summary 自动选第一个
  if (roleId.value) {
    selectedRoleId.value = roleId.value
  } else if (needDefault && roles.value.length > 0) {
    selectedRoleId.value = roles.value[0]!.roleId
  }

  if (routeDeviceId.value) {
    await loadRouteDeviceOption(routeDeviceId.value)
  }

  if (routeDeviceId.value && deviceOptions.value.some((d) => d.deviceId === routeDeviceId.value)) {
    selectedDeviceId.value = routeDeviceId.value
  } else if (needDefault && deviceOptions.value.length > 0) {
    selectedDeviceId.value = deviceOptions.value[0]!.deviceId
  }

  await fetchMemoryData()
}

/**
 * 角色筛选函数（按 a-select-option 上显式声明的 label 匹配）
 */
function filterRoleOption(input: string, option: { label?: string }) {
  return (option.label ?? '').toLowerCase().includes(input.toLowerCase())
}

/**
 * 处理角色切换
 */
async function handleRoleChange(roleIdValue: number) {
  selectedRoleId.value = roleIdValue
  data.value = []
  resetPagination()
  await fetchMemoryData()
}

/**
 * 按当前记忆类型分流请求，分页参数由 useTable 注入
 */
async function fetchMemoryPage(
  { pageNo, pageSize }: TablePageParams
): Promise<PageResponse<MemoryRecord>> {
  // 「全部」用 0 / 空串表示，发给后端必须整个不带该字段：roleId=0 会真的按 0 过滤
  const roleId = selectedRoleId.value || undefined
  const deviceId = selectedDeviceId.value || undefined

  if (memoryType.value === 'chat') {
    return queryChatMemory({
      pageNo,
      pageSize,
      startTime: timeRange.value[0].format('YYYY-MM-DD HH:mm:ss'),
      endTime: timeRange.value[1].format('YYYY-MM-DD HH:mm:ss'),
      // queryChatMemory 把 roleId/deviceId 声明成必填，实际「全部」时要留空，
      // 待 services/memory.ts 把这两项放宽为可选后可去掉这次断言
      ...({ roleId, deviceId } as { roleId: number; deviceId: string }),
    })
  }

  // 摘要记忆按「设备+角色」存储，缺任一项会请求到 undefined/undefined，直接返回空页并提示
  if (!roleId || !deviceId) {
    antMessage.warning(t('memory.needRoleAndDevice'))
    return { code: 200, message: '', data: { list: [], total: 0, pageNo, pageSize } }
  }

  return querySummaryMemory({ pageNo, pageSize, roleId, deviceId })
}

/**
 * 处理删除记忆
 */
// 摘要的 id 是 createTime 毫秒数
async function handleDeleteMemory(record: { id: number }) {
  // summary 用 id（createTime 的毫秒数）删指定条；其余类型没有删除接口
  const requestFn = memoryType.value === 'summary'
    ? () => deleteSummaryMemory(selectedRoleId.value, selectedDeviceId.value, record.id)
    : null

  if (!requestFn) {
    antMessage.error(t('common.deleteFailed'))
    return
  }

  const removed = await executeDeleteMemory(requestFn, {
    loadingRef: loading,
    showSuccess: true,
    successText: t('common.deleteSuccess'),
    errorText: t('common.deleteFailed'),
  })

  if (removed) {
    await fetchMemoryData()
  }
}

/**
 * 处理设备切换
 */
async function handleDeviceChange(deviceId: string) {
  selectedDeviceId.value = deviceId
  resetPagination()
  await fetchMemoryData()
}

/**
 * 处理时间范围切换
 */
async function handleTimeRangeChange() {
  resetPagination()
  await fetchMemoryData()
}

/**
 * 获取发送方显示文本
 */
function getSenderText(sender: string) {
  if (sender === 'user') return t('message.user')
  // 工具回执的行 sender 是 tool，混进「助手」会让人以为是模型自己说的
  if (sender === 'tool') return t('message.tool')
  return t('message.assistant')
}

/** 展开行与工具列统一按这三个字段渲染 */
interface ToolCall {
  name: string
  arguments: string
  result: string
}

/**
 * 后端 toolCalls 有两种负载：
 * TOOL_CALL 存 {id, name, arguments}，TOOL_RESPONSE 存 {toolCallId, toolName}、结果文本在 message 列。
 * 只认第一种的话，回执行的工具名与结果都是空的。
 */
interface RawToolCall {
  name?: string
  toolName?: string
  arguments?: string
  result?: string
}

/** 展开行是一张 a-table，_key 只用来当 row-key */
type ToolCallRow = ToolCall & { _key: number }

/**
 * 解析 toolCalls JSON 字符串为数组
 */
function parseToolCalls(toolCalls: string | undefined | null): RawToolCall[] {
  if (!toolCalls) return []
  try {
    const parsed = JSON.parse(toolCalls)
    return Array.isArray(parsed) ? parsed : [parsed]
  } catch {
    return []
  }
}

/** 把两种负载拍平成统一形状；回执行的结果文本取自本行的 message */
function normalizeToolCalls(record: ChatMemoryRow): ToolCall[] {
  return parseToolCalls(record.toolCalls).map((raw) => ({
    name: raw.name || raw.toolName || '',
    arguments: raw.arguments || '',
    result: raw.result || (record.messageType === 'TOOL_RESPONSE' ? record.message || '' : ''),
  }))
}

const EMPTY_TOOL_CALLS: ToolCallRow[] = []

// 同一行的 toolCalls 在展开行、tooltip、列内文本三处都要用，
// 按 messageId 解析一次收进 Map，模板里直接取，避免每次渲染重复 JSON.parse
const toolCallsByMessage = computed(() => {
  const rows = new Map<number, ToolCallRow[]>()
  if (memoryType.value !== 'chat') return rows

  for (const record of data.value as ChatMemoryRow[]) {
    if (!record.toolCalls) continue
    rows.set(
      record.messageId,
      normalizeToolCalls(record).map((tool, index) => ({ ...tool, _key: index })),
    )
  }
  return rows
})

function toolCallsOf(messageId: number): ToolCallRow[] {
  return toolCallsByMessage.value.get(messageId) ?? EMPTY_TOOL_CALLS
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
async function handleDeleteMessage(record: { messageId: number }) {
  const removed = await executeDeleteMessage(() => deleteMessage(record.messageId), {
    loadingRef: loading,
    showSuccess: true,
    successText: t('common.deleteSuccess'),
    errorText: t('common.deleteFailed'),
  })

  if (removed) {
    await fetchMemoryData()
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
    let columns: ExportColumn<MemoryRecord>[] = []
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

// 初始化。失败必须在这里收住：async onMounted 抛出会冒泡到 ErrorBoundary，整个业务区被替换成错误页
onMounted(async () => {
  try {
    await initSelects()
  } catch (error) {
    if (shouldIgnoreRequestError(error)) return
    console.error('初始化记忆管理页失败:', error)
    antMessage.error(t('common.loadDataFailed'))
  }
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
                :label="role.roleName"
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
                v-for="device in deviceOptions"
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
              @change="handleTimeRangeChange"
            />
          </a-form-item>
        </a-col>
      </a-row>
    </a-card>

    <!-- 记忆数据表格：class 挂在卡片上，内部几张 a-table（含展开行里的工具调用表）共用同一份省略号截断规则 -->
    <a-card :bordered="false" class="ellipsis-table">
      <template #title>
        <a-space>
          <span>{{ t(`router.title.${memoryType === 'chat' ? 'shortTermMemory' : 'summaryMemory'}`) }}</span>
        </a-space>
      </template>
      <template #extra>
        <a-button v-permission="'system:role:memory:export'" type="primary" @click="handleExport" :loading="exporting">
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
        :expandable="{
          rowExpandable: (record: ChatMemoryRow) => !!record.toolCalls,
        }"
        @change="onTableChange"
      >
        <template #emptyText>
          <TableEmptyState :error="loadError" @retry="retryLoad" />
        </template>

        <template #expandedRowRender="{ record }">
          <a-table
            :columns="[
              { title: t('message.toolName'), dataIndex: 'name', width: 300 },
              { title: t('message.toolArguments'), dataIndex: 'arguments', width: 300 },
              { title: t('message.toolResult'), dataIndex: 'result' },
            ]"
            :data-source="toolCallsOf(record.messageId)"
            :pagination="false"
            size="small"
            :row-key="(r: { _key: number }) => r._key"
          >
            <template #bodyCell="{ column, record: tool }">
              <template v-if="column.dataIndex === 'arguments'">
                <pre class="tool-json">{{ tool.arguments }}</pre>
              </template>
              <template v-else-if="column.dataIndex === 'result'">
                <pre class="tool-json">{{ tool.result }}</pre>
              </template>
            </template>
          </a-table>
        </template>

        <template #bodyCell="{ column, record }">
          <!-- 发送方列 -->
          <template v-if="column.dataIndex === 'sender'">
            {{ getSenderText(record.sender) }}
          </template>

          <!-- 消息类型列 -->
          <template v-else-if="column.dataIndex === 'messageType'">
            <template v-if="!!record.toolCalls">
              <a-tooltip placement="topLeft" :mouse-enter-delay="0.5" :overlay-style="{ maxWidth: '400px' }">
                <template #title>
                  <div v-for="tool in toolCallsOf(record.messageId)" :key="tool._key">{{ tool.name }}</div>
                </template>
                <div v-for="tool in toolCallsOf(record.messageId)" :key="tool._key" class="ellipsis-text">{{ tool.name }}</div>
              </a-tooltip>
            </template>
            <span v-else>-</span>
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
            <a-space>
              <TableActionButtons
                :record="record"
                :permissions="{ delete: 'system:role:memory:chat:delete' }"
                :show-delete="record.state !== '0'"
                :delete-title="t('message.confirmDeleteMessage')"
                @delete="() => handleDeleteMessage(record)"
              />
            </a-space>
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
        <template #emptyText>
          <TableEmptyState :error="loadError" @retry="retryLoad" />
        </template>

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
              :permissions="{ delete: 'system:role:memory:summary:delete' }"
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

.audio-player-container {
  position: relative;
  width: 100%;
  overflow: hidden;
  z-index: 1;
}

// 工具调用 JSON 展示
.tool-json {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.5;
  max-height: 200px;
  overflow-y: auto;
}
</style>
