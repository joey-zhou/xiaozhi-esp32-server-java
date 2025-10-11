<template>
  <div class="template-container">
    <!-- 搜索区域 -->
    <a-card class="search-card">
      <a-form layout="inline">
        <a-form-item :label="t('template.templateName')">
          <a-input
            v-model:value="searchForm.templateName"
            :placeholder="t('template.enterTemplateName')"
            allow-clear
            style="width: 200px"
            @pressEnter="handleSearch"
          />
        </a-form-item>
        <a-form-item :label="t('template.category')">
          <a-select
            v-model:value="searchForm.category"
            :placeholder="t('common.all')"
            style="width: 150px"
            @change="handleSearch"
          >
            <a-select-option
              v-for="item in categoryOptions"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 表格区域 -->
    <a-card :title="t('template.templateList')" :bordered="false" style="margin-top: 16px">
      <template #extra>
        <a-button type="primary" @click="handleCreate">
          <template #icon><PlusOutlined /></template>
          {{ t('template.createTemplate') }}
        </a-button>
      </template>

      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 800 }"
        row-key="templateId"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'templateContent'">
            <a-tooltip :title="record.templateContent" placement="leftTop">
              <span>{{ record.templateContent }}</span>
            </a-tooltip>
          </template>

          <template v-else-if="column.key === 'isDefault'">
            <a-tag v-if="record.isDefault == 1" color="green">{{ t('common.default') }}</a-tag>
            <span v-else>-</span>
          </template>

          <template v-else-if="column.key === 'operation'">
            <a-space>
              <a @click="handleEdit(record)">{{ t('common.edit') }}</a>
              <a @click="handlePreview(record)">{{ t('common.view') }}</a>
              <a v-if="record.isDefault != 1" @click="handleSetDefault(record)">{{ t('common.setAsDefault') }}</a>
              <a-popconfirm
                :title="t('template.confirmDelete')"
                :ok-text="t('common.confirm')"
                :cancel-text="t('common.cancel')"
                @confirm="handleDelete(record)"
              >
                <a style="color: #ff4d4f">{{ t('common.delete') }}</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 创建/编辑对话框 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? t('template.editTemplate') : t('template.createTemplate')"
      :confirm-loading="modalLoading"
      :mask-closable="false"
      width="800px"
      @ok="handleSubmit"
      @cancel="handleModalCancel"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :label-col="{ span: 4 }"
        :wrapper-col="{ span: 20 }"
      >
        <a-form-item
          :label="t('template.templateName')"
          name="templateName"
          :rules="[{ required: true, message: t('template.enterTemplateName') }]"
        >
          <a-input v-model:value="formData.templateName" :placeholder="t('template.enterTemplateName')" />
        </a-form-item>

        <a-form-item
          :label="t('template.templateCategory')"
          name="category"
          :rules="[{ required: true, message: t('template.selectCategory') }]"
        >
          <a-select
            v-model:value="formData.category"
            :placeholder="t('template.selectCategory')"
            @change="handleCategoryChange"
          >
            <a-select-option
              v-for="category in categoryOptions"
              :key="category.value"
              :value="category.value"
            >
              {{ category.label }}
            </a-select-option>
            <a-select-option value="custom">{{ t('template.customCategory') }}</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item
          v-if="showCustomCategory"
          :label="t('template.customCategory')"
          name="customCategory"
          :rules="[{ required: true, message: t('template.enterCustomCategory') }]"
        >
          <a-input v-model:value="formData.customCategory" :placeholder="t('template.enterCustomCategory')" />
        </a-form-item>

        <a-form-item :label="t('template.templateDesc')" name="templateDesc">
          <a-input
            v-model:value="formData.templateDesc"
            :placeholder="t('template.enterTemplateDesc')"
          />
        </a-form-item>

        <a-form-item :label="t('common.isDefault')" name="isDefault">
          <a-switch v-model:checked="formData.isDefault" />
          <span style="margin-left: 8px; color: #999">{{ t('template.defaultTip') }}</span>
        </a-form-item>

        <a-form-item
          :label="t('template.templateContent')"
          name="templateContent"
          :rules="[{ required: true, message: t('template.enterTemplateContent') }]"
        >
          <a-textarea
            v-model:value="formData.templateContent"
            :rows="12"
            :placeholder="t('template.templateContentPlaceholder')"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 预览对话框 -->
    <a-modal
      v-model:open="previewVisible"
      :title="t('template.templatePreview')"
      :footer="null"
      width="800px"
    >
      <a-typography v-if="previewTemplate">
        <a-typography-title :level="3">
          {{ previewTemplate.templateName }}
        </a-typography-title>
        
        <a-typography-paragraph v-if="previewTemplate.templateDesc" type="secondary">
          <template #icon><InfoCircleOutlined /></template>
          {{ previewTemplate.templateDesc }}
        </a-typography-paragraph>
        
        <a-typography-paragraph>
          <a-space>
            <a-tag color="blue">{{ previewTemplate.category }}</a-tag>
            <a-tag v-if="previewTemplate.isDefault == 1" color="green">{{ t('template.defaultTemplate') }}</a-tag>
          </a-space>
        </a-typography-paragraph>
        
        <a-divider />
        
        <a-typography-title :level="5">{{ t('template.templateContent') }}</a-typography-title>
        <a-typography-paragraph>
          <blockquote class="template-preview-content">
            {{ previewTemplate.templateContent }}
          </blockquote>
        </a-typography-paragraph>
        
        <a-typography-paragraph v-if="previewTemplate.createTime" type="secondary" style="margin-top: 16px">
          <small>{{ t('common.createTime') }}：{{ previewTemplate.createTime }}</small>
        </a-typography-paragraph>
      </a-typography>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { PlusOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import type { FormInstance, TableProps } from 'ant-design-vue'
import type { PromptTemplate, TemplateFormData, CategoryOption } from '@/types/template'
import {
  queryTemplates,
  addTemplate,
  updateTemplate,
  deleteTemplate,
  setDefaultTemplate
} from '@/services/template'
import { useTable } from '@/composables/useTable'

const { t } = useI18n()

const columns = computed(() => [
  {
    title: t('template.templateName'),
    dataIndex: 'templateName',
    width: 100,
    align: 'center'
  },
  {
    title: t('template.category'),
    dataIndex: 'category',
    width: 120,
    align: 'center'
  },
  {
    title: t('template.templateContent'),
    dataIndex: 'templateContent',
    key: 'templateContent',
    width: 200,
    align: 'center'
  },
  {
    title: t('common.isDefault'),
    dataIndex: 'isDefault',
    key: 'isDefault',
    width: 80,
    align: 'center'
  },
  {
    title: t('common.createTime'),
    dataIndex: 'createTime',
    width: 180,
    align: 'center'
  },
  {
    title: t('table.action'),
    key: 'operation',
    width: 220,
    fixed: 'right',
    align: 'center'
  }
])

// 搜索表单
const searchForm = reactive({
  templateName: '',
  category: ''
})

// 默认分类选项
const defaultCategoryOptions = computed<CategoryOption[]>(() => [
  { label: t('common.all'), value: '' },
  { label: t('template.categoryBasic'), value: '基础角色' },
  { label: t('template.categoryProfessional'), value: '专业角色' },
  { label: t('template.categorySocial'), value: '社交角色' },
  { label: t('template.categoryEntertainment'), value: '娱乐角色' }
])

// 分类选项（包含动态加载的自定义分类）
const categoryOptions = ref<CategoryOption[]>([...defaultCategoryOptions.value])

// 使用表格组合式函数
const {
  data: dataSource,
  loading,
  pagination,
  handleTableChange: onTableChange,
  loadData
} = useTable<PromptTemplate>()

// 模态框相关
const modalVisible = ref(false)
const modalLoading = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const currentRecord = ref<PromptTemplate | null>(null)
const showCustomCategory = ref(false)

// 表单数据
const formData = reactive<TemplateFormData>({
  templateName: '',
  category: '',
  customCategory: '',
  templateDesc: '',
  templateContent: '',
  isDefault: false
})

// 预览相关
const previewVisible = ref(false)
const previewTemplate = ref<PromptTemplate | null>(null)

// 搜索
const handleSearch = () => {
  fetchData()
}

// 加载数据
const fetchData = async () => {
  await loadData(async ({ start, limit }) => {
    const res = await queryTemplates({
      ...searchForm,
      start,
      limit
    })
    
    // 更新分类选项
    if (res.data?.list) {
      const categories = new Set<string>()
      res.data.list.forEach((item: PromptTemplate) => {
        if (item.category) {
          categories.add(item.category)
        }
      })
      
      const defaultValues = defaultCategoryOptions.value.map(c => c.value)
      const customCategories = [...categories].filter(c => !defaultValues.includes(c) && c !== '')
      
      if (customCategories.length > 0) {
        categoryOptions.value = [
          ...defaultCategoryOptions.value,
          ...customCategories.map(c => ({ label: c, value: c }))
        ]
      } else {
        categoryOptions.value = [...defaultCategoryOptions.value]
      }
    }
    
    return res
  })
}

// 创建
const handleCreate = () => {
  isEdit.value = false
  modalVisible.value = true
  showCustomCategory.value = false
  resetForm()
  formData.category = '基础角色'
  formData.isDefault = false
}

// 编辑
const handleEdit = (record: PromptTemplate) => {
  isEdit.value = true
  modalVisible.value = true
  currentRecord.value = record
  
  // 检查是否需要显示自定义分类输入框
  const isCustomCategory = !categoryOptions.value.some(c => c.value === record.category)
  showCustomCategory.value = isCustomCategory
  
  // 填充表单
  formData.templateName = record.templateName
  formData.category = isCustomCategory ? 'custom' : record.category
  formData.customCategory = isCustomCategory ? record.category : ''
  formData.templateDesc = record.templateDesc || ''
  formData.templateContent = record.templateContent
  formData.isDefault = record.isDefault == 1
}

// 删除
const handleDelete = async (record: PromptTemplate) => {
  if (!record.templateId) return
  
  try {
    loading.value = true
    const res = await deleteTemplate(record.templateId)
    if (res.code === 200) {
      message.success(t('template.deleteSuccess'))
      await fetchData()
    } else {
      message.error(res.message || t('template.deleteFailed'))
    }
  } catch (error) {
    message.error(t('template.deleteFailed'))
  } finally {
    loading.value = false
  }
}

// 设为默认
const handleSetDefault = async (record: PromptTemplate) => {
  if (!record.templateId) return
  
  try {
    loading.value = true
    const res = await setDefaultTemplate(record.templateId)
    if (res.code === 200) {
      message.success(t('template.setDefaultSuccess'))
      await fetchData()
    } else {
      message.error(res.message || t('template.operationFailed'))
    }
  } catch (error) {
    message.error(t('template.operationFailed'))
  } finally {
    loading.value = false
  }
}

// 预览
const handlePreview = (record: PromptTemplate) => {
  previewTemplate.value = record
  previewVisible.value = true
}

// 处理分类变化
const handleCategoryChange = (value: string) => {
  showCustomCategory.value = value === 'custom'
  if (value !== 'custom') {
    formData.customCategory = ''
  }
}

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    
    modalLoading.value = true
    
    // 处理自定义分类
    let category = formData.category
    if (category === 'custom' && formData.customCategory) {
      category = formData.customCategory
    }
    
    // 构建请求数据
    const requestData: Partial<PromptTemplate> = {
      templateName: formData.templateName,
      templateDesc: formData.templateDesc || '',
      category: category,
      templateContent: formData.templateContent,
      isDefault: formData.isDefault ? 1 : 0,
      state: 1
    }
    
    // 如果是编辑模式，添加templateId
    if (isEdit.value && currentRecord.value?.templateId) {
      requestData.templateId = currentRecord.value.templateId
    }
    
    // 发送请求
    const res = isEdit.value
      ? await updateTemplate(requestData)
      : await addTemplate(requestData)
    
    if (res.code === 200) {
      message.success(isEdit.value ? t('template.updateSuccess') : t('template.createSuccess'))
      modalVisible.value = false
      await fetchData()
    } else {
      message.error(res.message || t('template.operationFailed'))
    }
  } catch (error) {
    console.error('表单验证失败:', error)
  } finally {
    modalLoading.value = false
  }
}

// 关闭模态框
const handleModalCancel = () => {
  modalVisible.value = false
  resetForm()
}

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields()
  formData.templateName = ''
  formData.category = ''
  formData.customCategory = ''
  formData.templateDesc = ''
  formData.templateContent = ''
  formData.isDefault = false
  showCustomCategory.value = false
}

// 表格变化处理
const handleTableChange: TableProps['onChange'] = (pag) => {
  onTableChange(pag)
}

await fetchData()
</script>

<style scoped>
.template-container {
  padding: 16px;
}

.search-card :deep(.ant-form-item) {
  margin-bottom: 0;
}

.template-preview-content {
  white-space: pre-wrap;
  background: var(--bg-secondary);
  padding: 16px 20px;
  border-left: 4px solid var(--primary-color);
  border-radius: 4px;
  max-height: 500px;
  overflow-y: auto;
  color: var(--text-color);
  line-height: 1.8;
  margin: 0;
}

blockquote.template-preview-content {
  margin: 8px 0;
}
</style>

