<script setup lang="ts">
import { reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { message, type TablePaginationConfig } from 'ant-design-vue'
import { useTable } from '@/composables/useTable'
import { useLoadingStore } from '@/store/loading'
import { queryUsers } from '@/services/user'
import { useAvatar } from '@/composables/useAvatar'
import type { User, UserQueryParams } from '@/types/user'

const { t } = useI18n()
const { getAvatarUrl } = useAvatar()

// 表格和分页
const {
  loading,
  data,
  pagination,
  handleTableChange,
  loadData,
  createDebouncedSearch
} = useTable<User>()

// 全局 Loading
const loadingStore = useLoadingStore()

// 查询表单
const queryForm = reactive({
  name: '',
  email: '',
  tel: '',
})

// 查询过滤器配置
const queryFilters = [
  { label: t('common.name'), key: 'name' as const, placeholder: t('common.name') },
  { label: t('user.email'), key: 'email' as const, placeholder: t('user.email') },
  { label: t('user.phone'), key: 'tel' as const, placeholder: t('user.phone') },
]

// 表格列配置
const columns = computed(() => [
  {
    title: t('common.name'),
    dataIndex: 'name',
    width: 100,
    fixed: 'left',
    align: 'center'
  },
  {
    title: t('common.avatar'),
    dataIndex: 'avatar',
    width: 80,
    fixed: 'left',
    align: 'center',
  },
  {
    title: t('user.email'),
    dataIndex: 'email',
    width: 180,
    align: 'center'
  },
  {
    title: t('user.phone'),
    dataIndex: 'tel',
    width: 150,
    align: 'center'
  },
  {
    title: t('user.deviceCount'),
    dataIndex: 'totalDevice',
    width: 100,
    align: 'center',
  },
  {
    title: t('user.onlineDeviceCount'),
    dataIndex: 'aliveNumber',
    width: 100,
    align: 'center',
  },
  {
    title: t('user.messageCount'),
    dataIndex: 'totalMessage',
    width: 120,
    align: 'center',
  },
  {
    title: t('common.status'),
    dataIndex: 'state',
    width: 80,
    align: 'center',
  },
  {
    title: t('user.accountType'),
    dataIndex: 'isAdmin',
    width: 100,
    align: 'center',
  },
  {
    title: t('user.lastLoginTime'),
    dataIndex: 'loginTime',
    width: 180,
    align: 'center',
  },
  {
    title: t('user.lastLoginIp'),
    dataIndex: 'loginIp',
    width: 150,
    align: 'center'
  },
])

// 获取用户数据
async function fetchData() {
  await loadData((params) => {
    const queryParams: UserQueryParams = {
      start: params.start,
      limit: params.limit,
    }
    
    if (queryForm.name) queryParams.name = queryForm.name
    if (queryForm.email) queryParams.email = queryForm.email
    if (queryForm.tel) queryParams.tel = queryForm.tel
    
    return queryUsers(queryParams)
  })
}

// 防抖搜索
const debouncedSearch = createDebouncedSearch(fetchData, 500)

// 导出用户数据
async function handleExport() {
  try {
    loadingStore.showLoading(t('message.prompt.exporting'))
    
    // TODO: 调用实际的导出接口
    await new Promise(resolve => setTimeout(resolve, 2000))
    
    message.success(t('message.prompt.exportSuccess'))
  } catch (error) {
    console.error('导出失败:', error)
    message.error(t('message.prompt.exportFailed'))
  } finally {
    loadingStore.hideLoading()
  }
}

// 获取头像URL
function getAvatar(avatar?: string) {
  return getAvatarUrl(avatar)
}

// 处理分页变化
const onTableChange = (pag: TablePaginationConfig) => {
  handleTableChange(pag)
  fetchData()
}

await fetchData()
</script>

<template>
  <div class="user-view">
    <!-- 查询表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col
            v-for="filter in queryFilters"
            :key="filter.key"
            :xl="8"
            :lg="12"
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
        </a-row>
      </a-form>
    </a-card>

    <!-- 数据表格 -->
    <a-card :title="t('menu.user')" :bordered="false">
      <template #extra>
        <a-button type="primary" @click="handleExport">
          {{ t('common.export') }}
        </a-button>
      </template>
      
      <a-table
        row-key="userId"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1200 }"
        size="middle"
        @change="onTableChange"
      >
        <!-- 头像列 -->
        <template #bodyCell="{ column, record }">
          <!-- 姓名列 -->
          <template v-if="column.dataIndex === 'name'">
            <a-tooltip :title="record.name" placement="top">
              <span class="ellipsis-text">{{ record.name }}</span>
            </a-tooltip>
          </template>

          <!-- 头像列 -->
          <template v-else-if="column.dataIndex === 'avatar'">
            <a-avatar :src="getAvatar(record.avatar)" />
          </template>
          
          <!-- 邮箱列 -->
          <template v-else-if="column.dataIndex === 'email'">
            <a-tooltip :title="record.email" placement="top">
              <span class="ellipsis-text">{{ record.email || '-' }}</span>
            </a-tooltip>
          </template>
          
          <!-- 电话列 -->
          <template v-else-if="column.dataIndex === 'tel'">
            <a-tooltip :title="record.tel" placement="top">
              <span class="ellipsis-text">{{ record.tel || '-' }}</span>
            </a-tooltip>
          </template>
          
          <!-- 登录IP列 -->
          <template v-else-if="column.dataIndex === 'loginIp'">
            <a-tooltip :title="record.loginIp" placement="topRight">
              <span class="ellipsis-text">{{ record.loginIp || '-' }}</span>
            </a-tooltip>
          </template>
          
          <!-- 状态列 -->
          <template v-else-if="column.dataIndex === 'state'">
            <a-tag v-if="record.state == 1" color="green">{{ t('user.normal') }}</a-tag>
            <a-tag v-else color="red">{{ t('user.disabled') }}</a-tag>
          </template>
          
          <!-- 账户类型列 -->
          <template v-else-if="column.dataIndex === 'isAdmin'">
            <a-tag v-if="record.isAdmin == 1" color="blue">{{ t('user.admin') }}</a-tag>
            <a-tag v-else>{{ t('user.normalUser') }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 回到顶部 -->
    <a-back-top />
  </div>
</template>

<style scoped lang="scss">
.user-view {
  padding: 24px;
}

.search-card :deep(.ant-form-item) {
  margin-bottom: 0;
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


