<script setup lang="ts">
import { ref, reactive, nextTick, computed } from 'vue'
import { message, Empty, type FormInstance, type UploadProps } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { 
  UserOutlined, 
  LoadingOutlined, 
  CameraOutlined, 
  DeleteOutlined, 
  SnippetsOutlined,
  SoundOutlined,
  PauseCircleOutlined
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useTable } from '@/composables/useTable'
import { useRoleManager } from '@/composables/useRoleManager'
import { useLoadingStore } from '@/store/loading'
import { queryRoles, addRole, updateRole, testVoice } from '@/services/role'
import { queryTemplates } from '@/services/template'
import { getResourceUrl } from '@/utils/resource'
import { useAvatar } from '@/composables/useAvatar'
import { uploadFile } from '@/services/upload'
import type { Role, RoleFormData, PromptTemplate } from '@/types/role'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import TableActionButtons from '@/components/TableActionButtons.vue'

const { t } = useI18n()
const loadingStore = useLoadingStore()
const { getAvatarUrl } = useAvatar()

const router = useRouter()

// 表格和分页
const { loading, data: roleList, pagination, handleTableChange, loadData, createDebouncedSearch } = useTable<Role>()


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
  getVoiceInfo
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
  voiceName: undefined,
  ttsId: undefined,
  gender: '',
  ttsPitch: 1.0,
  ttsSpeed: 1.0
})

// 编辑状态
const editingRoleId = ref<number>()
const submitLoading = ref(false)

// 头像上传
const avatarUrl = ref('')
const avatarLoading = ref(false)

// 音色播放状态
const playingVoiceId = ref<string>('')
const loadingVoiceId = ref<string>('') // loading状态（API请求期间）
const voiceAudioCache = new Map<string, HTMLAudioElement>()

// 提示词模板
const promptEditorMode = ref<'custom' | 'template'>('custom')
const selectedTemplateId = ref<number>()
const promptTemplates = ref<PromptTemplate[]>([])
const templatesLoading = ref(false)

const selectedTtsId = ref<number | string>('edge_default')
const selectedProvider = ref<string>('edge')

// 折叠面板展开状态
const modelAdvancedVisible = ref<string[]>([])
const vadAdvancedVisible = ref<string[]>([])
const ttsAdvancedVisible = ref<string[]>([])

// 待设置的折叠面板值
const pendingVadValues = ref<Record<string, number> | null>(null)
const pendingModelValues = ref<Record<string, number> | null>(null)
const pendingTtsValues = ref<Record<string, number> | null>(null)

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
    width: 200,
    align: 'center',
    fixed: 'right'
  }
])


// 加载角色列表
const fetchData = async () => {
  await loadData((params) => queryRoles({
    ...params,
    roleName: searchForm.roleName || undefined
  }))
}

// 防抖搜索
const debouncedSearch = createDebouncedSearch(fetchData, 500)

// 处理表格分页变化
const onTableChange = (pag: TablePaginationConfig) => {
  handleTableChange(pag)
  fetchData()
}

// 标签页切换
const handleTabChange = (key: string) => {
  activeTabKey.value = key
  if (key === '1') {
    fetchData()
  } else if (key === '2') {
    resetForm()
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
    const voiceInfo = getVoiceInfo(record.voiceName || undefined)

    // 准备折叠面板内的VAD参数值（延迟到用户展开时设置）
    pendingVadValues.value = {
      vadSpeechTh: record.vadSpeechTh ?? 0.5,
      vadSilenceTh: record.vadSilenceTh ?? 0.3,
      vadEnergyTh: record.vadEnergyTh ?? 0.01,
      vadSilenceMs: record.vadSilenceMs ?? 1200
    }

    // 准备折叠面板内的模型参数值（延迟到用户展开时设置）
    pendingModelValues.value = {
      temperature: record.temperature ?? 0.7,
      topP: record.topP ?? 0.9
    }

    // 准备折叠面板内的TTS参数值（延迟到用户展开时设置）
    pendingTtsValues.value = {
      ttsPitch: record.ttsPitch ?? 1.0,
      ttsSpeed: record.ttsSpeed ?? 1.0
    }

    // 设置表单基础值（不包括折叠面板内的值）
    Object.assign(formData, {
      roleName: record.roleName,
      roleDesc: record.roleDesc || '',
      avatar: record.avatar || '',
      isDefault: record.isDefault === '1',
      modelType: modelInfo?.type || 'llm',
      modelId: record.modelId,
      sttId: record.sttId ?? -1,
      voiceName: record.voiceName || '',
      ttsId: voiceInfo?.ttsId,
      gender: voiceInfo?.gender || ''
    })
  })
}

// 删除角色
const handleDelete = async (roleId: number) => {
  loading.value = true
  try {
    const res = await updateRole({ roleId, state: '0' })
    if (res.code === 200) {
      message.success(t('role.deleteRoleSuccess'))
      await fetchData()
    } else {
      message.error(res.message || t('role.deleteRoleFailed'))
    }
  } catch (error) {
    console.error('删除角色失败:', error)
    message.error(t('role.deleteRoleFailed'))
  } finally {
    loading.value = false
  }
}

// 设为默认角色
const handleSetDefault = async (roleId: number) => {
  loading.value = true
  try {
    const res = await updateRole({ 
      roleId, 
      isDefault: '1',
    })
    if (res.code === 200) {
      message.success(t('role.setAsDefaultSuccess'))
      await fetchData()
    } else {
      message.error(res.message || t('role.setAsDefaultFailed'))
    }
  } catch (error) {
    console.error('设置默认角色失败:', error)
    message.error(t('role.setAsDefaultFailed'))
  } finally {
    loading.value = false
  }
}

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    submitLoading.value = true

    // 统一处理：从所有可用音色中查找
    const voiceInfo = filteredVoices.value.find(v => v.value === formData.voiceName)
    const ttsId = voiceInfo?.ttsId || -1
    
    const submitData = {
      ...formData,
      avatar: avatarUrl.value,
      // 将 isDefault 布尔值转换为字符串 '1' 或 '0'
      isDefault: formData.isDefault ? '1' : '0',
      ttsId: ttsId
    }

    if (editingRoleId.value) {
      submitData.roleId = editingRoleId.value
    }

    // 1. 保存角色信息
    const res = editingRoleId.value 
      ? await updateRole(submitData)
      : await addRole(submitData)

    if (res.code === 200) {
      message.success(editingRoleId.value ? t('role.updateRoleSuccess') : t('role.createRoleSuccess'))
      resetForm()
      activeTabKey.value = '1'
      fetchData()
    } else {
      message.error(res.message || t('common.operationFailed'))
    }
  } catch (error: unknown) {
    console.error('提交表单失败:', error)
    if (error && typeof error === 'object' && 'errorFields' in error) {
      message.error(t('role.checkForm'))
    }
  } finally {
    submitLoading.value = false
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
  
  // 清空待设置的折叠面板值
  pendingVadValues.value = null
  pendingModelValues.value = null
  pendingTtsValues.value = null
  
  // 停止所有音频播放
  playingVoiceId.value = ''
  voiceAudioCache.forEach(audio => {
    audio.pause()
    audio.currentTime = 0
  })
  
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
    voiceName: undefined,
    ttsId: undefined,
    gender: '',
    ttsPitch: 1.0,
    ttsSpeed: 1.0
  })
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

// 音色类型切换
const handleVoiceTypeChange = () => {
  // 清空当前选择的音色
  formData.voiceName = undefined
  formData.ttsId = undefined
  formData.gender = ''
}

// 播放音色示例
const handlePlayVoice = async (voiceName: string) => {
  try {
    // 如果正在播放同一个音色，则停止
    if (playingVoiceId.value === voiceName) {
      const audio = voiceAudioCache.get(voiceName)
      if (audio) {
        audio.pause()
        audio.currentTime = 0
      }
      playingVoiceId.value = ''
      return
    }

    // 停止之前的播放
    if (playingVoiceId.value) {
      const prevAudio = voiceAudioCache.get(playingVoiceId.value)
      if (prevAudio) {
        prevAudio.pause()
        prevAudio.currentTime = 0
      }
    }

    // 设置loading状态（API请求期间）
    loadingVoiceId.value = voiceName

    // 检查缓存
    let audio = voiceAudioCache.get(voiceName)
    
    if (!audio) {
      // 统一处理：从所有可用音色中查找
      const voiceInfo = filteredVoices.value.find(v => v.value === voiceName)
      if (!voiceInfo) {
        message.error(t('role.voiceNotFound'))
        loadingVoiceId.value = ''
        return
      }

      const testParams = {
        message: t('role.voiceTestMessage'),
        voiceName: voiceName,
        ttsId: voiceInfo.ttsId || -1,
        provider: voiceInfo.provider,
        ttsPitch: formData.ttsPitch || 1.0,
        ttsSpeed: formData.ttsSpeed || 1.0
      }

      // 调用测试接口获取音频URL
      const result: any = await testVoice(testParams)
      
      // 清除loading状态
      loadingVoiceId.value = ''

      if (result.code === 200 && result.data) {
        // 使用 getResourceUrl 处理音频路径
        const audioUrl = getResourceUrl(result.data)
        if (audioUrl) {
          // 创建音频对象
          audio = new Audio(audioUrl)
          voiceAudioCache.set(voiceName, audio)
        } else {
          message.error(t('common.audioUrlInvalid'))
          return
        }
        
        // 监听播放结束
        audio.onended = () => {
          if (playingVoiceId.value === voiceName) {
            playingVoiceId.value = ''
          }
        }
        
        // 监听错误
        audio.onerror = () => {
          message.error(t('common.audioPlayFailed'))
          playingVoiceId.value = ''
          voiceAudioCache.delete(voiceName)
        }
      } else {
        message.error(t('role.getTestAudioFailed'))
        return
      }
    } else {
      // 清除loading状态
      loadingVoiceId.value = ''
    }

    // 播放音频
    if (audio) {
      await audio.play()
      // 播放成功后设置playing状态
      playingVoiceId.value = voiceName
    }
  } catch (error: unknown) {
    console.error('播放音色失败:', error)
    const errorMessage = error instanceof Error ? error.message : t('role.playVoiceFailed')
    message.error(errorMessage)
    loadingVoiceId.value = ''
    playingVoiceId.value = ''
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
  router.push('/template')
}

// 处理VAD折叠面板变化
const handleVadCollapseChange = (activeKeys: string | string[]) => {
  const keys = Array.isArray(activeKeys) ? activeKeys : [activeKeys]
  if (keys.includes('vad') && pendingVadValues.value) {
    nextTick(() => {
      Object.assign(formData, pendingVadValues.value)
      pendingVadValues.value = null
    })
  }
}

// 处理模型折叠面板变化
const handleModelCollapseChange = (activeKeys: string | string[]) => {
  const keys = Array.isArray(activeKeys) ? activeKeys : [activeKeys]
  if (keys.includes('advanced') && pendingModelValues.value) {
    nextTick(() => {
      Object.assign(formData, pendingModelValues.value)
      pendingModelValues.value = null
    })
  }
}

// 处理TTS折叠面板变化
const handleTtsCollapseChange = (activeKeys: string | string[]) => {
  const keys = Array.isArray(activeKeys) ? activeKeys : [activeKeys]
  if (keys.includes('tts') && pendingTtsValues.value) {
    nextTick(() => {
      Object.assign(formData, pendingTtsValues.value)
      pendingTtsValues.value = null
    })
  }
}

// 头像上传前检查
const beforeAvatarUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isImage) {
    message.error(t('common.onlyImageFiles'))
    return false
  }
  if (!isLt2M) {
    message.error(t('common.imageSizeLimit'))
    return false
  }

  avatarLoading.value = true
  uploadAvatarFile(file)
    .then(url => {
      avatarUrl.value = url
      avatarLoading.value = false
    })
    .catch(error => {
      message.error(t('common.avatarUploadFailed') + error)
      avatarLoading.value = false
    })

  return false
}

// 上传头像文件
const uploadAvatarFile = async (file: File): Promise<string> => {
  return await uploadFile(file, 'avatar') as string
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
const getVoiceDisplayName = (record: any) => {
  if (!record.voiceName) return ''
  
  // 统一处理：从所有可用音色中查找
  const voiceInfo = allVoices.value.find(v => v.value === record.voiceName)
  return voiceInfo?.label || record.voiceName
}

// 加载提示词模板
const loadTemplates = async () => {
  try {
    templatesLoading.value = true
    const res = await queryTemplates({})
    if (res.code === 200 && res.data) {
      promptTemplates.value = (res.data.list || []) as PromptTemplate[]
    }
  } catch (error) {
    console.error('加载模板列表失败:', error)
    message.error(t('role.loadTemplateFailed'))
  } finally {
    templatesLoading.value = false
  }
}

// 根据音色类型过滤音色选项
const filteredVoices = computed(() => {
  // 标准音色：显示所有标准音色
  return allVoices.value
})

// 初始化：并行加载所有数据（非阻塞式）
Promise.all([
  loadAllModels(),
  loadAllVoices(),
  loadSttOptions(),
  loadTemplates(),
  fetchData()
])

// 加载模板后，应用默认模板（新建时）
if (!editingRoleId.value) {
  const defaultTemplate = promptTemplates.value.find(t => t.isDefault == 1)
  if (defaultTemplate) {
    selectedTemplateId.value = defaultTemplate.templateId
    formData.roleDesc = defaultTemplate.templateContent
  }
}

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
            row-key="roleId"
            :columns="columns"
            :data-source="roleList"
            :loading="loading"
            :pagination="pagination"
            :scroll="{ x: 1000 }"
            size="middle"
            @change="onTableChange"
          >
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
                  :title="getModelInfo(record.modelId)?.desc || (getModelInfo(record.modelId)?.label || record.modelName || t('role.unknownModel'))"
                  :mouse-enter-delay="0.5"
                  placement="top"
                >
                  <span v-if="record.modelId" class="ellipsis-text">
                    {{ getModelInfo(record.modelId)?.label || record.modelName || t('role.unknownModel') }}
                  </span>
                  <span v-else>-</span>
                </a-tooltip>
              </template>

              <!-- 语音识别 -->
              <template v-else-if="column.dataIndex === 'sttName'">
                <a-tooltip 
                  :title="record.sttId === -1 || record.sttId === null ? t('role.voskLocalRecognition') : (sttOptions.find(s => s.value === record.sttId)?.label || t('common.unknown'))"
                  placement="top"
                >
                  <span v-if="record.sttId === -1 || record.sttId === null" class="ellipsis-text">
                    {{ t('role.voskLocalRecognition') }}
                  </span>
                  <span v-else class="ellipsis-text">
                    {{ sttOptions.find(s => s.value === record.sttId)?.label || t('common.unknown') }}
                  </span>
                </a-tooltip>
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
                  show-edit
                  show-set-default
                  show-delete
                  :is-default="record.isDefault == 1"
                  :delete-title="t('role.confirmDeleteRole')"
                  @edit="handleEdit"
                  @set-default="() => handleSetDefault(record.roleId)"
                  @delete="() => handleDelete(record.roleId)"
                />
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- 创建/编辑角色 -->
        <a-tab-pane key="2" :tab="t('role.createRole')">
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
                      accept=".jpg,.jpeg,.png,.gif"
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
                          <user-outlined />
                          <p>{{ t('common.clickToUpload') }}</p>
                        </div>

                        <div class="avatar-hover-mask">
                          <loading-outlined v-if="avatarLoading" />
                          <camera-outlined v-else />
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
                      <delete-outlined /> {{ t('common.removeAvatar') }}
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
                <a-form-item :label="t('role.setAsDefaultRole')">
                  <a-switch v-model:checked="formData.isDefault" />
                  <span style="margin-left: 8px; color: var(--ant-color-text-tertiary)">
                    {{ t('role.defaultRoleTip') }}
                  </span>
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
              @change="handleModelCollapseChange"
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
              @change="handleVadCollapseChange"
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
                        <span>{{ voice.label }}</span>
                        <a-button
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
              @change="handleTtsCollapseChange"
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
                  <snippets-outlined /> {{ t('role.templateManagement') }}
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
              <a-button type="primary" html-type="submit" :loading="submitLoading">
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

.search-card :deep(.ant-form-item) {
  margin-bottom: 0;
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
