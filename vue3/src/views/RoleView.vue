<script setup lang="ts">
import { ref, reactive, nextTick, computed } from 'vue'
import { message, type FormInstance, type UploadProps } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { 
  UserOutlined, 
  LoadingOutlined, 
  CameraOutlined, 
  DeleteOutlined, 
  SnippetsOutlined,
  SoundOutlined
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useTable } from '@/composables/useTable'
import { useRoleManager } from '@/composables/useRoleManager'
import { queryRoles, addRole, updateRole, testVoice, queryTemplates } from '@/services/role'
import { getResourceUrl } from '@/utils/resource'
import { useAvatar } from '@/composables/useAvatar'
import { uploadAvatar } from '@/services/upload'
import type { Role, RoleFormData, PromptTemplate } from '@/types/role'
import type { TableColumnsType } from 'ant-design-vue'

const { t } = useI18n()
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
  getVoiceInfo,
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
  gender: ''
})

// 编辑状态
const editingRoleId = ref<number>()
const submitLoading = ref(false)

// 头像上传
const avatarUrl = ref('')
const avatarLoading = ref(false)

// 音色播放状态
const playingVoiceId = ref<string>('')
const voiceAudioCache = new Map<string, HTMLAudioElement>()

// 提示词模板
const promptEditorMode = ref<'custom' | 'template'>('custom')
const selectedTemplateId = ref<number>()
const promptTemplates = ref<PromptTemplate[]>([])
const templatesLoading = ref(false)

// 折叠面板展开状态
const modelAdvancedVisible = ref<string[]>([])
const vadAdvancedVisible = ref<string[]>([])

// 待设置的折叠面板值
const pendingVadValues = ref<any>(null)
const pendingModelValues = ref<any>(null)

// 表格列定义
const columns = computed<TableColumnsType>(() => [
  {
    title: t('common.avatar'),
    dataIndex: 'avatar',
    width: 80,
    align: 'center'
  },
  {
    title: t('device.roleName'),
    dataIndex: 'roleName',
    width: 120,
    align: 'center',
    ellipsis: {
      showTitle: false
    }
  },
  {
    title: t('device.roleDesc'),
    dataIndex: 'roleDesc',
    width: 200,
    align: 'center',
    ellipsis: {
      showTitle: false
    }
  },
  {
    title: t('device.voiceName'),
    dataIndex: 'voiceName',
    width: 200,
    align: 'center',
    ellipsis: {
      showTitle: false
    }
  },
  {
    title: t('device.modelName'),
    dataIndex: 'modelName',
    width: 200,
    align: 'center',
    ellipsis: {
      showTitle: false
    }
  },
  {
    title: t('device.sttName'),
    dataIndex: 'sttName',
    width: 150,
    align: 'center',
    ellipsis: {
      showTitle: false
    }
  },
  {
    title: t('device.totalDevice'),
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
    width: 180,
    align: 'center',
    fixed: 'right'
  }
])


// 加载角色列表
const fetchData = async () => {
  await loadData(async (params) => {
    const result = await queryRoles({
      start: params.start,
      limit: params.limit,
      roleName: searchForm.roleName || undefined
    })
    // 确保返回正确的数据结构
    if (result.code === 200 && result.data && 'list' in result.data) {
      return {
        code: result.code,
        data: {
          list: (result.data.list || []) as Role[],
          total: ('total' in result.data ? result.data.total : 0) as number
        },
        message: result.message
      }
    }
    return result as any
  })
}

// 防抖搜索
const debouncedSearch = createDebouncedSearch(fetchData, 500)

// 处理表格分页变化
const onTableChange = (pag: any) => {
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
  try {
    const res = await updateRole({ roleId, state: '0' })
    if (res.code === 200) {
      message.success(t('device.deleteRoleSuccess'))
      fetchData()
    } else {
      message.error(res.message || t('device.deleteRoleFailed'))
    }
  } catch (error) {
    console.error('删除角色失败:', error)
    message.error(t('device.deleteRoleFailed'))
  }
}

// 设为默认角色
const handleSetDefault = async (roleId: number) => {
  try {
    const res = await updateRole({ 
      roleId, 
      isDefault: 1,
    })
    if (res.code === 200) {
      message.success(t('device.setAsDefaultSuccess'))
      fetchData()
    } else {
      message.error(res.message || t('device.setAsDefaultFailed'))
    }
  } catch (error) {
    console.error('设置默认角色失败:', error)
    message.error(t('device.setAsDefaultFailed'))
  }
}

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    submitLoading.value = true

    // 获取语音信息以获取ttsId
    const voiceInfo = getVoiceInfo(formData.voiceName || '')
    
    const submitData: any = {
      ...formData,
      avatar: avatarUrl.value,
      isDefault: formData.isDefault ? 1 : 0,
      ttsId: voiceInfo?.ttsId || -1
    }

    if (editingRoleId.value) {
      submitData.roleId = editingRoleId.value
    }

    const res = editingRoleId.value 
      ? await updateRole(submitData)
      : await addRole(submitData)

    if (res.code === 200) {
      message.success(editingRoleId.value ? t('device.updateRoleSuccess') : t('device.createRoleSuccess'))
      resetForm()
      activeTabKey.value = '1'
      fetchData()
    } else {
      message.error(res.message || t('device.operationFailed'))
    }
  } catch (error: any) {
    console.error('提交表单失败:', error)
    if (error?.errorFields) {
      message.error(t('device.checkForm'))
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
    gender: ''
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

    playingVoiceId.value = voiceName

    // 检查缓存
    let audio = voiceAudioCache.get(voiceName)
    
    if (!audio) {
      // 获取音色信息
      const voiceInfo = getVoiceInfo(voiceName)
      if (!voiceInfo) {
        message.error(t('device.voiceNotFound'))
        playingVoiceId.value = ''
        return
      }

      // 调用测试接口获取音频URL
      const result: any = await testVoice({
        message: t('device.voiceTestMessage'),
        voiceName: voiceName,
        ttsId: voiceInfo.ttsId || -1,
        provider: voiceInfo.provider
      })

      if (result.code === 200 && result.data) {
        // 使用 getResourceUrl 处理音频路径
        const audioUrl = getResourceUrl(result.data)
        if (audioUrl) {
          // 创建音频对象
          audio = new Audio(audioUrl)
          voiceAudioCache.set(voiceName, audio)
        } else {
          message.error(t('device.audioUrlInvalid'))
          playingVoiceId.value = ''
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
          message.error(t('device.audioPlayFailed'))
          playingVoiceId.value = ''
          voiceAudioCache.delete(voiceName)
        }
      } else {
        message.error(t('device.getTestAudioFailed'))
        playingVoiceId.value = ''
        return
      }
    }

    // 播放音频
    if (audio) {
      await audio.play()
    }
  } catch (error: any) {
    console.error('播放音色失败:', error)
    message.error(error.message || t('device.playVoiceFailed'))
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
const uploadAvatarFile = (file: File): Promise<string> => {
  return uploadAvatar(file)
}

// 移除头像
const removeAvatar = () => {
  avatarUrl.value = ''
}

// 获取头像URL
const getAvatar = (avatar?: string) => {
  return getAvatarUrl(avatar)
}

// 加载提示词模板
const loadTemplates = async () => {
  try {
    templatesLoading.value = true
    const res = await queryTemplates({})
    if (res.code === 200 && res.data && 'list' in res.data) {
      promptTemplates.value = (res.data.list || []) as PromptTemplate[]
    }
  } catch (error) {
    console.error('加载模板列表失败:', error)
    message.error(t('device.loadTemplateFailed'))
  } finally {
    templatesLoading.value = false
  }
}

// 并行加载所有数据
await Promise.all([
  loadAllModels(),
  loadAllVoices(),
  loadSttOptions(),
  loadTemplates()
])

// 加载模板后，应用默认模板（新建时）
if (!editingRoleId.value) {
  const defaultTemplate = promptTemplates.value.find(t => t.isDefault == 1)
  if (defaultTemplate) {
    selectedTemplateId.value = defaultTemplate.templateId
    formData.roleDesc = defaultTemplate.templateContent
  }
}

// 加载角色列表
await fetchData()
</script>

<template>
  <div class="role-view">
    <!-- 查询表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('device.roleName')">
              <a-input
                v-model:value="searchForm.roleName"
                :placeholder="t('device.enterRoleName')"
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
        <a-tab-pane key="1" :tab="t('device.roleList')">
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
                  :title="record.voiceName ? (getVoiceInfo(record.voiceName)?.label || record.voiceName) : ''"
                  placement="top"
                >
                  <span v-if="record.voiceName" class="ellipsis-text">
                    {{ getVoiceInfo(record.voiceName)?.label || record.voiceName }}
                  </span>
                  <span v-else>-</span>
                </a-tooltip>
              </template>

              <!-- 模型 -->
              <template v-else-if="column.dataIndex === 'modelName'">
                <a-tooltip 
                  :title="getModelInfo(record.modelId)?.desc || (getModelInfo(record.modelId)?.label || record.modelName || t('device.unknownModel'))"
                  :mouse-enter-delay="0.5"
                  placement="top"
                >
                  <span v-if="record.modelId" class="ellipsis-text">
                    {{ getModelInfo(record.modelId)?.label || record.modelName || t('device.unknownModel') }}
                  </span>
                  <span v-else>-</span>
                </a-tooltip>
              </template>

              <!-- 语音识别 -->
              <template v-else-if="column.dataIndex === 'sttName'">
                <a-tooltip 
                  :title="record.sttId === -1 || record.sttId === null ? t('device.voskLocalRecognition') : (sttOptions.find(s => s.value === record.sttId)?.label || t('device.unknown'))"
                  placement="top"
                >
                  <span v-if="record.sttId === -1 || record.sttId === null" class="ellipsis-text">
                    {{ t('device.voskLocalRecognition') }}
                  </span>
                  <span v-else class="ellipsis-text">
                    {{ sttOptions.find(s => s.value === record.sttId)?.label || t('device.unknown') }}
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
                <a-space>
                  <a @click="handleEdit(record)">{{ t('common.edit') }}</a>
                  <a v-if="record.isDefault != 1" @click="handleSetDefault(record.roleId)">
                    {{ t('common.setAsDefault') }}
                  </a>
                  <a-popconfirm
                    :title="t('device.confirmDeleteRole')"
                    @confirm="handleDelete(record.roleId)"
                  >
                    <a style="color: #ff4d4f">{{ t('common.delete') }}</a>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- 创建/编辑角色 -->
        <a-tab-pane key="2" :tab="t('device.createRole')">
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
                  :label="t('device.roleName')"
                  name="roleName"
                  :rules="[{ required: true, message: t('device.enterRoleName') }]"
                >
                  <a-input
                    v-model:value="formData.roleName"
                    :placeholder="t('device.enterRoleName')"
                  />
                </a-form-item>
              </a-col>

              <a-col :span="24">
                <a-form-item :label="t('device.setAsDefaultRole')">
                  <a-switch v-model:checked="formData.isDefault" />
                  <span style="margin-left: 8px; color: #999">
                    {{ t('device.defaultRoleTip') }}
                  </span>
                </a-form-item>
              </a-col>
            </a-row>

            <!-- 对话模型设置 -->
            <a-divider orientation="left">{{ t('device.conversationModelSettings') }}</a-divider>

            <a-row :gutter="20">
              <a-col :span="24">
                <a-form-item :label="t('device.modelType')" name="modelType">
                  <a-radio-group
                    v-model:value="formData.modelType"
                    button-style="solid"
                    @change="handleModelTypeChange"
                  >
                    <a-radio-button value="llm">{{ t('device.llmModel') }}</a-radio-button>
                    <a-radio-button value="agent">{{ t('device.agent') }}</a-radio-button>
                  </a-radio-group>
                </a-form-item>
              </a-col>

              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('device.model')"
                  name="modelId"
                  :rules="[{ required: true, message: t('device.selectModel') }]"
                >
                  <a-select
    v-model:value="formData.modelId"
    :placeholder="t('device.selectModel')"
    :loading="modelLoading"
    show-search
    :filter-option="(input: string, option: any) => 
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
              <a-collapse-panel :header="t('device.conversationModelAdvanced')" key="advanced">
                <a-row :gutter="16">
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('device.temperature')"
                      name="temperature"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-tooltip placement="top">
                        <template #title>
                          <div v-html="t('device.temperatureTip').replace(/\n/g, '<br>')"></div>
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
                      :label="t('device.topP')"
                      name="topP"
                      :label-col="{ span: 10 }"
                      :wrapper-col="{ span: 14 }"
                    >
                      <a-tooltip placement="top">
                        <template #title>
                          <div v-html="t('device.topPTip').replace(/\n/g, '<br>')"></div>
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
            <a-divider orientation="left">{{ t('device.speechRecognitionSettings') }}</a-divider>

            <a-row :gutter="20">
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('device.speechRecognition')"
                  name="sttId"
                  :rules="[{ required: true, message: t('device.selectSpeechRecognition') }]"
                >
                  <a-select
                    v-model:value="formData.sttId"
                    :placeholder="t('device.selectSpeechRecognition')"
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
              <a-collapse-panel :header="t('device.speechRecognitionAdvanced')" key="vad">
                <a-row :gutter="16">
                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item
                      :label="t('device.speechThreshold')"
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
                      :label="t('device.silenceThreshold')"
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
                      :label="t('device.energyThreshold')"
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
                      :label="t('device.silenceDuration')"
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
            <a-divider orientation="left">{{ t('device.voiceSynthesisSettings') }}</a-divider>

            <a-row :gutter="20">
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('device.voiceName')"
                  name="voiceName"
                  :rules="[{ required: true, message: t('device.selectVoice') }]"
                >
                  <a-select
                    v-model:value="formData.voiceName"
                    :placeholder="t('device.selectVoice')"
                    :loading="voiceLoading"
                    show-search
                    :filter-option="(input: string, option: any) => 
                      option.label.toLowerCase().includes(input.toLowerCase())
                    "
                  >
                    <a-select-option
                      v-for="voice in allVoices"
                      :key="voice.value"
                      :value="voice.value"
                      :label="voice.label"
                    >
                      <div style="display: flex; align-items: center; justify-content: space-between;">
                        <span>{{ voice.label }}</span>
                        <a-button
                          type="text"
                          size="small"
                          :loading="playingVoiceId === voice.value"
                          @click.stop="handlePlayVoice(voice.value)"
                          style="margin-left: 8px; padding: 0 4px;"
                        >
                          <template #icon>
                            <LoadingOutlined v-if="playingVoiceId === voice.value" />
                            <SoundOutlined v-else />
                          </template>
                        </a-button>
                      </div>
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>


            <!-- 角色提示词 -->
            <a-divider orientation="left">{{ t('device.rolePrompt') }}</a-divider>

            <!-- 智能体提示 -->
            <a-alert
              v-if="formData.modelType === 'agent'"
              :message="t('device.agentPrompt')"
              :description="t('device.agentPromptDesc')"
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
                    <a-radio-button value="template">{{ t('device.useTemplate') }}</a-radio-button>
                    <a-radio-button value="custom">{{ t('device.custom') }}</a-radio-button>
                  </a-radio-group>

                  <template v-if="promptEditorMode === 'template'">
                    <a-select
                      v-model:value="selectedTemplateId"
                      style="width: 200px"
                      :placeholder="t('device.selectTemplate')"
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
                  <snippets-outlined /> {{ t('device.templateManagement') }}
                </a-button>
              </div>
            </template>

            <!-- 提示词输入 -->
            <a-form-item name="roleDesc">
              <a-textarea
                v-model:value="formData.roleDesc"
                :disabled="formData.modelType === 'agent'"
                :rows="10"
                :placeholder="t('device.enterRolePrompt')"
              />
            </a-form-item>

            <!-- 表单操作按钮 -->
            <a-form-item>
              <a-button type="primary" html-type="submit" :loading="submitLoading">
                {{ editingRoleId ? t('device.updateRole') : t('device.createRole') }}
              </a-button>
              <a-button style="margin-left: 8px" @click="handleCancel">
                {{ t('device.cancel') }}
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
  background-color: #fafafa;
  border: 1px dashed #d9d9d9;
  overflow: hidden;
  transition: all 0.3s;
}

.avatar-content:hover {
  border-color: #1890ff;
}

.avatar-placeholder {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #999;

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
  color: #8c8c8c;
  font-size: 12px;
}

// 折叠面板样式
:deep(.ant-collapse) {
  background: transparent;
}

:deep(.ant-collapse-borderless > .ant-collapse-item) {
  border-bottom: 1px dashed #e8e8e8;
}

:deep(.ant-collapse-borderless > .ant-collapse-item:last-child) {
  border-bottom: none;
}

// 折叠面板标题颜色（适配深色模式）
:deep(.ant-collapse-header) {
  color: var(--text-color) !important;
}

// 深色模式下的边框
html.dark :deep(.ant-collapse-borderless > .ant-collapse-item),
html[data-theme='dark'] :deep(.ant-collapse-borderless > .ant-collapse-item) {
  border-bottom-color: #434343;
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
