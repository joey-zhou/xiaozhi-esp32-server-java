<script setup lang="ts">
import { ref, computed } from 'vue'
import { message as antMessage, Modal } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { useConfigManager } from '@/composables/useConfigManager'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import TableActionButtons from '@/components/TableActionButtons.vue'
import type { ConfigType, Config, ConfigField, LLMModel, LlmModelOption } from '@/types/config'
import { addConfig, updateConfig, testConfig } from '@/services/config'
import { CODE_CONFIRM_REQUIRED } from '@/constants/api'

const { t } = useI18n()
const userStore = useUserStore()
const { confirmAsync } = useConfirm()

interface Props {
  configType: ConfigType
}

const props = defineProps<Props>()

// 使用 composable
const {
  loading,
  configItems,
  currentType,
  editingConfigId,
  activeTabKey,
  modelOptions,
  pagination,
  queryForm,
  configTypeInfo,
  typeOptions,
  currentTypeFields,
  fetchData,
  deleteConfig,
  setAsDefault,
  updateModelOptions,
  getModelsByProviderAndType,
  handleTableChange,
  createDebouncedSearch,
} = useConfigManager(props.configType)

const debouncedSearch = createDebouncedSearch(fetchData, 500)

// 敏感字段：编辑时留空表示保持原值
const SECRET_FIELDS = ['apiKey', 'apiSecret', 'ak', 'sk']

/** providerConfig 里 config. 开头的值是 i18n key，其余（品牌名、示例值）原样展示 */
function localized(text?: string) {
  if (!text) return ''
  return text.startsWith('config.') ? t(text) : text
}

function isSecretField(field: ConfigField) {
  return SECRET_FIELDS.includes(field.name)
}

function fieldRequired(field: ConfigField) {
  return editingConfigId.value && isSecretField(field) ? false : field.required
}

function fieldPlaceholder(field: ConfigField) {
  if (editingConfigId.value && isSecretField(field) && currentType.value !== 'sherpa-onnx') {
    return t('config.keepUnchangedHint')
  }
  return localized(field.placeholder) || t('config.enterField', { field: localized(field.label) })
}

// 当前 provider 的 configName 预设选项（用于 AutoComplete）
const currentConfigNameOptions = computed(() => {
  const option = typeOptions.value.find((item) => item.value === currentType.value)
  return option?.configNameOptions?.map((v) => ({ value: v })) ?? []
})

// 表单
const formRef = ref<FormInstance>()
const formData = ref<Partial<Config>>({
  provider: undefined,
  configName: undefined,
  configDesc: undefined,
  // modelType 仅对 llm 有意义（chat/vision/intent/embedding）；其他类型不应携带，避免写入脏值
  modelType: props.configType === 'llm' ? 'chat' : undefined,
  isDefault: false,
  apiKey: undefined,
  apiUrl: undefined,
  appId: undefined,
  apiSecret: undefined,
  ak: undefined,
  sk: undefined,
  enableThinking: false,
})

// providerConfig 驱动的动态字段一律是文本输入，Config 的索引签名比这更宽（要容纳 isDefault/enableThinking 这类布尔具名字段），
// 所以读写收敛到这两个函数里做一次收窄，避免把 any 撒到模板上
function dynamicFieldValue(name: string): string {
  const value = formData.value[name]
  return value === undefined || value === null ? '' : String(value)
}

function setDynamicField(name: string, value: string) {
  formData.value[name] = value
}

// 表格列配置
const columns = computed(() => {
  const baseColumns: TableColumnsType = [
    {
      title: t('config.category'),
      dataIndex: 'provider',
      width: 150,
      align: 'center',
      customRender: ({ text }: { text: string }) => {
        const provider = typeOptions.value.find((item) => item.value === text)
        return provider ? provider.label : text
      },
    },
    {
      title: t('common.name'),
      dataIndex: 'configName',
      width: 200,
      align: 'center',
    },
  ]

  // LLM 添加模型类型列
  if (props.configType === 'llm') {
    baseColumns.push({
      title: t('config.modelType'),
      dataIndex: 'modelType',
      width: 120,
      align: 'center' as const,
    })
  }

  baseColumns.push(
    {
      title: t('common.description'),
      dataIndex: 'configDesc',
      width: 200,
      align: 'center' as const
    },
    {
      title: t('common.isDefault'),
      dataIndex: 'isDefault',
      width: 120,
      align: 'center' as const,
    },
    {
      title: t('common.createTime'),
      dataIndex: 'createTime',
      width: 200,
      align: 'center' as const,
    },
    {
      title: t('table.action'),
      dataIndex: 'operation',
      width: 180,
      align: 'center' as const,
      fixed: 'right' as const,
    }
  )

  // TTS 过滤掉默认列
  if (props.configType === 'tts') {
    return baseColumns.filter((col) => {
      return 'dataIndex' in col && col.dataIndex !== 'isDefault'
    })
  }

  return baseColumns
})

/**
 * 处理类型变化
 */
function handleTypeChange(value: string) {
  currentType.value = value
  
  // 清空模型名称
  formData.value.configName = undefined
  
  // 如果是 LLM，更新模型选项
  if (props.configType === 'llm' && formData.value.modelType) {
    updateModelOptions(value, formData.value.modelType)
  }
  
  // 填充默认 URL
  const typeField = currentTypeFields.value.find((f: ConfigField) => f.name === 'apiUrl')
  if (typeField?.placeholder) {
    formData.value.apiUrl = typeField.placeholder
  }
}

/**
 * 处理模型类型变化
 */
function handleModelTypeChange(value: string) {
  if (currentType.value) {
    updateModelOptions(currentType.value, value)
    formData.value.configName = undefined
  }
}

/**
 * apiUrl 输入框后缀提示：向量模型使用 embeddings 端点，而非 chat/completions
 */
function getFieldSuffix(field: ConfigField) {
  if (field.name === 'apiUrl' && formData.value.modelType === 'embedding' && field.suffix?.endsWith('/chat/completions')) {
    return field.suffix.replace(/\/chat\/completions$/, '/embeddings')
  }
  return field.suffix
}

/**
 * 处理标签页切换
 */
function handleTabChange(key: string) {
  activeTabKey.value = key
  if (key === '1') {
    fetchData()
  } else if (key === '2') {
    resetForm()
  }
}

/**
 * 编辑配置
 */
function handleEdit(record: Config) {
  editingConfigId.value = record.configId
  currentType.value = record.provider || ''
  activeTabKey.value = '2'

  // 设置表单值，将后端的 string ('0'/'1') 转换为 boolean
  formData.value = {
    ...record,
    // modelType 仅 llm 使用；非 llm 一律清空，避免把历史脏值再次提交
    modelType: props.configType === 'llm' ? (record.modelType || 'chat') : undefined,
    isDefault: props.configType != 'tts' ? record.isDefault === '1' : false,
    enableThinking: !!record.enableThinking,
  }

  // LLM 更新模型选项
  if (props.configType === 'llm') {
    updateModelOptions(record.provider, record.modelType || 'chat')
  }
}

// 测试连接中
const testing = ref(false)

// 表单提交中
const submitLoading = ref(false)

/**
 * 测试配置（使用当前表单值，无需先保存）
 */
async function handleTest() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()

    const submitData: Partial<Config> = {
      ...formData.value,
      configId: editingConfigId.value,
      configType: props.configType,
    }
    submitData.isDefault = formData.value.isDefault == '1' ? '1' : '0'

    testing.value = true
    const res = await testConfig(submitData)

    if (res.code === 200) {
      Modal.success({
        title: t('config.testSuccess'),
        content: res.message,
      })
    } else {
      Modal.error({
        title: t('config.testFailed'),
        content: res.message,
      })
    }
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'errorFields' in error) {
      antMessage.error(t('config.fillRequiredFields'))
    } else {
      console.error('测试配置失败:', error)
      antMessage.error(t('config.testFailed'))
    }
  } finally {
    testing.value = false
  }
}

/**
 * 提交表单
 */
async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    
    // 准备提交数据
    const submitData: Partial<Config> = {
      ...formData.value,
      configId: editingConfigId.value,
      configType: props.configType,
    }

    // 处理 isDefault：将 boolean 转换为后端需要的 string enum ('0'/'1')
    submitData.isDefault = formData.value.isDefault == '1' ? '1' : '0'

    // LLM 特殊验证
    if (props.configType === 'llm') {
      // 验证模型名称
      if (submitData.configName && /[\u4e00-\u9fa5]/.test(submitData.configName)) {
        antMessage.error(t('config.modelNameNoChinese'))
        return
      }

      const validModels = getModelsByProviderAndType(
        submitData.provider || '',
        submitData.modelType || 'chat'
      )
      const isValid = validModels.some((m: LLMModel) => m.llm_name === submitData.configName)
      
      // 模型名称不在已知列表里时问一句，取消则中断提交
      if (!isValid && validModels.length > 0) {
        const shouldContinue = await confirmAsync({
          title: t('common.confirmSubmit'),
          content: t('config.modelNameInvalid', { name: submitData.configName }),
          okText: t('common.confirm'),
        })
        if (!shouldContinue) return
      }
    }

    submitLoading.value = true
    await submitConfig(submitData, false)
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'errorFields' in error) {
      antMessage.error(t('config.fillRequiredFields'))
    } else {
      console.error('提交配置失败:', error)
      antMessage.error(t('common.operationFailed'))
    }
  } finally {
    submitLoading.value = false
  }
}

/**
 * 发一次提交请求。
 * 后端判定这次改动会换掉当前生效的对象存储时不直接执行，先带存量条数回一个待确认，
 * 由这里把后果原样问给用户，确认后带确认参数原样重发一次。
 */
async function submitConfig(submitData: Partial<Config>, confirmStorageSwitch: boolean) {
  const isEditing = !!editingConfigId.value
  const res = isEditing
    ? await updateConfig(submitData, confirmStorageSwitch)
    : await addConfig(submitData, confirmStorageSwitch)

  if (res.code === 200) {
    antMessage.success(isEditing ? t('config.updateSuccess') : t('config.createSuccess'))
    resetForm()
    activeTabKey.value = '1'
    await fetchData()
    return
  }

  if (res.code === CODE_CONFIRM_REQUIRED) {
    const confirmed = await confirmAsync({
      title: t('config.storageSwitchTitle'),
      content: res.message,
      okText: t('config.storageSwitchOk'),
      okType: 'danger',
    })
    if (confirmed) {
      await submitConfig(submitData, true)
    }
    return
  }

  antMessage.error(res.message || t('common.operationFailed'))
}

/**
 * 重置表单
 */
function resetForm() {
  formRef.value?.resetFields()
  currentType.value = ''
  editingConfigId.value = undefined
  modelOptions.value = []
  formData.value = {
    provider: undefined,
    configName: undefined,
    configDesc: undefined,
    // modelType 仅对 llm 有意义，其他类型保持 undefined 不提交
    modelType: props.configType === 'llm' ? 'chat' : undefined,
    isDefault: false,
    apiKey: undefined,
    apiUrl: undefined,
    appId: undefined,
    apiSecret: undefined,
    ak: undefined,
    sk: undefined,
    enableThinking: false,
  }
}

/**
 * 取消
 */
function handleCancel() {
  resetForm()
  activeTabKey.value = '1'
}

/**
 * 获取默认标签颜色
 */
function getDefaultTagColor(record: Config) {
  if (props.configType === 'llm') {
    const colors: Record<string, string> = {
      chat: 'blue',
      vision: 'purple',
      intent: 'orange',
      embedding: 'green',
    }
    return colors[record.modelType || ''] || 'green'
  }
  return props.configType === 'stt' ? 'cyan' : 'green'
}

/**
 * 获取默认标签文本
 */
function getDefaultTagText(record: Config) {
  if (props.configType === 'llm') {
    const texts: Record<string, string> = {
      chat: t('config.defaultChat'),
      vision: t('config.defaultVision'),
      intent: t('config.defaultIntent'),
      embedding: t('config.defaultEmbedding'),
    }
    return texts[record.modelType || ''] || t('common.default')
  }
  return props.configType === 'stt' ? t('config.defaultStt') : t('common.default')
}

/**
 * 模型类型标签
 */
function getModelTypeTag(modelType: string) {
  const tags: Record<string, { text: string; color: string }> = {
    chat: { text: t('config.chatModel'), color: 'blue' },
    vision: { text: t('config.visionModel'), color: 'purple' },
    intent: { text: t('config.intentModel'), color: 'orange' },
    embedding: { text: t('config.embeddingModel'), color: 'green' },
  }
  return tags[modelType] || { text: '-', color: 'default' }
}

// 处理表格变化
const onTableChange = (pag: TablePaginationConfig) => {
  handleTableChange(pag)
  fetchData()
}

// 初始化
fetchData()
</script>

<template>
  <div class="config-manager">
    <!-- 查询表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col :xxl="8" :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('config.category')">
              <a-select v-model:value="queryForm.provider" @change="debouncedSearch">
                <a-select-option value="">{{ t('common.all') }}</a-select-option>
                <a-select-option
                  v-for="item in typeOptions"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>

          <a-col :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('common.name')">
              <a-input
                v-model:value="queryForm.configName"
                :placeholder="t('config.pleaseEnter')"
                allow-clear
                @input="debouncedSearch"
              />
            </a-form-item>
          </a-col>

          <a-col v-if="configType === 'llm'" :xxl="8" :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('config.modelType')">
              <a-select v-model:value="queryForm.modelType" @change="debouncedSearch">
                <a-select-option value="">{{ t('common.all') }}</a-select-option>
                <a-select-option value="chat">{{ t('config.chatModel') }}</a-select-option>
                <a-select-option value="vision">{{ t('config.visionModel') }}</a-select-option>
                <a-select-option value="intent">{{ t('config.intentModel') }}</a-select-option>
                <a-select-option value="embedding">{{ t('config.embeddingModel') }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 表格和表单 -->
    <a-card :bordered="false" :body-style="{ padding: '0 24px 24px 24px' }">
      <a-tabs
        v-model:active-key="activeTabKey"
        @change="handleTabChange"
      >
        <!-- 列表标签页 -->
        <a-tab-pane key="1" :tab="`${t(configTypeInfo.label)} ${t('config.list')}`">
          <a-table
            :columns="columns"
            :data-source="configItems"
            :loading="loading"
            :pagination="pagination"
            @change="onTableChange"
            row-key="configId"
            :scroll="{ x: 800 }"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <!-- 模型类型列 -->
              <template v-if="column.dataIndex === 'modelType' && configType === 'llm'">
                <a-tag :color="getModelTypeTag(record.modelType).color">
                  {{ getModelTypeTag(record.modelType).text }}
                </a-tag>
                <a-tag v-if="record.enableThinking" color="cyan">
                  {{ t('config.enableThinking') }}
                </a-tag>
              </template>

              <!-- 描述列 -->
              <template v-else-if="column.dataIndex === 'configDesc'">
                <a-tooltip :title="record.configDesc" :mouse-enter-delay="0.5" placement="topLeft">
                  <span v-if="record.configDesc" class="ellipsis-text">{{ record.configDesc }}</span>
                  <span v-else>-</span>
                </a-tooltip>
              </template>

              <!-- 默认标识列 -->
              <template v-else-if="column.dataIndex === 'isDefault'">
                <a-tag v-if="record.isDefault === '1'" :color="getDefaultTagColor(record)">
                  {{ getDefaultTagText(record) }}
                </a-tag>
                <span v-else>-</span>
              </template>

              <!-- 操作列 -->
              <template v-else-if="column.dataIndex === 'operation'">
                <TableActionButtons
                  :record="record"
                  :permission-prefix="configTypeInfo.permissionPrefix"
                  show-edit
                  :show-set-default="configType !== 'tts'"
                  :show-delete="record.isDefault !== '1'"
                  :is-default="record.isDefault === '1'"
                  :delete-title="t('config.confirmDelete', { type: t(configTypeInfo.label) })"
                  @edit="() => handleEdit(record)"
                  @set-default="() => setAsDefault(record)"
                  @delete="() => deleteConfig(record.configId)"
                />
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- 创建/编辑标签页 -->
        <a-tab-pane
          v-if="userStore.hasPermission(editingConfigId ? `${configTypeInfo.permissionPrefix}:update` : `${configTypeInfo.permissionPrefix}:create`)"
          key="2"
          :tab="editingConfigId ? `${t('config.update', { type: t(configTypeInfo.label) })}` : `${t('config.create')} ${t(configTypeInfo.label)}`"
        >
          <a-form
            ref="formRef"
            :model="formData"
            layout="horizontal"
            :colon="false"
            style="padding: 10px 24px"
            :hideRequiredMark="true"
          >
            <a-row :gutter="20">
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="`${t(configTypeInfo.label)} ${t('config.category')}`"
                  name="provider"
                  :rules="[{ required: true, message: t('config.selectCategory', { type: t(configTypeInfo.label) }) }]"
                >
                  <a-select
                    v-model:value="formData.provider"
                    :placeholder="t('config.selectCategory', { type: t(configTypeInfo.label) })"
                    @change="handleTypeChange"
                  >
                    <a-select-option
                      v-for="item in typeOptions"
                      :key="item.value"
                      :value="item.value"
                    >
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>

              <!-- LLM 模型类型 -->
              <a-col v-if="configType === 'llm'" :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('config.modelType')"
                  name="modelType"
                  :rules="[{ required: true, message: t('config.selectModelType') }]"
                >
                  <a-select
                    v-model:value="formData.modelType"
                    :placeholder="t('config.selectModelType')"
                    @change="handleModelTypeChange"
                  >
                    <a-select-option value="chat">{{ t('config.chatModel') }}</a-select-option>
                    <a-select-option value="vision">{{ t('config.visionModel') }}</a-select-option>
                    <a-select-option value="intent">{{ t('config.intentModel') }}</a-select-option>
                    <a-select-option value="embedding">{{ t('config.embeddingModel') }}</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>

              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="`${t(configTypeInfo.label)} ${t('common.name')}`"
                  name="configName"
                  :rules="[{ required: true, message: t('config.enterName', { type: t(configTypeInfo.label) }) }]"
                >
                  <!-- LLM 使用 AutoComplete（可选可填） -->
                  <a-auto-complete
                    v-if="configType === 'llm' && currentType"
                    v-model:value="formData.configName"
                    allow-clear
                    :placeholder="t('config.enterName', { type: t(configTypeInfo.label) })"
                    :options="modelOptions"
                    :filter-option="
                      (input: string, option: LlmModelOption) =>
                        option.label.toLowerCase().includes(input.toLowerCase())
                    "
                  />
                  <!-- 有预设模型选项时使用 AutoComplete（可选可填） -->
                  <a-auto-complete
                    v-else-if="currentConfigNameOptions.length > 0"
                    v-model:value="formData.configName"
                    :options="currentConfigNameOptions"
                    :placeholder="t('config.enterName', { type: t(configTypeInfo.label) })"
                    :filter-option="
                      (input: string, option: { value: string }) =>
                        option.value.toLowerCase().includes(input.toLowerCase())
                    "
                    allow-clear
                  />
                  <!-- 其他使用输入框 -->
                  <a-input
                    v-else
                    v-model:value="formData.configName"
                    :placeholder="t('config.enterName', { type: t(configTypeInfo.label) })"
                  />
                </a-form-item>
              </a-col>
            </a-row>

            <a-form-item :label="`${t(configTypeInfo.label)} ${t('common.description')}`" name="configDesc">
              <a-textarea
                v-model:value="formData.configDesc"
                :placeholder="t('config.enterDescription', { type: t(configTypeInfo.label) })"
                :rows="4"
              />
            </a-form-item>

            <!-- 设为默认 -->
            <a-form-item
              v-if="configType !== 'tts'"
              v-permission="`${configTypeInfo.permissionPrefix}:update`"
              :label="t('config.setAsDefault', { type: t(configTypeInfo.label) })"
              name="isDefault"
            >
              <a-switch v-model:checked="formData.isDefault" />
              <span style="margin-left: 8px; color: var(--ant-color-text-tertiary)">
                {{ t('config.defaultTip') }}
              </span>
            </a-form-item>

            <!-- 思考模式开关（仅 LLM） -->
            <a-form-item
              v-if="configType === 'llm'"
              :label="t('config.enableThinking')"
              name="enableThinking"
            >
              <a-switch v-model:checked="formData.enableThinking" />
              <span style="margin-left: 8px; color: var(--ant-color-text-tertiary)">
                {{ t('config.enableThinkingTip') }}
              </span>
            </a-form-item>

            <a-divider>{{ t('config.parameterConfig') }}</a-divider>

            <!-- 动态参数字段 -->
            <a-card
              v-if="currentType"
              size="small"
              :bordered="false"
            >
              <!-- 本地模型使用说明 -->
              <a-alert
                v-if="currentType === 'sherpa-onnx' && configType === 'tts'"
                type="info"
                show-icon
                style="margin-bottom: 16px"
              >
                <template #message>{{ t('config.sherpaOnnx.title') }}</template>
                <template #description>
                  <div style="font-size: 13px; line-height: 2">
                    <p style="margin: 0">{{ t('config.sherpaOnnx.noParams') }}</p>
                    <p style="margin: 0" v-html="t('config.sherpaOnnx.architectures')"></p>
                    <p style="margin: 0" v-html="t('config.sherpaOnnx.modelDir')"></p>
                    <p style="margin: 0" v-html="t('config.sherpaOnnx.matchaVocoder')"></p>
                    <p style="margin: 0" v-html="t('config.sherpaOnnx.chineseDict')"></p>
                  </div>
                </template>
              </a-alert>
              <a-row :gutter="20">
                <a-col
                  v-for="field in currentTypeFields"
                  :key="field.name"
                  :xl="field.span || 12"
                  :lg="12"
                  :xs="24"
                >
                  <a-form-item
                    :label="localized(field.label)"
                    :name="field.name"
                    :rules="[{ required: fieldRequired(field), message: t('config.enterField', { field: localized(field.label) }) }]"
                    style="margin-bottom: 24px"
                  >
                    <a-input
                      :value="dynamicFieldValue(field.name)"
                      :placeholder="fieldPlaceholder(field)"
                      :type="field.inputType || 'text'"
                      @update:value="(v: string) => setDynamicField(field.name, v)"
                    >
                      <template v-if="field.suffix" #suffix>
                        <span style="color: var(--ant-color-text-tertiary)">{{ getFieldSuffix(field) }}</span>
                      </template>
                    </a-input>
                    <div v-if="field.help" class="field-help">
                      {{ localized(field.help) }}
                    </div>
                  </a-form-item>
                </a-col>
              </a-row>
            </a-card>

            <a-card v-else :bordered="false">
              <a-empty :description="t('config.selectCategoryFirst', { type: t(configTypeInfo.label) })" />
            </a-card>

            <a-form-item style="margin-top: 24px">
              <a-space>
                <!-- a-space 会给每个子项包一层 item，v-permission 的 display:none 会留下空格子，这里改用 v-if 整块不渲染 -->
                <a-button
                  v-if="userStore.hasPermission(editingConfigId ? `${configTypeInfo.permissionPrefix}:update` : `${configTypeInfo.permissionPrefix}:create`)"
                  type="primary"
                  :loading="submitLoading"
                  @click="handleSubmit"
                >
                  {{ editingConfigId ? t('config.update', { type: t(configTypeInfo.label) }) : t('config.create', { type: t(configTypeInfo.label) }) }}
                </a-button>
                <a-button v-if="['llm', 'stt'].includes(props.configType)" :loading="testing" @click="handleTest">
                  {{ t('config.test') }}
                </a-button>
                <a-button @click="handleCancel">{{ t('common.cancel') }}</a-button>
              </a-space>
            </a-form-item>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<style scoped lang="scss">
.config-manager {
  padding: 24px;
}

.search-card :deep(.ant-form-item) {
  margin-bottom: 0;
}

.field-help {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}

.ellipsis-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
