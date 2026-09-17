<script setup lang="ts">
import { ref, reactive, nextTick, computed } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import {
  CameraOutlined,
  DeleteOutlined,
  LoadingOutlined,
  PauseCircleOutlined,
  SnippetsOutlined,
  SoundOutlined,
  UserOutlined
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useTable } from '@/composables/useTable'
import { useRoleManager } from '@/composables/useRoleManager'
import { useMemoryView } from '@/composables/useMemoryView'
import { useAudioPlayer } from '@/composables/useAudioPlayer'
import { useMcpToolSelection } from '@/composables/useMcpToolSelection'
import { useRequest } from '@/composables/useRequest'
import { useUserStore } from '@/store/user'
import { ROUTES } from '@/router/routes'
import { shouldIgnoreRequestError } from '@/services/request'
import { queryRoles, addRole, updateRole, deleteRole, updateToolsStatus } from '@/services/role'
import { queryTemplates } from '@/services/template'
import { useAvatar } from '@/composables/useAvatar'
import { useAvatarUpload } from '@/composables/useAvatarUpload'
import type { ModelOption, PromptTemplate, Role, RoleFormData } from '@/types/role'
import type { TableColumnsType } from 'ant-design-vue'
import TableActionButtons from '@/components/TableActionButtons.vue'
import TableEmptyState from '@/components/TableEmptyState.vue'

const { t } = useI18n()
const { getAvatarUrl } = useAvatar()
const userStore = useUserStore()

const router = useRouter()
const { navigateToMemory } = useMemoryView()

// 表格和分页：分页参数由 useTable 注入，查询条件从 searchForm 现取
const {
  loading,
  data: roleList,
  pagination,
  loadError,
  retryLoad,
  fetchData,
  onTableChange,
  debouncedSearch,
} = useTable<Role>((params) => queryRoles({
  ...params,
  roleName: searchForm.roleName || undefined,
}))


// 角色管理器
const {
  modelLoading,
  voiceLoading,
  sttLoading,
  allModels,
  allVoices,
  sttOptions,
  loadAllModels,
  loadAllVoices,
  loadSttOptions,
  getModelInfo,
  formatProviderName,
} = useRoleManager()

// 查询表单
const searchForm = reactive({
  roleName: ''
})

// Tab相关
const activeTabKey = ref('1')

// 表单相关
const formRef = ref<FormInstance>()
const formData = reactive<RoleFormData>({
  roleName: '',
  roleDesc: '',
  avatar: '',
  isDefault: false,
  modelType: 'llm',
  modelId: undefined,
  temperature: 0.7,
  topP: 0.9,
  sttId: -1,
  vadSpeechTh: 0.5,
  vadSilenceTh: 0.3,
  vadEnergyTh: 0.01,
  vadSilenceMs: 1200,
  inactiveTimeoutSeconds: 60,
  voiceName: undefined,
  ttsId: undefined,
  gender: '',
  ttsPitch: 1.0,
  ttsSpeed: 1.0,
  memoryType: 'window'
})

// 编辑状态
const editingRoleId = ref<number>()
const submitLoading = ref(false)
const lastInactiveTimeoutSeconds = ref(60)
const inactiveTimeoutEnabled = computed({
  get: () => formData.inactiveTimeoutSeconds > 0,
  set: (enabled: boolean) => {
    if (enabled) {
      formData.inactiveTimeoutSeconds = lastInactiveTimeoutSeconds.value
    } else {
      lastInactiveTimeoutSeconds.value = formData.inactiveTimeoutSeconds || 60
      formData.inactiveTimeoutSeconds = 0
    }
  }
})

// 头像上传：校验/上传/loading 收敛进 useAvatarUpload，这里只接收上传结果落到表单字段
const avatarUrl = ref('')
const { avatarAccept, avatarLoading, beforeAvatarUpload } = useAvatarUpload({
  onUploaded: (url) => {
    avatarUrl.value = url
  }
})

// 音色试听：播放/加载态与缓存交给 useAudioPlayer 统一管理，卸载时自动停止并释放
const {
  playingAudioId: playingVoiceId,
  loadingAudioId: loadingVoiceId,
  playAudioFromApi,
  stopAllAudio: stopAllVoicePreview,
} = useAudioPlayer()

// 试听音频缓存 key。语速/语调/指令改了就是另一段音频，必须一起进 key，否则会放出旧参数的录音
const voiceCacheKey = (voiceName: string) =>
  [voiceName, formData.ttsPitch ?? 1.0, formData.ttsSpeed ?? 1.0].join('|')

// 提示词模板
const promptEditorMode = ref<'custom' | 'template'>('custom')
const selectedTemplateId = ref<number>()
const promptTemplates = ref<PromptTemplate[]>([])
const { loading: templatesLoading, execute: executeTemplates } = useRequest()

const selectedProvider = ref<string>('')

// 行内操作与表单提交：每个请求一个实例，失败文案互不干扰
const { executeOk: executeDeleteRole } = useRequest()
const { executeOk: executeSetDefaultRole } = useRequest()
const { executeFull: executeSaveRole } = useRequest()

// 折叠面板展开状态
const modelAdvancedVisible = ref<string[]>([])
const vadAdvancedVisible = ref<string[]>([])
const ttsAdvancedVisible = ref<string[]>([])

// MCP 工具
const {
  allMcpTools,
  availableTools,
  buildExcludeTools,
  filterToolOption,
  formatToolName,
  loadTools,
  mcpToolsLoading,
  selectedToolNames,
} = useMcpToolSelection()

// 表格列定义
const columns = computed<TableColumnsType>(() => [
  {
    title: t('common.avatar'),
    dataIndex: 'avatar',
    width: 80,
    align: 'center'
  },
  {
    title: t('role.roleName'),
    dataIndex: 'roleName',
    width: 120,
    align: 'center'
  },
  {
    title: t('role.roleDesc'),
    dataIndex: 'roleDesc',
    width: 200,
    align: 'center'
  },
  {
    title: t('role.voiceName'),
    dataIndex: 'voiceName',
    width: 200,
    align: 'center'
  },
  {
    title: t('role.modelName'),
    dataIndex: 'modelName',
    width: 200,
    align: 'center'
  },
  {
    title: t('role.sttName'),
    dataIndex: 'sttName',
    width: 150,
    align: 'center'
  },
  {
    title: t('role.memoryTypeLabel'),
    dataIndex: 'memoryType',
    width: 120,
    align: 'center'
  },
  {
    title: t('role.totalDevice'),
    dataIndex: 'totalDevice',
    width: 100,
    align: 'center'
  },
  {
    title: t('common.isDefault'),
    dataIndex: 'isDefault',
    width: 100,
    align: 'center'
  },
  {
    title: t('table.action'),
    dataIndex: 'operation',
    width: 250,
    align: 'center',
    fixed: 'right'
  }
])


// 标签页切换
const handleTabChange = (key: string) => {
  activeTabKey.value = key
  if (key === '1') {
    fetchData()
  } else if (key === '2') {
    resetForm()
    // 切换到创建角色时，加载 MCP 工具列表
    loadTools(editingRoleId.value)
  }
}

// 编辑角色
const handleEdit = (record: Role) => {
  editingRoleId.value = record.roleId
  avatarUrl.value = record.avatar || ''
  activeTabKey.value = '2'
  
  // 编辑时默认使用自定义模式
  promptEditorMode.value = 'custom'

  nextTick(() => {
    // 获取模型信息
    const modelInfo = getModelInfo(record.modelId || undefined)

    // 获取语音信息
    const voiceInfo = allVoices.value.find(v => v.value === (record.voiceName || ''))

    // 设置表单所有值（包括高级设置的值）
    Object.assign(formData, {
      roleName: record.roleName,
      roleDesc: record.roleDesc || '',
      avatar: record.avatar || '',
      isDefault: record.isDefault === '1',
      modelType: modelInfo?.type || 'llm',
      modelId: record.modelId,
      temperature: record.temperature ?? 0.7,
      topP: record.topP ?? 0.9,
      sttId: record.sttId ?? -1,
      vadSpeechTh: record.vadSpeechTh ?? 0.5,
      vadSilenceTh: record.vadSilenceTh ?? 0.3,
      vadEnergyTh: record.vadEnergyTh ?? 0.01,
      vadSilenceMs: record.vadSilenceMs ?? 1200,
      inactiveTimeoutSeconds: record.inactiveTimeoutSeconds ?? 60,
      voiceName: record.voiceName || '',
      ttsId: voiceInfo?.ttsId,
      gender: voiceInfo?.gender || '',
      ttsPitch: record.ttsPitch ?? 1.0,
      ttsSpeed: record.ttsSpeed ?? 1.0,
      memoryType: record.memoryType || 'window'
    })
    lastInactiveTimeoutSeconds.value = record.inactiveTimeoutSeconds && record.inactiveTimeoutSeconds > 0
      ? record.inactiveTimeoutSeconds
      : 60

    // 加载 MCP 工具列表
    loadTools(editingRoleId.value)
  })
}

// 删除角色。sys_device.roleId 没有外键，后端在仍有设备绑定时拒绝删除，这里先拦一次
const handleDelete = async (record: Role) => {
  const boundDevices = record.totalDevice || 0
  if (boundDevices > 0) {
    message.warning(t('role.deleteRoleHasDevices', { count: boundDevices }))
    return
  }

  const removed = await executeDeleteRole(() => deleteRole(record.roleId), {
    loadingRef: loading,
    showSuccess: true,
    successText: t('role.deleteRoleSuccess'),
    errorText: t('role.deleteRoleFailed'),
  })

  if (removed) {
    await fetchData()
  }
}

// 设为默认角色
const handleSetDefault = async (roleId: number) => {
  const updated = await executeSetDefaultRole(() => updateRole({ roleId, isDefault: '1' }), {
    loadingRef: loading,
    showSuccess: true,
    successText: t('role.setAsDefaultSuccess'),
    errorText: t('role.setAsDefaultFailed'),
  })

  if (updated) {
    await fetchData()
  }
}

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value?.validate()

    // 统一处理：从所有可用音色中查找
    const voiceInfo = allVoices.value.find(v => v.value === formData.voiceName)
    const ttsId = voiceInfo?.ttsId || -1
    
    const submitData: Partial<RoleFormData> & { avatar?: string } = {
      ...formData,
      avatar: avatarUrl.value || '',
      // 将 isDefault 布尔值转换为字符串 '1' 或 '0'
      isDefault: formData.isDefault ? '1' : '0',
      ttsId: ttsId,
    }

    if (editingRoleId.value) {
      submitData.roleId = editingRoleId.value
    }

    // 1. 保存角色信息
    const { ok, data } = await executeSaveRole(
      () => (editingRoleId.value ? updateRole(submitData) : addRole(submitData)),
      { loadingRef: submitLoading },
    )

    if (!ok) {
      return
    }

    const savedRoleId = editingRoleId.value ?? data?.roleId

    // 2. 保存工具选择（使用 exclude 方式）
    if (savedRoleId && allMcpTools.value.length > 0) {
      try {
        await updateToolsStatus(savedRoleId, buildExcludeTools())
      } catch (error) {
        console.error('保存工具选择失败:', error)
        message.warning(t('role.mcpSaveFailed'))
      }
    }

    message.success(editingRoleId.value ? t('role.updateRoleSuccess') : t('role.createRoleSuccess'))
    resetForm()
    activeTabKey.value = '1'
    fetchData()
  } catch (error: unknown) {
    console.error('提交表单失败:', error)
    if (error && typeof error === 'object' && 'errorFields' in error) {
      message.error(t('role.checkForm'))
    }
  }
}

// 取消编辑
const handleCancel = () => {
  resetForm()
  activeTabKey.value = '1'
}

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields()
  editingRoleId.value = undefined
  avatarUrl.value = ''

  // 停止所有音频播放
  stopAllVoicePreview()

  // 新建时使用模板模式并应用默认模板
  promptEditorMode.value = 'template'
  const defaultTemplate = promptTemplates.value.find(t => t.isDefault == 1)
  if (defaultTemplate) {
    selectedTemplateId.value = defaultTemplate.templateId
    formData.roleDesc = defaultTemplate.templateContent
  } else {
    selectedTemplateId.value = undefined
    formData.roleDesc = ''
  }

  // 重置为默认值
  Object.assign(formData, {
    roleName: '',
    avatar: '',
    isDefault: false,
    modelType: 'llm',
    modelId: undefined,
    temperature: 0.7,
    topP: 0.9,
    sttId: -1,
    vadSpeechTh: 0.5,
    vadSilenceTh: 0.3,
    vadEnergyTh: 0.01,
    vadSilenceMs: 1200,
    inactiveTimeoutSeconds: 60,
    voiceName: undefined,
    ttsId: undefined,
    gender: '',
    ttsPitch: 1.0,
    ttsSpeed: 1.0,
    memoryType: 'window'
  })
  lastInactiveTimeoutSeconds.value = 60
}

// 模型类型变化
const handleModelTypeChange = () => {
  formData.modelId = undefined
  if (formData.modelType === 'agent') {
    formData.roleDesc = ''
  }
}

// 模型选择变化
const handleModelChange = (modelId: number | undefined) => {
  if (!modelId) return
  const modelInfo = getModelInfo(modelId)
  if (modelInfo && modelInfo.type === 'agent') {
    formData.roleDesc = modelInfo.agentDesc || ''
  }
}

// 播放音色示例
const handlePlayVoice = async (voiceName?: string) => {
  if (!voiceName) return

  // 统一处理：从所有可用音色中查找
  const voiceInfo = allVoices.value.find(v => v.value === voiceName)
  if (!voiceInfo) {
    message.error(t('role.voiceNotFound'))
    return
  }

  try {
    // 播放态/加载态/缓存都由 useAudioPlayer 处理，同一音色再点一次即停止
    await playAudioFromApi({
      voiceName,
      ttsId: voiceInfo.ttsId || -1,
      provider: voiceInfo.provider,
      audioId: voiceName,
      cacheKey: voiceCacheKey(voiceName),
      ttsPitch: formData.ttsPitch || 1.0,
      ttsSpeed: formData.ttsSpeed || 1.0,
    })
  } catch (error: unknown) {
    console.error('播放音色失败:', error)
    const errorMessage = error instanceof Error ? error.message : t('role.playVoiceFailed')
    message.error(errorMessage)
  }
}

// 提示词模式变化
const handlePromptModeChange = () => {
  if (promptEditorMode.value === 'template') {
    // 切换到模板模式时，如果没有选中模板，则选择默认模板
    if (!selectedTemplateId.value) {
      const defaultTemplate = promptTemplates.value.find(t => t.isDefault == 1)
      if (defaultTemplate) {
        selectedTemplateId.value = defaultTemplate.templateId
        formData.roleDesc = defaultTemplate.templateContent
      }
    } else {
      // 如果已选中模板，应用该模板
      const template = promptTemplates.value.find(t => t.templateId === selectedTemplateId.value)
      if (template) {
        formData.roleDesc = template.templateContent
      }
    }
  }
}

// 模板选择变化
const handleTemplateChange = (templateId: number) => {
  const template = promptTemplates.value.find(t => t.templateId === templateId)
  if (template) {
    formData.roleDesc = template.templateContent
  }
}

// 跳转到模板管理
const goToTemplateManager = () => {
  router.push(ROUTES.TEMPLATE)
}


// 移除头像
const removeAvatar = () => {
  avatarUrl.value = ''
}

// 获取头像URL
const getAvatar = (avatar?: string) => {
  return getAvatarUrl(avatar)
}


// 获取音色显示名称
const getVoiceDisplayName = (record: Role) => {
  if (!record.voiceName) return ''
  return voiceLabelMap.value.get(record.voiceName) || record.voiceName
}

// 获取记忆类型显示信息
const getMemoryTypeInfo = (memoryType?: string) => {
  switch (memoryType) {
    case 'window':
      return { label: t('device.windowMemory'), color: 'orange' }
    case 'summary':
      return { label: t('device.summaryMemory'), color: 'blue' }
    default:
      return { label: t('device.windowMemory'), color: 'orange' }
  }
}

// 列表加载失败提示。业务码失败交回的是响应体，静默保留原列表；只有传输层异常才提示，
// 切页取消的不提示，与 request.ts 拦截器共用 message key，避免同一次失败叠两条
const notifyLoadFailed = (error: unknown, messageKey: string) => {
  if (!(error instanceof Error) || shouldIgnoreRequestError(error)) return
  message.error({ content: t(messageKey), key: 'request-error' })
}

// 加载提示词模板
const loadTemplates = async () => {
  await executeTemplates(() => queryTemplates({ pageNo: 1, pageSize: 1000 }), {
    showError: false,
    // data 为空时保留原列表，不能让 undefined 盖掉
    onSuccess: (data) => {
      promptTemplates.value = data?.list ?? promptTemplates.value
    },
    onError: (error) => notifyLoadFailed(error, 'role.loadTemplateFailed'),
  })
}

// 列表每行都要查音色/模型/识别引擎，先建索引，避免模板里逐行做线性查找
const voiceLabelMap = computed(() => {
  const map = new Map<string, string>()
  allVoices.value.forEach(voice => {
    if (voice.value) map.set(voice.value, voice.label || voice.value)
  })
  return map
})

const modelOptionMap = computed(() => {
  const map = new Map<number, ModelOption>()
  allModels.value.forEach(model => map.set(model.value, model))
  return map
})

const sttLabelMap = computed(() => {
  const map = new Map<number, string>()
  sttOptions.value.forEach(stt => map.set(stt.value, stt.label))
  return map
})

// 列表行用：按 id 取模型信息
const getRowModelInfo = (modelId?: number | null) => {
  return modelId ? modelOptionMap.value.get(modelId) : undefined
}

// 列表行用：按 id 取语音识别名称
const getSttLabel = (sttId?: number | null) => {
  return (sttId == null ? undefined : sttLabelMap.value.get(sttId)) || t('common.unknown')
}

// 提供商选项
const providerOptions = computed(() => {
  const providers = new Set<string>()
  allVoices.value.forEach(v => {
    if (v.provider) providers.add(v.provider)
  })
  const items = Array.from(providers).map(p => ({ label: formatProviderName(p), value: p }))
  // prepend All option (empty value means all)
  return [{ label: t('common.all'), value: '' }, ...items]
})

// 根据提供商过滤音色选项
const filteredVoices = computed(() => {
  const list = allVoices.value
  return selectedProvider.value ? list.filter(v => v.provider === selectedProvider.value) : list
})

// 提供商切换：切换后清空已选音色
const handleProviderChange = () => {
  formData.voiceName = undefined
}

// 初始化：并行加载所有数据（非阻塞式）
Promise.all([
  loadAllModels(),
  loadAllVoices(),
  loadSttOptions(),
  loadTemplates(),
  fetchData()
])

</script>

<template>
  <div class="role-view">
    <!-- 查询表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('role.roleName')">
              <a-input
                v-model:value="searchForm.roleName"
                :placeholder="t('role.enterRoleName')"
                allow-clear
                @input="debouncedSearch"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 主内容 -->
    <a-card :bordered="false" :body-style="{ padding: '0 24px 24px 24px' }">
      <a-tabs
        v-model:active-key="activeTabKey"
        @change="handleTabChange"
      >
        <!-- 角色列表 -->
        <a-tab-pane key="1" :tab="t('role.roleList')">
          <a-table
            class="ellipsis-table"
            row-key="roleId"
            :columns="columns"
            :data-source="roleList"
            :loading="loading"
            :pagination="pagination"
            :scroll="{ x: 1000 }"
            size="middle"
            @change="onTableChange"
          >
            <template #emptyText>
              <TableEmptyState :error="loadError" @retry="retryLoad" />
            </template>

            <!-- 头像 -->
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'avatar'">
                <a-avatar :src="getAvatar(record.avatar)" icon="user" :size="40" />
              </template>

              <!-- 角色名称 -->
              <template v-else-if="column.dataIndex === 'roleName'">
                <a-tooltip :title="record.roleName" placement="top">
                  <span class="ellipsis-text">{{ record.roleName }}</span>
                </a-tooltip>
              </template>

              <!-- 角色描述 -->
              <template v-else-if="column.dataIndex === 'roleDesc'">
                <a-tooltip :title="record.roleDesc" :mouse-enter-delay="0.5" placement="topLeft">
                  <span v-if="record.roleDesc" class="ellipsis-text">{{ record.roleDesc }}</span>
                  <span v-else>-</span>
                </a-tooltip>
              </template>

              <!-- 音色 -->
              <template v-else-if="column.dataIndex === 'voiceName'">
                <a-tooltip 
                  :title="getVoiceDisplayName(record)"
                  placement="top"
                >
                  <span v-if="record.voiceName" class="ellipsis-text">
                    {{ getVoiceDisplayName(record) }}
                  </span>
                  <span v-else>-</span>
                </a-tooltip>
              </template>

              <!-- 模型 -->
              <template v-else-if="column.dataIndex === 'modelName'">
                <a-tooltip 
                  :title="getRowModelInfo(record.modelId)?.desc || (getRowModelInfo(record.modelId)?.label || record.modelName || t('role.unknownModel'))"
                  :mouse-enter-delay="0.5"
                  placement="top"
                >
                  <span v-if="record.modelId" class="ellipsis-text">
                    {{ getRowModelInfo(record.modelId)?.label || record.modelName || t('role.unknownModel') }}
                  </span>
                  <span v-else>-</span>
                </a-tooltip>
              </template>

              <!-- 语音识别 -->
              <template v-else-if="column.dataIndex === 'sttName'">
                <a-tooltip
                  :title="record.sttId === -1 || record.sttId === null ? t('role.voskLocalRecognition') : getSttLabel(record.sttId)"
                  placement="top"
                >
                  <span v-if="record.sttId === -1 || record.sttId === null" class="ellipsis-text">
                    {{ t('role.voskLocalRecognition') }}
                  </span>
                  <span v-else class="ellipsis-text">
                    {{ getSttLabel(record.sttId) }}
                  </span>
                </a-tooltip>
              </template>

              <!-- 记忆类型 -->
              <template v-else-if="column.dataIndex === 'memoryType'">
                <a-tag :color="getMemoryTypeInfo(record.memoryType).color">
                  {{ getMemoryTypeInfo(record.memoryType).label }}
                </a-tag>
              </template>

              <!-- 默认状态 -->
              <template v-else-if="column.dataIndex === 'isDefault'">
                <a-tag v-if="record.isDefault == 1" color="green">{{ t('common.default') }}</a-tag>
                <span v-else>-</span>
              </template>

              <!-- 操作 -->
              <template v-else-if="column.dataIndex === 'operation'">
                <TableActionButtons
                  :record="record"
                  permission-prefix="system:role"
                  show-edit
                  show-set-default
                  show-delete
                  :is-default="record.isDefault == 1"
                  :delete-title="t('role.confirmDeleteRole')"
                  @edit="() => handleEdit(record)"
                  @set-default="() => handleSetDefault(record.roleId)"
                  @delete="() => handleDelete(record)"
                >
                  <template #actions>
                    <a-button
                      v-permission="'system:role:memory'"
                      type="link"
                      size="small"
                      class="table-action-link"
                      @click="() => navigateToMemory({ roleId: record.roleId })"
                    >
                      {{ t('role.memory') }}
                    </a-button>
                  </template>
                </TableActionButtons>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- 创建/编辑角色 -->
        <a-tab-pane
          key="2"
          :tab="editingRoleId ? t('role.updateRole') : t('role.createRole')"
          v-if="userStore.hasPermission(editingRoleId ? 'system:role:update' : 'system:role:create')"
        >
          <a-form
            ref="formRef"
            :model="formData"
            layout="horizontal"
            :colon="false"
            @finish="handleSubmit"
            :hideRequiredMark="true"
          >
            <!-- 基本信息 -->
            <a-row :gutter="20">
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item :label="t('common.avatar')">
                  <div class="avatar-uploader-wrapper">
                    <a-upload
                      name="file"
                      :show-upload-list="false"
                      :before-upload="beforeAvatarUpload"
                      :accept="avatarAccept"
                      class="avatar-uploader"
                    >
                      <div class="avatar-content">
                        <a-avatar
                          v-if="avatarUrl"
                          :size="128"
                          :src="getAvatar(avatarUrl)"
                          icon="user"
                        />
                        <div v-else class="avatar-placeholder">
                          <UserOutlined />
                          <p>{{ t('common.clickToUpload') }}</p>
                        </div>

                        <div class="avatar-hover-mask">
                          <LoadingOutlined v-if="avatarLoading" />
                          <CameraOutlined v-else />
                          <p>{{ avatarUrl ? t('common.changeAvatar') : t('common.uploadAvatar') }}</p>
                        </div>
                      </div>
                    </a-upload>

                    <a-button
                      v-if="avatarUrl"
                      type="primary"
                      danger
                      size="small"
                      @click.stop="removeAvatar"
                      class="avatar-remove-btn"
                    >
                      <DeleteOutlined /> {{ t('common.removeAvatar') }}
                    </a-button>

                    <div class="avatar-tip">
                      {{ t('common.avatarTip') }}
                    </div>
                  </div>
                </a-form-item>
              </a-col>
              <a-col :span="24"></a-col>

              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('role.roleName')"
                  name="roleName"
                  :rules="[{ required: true, message: t('role.enterRoleName') }]"
                >
                  <a-input
                    v-model:value="formData.roleName"
                    :placeholder="t('role.enterRoleName')"
                  />
                </a-form-item>
              </a-col>

              <a-col :span="24">
                <a-form-item v-permission="'system:role:update'" :label="t('role.setAsDefaultRole')">
                  <a-switch v-model:checked="formData.isDefault" />
                  <span style="margin-left: 8px; color: var(--ant-color-text-tertiary)">
                    {{ t('role.defaultRoleTip') }}
                  </span>
                </a-form-item>
              </a-col>
            </a-row>

            <!-- 会话设置 -->
            <a-divider orientation="left">{{ t('role.sessionSettings') }}</a-divider>

            <a-row :gutter="20">
              <a-col :span="24">
                <a-form-item :label="t('role.inactiveAutoEnd')">
                  <a-switch v-model:checked="inactiveTimeoutEnabled" />
                  <span style="margin-left: 8px; color: var(--ant-color-text-tertiary)">
                    {{ t('role.inactiveAutoEndTip') }}
                  </span>
                </a-form-item>
              </a-col>

              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('role.inactiveDuration')"
                  name="inactiveTimeoutSeconds"
                  :rules="inactiveTimeoutEnabled ? [{ type: 'number', min: 10, max: 3600, message: t('role.inactiveDurationRange') }] : []"
                >
                  <a-input-number
                    v-model:value="formData.inactiveTimeoutSeconds"
                    :disabled="!inactiveTimeoutEnabled"
                    :min="10"
                    :max="3600"
                    :step="10"
                    style="width: 100%"
                  >
                    <template #addonAfter>{{ t('role.seconds') }}</template>
                  </a-input-number>
                </a-form-item>
              </a-col>
            </a-row>

            <!-- 对话模型设置 -->
            <a-divider orientation="left">{{ t('role.conversationModelSettings') }}</a-divider>

            <a-row :gutter="20">
              <a-col :span="24">
                <a-form-item :label="t('role.modelType')" name="modelType">
                  <a-radio-group
                    v-model:value="formData.modelType"
                    button-style="solid"
                    @change="handleModelTypeChange"
                  >
                    <a-radio-button value="llm">{{ t('role.llmModel') }}</a-radio-button>
                    <a-radio-button value="agent">{{ t('role.agent') }}</a-radio-button>
                  </a-radio-group>
                </a-form-item>
              </a-col>

              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('role.model')"
                  name="modelId"
                  :rules="[{ required: true, message: t('role.selectModel') }]"
                >
                  <a-select
                    v-model:value="formData.modelId"
                    :placeholder="t('role.selectModel')"
                    :loading="modelLoading"
                    show-search
                    :filter-option="(input: string, option: { label: string; value: number }) => 
                      option.label.toLowerCase().includes(input.toLowerCase())
                    "
                    @change="(value: number) => handleModelChange(value)"
                  >
                    <a-select-option
                      v-for="model in allModels.filter(m => m.type === formData.modelType)"
                      :key="model.value"
                      :value="model.value"
                      :label="model.label"
                    >
                      {{ model.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>

            <!-- 对话模型高级设置 -->
            <a-collapse
              v-model:active-key="modelAdvancedVisible"
              :bordered="false"
              style="background: transparent; margin-bottom: 24px"
            >
              <a-collapse-panel :header="t('role.conversationModelAdvanced')" key="advanced">
                <a-row :gutter="16">
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('role.temperature')"
                      name="temperature"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-tooltip placement="top">
                        <template #title>
                          <div v-html="t('role.temperatureTip').replace(/\n/g, '<br>')"></div>
                        </template>
                        <a-input-number
                          v-model:value="formData.temperature"
                          :min="0"
                          :max="2"
                          :step="0.1"
                          style="width: 100%"
                        />
                      </a-tooltip>
                    </a-form-item>
                  </a-col>

                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('role.topP')"
                      name="topP"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-tooltip placement="top">
                        <template #title>
                          <div v-html="t('role.topPTip').replace(/\n/g, '<br>')"></div>
                        </template>
                        <a-input-number
                          v-model:value="formData.topP"
                          :min="0"
                          :max="1"
                          :step="0.05"
                          style="width: 100%"
                        />
                      </a-tooltip>
                    </a-form-item>
                  </a-col>
                </a-row>
              </a-collapse-panel>
            </a-collapse>

            <!-- 语音识别设置 -->
            <a-divider orientation="left">{{ t('role.speechRecognitionSettings') }}</a-divider>

            <a-row :gutter="20">
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('role.speechRecognition')"
                  name="sttId"
                  :rules="[{ required: true, message: t('role.selectSpeechRecognition') }]"
                >
                  <a-select
                    v-model:value="formData.sttId"
                    :placeholder="t('role.selectSpeechRecognition')"
                    :loading="sttLoading"
                  >
                    <a-select-option
                      v-for="stt in sttOptions"
                      :key="stt.value"
                      :value="stt.value"
                    >
                      {{ stt.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>

            <!-- VAD高级设置 -->
            <a-collapse
              v-model:active-key="vadAdvancedVisible"
              :bordered="false"
              style="background: transparent; margin-bottom: 24px"
            >
              <a-collapse-panel :header="t('role.speechRecognitionAdvanced')" key="vad">
                <a-row :gutter="16">
                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('role.speechThreshold')"
                      name="vadSpeechTh"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-input-number
                        v-model:value="formData.vadSpeechTh"
                        :min="0"
                        :max="1"
                        :step="0.1"
                        style="width: 100%"
                      />
                    </a-form-item>
                  </a-col>

                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('role.silenceThreshold')"
                      name="vadSilenceTh"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-input-number
                        v-model:value="formData.vadSilenceTh"
                        :min="0"
                        :max="1"
                        :step="0.1"
                        style="width: 100%"
                      />
                    </a-form-item>
                  </a-col>

                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('role.energyThreshold')"
                      name="vadEnergyTh"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-input-number
                        v-model:value="formData.vadEnergyTh"
                        :min="0"
                        :max="1"
                        :step="0.01"
                        style="width: 100%"
                      />
                    </a-form-item>
                  </a-col>

                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('role.silenceDuration')"
                      name="vadSilenceMs"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-input-number
                        v-model:value="formData.vadSilenceMs"
                        :min="0"
                        :max="5000"
                        :step="100"
                        style="width: 100%"
                      />
                    </a-form-item>
                  </a-col>
                </a-row>
              </a-collapse-panel>
            </a-collapse>

            <!-- 语音合成设置 -->
            <a-divider orientation="left">{{ t('role.voiceSynthesisSettings') }}</a-divider>

            <!-- 音色选择 -->
            <a-row :gutter="20">
              <a-col :xl="6" :lg="8" :xs="24">
                <a-form-item>
                  <a-select
                    v-model:value="selectedProvider"
                    :placeholder="t('role.selectProvider')"
                    :loading="voiceLoading"
                    @change="handleProviderChange"
                  >
                    <a-select-option
                      v-for="p in providerOptions"
                      :key="p.value"
                      :value="p.value"
                      :label="p.label"
                    >
                      {{ p.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('role.voiceName')"
                  name="voiceName"
                  :rules="[{ required: true, message: t('role.selectVoice') }]"
                >
                  <a-select
                    v-model:value="formData.voiceName"
                    :placeholder="t('role.selectVoice')"
                    :loading="voiceLoading"
                    show-search
                    :filter-option="(input: string, option: { label: string; value: string }) => 
                      option.label.toLowerCase().includes(input.toLowerCase())
                    "
                  >
                    <a-select-option
                      v-for="voice in filteredVoices"
                      :key="voice.value"
                      :value="voice.value"
                      :label="voice.label"
                    >
                      <div style="display: flex; align-items: center; justify-content: space-between;">
                        <a-tag color="blue" v-if="voice.model">{{ voice.model }}</a-tag>
                        <span>{{ voice.label }}</span>
                        <a-button
                          v-permission="'system:role'"
                          type="text"
                          size="small"
                          :loading="loadingVoiceId === voice.value"
                          @click.stop="handlePlayVoice(voice.value)"
                          style="margin-left: 8px; padding: 0 4px;"
                        >
                          <template #icon>
                            <LoadingOutlined v-if="loadingVoiceId === voice.value" />
                            <PauseCircleOutlined v-else-if="playingVoiceId === voice.value" />
                            <SoundOutlined v-else />
                          </template>
                        </a-button>
                      </div>
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>

            <!-- 语音合成高级设置 -->
            <a-collapse
              v-model:active-key="ttsAdvancedVisible"
              :bordered="false"
              style="background: transparent; margin-bottom: 24px"
            >
              <a-collapse-panel :header="t('role.voiceSynthesisAdvanced')" key="tts">
                <a-row :gutter="16">
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('role.voiceSpeed')"
                      name="ttsSpeed"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-tooltip placement="top">
                        <template #title>
                          {{ t('role.voiceSpeedTip') }}
                        </template>
                        <a-slider
                          v-model:value="formData.ttsSpeed"
                          :min="0.5"
                          :max="2.0"
                          :step="0.1"
                          :marks="{ 0.5: '0.5', 1.0: '1.0', 2.0: '2.0' }"
                        />
                      </a-tooltip>
                    </a-form-item>
                  </a-col>

                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('role.voicePitch')"
                      name="ttsPitch"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-tooltip placement="top">
                        <template #title>
                          {{ t('role.voicePitchTip') }}
                        </template>
                        <a-slider
                          v-model:value="formData.ttsPitch"
                          :min="0.5"
                          :max="2.0"
                          :step="0.1"
                          :marks="{ 0.5: '0.5', 1.0: '1.0', 2.0: '2.0' }"
                        />
                      </a-tooltip>
                    </a-form-item>
                  </a-col>
                </a-row>
              </a-collapse-panel>
            </a-collapse>

            <div v-permission="'system:role:mcp-tools'">
              <!-- MCP 工具设置 -->
              <a-divider orientation="left">{{ t('role.mcpTools') }}</a-divider>
              <a-row :gutter="20">
                <a-col :xl="8" :lg="12" :xs="24">
                  <a-form-item :label="t('role.mcpTools')">
                    <a-select
                      v-model:value="selectedToolNames"
                      mode="multiple"
                      :placeholder="t('role.mcpSelectTools')"
                      :loading="mcpToolsLoading"
                      :max-tag-count="10"
                      :maxTagTextLength="10"
                      show-search
                      :filter-option="filterToolOption"
                      allow-clear
                    >
                      <a-select-option
                        v-for="tool in availableTools"
                        :key="tool.name"
                        :value="tool.name"
                        :label="formatToolName(tool.name)"
                      >
                        <a-tooltip :title="tool.description" placement="right">
                          <span>{{ formatToolName(tool.name) }}</span>
                        </a-tooltip>
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>
            </div>
            <!-- 记忆类型配置 -->
            <a-divider orientation="left">{{ t('role.memoryTypeSettings') }}</a-divider>

            <a-row :gutter="20">
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item :label="t('role.memoryTypeLabel')">
                  <a-select
                    v-model:value="formData.memoryType"
                    :placeholder="t('role.selectMemoryType')"
                  >
                    <a-select-option value="window">
                      {{ t('device.windowMemory') }}
                    </a-select-option>
                    <a-select-option value="summary">
                      {{ t('device.summaryMemory') }}
                    </a-select-option>
                  </a-select>
                  <div style="margin-top: 8px; color: var(--ant-color-text-tertiary); font-size: 12px">
                    {{ t('role.memoryTypeTip') }}
                  </div>
                </a-form-item>
              </a-col>
            </a-row>

            <!-- 角色提示词 -->
            <a-divider orientation="left">{{ t('role.rolePrompt') }}</a-divider>

            <!-- 智能体提示 -->
            <a-alert
              v-if="formData.modelType === 'agent'"
              :message="t('role.agentPrompt')"
              :description="t('role.agentPromptDesc')"
              type="info"
              show-icon
              style="margin-bottom: 16px"
            />

            <!-- 提示词编辑 -->
            <template v-else>
              <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
                <a-space>
                  <a-radio-group
                    v-model:value="promptEditorMode"
                    button-style="solid"
                    @change="handlePromptModeChange"
                  >
                    <a-radio-button value="template">{{ t('role.useTemplate') }}</a-radio-button>
                    <a-radio-button value="custom">{{ t('role.custom') }}</a-radio-button>
                  </a-radio-group>

                  <template v-if="promptEditorMode === 'template'">
                    <a-select
                      v-model:value="selectedTemplateId"
                      style="width: 200px"
                      :placeholder="t('role.selectTemplate')"
                      :loading="templatesLoading"
                      @change="handleTemplateChange"
                    >
                      <a-select-option
                        v-for="template in promptTemplates"
                        :key="template.templateId"
                        :value="template.templateId"
                      >
                        {{ template.templateName }}
                        <a-tag v-if="template.isDefault == 1" color="green" size="small">
                          {{ t('common.default') }}
                        </a-tag>
                      </a-select-option>
                    </a-select>
                  </template>
                </a-space>

                <a-button type="primary" @click="goToTemplateManager">
                  <SnippetsOutlined /> {{ t('role.templateManagement') }}
                </a-button>
              </div>
            </template>

            <!-- 提示词输入 -->
            <a-form-item name="roleDesc">
              <a-textarea
                v-model:value="formData.roleDesc"
                :disabled="formData.modelType === 'agent'"
                :rows="10"
                :placeholder="t('role.enterRolePrompt')"
              />
            </a-form-item>

            <!-- 表单操作按钮 -->
            <a-form-item>
              <a-button
                v-permission="editingRoleId ? 'system:role:update' : 'system:role:create'"
                type="primary"
                html-type="submit"
                :loading="submitLoading"
              >
                {{ editingRoleId ? t('role.updateRole') : t('role.createRole') }}
              </a-button>
              <a-button style="margin-left: 8px" @click="handleCancel">
                {{ t('role.cancel') }}
              </a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 回到顶部 -->
    <a-back-top />
  </div>
</template>

<style scoped lang="scss">
.role-view {
  padding: 24px;
}

// 头像上传样式
.avatar-uploader-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.avatar-uploader {
  cursor: pointer;
}

.avatar-content {
  position: relative;
  width: 128px;
  height: 128px;
  border-radius: 64px;
  background-color: var(--ant-color-fill-quaternary);
  border: 1px dashed var(--ant-color-border);
  overflow: hidden;
  transition: all 0.3s;
}

.avatar-content:hover {
  border-color: var(--ant-color-primary);
}

.avatar-placeholder {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: var(--ant-color-text-tertiary);

  .anticon {
    font-size: 32px;
    margin-bottom: 8px;
  }

  p {
    margin: 0;
  }
}

.avatar-hover-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;
  opacity: 0;
  transition: opacity 0.3s;

  .anticon {
    font-size: 24px;
    margin-bottom: 8px;
  }

  p {
    margin: 0;
  }
}

.avatar-content:hover .avatar-hover-mask {
  opacity: 1;
}

.avatar-remove-btn {
  margin-top: 8px;
}

.avatar-tip {
  margin-top: 8px;
  color: var(--ant-color-text-tertiary);
  font-size: 12px;
}

// 折叠面板样式
:deep(.ant-collapse) {
  background: transparent;
}

:deep(.ant-collapse-borderless > .ant-collapse-item) {
  border-bottom: 1px dashed var(--ant-color-border);
}

:deep(.ant-collapse-borderless > .ant-collapse-item:last-child) {
  border-bottom: none;
}

// 折叠面板标题颜色（适配深色模式）
:deep(.ant-collapse-header) {
  color: var(--ant-color-text) !important;
}

// 使用 Ant Design 变量，无需特殊处理

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

:deep(.ant-select-selection-item-content) {
  max-width: 100px; // 设置最大宽度
}

</style>
