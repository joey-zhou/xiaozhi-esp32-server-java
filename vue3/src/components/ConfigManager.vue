<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'ant-design-vue'
import { useConfigManager } from '@/composables/useConfigManager'
import type { ConfigType, Config, ConfigField, ModelType } from '@/types/config'
import { addConfig, updateConfig } from '@/services/config'

const { t } = useI18n()

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
  getData,
  deleteConfig,
  setAsDefault,
  updateModelOptions,
  getModelsByProviderAndType,
} = useConfigManager(props.configType)

// 表单
interface FormData extends Record<string, any> {
  provider?: string
  configName?: string
  configDesc?: string
  modelType?: ModelType
  isDefault?: boolean | number
}

const formRef = ref<FormInstance>()
const formData = ref<FormData>({
  provider: undefined,
  configName: undefined,
  configDesc: undefined,
  modelType: 'chat',
  isDefault: false,
})

// 表格列配置
const columns = computed(() => {
  const baseColumns = [
    {
      title: t('config.category'),
      dataIndex: 'provider',
      width: 200,
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
      width: 80,
      align: 'center' as const,
    },
    {
      title: t('common.createTime'),
      dataIndex: 'createTime',
      width: 180,
      align: 'center' as const,
    },
    {
      title: t('table.action'),
      dataIndex: 'operation',
      width: 180,
      align: 'center',
      fixed: 'right',
    } as any,
  )

  // TTS 过滤掉默认列
  if (props.configType === 'tts') {
    return baseColumns.filter((col) => col.dataIndex !== 'isDefault')
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
 * 处理标签页切换
 */
function handleTabChange(key: string) {
  activeTabKey.value = key
  if (key === '1') {
    getData()
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

  // 设置表单值
  formData.value = { 
    ...record,
    isDefault: props.configType !== 'tts' ? record.isDefault === '1' : false
  }

  // LLM 更新模型选项
  if (props.configType === 'llm') {
    updateModelOptions(record.provider, record.modelType || 'chat')
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
      configId: editingConfigId.value,
      configType: props.configType,
      provider: formData.value.provider,
      configName: formData.value.configName,
      configDesc: formData.value.configDesc,
      modelType: formData.value.modelType
    }

    // 处理 isDefault
    if (props.configType !== 'tts') {
      submitData.isDefault = formData.value.isDefault ? '1' : '0'
    }

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
      const isValid = validModels.some((m: any) => m.llm_name === submitData.configName)
      
      if (!isValid && validModels.length > 0) {
        antMessage.error(t('config.modelNameInvalid', { name: submitData.configName }))
        return
      }
    }

    loading.value = true

    const res = editingConfigId.value
      ? await updateConfig(submitData)
      : await addConfig(submitData)

    if (res.code === 200) {
      antMessage.success(editingConfigId.value ? t('config.updateSuccess') : t('config.createSuccess'))
      resetForm()
      getData()
      activeTabKey.value = '1'
    } else {
      antMessage.error(res.message || t('common.operationFailed'))
    }
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'errorFields' in error) {
      antMessage.error(t('config.fillRequiredFields'))
    } else {
      console.error('提交配置失败:', error)
      antMessage.error(t('common.operationFailed'))
    }
  } finally {
    loading.value = false
  }
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
    modelType: 'chat',
    isDefault: false,
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
const handleTableChangeWrapper = (pag: any) => {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  getData()
}

// 初始化
onMounted(() => {
  getData()
})
</script>

<template>
  <div class="config-manager">
    <!-- 查询表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col :xxl="8" :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('config.category')">
              <a-select v-model:value="queryForm.provider" @change="getData">
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
                @press-enter="getData"
              />
            </a-form-item>
          </a-col>

          <a-col v-if="configType === 'llm'" :xxl="8" :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('config.modelType')">
              <a-select v-model:value="queryForm.modelType" @change="getData">
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
            @change="handleTableChangeWrapper"
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
                <a-space>
                  <a @click="() => handleEdit(record)">{{ t('common.edit') }}</a>
                  <a
                    v-if="configType !== 'tts' && record.isDefault !== '1'"
                    @click="() => setAsDefault(record)"
                  >
                    {{ t('common.setAsDefault') }}
                  </a>
                  <a-popconfirm
                    :title="t('config.confirmDelete', { type: t(configTypeInfo.label) })"
                    @confirm="() => deleteConfig(record.configId)"
                  >
                    <a v-if="record.isDefault !== '1'" style="color: #ff4d4f">{{ t('common.delete') }}</a>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- 创建/编辑标签页 -->
        <a-tab-pane key="2" :tab="`${t('config.create')} ${t(configTypeInfo.label)}`">
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
                  <!-- LLM 使用下拉框 -->
                  <a-select
                    v-if="configType === 'llm' && currentType"
                    v-model:value="formData.configName"
                    show-search
                    allow-clear
                    :placeholder="t('config.enterName', { type: t(configTypeInfo.label) })"
                    :options="modelOptions"
                    :filter-option="
                      (input: string, option: any) =>
                        option.label.toLowerCase().includes(input.toLowerCase())
                    "
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
              :label="`${t('common.setAsDefault')}${t(configTypeInfo.label)}`"
              name="isDefault"
            >
              <a-switch v-model:checked="formData.isDefault" />
              <span style="margin-left: 8px; color: #999">
                {{ t('config.defaultTip') }}
              </span>
            </a-form-item>

            <a-divider>{{ t('config.parameterConfig') }}</a-divider>

            <!-- 动态参数字段 -->
            <a-card
              v-if="currentType"
              size="small"
              :bordered="false"
            >
              <a-row :gutter="20">
                <a-col
                  v-for="field in currentTypeFields"
                  :key="field.name"
                  :xl="field.span || 12"
                  :lg="12"
                  :xs="24"
                >
                  <a-form-item
                    :label="field.label"
                    :name="field.name"
                    :rules="[{ required: field.required, message: t('config.enterField', { field: field.label }) }]"
                    style="margin-bottom: 24px"
                  >
                    <a-input
                      v-model:value="formData[field.name]"
                      :placeholder="field.placeholder || t('config.enterField', { field: field.label })"
                      :type="field.inputType || 'text'"
                    >
                      <template v-if="field.suffix" #suffix>
                        <span style="color: #999">{{ field.suffix }}</span>
                      </template>
                    </a-input>
                    <div v-if="field.help" class="field-help">
                      {{ field.help }}
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
                <a-button type="primary" :loading="loading" @click="handleSubmit">
                  {{ editingConfigId ? t('config.update', { type: t(configTypeInfo.label) }) : t('config.create', { type: t(configTypeInfo.label) }) }}
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

