import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { ConfigType, Config, ConfigField, ModelOption } from '@/types/config'
import { queryConfigs, addConfig, updateConfig, getModels } from '@/services/config'
import { configTypeMap } from '@/config/providerConfig'
import llmFactoriesData from '@/config/llm_factories.json'
import { useTable } from './useTable'

export function useConfigManager(configType: ConfigType) {
  const { t } = useI18n()
  
  // 使用统一的表格管理
  const {
    loading,
    data: configItems,
    pagination,
    handleTableChange,
    loadData: loadTableData,
    resetPagination
  } = useTable<Config>()

  // 状态
  const currentType = ref('')
  const editingConfigId = ref<string | null>(null)
  const activeTabKey = ref('1')
  const modelOptions = ref<ModelOption[]>([])
  
  // LLM 工厂数据
  const llmFactoryData = ref<Record<string, any>>({})
  const availableProviders = ref<Array<{ value: string; label: string }>>([])

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
      return availableProviders.value
    }
    return configTypeInfo.value.typeOptions || []
  })

  // 当前类型字段
  const currentTypeFields = computed((): ConfigField[] => {
    const typeFieldsMap = configTypeInfo.value.typeFields || {}
    
    if (configType === 'llm' && currentType.value && !typeFieldsMap[currentType.value]) {
      return typeFieldsMap['default'] || []
    }
    
    return typeFieldsMap[currentType.value] || []
  })

  /**
   * 初始化 LLM 工厂数据
   */
  function initLlmFactoriesData() {
    if (!llmFactoriesData || !llmFactoriesData.factory_llm_infos) {
      console.warn('llm_factories.json 数据格式不正确')
      return
    }

    const factoryData: any = {}
    const providers: Array<{ value: string; label: string }> = []

    llmFactoriesData.factory_llm_infos.forEach((factory: any) => {
      const providerName = factory.name
      providers.push({
        value: providerName,
        label: providerName,
      })

      // 按模型类型分组存储模型
      const modelsByType: any = {
        chat: [],
        embedding: [],
        vision: [],
      }

      if (factory.llm && Array.isArray(factory.llm)) {
        factory.llm.forEach((llm: any) => {
          let mappedModelType = llm.model_type

          // 映射模型类型
          if (mappedModelType === 'speech2text' || mappedModelType === 'image2text') {
            mappedModelType = 'vision'
          }

          // 只保留需要的模型类型
          if (['chat', 'embedding', 'vision'].includes(mappedModelType)) {
            modelsByType[mappedModelType].push({
              llm_name: llm.llm_name,
              model_type: mappedModelType,
              max_tokens: llm.max_tokens,
              is_tools: llm.is_tools || false,
              tags: llm.tags || '',
            })
          }
        })
      }

      factoryData[providerName] = modelsByType
    })

    llmFactoryData.value = factoryData
    availableProviders.value = providers
  }

  /**
   * 根据 provider 和 modelType 获取模型列表
   */
  function getModelsByProviderAndType(provider: string, modelType: string) {
    if (!llmFactoryData.value[provider]) {
      return []
    }
    return llmFactoryData.value[provider][modelType] || []
  }

  /**
   * 更新模型选项列表
   */
  function updateModelOptions(provider: string, modelType: string) {
    if (configType !== 'llm') {
      return
    }

    const models = getModelsByProviderAndType(provider, modelType)
    modelOptions.value = models.map((model: any) => ({
      value: model.llm_name,
      label: model.llm_name,
    }))
  }

  /**
   * 获取配置列表
   */
  async function getData() {
    await loadTableData(async ({ start, limit }) => {
      const res = await queryConfigs({
        start,
        limit,
        configType,
        ...queryForm.value,
      })
      // 转换数据格式以匹配 useTable 的期望
      return {
        code: res.code,
        data: res.data ? {
          list: res.data.list,
          total: res.data.total,
          pageNum: Math.ceil(res.data.total / limit),
          pageSize: limit
        } : undefined,
        message: res.message
      }
    })
  }

  /**
   * 删除配置
   */
  async function deleteConfig(configId: string) {
    loading.value = true
    try {
      const res = await updateConfig({
        configId: parseInt(configId),
        configType,
        state: '0',
      })

      if (res.code === 200) {
        message.success(t('config.deleteSuccess'))
        getData()
      } else {
        message.error(res.message || t('config.deleteFailed'))
      }
    } catch (error) {
      console.error('删除配置失败:', error)
      message.error(t('message.prompt.serverMaintenance'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 设置为默认配置
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
        getData()
      } else {
        message.error(res.message || t('common.setDefaultFailed'))
      }
    } catch (error) {
      console.error('设置默认配置失败:', error)
      message.error(t('message.prompt.serverMaintenance'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取API模型列表
   */
  async function fetchModels(formData: any) {
    if (!formData.apiKey || !formData.apiUrl) {
      return
    }

    try {
      const res = await getModels(formData)
      if (res.code === 200) {
        modelOptions.value = res.data.map((id: string) => ({
          value: id,
          label: id,
        }))
      }
    } catch (error) {
      console.error('获取模型列表失败:', error)
    }
  }

  // 初始化
  if (configType === 'llm') {
    initLlmFactoriesData()
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
    getData,
    deleteConfig,
    setAsDefault,
    updateModelOptions,
    getModelsByProviderAndType,
    fetchModels,
  }
}

