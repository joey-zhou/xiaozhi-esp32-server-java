<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { useTable } from '@/composables/useTable'
import { useInlineEdit } from '@/composables/useInlineEdit'
import { useLoadingStore } from '@/store/loading'
import { queryDevices, addDevice, updateDevice, deleteDevice, clearDeviceMemory } from '@/services/device'
import { queryRoles } from '@/services/role'
import DeviceEditDialog from '@/components/DeviceEditDialog.vue'
import TableActionButtons from '@/components/TableActionButtons.vue'
import type { Device, DeviceQueryParams, Role } from '@/types/device'
import type { TablePaginationConfig } from 'ant-design-vue'

const { t } = useI18n()
const loadingStore = useLoadingStore()

// 表格和分页
const {
  loading,
  data,
  pagination,
  handleTableChange,
  loadData,
  createDebouncedSearch
} = useTable<Device>()


// 查询表单
const queryForm = reactive({
  deviceId: '',
  deviceName: '',
  roleName: '',
  state: '',
})

// 查询过滤器配置
const queryFilters = [
  { label: t('device.deviceId'), key: 'deviceId' as const, placeholder: t('device.deviceId') },
  { label: t('device.deviceName'), key: 'deviceName' as const, placeholder: t('device.deviceName') },
  { label: t('role.roleName'), key: 'roleName' as const, placeholder: t('role.roleName') },
]

// 设备状态选项
const stateOptions = [
  { label: t('common.all'), value: '' },
  { label: t('device.onlineStatus'), value: '1' },
  { label: t('device.standbyStatus'), value: '2' },
  { label: t('device.offlineStatus'), value: '0' },
]

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
  onSave: async (item) => {
    loading.value = true
    try {
      const res = await updateDevice(item)
      if (res.code === 200) {
        message.success(t('common.updateSuccess'))
        await fetchData()
        return true
      } else {
        message.error(res.message || t('common.updateFailed'))
        return false
      }
    } catch (error) {
      console.error('更新设备失败:', error)
      message.error(t('common.serverMaintenance'))
      return false
    } finally {
      loading.value = false
    }
  }
})

// 弹窗相关
const editVisible = ref(false)
const currentDevice = ref<Device | null>(null)
const clearMemoryLoading = ref(false)

// 添加设备输入框
const addDeviceCode = ref('')
const addDeviceLoading = ref(false)

// 表格列配置
const columns = computed(() => [
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
    dataIndex: 'lastLogin',
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
    width: 150,
    align: 'center',
    fixed: 'right',
  },
])

// 获取设备数据
async function fetchData() {
  // 重置编辑状态
  editingKey.value = ''
  
  await loadData((params) => {
    const queryParams: DeviceQueryParams = {
      start: params.start,
      limit: params.limit,
    }

    if (queryForm.deviceId) queryParams.deviceId = queryForm.deviceId
    if (queryForm.deviceName) queryParams.deviceName = queryForm.deviceName
    if (queryForm.roleName) queryParams.roleName = queryForm.roleName
    if (queryForm.state !== '') queryParams.state = queryForm.state

    return queryDevices(queryParams)
  })
}

// 防抖搜索
const debouncedSearch = createDebouncedSearch(fetchData, 500)

// 获取角色列表
async function getRoles() {
  try {
    const res = await queryRoles({})
    if (res.code === 200 && res.data) {
      roleItems.value = res.data.list
    }
  } catch (error) {
    console.error('获取角色列表失败:', error)
  }
}

/**
 * 添加设备（保留全局 loading）
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
  if (addDeviceLoading.value) {
    return
  }

  addDeviceLoading.value = true
  loadingStore.showLoading(t('common.adding'))
  try {
    const res = await addDevice(code)
    if (res.code === 200) {
      message.success(t('common.addSuccess'))
      addDeviceCode.value = ''
      await fetchData()
    } else {
      message.error(res.message || t('common.addFailed'))
    }
  } catch (error) {
    console.error('添加设备失败:', error)
    message.error(t('common.serverMaintenance'))
  } finally {
    addDeviceLoading.value = false
    loadingStore.hideLoading()
  }
}

/**
 * 删除设备（快速操作，只用 table loading）
 */
async function handleDeleteDevice(record: Device) {
  loading.value = true
  try {
    const res = await deleteDevice(record.deviceId)
    if (res.code === 200) {
      message.success(t('common.deleteSuccess'))
      await fetchData()
    } else {
      message.error(res.message || t('common.deleteFailed'))
    }
  } catch (error) {
    console.error('删除设备失败:', error)
    message.error(t('common.serverMaintenance'))
  } finally {
    loading.value = false
  }
}

/**
 * 更新设备（弹窗编辑后的更新，只用 table loading）
 */
async function handleUpdate(device: Device) {
  loading.value = true
  try {
    const res = await updateDevice(device)
    if (res.code === 200) {
      message.success(t('common.updateSuccess'))
      editVisible.value = false
      await fetchData()
    } else {
      message.error(res.message || t('common.updateFailed'))
    }
  } catch (error) {
    console.error('更新设备失败:', error)
    message.error(t('common.serverMaintenance'))
  } finally {
    loading.value = false
  }
}

/**
 * 清除设备记忆（保留全局 loading）
 */
async function handleClearMemory(device: Device) {
  clearMemoryLoading.value = true
  loadingStore.showLoading(t('device.clearingMemory'))
  try {
    const res = await clearDeviceMemory(device.deviceId)
    if (res.code === 200) {
      message.success(t('common.deleteSuccess'))
      editVisible.value = false
      await fetchData()
    } else {
      message.error(res.message || t('common.deleteFailed'))
    }
  } catch (error) {
    console.error('清除记忆失败:', error)
    message.error(t('common.serverMaintenance'))
  } finally {
    clearMemoryLoading.value = false
    loadingStore.hideLoading()
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
  return role ? role.roleName : `角色ID:${roleId}`
}

// 处理分页变化
const onTableChange = (pag: TablePaginationConfig) => {
  handleTableChange(pag)
  fetchData()
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
        <span>{{ t('menu.device') }}</span>
      </template>

      <template #extra>
        <a-input-search
          v-model:value="addDeviceCode"
          :loading="addDeviceLoading"
          :enter-button="t('device.addDevice')"
          :placeholder="t('device.enterDeviceCode')"
          style="width: 300px"
          @search="handleAddDevice"
        />
      </template>

      <a-table
        row-key="deviceId"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1200 }"
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
            <div>
              <a-input
                v-if="record.editable"
                :value="record.deviceName"
                style="margin: -5px 0; text-align: center"
                @update:value="(val: string) => handleInputEdit(val, record.deviceId, 'deviceName')"
                @press-enter="() => handleUpdate(record)"
                @keyup.esc="() => handleCancel(record.deviceId)"
              />
              <span
                v-else-if="editingKey === ''"
                style="cursor: pointer"
                @click="() => handleEdit(record.deviceId)"
              >
                <a-tooltip :title="`点击编辑: ${record.deviceName || '未命名'}`" :mouse-enter-delay="0.5">
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
              style="cursor: pointer"
              @click="() => handleEdit(record.deviceId)"
            >
              <a-tooltip
                :title="`点击编辑: ${record.roleDesc || getRoleName(record.roleId)}`"
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
            <a-tag :color="record.state == 1 ? 'green' : record.state == 2 ? 'blue' : 'red'">
              {{ record.state == 1 ? t('device.onlineStatus') : record.state == 2 ? t('device.standbyStatus') : t('device.offlineStatus') }}
            </a-tag>
          </template>

          <!-- 时间列 -->
          <template v-else-if="column.dataIndex === 'lastLogin' || column.dataIndex === 'createTime'">
            {{ record[column.dataIndex] || '-' }}
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.dataIndex === 'operation'">
            <a-space v-if="record.editable">
              <a-popconfirm :title="t('common.confirmSave')" @confirm="() => handleUpdate(record)">
                <a>{{ t('common.save') }}</a>
              </a-popconfirm>
              <a @click="() => handleCancel(record.deviceId)">{{ t('common.cancel') }}</a>
            </a-space>
            <TableActionButtons
              v-else
              :record="record"
              show-edit
              show-view
              show-delete
              :delete-title="t('device.confirmDelete')"
              @edit="() => handleEdit(record.deviceId)"
              @view="() => handleEditWithDialog(record)"
              @delete="() => handleDeleteDevice(record)"
            />
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 设备详情弹窗 -->
    <DeviceEditDialog
      :visible="editVisible"
      :current="currentDevice"
      :role-items="roleItems"
      :clear-memory-loading="clearMemoryLoading"
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

.search-card :deep(.ant-form-item) {
  margin-bottom: 0;
}

// 表格中的下拉框居中
:deep(.ant-select-selection-item) {
  text-align: center;
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


