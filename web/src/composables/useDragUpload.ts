import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

import type { FileValidator } from '@/utils/fileValidators'

/**
 * 拖拽上传配置
 */
export interface DragUploadOptions {
  /**
   * 文件验证器；拖拽入口没有文件选择器，用不上规则表的 accept
   */
  validator: Pick<FileValidator, 'validate'>

  /**
   * 文件处理回调
   * @param file 上传的文件
   * @returns 返回 false 表示未受理该文件，此时不弹成功提示
   */
  onDrop: (file: File) => void | boolean | Promise<void | boolean>

  /**
   * 是否启用拖拽上传
   * @returns 返回 true 表示启用，false 表示禁用
   */
  enabled?: () => boolean

  /**
   * 拖拽提示文本配置
   */
  messages?: {
    /** 主提示文本的 i18n key */
    dragText?: string
    /** 辅助提示文本的 i18n key */
    dragHint?: string
    /** 成功提示文本的 i18n key */
    successMessage?: string
  }

  /**
   * 是否显示成功提示
   */
  showSuccessMessage?: boolean
}

/**
 * 通用拖拽上传 Composable
 *
 * 提供全局文件拖拽上传功能，支持自定义文件验证和处理逻辑
 *
 * @example
 * ```ts
 * // 音频文件上传：onDrop 必须把处理结果返回出来，返回 false 时不弹成功提示
 * const { isDragging } = useDragUpload({
 *   validator: fileValidators.audio,
 *   onDrop: (file) => handleFileUpload(file),
 *   enabled: () => activeTab.value === 'upload',
 *   messages: {
 *     dragText: 'common.dragDropFile',
 *     dragHint: 'common.dragDropFile',
 *     successMessage: 'common.saveSuccess'
 *   }
 * })
 * ```
 */
export function useDragUpload(options: DragUploadOptions) {
  const { t } = useI18n()

  const {
    validator,
    onDrop,
    enabled = () => true,
    messages = {},
    showSuccessMessage = true
  } = options

  // 拖拽状态
  const isDragging = ref(false)
  let dragCounter = 0

  // 拖拽提示文本
  const dragText = computed(() =>
    messages.dragText ? t(messages.dragText) : t('common.dragDropFile')
  )

  const dragHint = computed(() =>
    messages.dragHint ? t(messages.dragHint) : ''
  )

  /**
   * 处理拖拽进入
   */
  const handleDragEnter = (e: DragEvent) => {
    e.preventDefault()
    e.stopPropagation()

    // 计数必须先于 enabled 判断，否则拖拽途中 enabled 翻转会让 enter/leave 配不上对
    dragCounter++

    // enabled 只决定是否显示遮罩
    if (!enabled()) {
      isDragging.value = false
      return
    }

    // 检查是否包含文件
    if (e.dataTransfer) {
      const types = e.dataTransfer.types
      const hasFiles = types.includes('Files') ||
                       types.includes('application/x-moz-file') ||
                       types.some(type =>
                         type.startsWith('application/') ||
                         type.startsWith('image/') ||
                         type.startsWith('audio/')
                       )

      if (hasFiles || e.dataTransfer.items?.length > 0) {
        isDragging.value = true
      }
    }
  }

  /**
   * 处理拖拽悬停
   */
  const handleDragOver = (e: DragEvent) => {
    e.preventDefault()
    e.stopPropagation()

    // enabled 只决定是否显示遮罩
    if (!enabled()) {
      isDragging.value = false
      return
    }

    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'copy'
    }

    // 确保拖拽状态保持
    if (dragCounter > 0 && !isDragging.value) {
      isDragging.value = true
    }
  }

  /**
   * 处理拖拽离开
   */
  const handleDragLeave = (e: DragEvent) => {
    e.preventDefault()
    e.stopPropagation()

    // 计数必须先于 enabled 判断；下限取 0，防止拖拽途中挂载导致 leave 多于 enter
    dragCounter = Math.max(0, dragCounter - 1)

    if (dragCounter === 0 || !enabled()) {
      isDragging.value = false
    }
  }

  /**
   * 处理文件放置
   */
  const handleDrop = async (e: DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    dragCounter = 0
    isDragging.value = false

    // 检查是否启用
    if (!enabled()) {
      return
    }

    const files = e.dataTransfer?.files
    if (files && files.length > 0) {
      const file = files[0] // 只取第一个文件

      // 确保文件存在
      if (!file) return

      // 验证文件
      const validationResult = validator.validate(file)
      if (validationResult !== true) {
        message.error(t(validationResult))
        return
      }

      // 处理文件
      try {
        // onDrop 返回 false 表示未受理，不能再弹成功提示
        if (await onDrop(file) === false) {
          return
        }

        // 显示成功提示
        if (showSuccessMessage && messages.successMessage) {
          message.success(t(messages.successMessage))
        }
      } catch (error) {
        console.error('File upload error:', error)
        const errorMessage = error instanceof Error ? error.message : t('common.operationFailed')
        message.error(errorMessage)
      }
    }
  }

  /**
   * 安装全局事件监听器
   */
  const install = () => {
    // 重置状态
    dragCounter = 0
    isDragging.value = false

    document.addEventListener('dragenter', handleDragEnter)
    document.addEventListener('dragover', handleDragOver)
    document.addEventListener('dragleave', handleDragLeave)
    document.addEventListener('drop', handleDrop)
  }

  /**
   * 卸载全局事件监听器
   */
  const uninstall = () => {
    document.removeEventListener('dragenter', handleDragEnter)
    document.removeEventListener('dragover', handleDragOver)
    document.removeEventListener('dragleave', handleDragLeave)
    document.removeEventListener('drop', handleDrop)

    // 清理状态
    dragCounter = 0
    isDragging.value = false
  }

  // 自动安装和卸载
  onMounted(install)
  onBeforeUnmount(uninstall)

  return {
    isDragging,
    dragText,
    dragHint,
    install,
    uninstall
  }
}
