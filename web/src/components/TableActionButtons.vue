<template>
  <a-space :size="size">
    <!-- 编辑按钮 -->
    <a v-if="showEdit && hasPermission('edit')" @click="handleEdit">
      {{ editText || t('common.edit') }}
    </a>
    
    <!-- 查看按钮 -->
    <a v-if="showView && hasPermission('view')" @click="handleView">
      {{ viewText || t('common.view') }}
    </a>
    
    <!-- 下载按钮 -->
    <a v-if="showDownload && hasPermission('download')" @click="handleDownload">
      {{ downloadText || t('common.download') }}
    </a>
    
    <!-- 复制按钮 -->
    <a v-if="showCopy && hasPermission('copy')" @click="handleCopy">
      {{ copyText || t('common.copy') }}
    </a>
    
    <!-- 设为默认按钮 -->
    <a 
      v-if="showSetDefault && hasPermission('setDefault') && !isDefault" 
      @click="handleSetDefault"
    >
      {{ setDefaultText || t('common.setAsDefault') }}
    </a>
    
    <!-- 自定义操作插槽（在删除之前） -->
    <slot name="actions" :record="record" />
    
    <!-- 分隔线（如果有删除按钮） -->
    <template v-if="showDelete && hasPermission('delete') && hasAnyVisibleButton">
      <a-divider v-if="showDivider" type="vertical" />
    </template>
    
    <!-- 删除按钮（带确认） -->
    <a-popconfirm
      v-if="showDelete && hasPermission('delete')"
      :title="deleteTitle || t('common.confirmDelete')"
      :ok-text="t('common.confirm')"
      :cancel-text="t('common.cancel')"
      :ok-type="deleteOkType"
      :placement="deletePopconfirmPlacement"
      @confirm="handleDelete"
    >
      <a :class="deleteClass">
        {{ deleteText || t('common.delete') }}
      </a>
    </a-popconfirm>
    
    <!-- 更多操作下拉菜单 -->
    <a-dropdown v-if="moreActions && moreActions.length > 0" :trigger="['click']">
      <a @click.prevent>
        {{ moreText || t('common.more') }}
        <DownOutlined />
      </a>
      <template #overlay>
        <a-menu @click="handleMoreAction">
          <a-menu-item
            v-for="action in visibleMoreActions"
            :key="action.key"
            :disabled="action.disabled"
            :danger="action.danger"
          >
            <component :is="action.icon" v-if="action.icon" style="margin-right: 8px" />
            {{ action.label }}
          </a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>
    
    <!-- 额外操作插槽（在最后） -->
    <slot name="extra" :record="record" />
  </a-space>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DownOutlined } from '@ant-design/icons-vue'
import type { Component } from 'vue'

/**
 * 更多操作项
 */
export interface MoreAction {
  /** 操作唯一键 */
  key: string
  /** 操作标签 */
  label: string
  /** 操作图标 */
  icon?: Component
  /** 是否禁用 */
  disabled?: boolean
  /** 是否为危险操作 */
  danger?: boolean
  /** 是否显示 */
  visible?: boolean
  /** 权限标识 */
  permission?: string
}

/**
 * 权限配置
 */
export interface PermissionConfig {
  edit?: boolean
  view?: boolean
  delete?: boolean
  download?: boolean
  copy?: boolean
  setDefault?: boolean
  [key: string]: boolean | undefined
}

export interface Props {
  /** 当前行数据 */
  record?: any
  
  /** 是否显示编辑按钮 */
  showEdit?: boolean
  /** 是否显示查看按钮 */
  showView?: boolean
  /** 是否显示删除按钮 */
  showDelete?: boolean
  /** 是否显示下载按钮 */
  showDownload?: boolean
  /** 是否显示复制按钮 */
  showCopy?: boolean
  /** 是否显示设为默认按钮 */
  showSetDefault?: boolean
  
  /** 是否为默认项（用于设为默认按钮） */
  isDefault?: boolean
  
  /** 自定义编辑按钮文本 */
  editText?: string
  /** 自定义查看按钮文本 */
  viewText?: string
  /** 自定义删除按钮文本 */
  deleteText?: string
  /** 自定义下载按钮文本 */
  downloadText?: string
  /** 自定义复制按钮文本 */
  copyText?: string
  /** 自定义设为默认按钮文本 */
  setDefaultText?: string
  /** 自定义更多按钮文本 */
  moreText?: string
  
  /** 删除确认标题 */
  deleteTitle?: string
  /** 删除按钮类名 */
  deleteClass?: string
  /** 删除确认框位置 */
  deletePopconfirmPlacement?: 'top' | 'left' | 'right' | 'bottom' | 'topLeft' | 'topRight' | 'bottomLeft' | 'bottomRight' | 'leftTop' | 'leftBottom' | 'rightTop' | 'rightBottom'
  /** 删除按钮确认类型 */
  deleteOkType?: 'default' | 'primary' | 'dashed' | 'link' | 'text' | 'danger'
  
  /** 是否显示分隔线 */
  showDivider?: boolean
  
  /** 按钮间距 */
  size?: 'small' | 'middle' | 'large' | number
  
  /** 更多操作 */
  moreActions?: MoreAction[]
  
  /** 权限配置 */
  permissions?: PermissionConfig
}

const props = withDefaults(defineProps<Props>(), {
  showEdit: false,
  showView: false,
  showDelete: false,
  showDownload: false,
  showCopy: false,
  showSetDefault: false,
  isDefault: false,
  deleteClass: 'delete-link',
  deletePopconfirmPlacement: 'topRight',
  deleteOkType: 'danger',
  showDivider: true,
  size: 'small',
  permissions: () => ({})
})

export interface Emits {
  (e: 'edit', record: any): void
  (e: 'view', record: any): void
  (e: 'delete', record: any): void
  (e: 'download', record: any): void
  (e: 'copy', record: any): void
  (e: 'setDefault', record: any): void
  (e: 'more', action: string, record: any): void
}

const emit = defineEmits<Emits>()

const { t } = useI18n()

/**
 * 检查权限
 */
const hasPermission = (action: string): boolean => {
  // 如果没有配置权限，默认允许
  if (!props.permissions || Object.keys(props.permissions).length === 0) {
    return true
  }
  
  // 检查具体权限
  return props.permissions[action] !== false
}

/**
 * 是否有任何可见按钮（用于判断是否显示分隔线）
 */
const hasAnyVisibleButton = computed(() => {
  return (
    (props.showEdit && hasPermission('edit')) ||
    (props.showView && hasPermission('view')) ||
    (props.showDownload && hasPermission('download')) ||
    (props.showCopy && hasPermission('copy')) ||
    (props.showSetDefault && hasPermission('setDefault') && !props.isDefault)
  )
})

/**
 * 过滤后的更多操作列表
 */
const visibleMoreActions = computed(() => {
  if (!props.moreActions) return []
  
  return props.moreActions.filter(action => {
    // 检查 visible 属性
    if (action.visible === false) return false
    
    // 检查权限
    if (action.permission && !hasPermission(action.permission)) {
      return false
    }
    
    return true
  })
})

// 事件处理
const handleEdit = () => emit('edit', props.record)
const handleView = () => emit('view', props.record)
const handleDelete = () => emit('delete', props.record)
const handleDownload = () => emit('download', props.record)
const handleCopy = () => emit('copy', props.record)
const handleSetDefault = () => emit('setDefault', props.record)
const handleMoreAction = ({ key }: { key: string }) => {
  emit('more', key, props.record)
}
</script>

<style scoped lang="scss">
.delete-link {
  color: var(--ant-color-error);
  
  &:hover {
    color: var(--ant-color-error-hover);
  }
}
</style>

