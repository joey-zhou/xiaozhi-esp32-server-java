<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { DeviceState } from '@/constants/enums'
import { useTable } from '@/composables/useTable'
import { useInlineEdit } from '@/composables/useInlineEdit'
import { useRequest } from '@/composables/useRequest'
import { useMemoryView } from '@/composables/useMemoryView'
import { queryDevices, addDevice, updateDevice, deleteDevice, clearDeviceMemory } from '@/services/device'
import { queryRoles } from '@/services/role'
import DeviceEditDialog from '@/components/DeviceEditDialog.vue'
import TableActionButtons from '@/components/TableActionButtons.vue'
import TableEmptyState from '@/components/TableEmptyState.vue'
import type { Device, DeviceQueryParams } from '@/types/device'
import type { Role } from '@/types/role'
import type { TableColumnsType } from 'ant-design-vue'

const { t } = useI18n()
const { navigateToMemory } = useMemoryView()

// 三个请求各自一个实例：要在模板上显示进度的解构出自己的 loading，其余只取执行函数
const { loading: addingDevice, executeOk: executeAdd } = useRequest()
const { executeOk: executeInlineUpdate } = useRequest()
const { loading: clearingMemory, executeOk: executeClearMemory } = useRequest()
const { execute: executeQueryRoles } = useRequest()
const { executeOk: executeDeleteDevice } = useRequest()
const { executeOk: executeUpdateDevice } = useRequest()

// 表格和分页：分页参数由 useTable 注入，查询条件从 queryForm 现取
const {
  loading,
  data,
  pagination,
  loadError,
  retryLoad,
  fetchData,
  onTableChange,
  debouncedSearch,
} = useTable<Device>((params) => {
  // 重新拉列表时退出行内编辑，避免旧行的编辑框留在新数据上
  editingKey.value = ''

  const queryParams: DeviceQueryParams = {
    pageNo: params.pageNo,
    pageSize: params.pageSize,
  }

  if (queryForm.deviceId) queryParams.deviceId = queryForm.deviceId
  if (queryForm.deviceName) queryParams.deviceName = queryForm.deviceName
  if (queryForm.roleName) queryParams.roleName = queryForm.roleName
  if (queryForm.state !== '') queryParams.state = queryForm.state

  return queryDevices(queryParams)
})


// 查询表单
const queryForm = reactive({
  deviceId: '',
  deviceName: '',
  roleName: '',
  state: '',
})

// 查询过滤器配置：文案跟着 i18n key 走，切换语言后要能刷新，不能是 setup 期一次性求值的普通数组
const queryFilters = computed(() => [
  { label: t('device.deviceId'), key: 'deviceId' as const, placeholder: t('device.deviceId') },
  { label: t('device.deviceName'), key: 'deviceName' as const, placeholder: t('device.deviceName') },
  { label: t('role.roleName'), key: 'roleName' as const, placeholder: t('role.roleName') },
])

// 设备状态选项
const stateOptions = computed(() => [
  { label: t('common.all'), value: '' },
  { label: t('device.onlineStatus'), value: DeviceState.ONLINE },
  { label: t('device.standbyStatus'), value: DeviceState.STANDBY },
  { label: t('device.offlineStatus'), value: DeviceState.OFFLINE },
])

// 角色列表
const roleItems = ref<Role[]>([])

// 使用行内编辑 composable
const {
  editingKey,
  startEdit,
  cancelEdit: cancelEditInline,
  saveEdit,
  updateField
} = useInlineEdit(data, {
  getKey: (item) => item.deviceId,
  // 行内保存与随后的列表刷新共占表格 loading，交给 loadingRef 计数，中间不断
  onSave: async (item) => {
    return await executeInlineUpdate(() => updateDevice(item), {
      loadingRef: loading,
      showSuccess: true,
      successText: t('common.updateSuccess'),
      errorText: t('common.updateFailed'),
      onSuccess: () => fetchData(),
    })
  }
})

// 弹窗相关
const editVisible = ref(false)
const currentDevice = ref<Device | null>(null)

// 添加设备输入框
const addDeviceCode = ref('')

// 表格列配置
const columns = computed<TableColumnsType>(() => [
  {
    title: t('device.deviceId'),
    dataIndex: 'deviceId',
    width: 160,
    fixed: 'left',
    align: 'center'
  },
  {
    title: t('device.deviceName'),
    dataIndex: 'deviceName',
    width: 100,
    align: 'center'
  },
  {
    title: t('role.roleName'),
    dataIndex: 'roleName',
    width: 100,
    align: 'center'
  },
  {
    title: t('device.wifiName'),
    dataIndex: 'wifiName',
    width: 100,
    align: 'center'
  },
  {
    title: t('device.location'),
    dataIndex: 'location',
    width: 180,
    align: 'center'
  },
  {
    title: t('common.status'),
    dataIndex: 'state',
    width: 100,
    align: 'center',
  },
  {
    title: t('device.productType'),
    dataIndex: 'chipModelName',
    width: 100,
    align: 'center'
  },
  {
    title: t('device.deviceType'),
    dataIndex: 'type',
    width: 150,
    align: 'center'
  },
  {
    title: t('device.version'),
    dataIndex: 'version',
    width: 100,
    align: 'center',
  },
  {
    title: t('device.activeTime'),
    dataIndex: 'updateTime',
    width: 180,
    align: 'center',
  },
  {
    title: t('common.createTime'),
    dataIndex: 'createTime',
    width: 180,
    align: 'center',
  },
  {
    title: t('table.action'),
    dataIndex: 'operation',
    width: 250,
    align: 'center',
    fixed: 'right',
  },
])


// 获取角色列表
async function getRoles() {
  // 只是「添加设备」前的可用角色校验，拉不到不打断页面，也不额外弹提示
  const page = await executeQueryRoles(() => queryRoles({}), { showError: false })
  if (page) {
    roleItems.value = page.list
  }
}

/**
 * 添加设备（有明确的输入框+按钮，用 addingDevice 驱动按钮自身的 loading 即可，不用全屏遮罩）
 */
async function handleAddDevice(code: string) {
  if (!code) {
    message.info(t('device.enterDeviceCode'))
    return
  }

  if (roleItems.value.length === 0) {
    message.warning(t('device.configureDefaultRole'))
    return
  }

  // 防抖处理：如果正在添加中，直接返回
  if (addingDevice.value) {
    return
  }

  const added = await executeAdd(() => addDevice(code), {
    showSuccess: true,
    successText: t('common.addSuccess'),
    errorText: t('common.addFailed'),
  })

  if (added) {
    addDeviceCode.value = ''
    await fetchData()
  }
}

/**
 * 删除设备（快速操作，只用 table loading）
 */
async function handleDeleteDevice(record: Device) {
  const removed = await executeDeleteDevice(() => deleteDevice(record.deviceId), {
    loadingRef: loading,
    showSuccess: true,
    successText: t('common.deleteSuccess'),
    errorText: t('common.deleteFailed'),
    networkErrorText: t('common.serverMaintenance'),
  })

  if (removed) {
    await fetchData()
  }
}

/**
 * 更新设备（弹窗编辑后的更新，只用 table loading）
 */
async function handleUpdate(device: Device) {
  const updated = await executeUpdateDevice(() => updateDevice(device), {
    loadingRef: loading,
    showSuccess: true,
    successText: t('common.updateSuccess'),
    errorText: t('common.updateFailed'),
    networkErrorText: t('common.serverMaintenance'),
  })

  if (updated) {
    editVisible.value = false
    await fetchData()
  }
}

/**
 * 清除设备记忆（危险操作，全程用全屏遮罩挡住页面，避免误触其他操作）
 */
async function handleClearMemory(device: Device) {
  const cleared = await executeClearMemory(() => clearDeviceMemory(device.deviceId), {
    showLoading: true,
    loadingText: t('device.clearingMemory'),
    showSuccess: true,
    successText: t('common.deleteSuccess'),
    errorText: t('common.deleteFailed'),
  })

  if (cleared) {
    editVisible.value = false
    await fetchData()
  }
}

/**
 * 在弹窗中编辑
 */
function handleEditWithDialog(record: Device) {
  currentDevice.value = { ...record }
  editVisible.value = true
}

// 直接导出 composable 的方法，无需额外包装
const handleEdit = startEdit
const handleCancel = cancelEditInline
// 行内保存走 composable，保存后由它清掉 editingKey 与行的编辑态
const handleSave = saveEdit

/**
 * 输入编辑 - 使用 composable
 */
function handleInputEdit(value: string, key: string, field: 'deviceName') {
  updateField(key, field, value)
}

/**
 * 角色选择变更 - 使用 composable
 */
function handleRoleChange(value: number, key: string) {
  const role = roleItems.value.find((item) => item.roleId === value)
  
  if (role) {
    updateField(key, 'roleId', value)
    updateField(key, 'roleName', role.roleName)
  }
}

/**
 * 获取角色名称
 */
function getRoleName(roleId?: number) {
  if (!roleId) return ''
  const role = roleItems.value.find((r) => r.roleId === roleId)
  return role ? role.roleName : t('device.unknownRole', { id: roleId })
}

/**
 * 获取角色描述（DeviceResp 不含角色描述，从角色列表取）
 */
function getRoleDesc(roleId?: number) {
  if (!roleId) return ''
  return roleItems.value.find((r) => r.roleId === roleId)?.roleDesc || ''
}

// 初始化（非阻塞式加载）
getRoles()
fetchData()
</script>

<template>
  <div class="device-view">
    <!-- 查询表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col
            v-for="filter in queryFilters"
            :key="filter.key"
            :xl="6"
            :lg="8"
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

          <a-col :xl="6" :lg="8" :md="12" :xs="24">
            <a-form-item :label="t('common.status')">
              <a-select
                v-model:value="queryForm.state"
                :placeholder="t('common.pleaseSelect')"
                @change="debouncedSearch"
              >
                <a-select-option
                  v-for="item in stateOptions"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 数据表格 -->
    <a-card :bordered="false">
      <template #title>
        <span>{{ t('router.title.device') }}</span>
      </template>

      <template #extra>
        <a-input-search
          v-model:value="addDeviceCode"
          v-permission="'system:device:create'"
          :loading="addingDevice"
          :enter-button="t('device.addDevice')"
          :placeholder="t('device.enterDeviceCode')"
          style="width: 300px"
          @search="handleAddDevice"
        />
      </template>

      <a-table
        class="ellipsis-table"
        row-key="deviceId"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1200 }"
        size="middle"
        @change="onTableChange"
      >
        <template #emptyText>
          <TableEmptyState :error="loadError" @retry="retryLoad" />
        </template>

        <template #bodyCell="{ column, record }">
          <!-- 设备编号列 -->
          <template v-if="column.dataIndex === 'deviceId'">
            <a-tooltip :title="record.deviceId" placement="topLeft">
              <span class="ellipsis-text">{{ record.deviceId }}</span>
            </a-tooltip>
          </template>

          <!-- 设备名称列 -->
          <template v-else-if="column.dataIndex === 'deviceName'">
            <div>
              <a-input
                v-if="record.editable"
                :value="record.deviceName"
                style="margin: -5px 0; text-align: center"
                @update:value="(val: string) => handleInputEdit(val, record.deviceId, 'deviceName')"
                @press-enter="() => handleSave(record)"
                @keyup.esc="() => handleCancel(record.deviceId)"
              />
              <span
                v-else-if="editingKey === ''"
              >
                <a-tooltip :title="record.deviceName || t('common.unnamed')" :mouse-enter-delay="0.5">
                  <span v-if="record.deviceName" class="ellipsis-text">{{ record.deviceName }}</span>
                  <span v-else>-</span>
                </a-tooltip>
              </span>
              <span v-else class="ellipsis-text">{{ record.deviceName }}</span>
            </div>
          </template>

          <!-- 角色列 -->
          <template v-else-if="column.dataIndex === 'roleName'">
            <a-select
              v-if="record.editable"
              :value="record.roleId"
              style="margin: -5px 0; width: 100%"
              @change="(val: number) => handleRoleChange(val, record.deviceId)"
            >
              <a-select-option
                v-for="role in roleItems"
                :key="role.roleId"
                :value="role.roleId"
              >
                {{ role.roleName }}
              </a-select-option>
            </a-select>
            <span
              v-else-if="editingKey === ''"
            >
              <a-tooltip
                :title="getRoleDesc(record.roleId) || getRoleName(record.roleId)"
                :mouse-enter-delay="0.5"
                placement="top"
              >
                <span v-if="record.roleId" class="ellipsis-text">{{ getRoleName(record.roleId) }}</span>
                <span v-else>-</span>
              </a-tooltip>
            </span>
            <span v-else class="ellipsis-text">{{ record.roleName }}</span>
          </template>

          <!-- WIFI名称列 -->
          <template v-else-if="column.dataIndex === 'wifiName'">
            <a-tooltip :title="record.wifiName" placement="top">
              <span class="ellipsis-text">{{ record.wifiName || '-' }}</span>
            </a-tooltip>
          </template>

          <!-- 地理位置列 -->
          <template v-else-if="column.dataIndex === 'location'">
            <a-tooltip :title="record.location" placement="top">
              <span class="ellipsis-text">{{ record.location || '-' }}</span>
            </a-tooltip>
          </template>

          <!-- 产品类型列 -->
          <template v-else-if="column.dataIndex === 'chipModelName'">
            <a-tooltip :title="record.chipModelName" placement="top">
              <span class="ellipsis-text">{{ record.chipModelName || '-' }}</span>
            </a-tooltip>
          </template>

          <!-- 设备类型列 -->
          <template v-else-if="column.dataIndex === 'type'">
            <a-tooltip :title="record.type" placement="top">
              <span class="ellipsis-text">{{ record.type || '-' }}</span>
            </a-tooltip>
          </template>

          <!-- 状态列 -->
          <template v-else-if="column.dataIndex === 'state'">
            <a-tag :color="record.state === DeviceState.ONLINE ? 'green' : record.state === DeviceState.STANDBY ? 'blue' : 'red'">
              {{ record.state === DeviceState.ONLINE ? t('device.onlineStatus') : record.state === DeviceState.STANDBY ? t('device.standbyStatus') : t('device.offlineStatus') }}
            </a-tag>
          </template>

          <!-- 时间列 -->
          <template v-else-if="column.dataIndex === 'updateTime' || column.dataIndex === 'createTime'">
            {{ record[column.dataIndex] || '-' }}
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.dataIndex === 'operation'">
            <a-space v-if="record.editable">
              <a-button type="link" size="small" class="table-action-link" @click="() => handleSave(record)">
                {{ t('common.save') }}
              </a-button>
              <a-button type="link" size="small" class="table-action-link" @click="() => handleCancel(record.deviceId)">
                {{ t('common.cancel') }}
              </a-button>
            </a-space>
            <TableActionButtons
              v-else
              :record="record"
              permission-prefix="system:device"
              show-edit
              show-view
              show-delete
              :delete-title="t('device.confirmDelete')"
              @edit="() => handleEdit(record.deviceId)"
              @view="() => handleEditWithDialog(record)"
              @delete="() => handleDeleteDevice(record)"
            >
              <template #actions>
                <a-button
                  v-permission="'system:device:memory'"
                  type="link"
                  size="small"
                  class="table-action-link"
                  @click="() => navigateToMemory({ roleId: record.roleId, deviceId: record.deviceId })"
                >
                  {{ t('role.memory') }}
                </a-button>
              </template>
            </TableActionButtons>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 设备详情弹窗 -->
    <DeviceEditDialog
      :visible="editVisible"
      :current="currentDevice"
      :role-items="roleItems"
      :clear-memory-loading="clearingMemory"
      @close="editVisible = false"
      @submit="handleUpdate"
      @clear-memory="handleClearMemory"
    />

    <!-- 回到顶部 -->
    <a-back-top />
  </div>
</template>

<style scoped lang="scss">
.device-view {
  padding: 24px;
}

// 表格中的下拉框居中
:deep(.ant-select-selection-item) {
  text-align: center;
}
</style>
