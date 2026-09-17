import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { ConfigType, Config, ConfigField, LlmModelOption, LLMModel, LLMFactory } from '@/types/config'
import { queryConfigs, updateConfig, deleteConfig as deleteConfigRequest } from '@/services/config'
import { configTypeMap } from '@/config/providerConfig'
import { CODE_CONFIRM_REQUIRED } from '@/constants/api'
import { i18n } from '@/locales'
import { useTable } from './useTable'
import { useConfirm } from './useConfirm'
import { useRequest } from './useRequest'

/**
 * llm_factories.json 的模块类型：只取类型、不产生运行时静态 import，
 * 真正加载走下面的动态 import，两者共用这份类型定义
 */
type LlmFactoriesModule = typeof import('@/config/llm_factories.json')

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

/**
 * 构建 LLM 工厂索引
 */
function buildLlmFactoryIndex(raw: LlmFactoriesModule | undefined): LLMFactoryIndex {
  if (!raw || !raw.factory_llm_infos) {
    console.warn('llm_factories.json 数据格式不正确')
    return EMPTY_LLM_FACTORY_INDEX
  }

  const models: Record<string, LLMFactoryModelInfo> = {}
  const urls: Record<string, string> = {}
  const ranks: Record<string, number> = {}
  const providers: Array<{ value: string; label: string }> = []

  raw.factory_llm_infos.forEach((factory: LLMFactory) => {
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

// llm_factories.json 有 233KB，只有配置 LLM 类型的页面才用得到，静态 import 会把它打进首屏必经的入口 chunk，
// 改成动态 import 按需加载。promise 直接当缓存用：并发的多个 useConfigManager('llm') 实例共享同一次加载，
// 加载成功后长期复用，不会重复请求
let llmFactoryIndexPromise: Promise<LLMFactoryIndex> | null = null

function loadLlmFactoryIndex(): Promise<LLMFactoryIndex> {
  if (!llmFactoryIndexPromise) {
    llmFactoryIndexPromise = import('@/config/llm_factories.json')
      .then((mod) => buildLlmFactoryIndex(mod))
      .catch((error) => {
        console.error('加载 llm_factories.json 失败', error)
        message.error(i18n.global.t('common.loadFailed'))
        // 允许下一次调用重新尝试加载，而不是把失败结果长期缓存住
        llmFactoryIndexPromise = null
        return EMPTY_LLM_FACTORY_INDEX
      })
  }
  return llmFactoryIndexPromise
}

export function useConfigManager(configType: ConfigType) {
  const { t } = useI18n()
  const { confirmAsync } = useConfirm()
  // 两个行内操作各自一个实例，失败文案互不干扰
  const { executeOk: executeDeleteConfig } = useRequest()
  const { executeOk: executeSetDefault } = useRequest()

  // 使用统一的表格管理：分页参数由 useTable 注入，查询条件从 queryForm 现取
  const {
    loading,
    data: configItems,
    pagination,
    loadError,
    retryLoad,
    fetchData,
    onTableChange,
    debouncedSearch,
  } = useTable<Config>(({ pageNo, pageSize }) => queryConfigs({
    pageNo,
    pageSize,
    configType,
    ...queryForm.value,
  }))

  // 状态
  const currentType = ref('')
  const editingConfigId = ref<number>()
  const activeTabKey = ref('1')
  const modelOptions = ref<LlmModelOption[]>([])

  // LLM 工厂数据：非 llm 类型的配置页永远用不到，保持空索引即可；
  // llm 类型异步加载，加载完成前 typeOptionsLoading 为 true，供下拉框显示加载态而不是空白无提示
  const llmFactory = ref<LLMFactoryIndex>(EMPTY_LLM_FACTORY_INDEX)
  const typeOptionsLoading = ref(configType === 'llm')
  // 记住最近一次被请求的 provider/modelType：如果用户在数据到位前就打开了编辑页，
  // 数据加载完成后据此重新算一次模型下拉选项，不然选项会一直空着
  let pendingModelSelection: { provider: string; modelType: string } | null = null

  if (configType === 'llm') {
    loadLlmFactoryIndex()
      .then((index) => {
        llmFactory.value = index
        if (pendingModelSelection) {
          updateModelOptions(pendingModelSelection.provider, pendingModelSelection.modelType)
        }
      })
      .finally(() => {
        typeOptionsLoading.value = false
      })
  }

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
      return llmFactory.value.providers
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
        const factoryUrl = llmFactory.value.urls[currentType.value]
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
      const factoryUrl = llmFactory.value.urls[currentType.value] || ''
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
    const providerData = llmFactory.value.models[provider]
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

    // 工厂数据还没加载完时也记下来，加载完成后会自动重放这次请求
    pendingModelSelection = { provider, modelType }
    const models = getModelsByProviderAndType(provider, modelType)
    modelOptions.value = models.map((model: LLMModel) => ({
      value: model.llm_name,
      label: model.llm_name,
    }))
  }

  /**
   * 删除配置（快速操作，只用 table loading）
   */
  async function deleteConfig(configId: number) {
    await submitDelete(configId, false)
  }

  /**
   * 删掉当前生效的对象存储配置会让历史文件不可访问，后端先拒一次，
   * 这里弹确认框、确认后带标记重发，与「设为默认」走同一套交互
   */
  async function submitDelete(configId: number, confirmStorageSwitch: boolean) {
    await executeDeleteConfig(() => deleteConfigRequest(configId, confirmStorageSwitch), {
      loadingRef: loading,
      showSuccess: true,
      successText: t('common.deleteSuccess'),
      errorText: t('common.deleteFailed'),
      onSuccess: () => fetchData(),
      onFailure: async (res) => {
        if (res.code !== CODE_CONFIRM_REQUIRED) {
          return false
        }
        const confirmed = await confirmAsync({
          title: t('config.storageSwitchTitle'),
          content: res.message,
          okText: t('config.storageSwitchOk'),
          okType: 'danger',
        })
        if (confirmed) {
          await submitDelete(configId, true)
        }
        return true
      },
    })
  }

  /**
   * 设置为默认配置（快速操作，只用 table loading）
   */
  async function setAsDefault(record: Config) {
    if (configType === 'tts') return

    await submitAsDefault(record, false)
  }

  /**
   * 发一次设为默认的请求。
   * 换掉当前生效的对象存储时后端先回待确认并带上存量条数，问过用户再带确认参数重发。
   */
  async function submitAsDefault(record: Config, confirmStorageSwitch: boolean) {
    await executeSetDefault(
      () => updateConfig({
        configId: record.configId,
        configType,
        modelType: configType === 'llm' ? record.modelType : undefined,
        isDefault: '1',
      }, confirmStorageSwitch),
      {
        loadingRef: loading,
        showSuccess: true,
        successText: t('common.setDefaultSuccess', { name: record.configName }),
        errorText: t('common.setDefaultFailed'),
        networkErrorText: t('common.serverMaintenance'),
        onSuccess: () => fetchData(),
        onFailure: async (res) => {
          if (res.code !== CODE_CONFIRM_REQUIRED) {
            return false
          }
          const confirmed = await confirmAsync({
            title: t('config.storageSwitchTitle'),
            content: res.message,
            okText: t('config.storageSwitchOk'),
            okType: 'danger',
          })
          if (confirmed) {
            await submitAsDefault(record, true)
          }
          return true
        },
      },
    )
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
    // 加载失败说明与重试，接到表格 emptyText 上区分「加载失败」与「真的没有数据」
    loadError,
    retryLoad,
    // llm 类型的工厂数据（233KB）异步加载中：true 时下拉框应显示加载态而不是当作"没有选项"
    typeOptionsLoading,

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
    onTableChange,
    debouncedSearch,
  }
}
