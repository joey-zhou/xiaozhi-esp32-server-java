<!-- 
  TableActionButtons 使用示例
  
  这个文件展示了如何在实际项目中使用 TableActionButtons 组件
-->

<template>
  <div class="example-container">
    <h3>TableActionButtons 使用示例</h3>
    
    <!-- 示例 1: 基础用法 -->
    <h4>1. 基础用法（编辑、查看、删除）</h4>
    <a-table :columns="columns" :data-source="dataSource" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <TableActionButtons
            :record="record"
            show-edit
            show-view
            show-delete
            @edit="handleEdit"
            @view="handleView"
            @delete="handleDelete"
          />
        </template>
      </template>
    </a-table>
    
    <!-- 示例 2: 带下载和复制 -->
    <h4>2. 知识库操作（查看、下载、删除）</h4>
    <a-table :columns="columns" :data-source="dataSource" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <TableActionButtons
            :record="record"
            show-view
            show-download
            show-delete
            @view="handleView"
            @download="handleDownload"
            @delete="handleDelete"
          />
        </template>
      </template>
    </a-table>
    
    <!-- 示例 3: 带设为默认 -->
    <h4>3. 角色/模板操作（编辑、设为默认、删除）</h4>
    <a-table :columns="columns" :data-source="dataSource" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <TableActionButtons
            :record="record"
            show-edit
            show-set-default
            show-delete
            :is-default="record.isDefault === 1"
            @edit="handleEdit"
            @set-default="handleSetDefault"
            @delete="handleDelete"
          />
        </template>
      </template>
    </a-table>
    
    <!-- 示例 4: 带权限控制 -->
    <h4>4. 权限控制（只有管理员可以删除）</h4>
    <a-table :columns="columns" :data-source="dataSource" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <TableActionButtons
            :record="record"
            show-edit
            show-view
            show-delete
            :permissions="{
              edit: true,
              view: true,
              delete: isAdmin
            }"
            @edit="handleEdit"
            @view="handleView"
            @delete="handleDelete"
          />
        </template>
      </template>
    </a-table>
    
    <!-- 示例 5: 带更多操作菜单 -->
    <h4>5. 更多操作（下拉菜单）</h4>
    <a-table :columns="columns" :data-source="dataSource" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <TableActionButtons
            :record="record"
            show-edit
            show-view
            show-delete
            :more-actions="getMoreActions(record)"
            @edit="handleEdit"
            @view="handleView"
            @delete="handleDelete"
            @more="handleMoreAction"
          />
        </template>
      </template>
    </a-table>
    
    <!-- 示例 6: 自定义插槽 -->
    <h4>6. 自定义操作（插槽）</h4>
    <a-table :columns="columns" :data-source="dataSource" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <TableActionButtons
            :record="record"
            show-edit
            show-delete
            @edit="handleEdit"
            @delete="handleDelete"
          >
            <!-- 在删除前添加自定义操作 -->
            <template #actions="{ record }">
              <a @click="handleCustomAction(record)">自定义操作</a>
            </template>
            
            <!-- 在最后添加额外操作 -->
            <template #extra="{ record }">
              <a-button type="link" size="small" @click="handleExtraAction(record)">
                额外操作
              </a-button>
            </template>
          </TableActionButtons>
        </template>
      </template>
    </a-table>
    
    <!-- 示例 7: 使用 useTableActions composable -->
    <h4>7. 配合 useTableActions 使用</h4>
    <a-table :columns="columns" :data-source="dataSource" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <TableActionButtons
            :record="record"
            show-edit
            show-view
            show-delete
            show-download
            @edit="actions.handleEdit"
            @view="actions.handleView"
            @delete="actions.handleDelete"
            @download="actions.handleDownload"
          />
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, ExportOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import TableActionButtons from './TableActionButtons.vue'
import { useTableActions } from '@/composables/useTableActions'
import type { MoreAction } from './TableActionButtons.vue'

// 模拟数据
const dataSource = ref([
  { id: 1, name: '项目1', isDefault: 1 },
  { id: 2, name: '项目2', isDefault: 0 },
  { id: 3, name: '项目3', isDefault: 0 }
])

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '操作', key: 'action', width: 300 }
]

// 权限（示例）
const isAdmin = ref(true)

// 使用 useTableActions
const actions = useTableActions({
  onEdit: (record) => {
    console.log('编辑:', record)
    message.success(`编辑 ${record.name}`)
  },
  onView: (record) => {
    console.log('查看:', record)
    message.info(`查看 ${record.name}`)
  },
  onDelete: async (record) => {
    console.log('删除:', record)
    // 模拟删除API
    await new Promise(resolve => setTimeout(resolve, 500))
    message.success(`删除 ${record.name} 成功`)
    return true
  },
  onDownload: (record) => {
    console.log('下载:', record)
    message.success(`下载 ${record.name}`)
  },
  autoRefresh: true,
  refreshData: async () => {
    console.log('刷新数据...')
  }
})

// 基础操作处理
const handleEdit = (record: any) => {
  message.success(`编辑: ${record.name}`)
}

const handleView = (record: any) => {
  message.info(`查看: ${record.name}`)
}

const handleDelete = (record: any) => {
  message.success(`删除: ${record.name}`)
}

const handleDownload = (record: any) => {
  message.success(`下载: ${record.name}`)
}

const handleSetDefault = (record: any) => {
  message.success(`设为默认: ${record.name}`)
}

const handleCustomAction = (record: any) => {
  message.info(`自定义操作: ${record.name}`)
}

const handleExtraAction = (record: any) => {
  message.info(`额外操作: ${record.name}`)
}

// 更多操作配置
const getMoreActions = (record: any): MoreAction[] => [
  {
    key: 'copy',
    label: '复制',
    icon: CopyOutlined
  },
  {
    key: 'export',
    label: '导出',
    icon: ExportOutlined
  },
  {
    key: 'log',
    label: '查看日志',
    icon: FileTextOutlined,
    disabled: record.isDefault === 1
  }
]

const handleMoreAction = (action: string, record: any) => {
  message.info(`执行操作 "${action}": ${record.name}`)
}
</script>

<style scoped>
.example-container {
  padding: 24px;
}

h4 {
  margin-top: 24px;
  margin-bottom: 16px;
}
</style>

