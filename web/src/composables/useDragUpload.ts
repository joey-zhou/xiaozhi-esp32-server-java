import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

/**
 * 文件验证器
 */
export interface FileValidator {
  /**
   * 验证文件是否合法
   * @param file 要验证的文件
   * @returns 验证结果，如果合法返回 true，否则返回错误消息
   */
  validate: (file: File) => true | string
}

/**
 * 拖拽上传配置
 */
export interface DragUploadOptions {
  /**
   * 文件验证器
   */
  validator: FileValidator

  /**
   * 文件处理回调
   * @param file 上传的文件
   */
  onDrop: (file: File) => void | Promise<void>

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
 * // 音频文件上传
 * const audioValidator: FileValidator = {
 *   validate: (file) => {
 *     const isAudio = file.type.startsWith('audio/') ||
 *                     file.name.match(/\.(wav|mp3|m4a)$/i)
 *     if (!isAudio) return 'common.audioFormatError'
 *
 *     const isLt10M = file.size / 1024 / 1024 < 10
 *     if (!isLt10M) return 'common.audioSizeError'
 *
 *     return true
 *   }
 * }
 *
 * const { isDragging } = useDragUpload({
 *   validator: audioValidator,
 *   onDrop: (file) => {
 *     handleFileUpload(file)
 *   },
 *   enabled: () => activeTab.value === 'upload',
 *   messages: {
 *     dragText: 'firmware.dragDropText',
 *     dragHint: 'firmware.dragDropHint',
 *     successMessage: 'firmware.fileUploadSuccess'
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

    // 检查是否启用
    if (!enabled()) {
      return
    }

    dragCounter++

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

    // 检查是否启用
    if (!enabled()) {
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

    // 检查是否启用
    if (!enabled()) {
      return
    }

    dragCounter--

    if (dragCounter === 0) {
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
        await onDrop(file)

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

/**
 * 预定义的文件验证器
 */
export const fileValidators = {
  /**
   * 音频文件验证器（10MB限制）
   */
  audio: {
    validate: (file: File) => {
      const isAudio = file.type.startsWith('audio/') ||
                      file.name.toLowerCase().match(/\.(wav|mp3|m4a|flac|ogg)$/)

      if (!isAudio) {
        return 'common.audioFormatError'
      }

      const isLt10M = file.size / 1024 / 1024 < 10
      if (!isLt10M) {
        return 'common.audioSizeError'
      }

      return true
    }
  } as FileValidator,

  /**
   * 图片文件验证器（2MB限制）
   */
  image: {
    validate: (file: File) => {
      const isImage = file.type.startsWith('image/')

      if (!isImage) {
        return 'common.onlyImageFiles'
      }

      const isLt2M = file.size / 1024 / 1024 < 2
      if (!isLt2M) {
        return 'common.imageSizeLimit'
      }

      return true
    }
  } as FileValidator,

  /**
   * 固件文件验证器（.bin/.hex文件，50MB限制）
   */
  firmware: {
    validate: (file: File) => {
      const isFirmware = file.name.toLowerCase().endsWith('.bin') ||
                        file.name.toLowerCase().endsWith('.hex')

      if (!isFirmware) {
        return 'firmware.invalidFileType'
      }

      const isLt50M = file.size / 1024 / 1024 < 50
      if (!isLt50M) {
        return 'firmware.fileSizeLimit'
      }

      return true
    }
  } as FileValidator
}
