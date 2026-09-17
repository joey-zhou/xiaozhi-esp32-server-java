import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { ConfigType, Config, ConfigField, ModelOption, LLMModel, LLMFactory } from '@/types/config'
import { queryConfigs, updateConfig, deleteConfig as deleteConfigRequest } from '@/services/config'
import { configTypeMap } from '@/config/providerConfig'
import llmFactoriesData from '@/config/llm_factories.json'
import { useTable } from './useTable'

/** 按模型类型分组的工厂模型表 */
interface LLMFactoryModelInfo {
  chat?: LLMModel[]
  vision?: LLMModel[]
  intent?: LLMModel[]
  embedding?: LLMModel[]
}

interface LLMFactoryIndex {
  /** provider -> 各模型类型下的模型列表 */
  models: Record<string, LLMFactoryModelInfo>
  /** provider -> 工厂默认 API URL */
  urls: Record<string, string>
  /** 按 rank 降序、同 rank 按字母序的 provider 下拉项 */
  providers: Array<{ value: string; label: string; configNameOptions?: string[] }>
}

const EMPTY_LLM_FACTORY_INDEX: LLMFactoryIndex = { models: {}, urls: {}, providers: [] }

// llm_factories.json 是不变常量，索引在模块作用域只建一次，多个 useConfigManager 实例共用
let llmFactoryIndex: LLMFactoryIndex | null = null

/**
 * 构建 LLM 工厂索引
 */
function buildLlmFactoryIndex(): LLMFactoryIndex {
  if (!llmFactoriesData || !llmFactoriesData.factory_llm_infos) {
    console.warn('llm_factories.json 数据格式不正确')
    return EMPTY_LLM_FACTORY_INDEX
  }

  const models: Record<string, LLMFactoryModelInfo> = {}
  const urls: Record<string, string> = {}
  const ranks: Record<string, number> = {}
  const providers: Array<{ value: string; label: string }> = []

  llmFactoriesData.factory_llm_infos.forEach((factory: LLMFactory) => {
    const providerName = factory.name
    providers.push({ value: providerName, label: providerName })

    if (factory.url) {
      urls[providerName] = factory.url
    }

    if (factory.rank) {
      ranks[providerName] = parseInt(factory.rank) || 0
    }

    const modelsByType: LLMFactoryModelInfo = {
      chat: [],
      embedding: [],
      vision: [],
      intent: []
    }

    if (factory.llm && Array.isArray(factory.llm)) {
      factory.llm.forEach((llm: LLMModel) => {
        let mappedModelType = llm.model_type

        // speech2text / image2text 统一并入 vision
        if (mappedModelType === 'speech2text' || mappedModelType === 'image2text') {
          mappedModelType = 'vision'
        }

        if (['chat', 'embedding', 'vision'].includes(mappedModelType as keyof LLMFactoryModelInfo)) {
          (modelsByType[mappedModelType as keyof LLMFactoryModelInfo] as LLMModel[]).push({
            llm_name: llm.llm_name,
            model_type: mappedModelType,
            max_tokens: llm.max_tokens,
            is_tools: llm.is_tools || false,
            tags: llm.tags || '',
          })
        }
      })
    }

    models[providerName] = modelsByType
  })

  // rank 越大越靠前，相同 rank 按字母序
  providers.sort((a, b) => {
    const rankA = ranks[a.value] || 0
    const rankB = ranks[b.value] || 0
    if (rankA !== rankB) return rankB - rankA
    return a.label.localeCompare(b.label)
  })

  return { models, urls, providers }
}

function getLlmFactoryIndex(): LLMFactoryIndex {
  if (!llmFactoryIndex) {
    llmFactoryIndex = buildLlmFactoryIndex()
  }
  return llmFactoryIndex
}

export function useConfigManager(configType: ConfigType) {
  const { t } = useI18n()

  // 使用统一的表格管理
  const {
    loading,
    data: configItems,
    pagination,
    loadData,
    handleTableChange,
    createDebouncedSearch,
  } = useTable<Config>()

  // 状态
  const currentType = ref('')
  const editingConfigId = ref<number>()
  const activeTabKey = ref('1')
  const modelOptions = ref<ModelOption[]>([])

  // LLM 工厂数据（常量索引，非 llm 类型不构建）
  const llmFactory = configType === 'llm' ? getLlmFactoryIndex() : EMPTY_LLM_FACTORY_INDEX

  // 查询表单
  const queryForm = ref({
    provider: '',
    configName: '',
    modelType: '',
  })

  // 配置类型信息
  const configTypeInfo = computed(() => {
    return configTypeMap[configType] || { label: '' }
  })

  // 类型选项
  const typeOptions = computed(() => {
    if (configType === 'llm') {
      return llmFactory.providers
    }
    return configTypeInfo.value.typeOptions || []
  })

  // 当前类型字段
  const currentTypeFields = computed((): ConfigField[] => {
    if (!currentType.value) return []

    const typeFieldsMap = configTypeInfo.value.typeFields || {}

    if (configType === 'llm') {
      // 如果 providerConfig 有明确定义，使用它
      if (typeFieldsMap[currentType.value]) {
        const fields = [...(typeFieldsMap[currentType.value] || [])]
        // 如果没有 apiUrl 字段但工厂有 URL，自动追加
        const factoryUrl = llmFactory.urls[currentType.value]
        if (factoryUrl && !fields.some(f => f.name === 'apiUrl')) {
          fields.push({
            name: 'apiUrl',
            label: 'API URL',
            required: true,
            inputType: 'text',
            placeholder: factoryUrl,
            span: 12,
            suffix: '/chat/completions',
          })
        }
        return fields
      }

      // 没有明确定义：根据工厂数据自动生成默认字段
      const factoryUrl = llmFactory.urls[currentType.value] || ''
      return [
        {
          name: 'apiKey',
          label: 'API Key',
          required: true,
          inputType: 'password',
          placeholder: 'your-api-key',
          span: 12,
        },
        {
          name: 'apiUrl',
          label: 'API URL',
          required: true,
          inputType: 'text',
          placeholder: factoryUrl,
          span: 12,
          suffix: '/chat/completions',
        }
      ]
    }

    return typeFieldsMap[currentType.value] || []
  })

  /**
   * 根据 provider 和 modelType 获取模型列表
   */
  function getModelsByProviderAndType(provider: string, modelType: string): LLMModel[] {
    const providerData = llmFactory.models[provider]
    if (!providerData) {
      return []
    }
    return (providerData[modelType as keyof LLMFactoryModelInfo] || []) as LLMModel[]
  }

  /**
   * 更新模型选项列表
   */
  function updateModelOptions(provider: string, modelType: string) {
    if (configType !== 'llm') {
      return
    }

    const models = getModelsByProviderAndType(provider, modelType)
    modelOptions.value = models.map((model: LLMModel) => ({
      value: model.llm_name,
      label: model.llm_name,
    }))
  }

  /**
   * 获取配置列表
   */
  async function fetchData() {
    await loadData(async ({ pageNo, pageSize }) => {
      return queryConfigs({
        pageNo,
        pageSize,
        configType,
        ...queryForm.value,
      })
    })
  }

  /**
   * 删除配置（快速操作，只用 table loading）
   */
  async function deleteConfig(configId: number) {
    loading.value = true
    try {
      const res = await deleteConfigRequest(configId)

      if (res.code === 200) {
        message.success(t('common.deleteSuccess'))
        await fetchData()
      } else {
        message.error(res.message)
      }
    } catch (error) {
      console.error('删除配置失败:', error)
      message.error(t('common.serverMaintenance'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 设置为默认配置（快速操作，只用 table loading）
   */
  async function setAsDefault(record: Config) {
    if (configType === 'tts') return

    loading.value = true
    try {
      const res = await updateConfig({
        configId: record.configId,
        configType,
        modelType: configType === 'llm' ? record.modelType : undefined,
        isDefault: '1',
      })

      if (res.code === 200) {
        message.success(t('common.setDefaultSuccess', { name: record.configName }))
        await fetchData()
      } else {
        message.error(res.message || t('common.setDefaultFailed'))
      }
    } catch (error) {
      console.error('设置默认配置失败:', error)
      message.error(t('common.serverMaintenance'))
    } finally {
      loading.value = false
    }
  }

  return {
    // 状态
    loading,
    configItems,
    currentType,
    editingConfigId,
    activeTabKey,
    modelOptions,
    pagination,
    queryForm,
    
    // 计算属性
    configTypeInfo,
    typeOptions,
    currentTypeFields,
    
    // 方法
    fetchData,
    deleteConfig,
    setAsDefault,
    updateModelOptions,
    getModelsByProviderAndType,
    handleTableChange,
    createDebouncedSearch,
  }
}
