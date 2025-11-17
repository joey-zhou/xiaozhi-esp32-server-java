<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { TableColumnsType, FormInstance, TablePaginationConfig } from 'ant-design-vue'
import { SettingOutlined } from '@ant-design/icons-vue'
import { useTable } from '@/composables/useTable'
import { useModal } from '@/composables/useModal'
import { useConfigManager } from '@/composables/useConfigManager'
import { useLoadingStore } from '@/store/loading'
import TableActionButtons from '@/components/TableActionButtons.vue'
import { updatePlatformConfig, queryPlatformConfig, addPlatformConfig } from '@/services/config'
import type { Agent, PlatformConfig, ProviderOption, PlatformFormItems } from '@/types/agent'
import { queryAgents } from '@/services/agent'

const { t } = useI18n()
const loadingStore = useLoadingStore()

// ==================== 配置管理 ====================
const { setAsDefault } = useConfigManager('llm')

// ==================== 查询表单 ====================
const searchForm = ref({
  provider: 'coze' as string,
  agentName: ''
})

// 平台选项
const providerOptions = computed<ProviderOption[]>(() => [
  { label: t('agent.coze'), value: 'coze' },
  { label: t('agent.dify'), value: 'dify' }
])

// ==================== 表格 ====================
const { loading, data: agentList, pagination, handleTableChange, loadData, createDebouncedSearch } = useTable<Agent>()

// 基础表格列
const baseColumns = computed<TableColumnsType>(() => [
  {
    title: t('common.avatar'),
    dataIndex: 'iconUrl',
    width: 80,
    align: 'center',
    fixed: 'left'
  },
  {
    title: t('agent.agentName'),
    dataIndex: 'agentName',
    width: 150,
    align: 'center',
    fixed: 'left'
  },
  {
    title: t('common.platform'),
    dataIndex: 'provider',
    width: 80,
    align: 'center'
  },
  {
    title: t('agent.agentDesc'),
    dataIndex: 'agentDesc',
    align: 'center'
  },
  {
    title: t('common.isDefault'),
    dataIndex: 'isDefault',
    width: 120,
    align: 'center'
  },
  {
    title: t('agent.publishTime'),
    dataIndex: 'publishTime',
    width: 180,
    align: 'center'
  },
  {
    title: t('table.action'),
    key: 'operation',
    width: 150,
    align: 'center',
    fixed: 'right'
  }
])

// 动态表格列（根据平台添加智能体ID列）
const tableColumns = computed(() => {
  if (searchForm.value.provider === 'coze') {
    const cols = [...baseColumns.value]
    const botIdColumn = {
      title: t('agent.botId'),
      dataIndex: 'botId',
      width: 180,
      align: 'center' as const
    }
    // 在第三列插入智能体ID列
    cols.splice(2, 0, botIdColumn)
    return cols
  }
  return baseColumns.value
})

// 加载数据
const fetchData = async () => {
  await loadData((params) => queryAgents({
    provider: searchForm.value.provider,
    agentName: searchForm.value.agentName,
    configType: 'agent',
    start: params.start,
    limit: params.limit
  }))
}

// 防抖搜索
const debouncedSearch = createDebouncedSearch(fetchData, 500)

// 处理表格分页变化
const onTableChange = (pag: TablePaginationConfig) => {
  handleTableChange(pag)
  fetchData()
}

// ==================== 平台配置 ====================
const currentConfigId = ref<number | null>(null)
const platformFormRef = ref<FormInstance>()

// 平台表单数据
const platformForm = reactive<PlatformConfig>({
  configType: 'agent',
  provider: 'coze',
  configName: '',
  configDesc: '',
  appId: '',
  apiKey: '',
  apiSecret: '',
  apiUrl: '',
  ak: '',
  sk: ''
})

// 使用 modal composable
const platformModal = useModal<PlatformConfig>({
  formRef: platformFormRef,
  onSubmit: async (data, isEdit) => {
    // Modal 内已有 submitLoading，不需要全局 loading
    try {
      // 如果是Dify平台，确保apiUrl有正确的格式
      if (data.provider === 'dify' && data.apiUrl) {
        let baseUrl = data.apiUrl
        if (baseUrl.endsWith('/')) {
          baseUrl = baseUrl.slice(0, -1)
        }
        data.apiUrl = baseUrl
      }
      
      // 如果是编辑模式，添加configId
      if (isEdit && currentConfigId.value) {
        data.configId = currentConfigId.value
      }
      
      // 调用API
      const apiFunc = isEdit ? updatePlatformConfig : addPlatformConfig
      const res = await apiFunc(data)
      
      if (res.code === 200) {
        message.success(isEdit ? t('common.updatePlatformConfigSuccess') : t('common.addPlatformConfigSuccess'))
        await fetchData()
        return true
      } else {
        message.error(res.message || (isEdit ? t('common.updatePlatformConfigFailed') : t('common.addPlatformConfigFailed')))
        return false
      }
    } catch (error) {
      console.error('Error with platform config:', error)
      message.error(isEdit ? t('common.updatePlatformConfigFailed') : t('common.addPlatformConfigFailed'))
      return false
    }
  },
  onOpen: async (item) => {
    loadingStore.showLoading(t('common.loading'))
    try {
      // 并行执行：API 请求和等待最小显示时间
      const [res] = await Promise.all([
        queryPlatformConfig('agent', searchForm.value.provider),
        loadingStore.awaitMinDisplay()
      ])
      
      // 此时最小时间已满足，立即处理数据
      if (res.code === 200) {
        const configs = (res.data as { list: PlatformConfig[] })?.list || []
        
        if (configs.length > 0) {
          // 编辑模式：填充已有配置
          const config = configs[0] as PlatformConfig
          currentConfigId.value = config.configId ?? null
          Object.assign(platformForm, {
            configType: config.configType || 'agent',
            provider: config.provider,
            configName: config.configName || '',
            configDesc: config.configDesc || '',
            appId: config.appId || '',
            apiSecret: config.apiSecret || '',
            apiKey: config.apiKey || '',
            apiUrl: config.apiUrl || '',
            ak: config.ak || '',
            sk: config.sk || ''
          })
        } else {
          // 新增模式：使用默认值
          currentConfigId.value = null
          Object.assign(platformForm, {
            configType: 'agent',
            provider: searchForm.value.provider,
            configName: '',
            configDesc: '',
            appId: '',
            apiKey: '',
            apiSecret: '',
            apiUrl: searchForm.value.provider === 'dify' ? 'https://api.dify.ai/v1' : '',
            ak: '',
            sk: ''
          })
        }
      } else {
        message.error(res.message || t('common.getPlatformConfigFailed'))
      }
    } catch (error) {
      // 出错时也要等待最小时间，避免闪烁
      await loadingStore.awaitMinDisplay()
      console.error('Error fetching platform config:', error)
      message.error(t('common.getPlatformConfigFailed'))
    } finally {
      loadingStore.hideLoading()
    }
  }
})

// 表单项配置
const formItems = computed<PlatformFormItems>(() => ({
  coze: [
    {
      field: 'appId',
      label: t('agent.appId'),
      placeholder: t('agent.enterAppId')
    },
    {
      field: 'apiSecret',
      label: t('agent.spaceId'),
      placeholder: t('agent.enterSpaceId')
    },
    {
      field: 'ak',
      label: t('agent.publicKey'),
      placeholder: t('agent.enterPublicKey')
    },
    {
      field: 'sk',
      label: t('agent.privateKey'),
      placeholder: t('agent.enterPrivateKey'),
      type: 'textarea'
    }
  ],
  dify: [
    {
      field: 'apiUrl',
      label: t('agent.apiUrl'),
      placeholder: t('agent.enterApiUrl'),
      suffix: '/chat_message'
    },
    {
      field: 'apiKey',
      label: t('agent.apiKey'),
      placeholder: t('agent.enterApiKey')
    }
  ]
}))

// 表单验证规则
const platformRules = computed(() => ({
  appId: [{ required: true, message: t('agent.enterAppId'), trigger: 'blur' }],
  apiKey: [{ required: true, message: t('agent.enterApiKey'), trigger: 'blur' }],
  apiSecret: [{ required: true, message: t('agent.enterSpaceId'), trigger: 'blur' }],
  ak: [{ required: true, message: t('agent.enterPublicKey'), trigger: 'blur' }],
  sk: [{ required: true, message: t('agent.enterPrivateKey'), trigger: 'blur' }],
  apiUrl: [{ required: true, message: t('agent.enterApiUrl'), trigger: 'blur' }]
}))

// 当前平台的表单项
const currentFormItems = computed(() => {
  return formItems.value[searchForm.value.provider] || []
})

// 平台配置标题
const platformModalTitle = computed(() => {
  const platformName = searchForm.value.provider === 'coze' ? t('agent.coze') : searchForm.value.provider === 'dify' ? t('agent.dify') : searchForm.value.provider
  return `${t('common.platformConfig')} - ${platformName}`
})

// 打开平台配置对话框
const handleConfigPlatform = async () => {
  await platformModal.openCreate()
}

// 平台配置提交
const handlePlatformModalOk = async () => {
  try {
    await platformFormRef.value?.validate()
    await platformModal.submit(platformForm)
  } catch (error) {
    // 表单验证失败，不执行任何操作
    console.error('表单验证失败:', error)
  }
}

// ==================== 设为默认 ====================
const handleSetDefault = async (record: Agent) => {
  // 将Agent转换为Config格式，然后调用统一的setAsDefault
  const configRecord = {
    configId: record.configId,
    configName: record.agentName || record.configName || '',
    modelType: 'chat' as const,
    configType: 'agent' as const,
    provider: 'coze' as const
  }
  
  await setAsDefault(configRecord)
  // 刷新数据
  await fetchData()
}

// ==================== 初始化（非阻塞式加载）====================
fetchData()
</script>

<template>
  <div class="agent-view">
    <!-- 查询框 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('common.platform')">
              <a-select v-model:value="searchForm.provider" @change="debouncedSearch">
                <a-select-option v-for="item in providerOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('agent.agentName')">
              <a-input
                v-model:value="searchForm.agentName"
                :placeholder="t('agent.enterAgentName')"
                allow-clear
                @input="debouncedSearch"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 表格数据 -->
    <a-card :title="t('menu.agent')" :bordered="false">
      <template #extra>
        <a-button type="primary" @click="handleConfigPlatform">
          <template #icon>
            <SettingOutlined />
          </template>
          {{ t('common.platformConfig') }}
        </a-button>
      </template>

      <a-table
        row-key="configId"
        :columns="tableColumns"
        :data-source="agentList"
        :loading="loading"
        :pagination="pagination"
        @change="onTableChange"
        size="middle"
        :scroll="{ x: 1000 }"
      >
        <!-- 头像 -->
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'iconUrl'">
            <a-avatar :src="record.iconUrl" shape="square" :size="48" />
          </template>

          <!-- 智能体名称 -->
          <template v-else-if="column.dataIndex === 'agentName'">
            <a-tooltip :title="record.agentName" :mouse-enter-delay="0.5" placement="topLeft">
              <span v-if="record.agentName" class="ellipsis-text">{{ record.agentName }}</span>
              <span v-else>-</span>
            </a-tooltip>
          </template>

          <!-- 平台 -->
          <template v-else-if="column.dataIndex === 'provider'">
            <a-tag color="blue">{{ record.provider }}</a-tag>
          </template>

          <!-- 智能体描述 -->
          <template v-else-if="column.dataIndex === 'agentDesc'">
            <a-tooltip :title="record.agentDesc" :mouse-enter-delay="0.5" placement="topLeft">
              <span v-if="record.agentDesc" class="ellipsis-text">{{ record.agentDesc }}</span>
              <span v-else>-</span>
            </a-tooltip>
          </template>

          <!-- 默认状态 -->
          <template v-else-if="column.dataIndex === 'isDefault'">
            <a-tag v-if="record.isDefault == 1" color="green">{{ t('common.default') }}</a-tag>
            <span v-else>-</span>
          </template>

          <!-- 操作 -->
          <template v-else-if="column.key === 'operation'">
            <TableActionButtons
              :record="record"
              show-set-default
              :is-default="record.isDefault == 1"
              @set-default="handleSetDefault"
            />
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 平台配置对话框 -->
    <a-modal
      :title="platformModalTitle"
      :open="platformModal.visible.value"
      :confirm-loading="platformModal.submitLoading.value"
      @ok="handlePlatformModalOk"
      @cancel="platformModal.close"
      :width="600"
    >
      <a-form
        ref="platformFormRef"
        :model="platformForm"
        :rules="platformRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
      >
        <a-form-item
          v-for="item in currentFormItems"
          :key="item.field"
          :label="item.label"
          :name="item.field"
        >
          <a-textarea
            v-if="item.type === 'textarea'"
            v-model:value="platformForm[item.field as keyof PlatformConfig] as string"
            :placeholder="item.placeholder"
            :rows="4"
            :auto-size="{ minRows: 4, maxRows: 8 }"
          />
          <a-input
            v-else
            v-model:value="platformForm[item.field as keyof PlatformConfig] as string"
            :placeholder="item.placeholder"
          >
            <template v-if="item.suffix" #suffix>
              <span style="color: var(--ant-color-text-tertiary)">{{ item.suffix }}</span>
            </template>
          </a-input>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.agent-view {
  padding: 24px;
}

.search-card :deep(.ant-form-item) {
  margin-bottom: 0;
}

.ellipsis-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
