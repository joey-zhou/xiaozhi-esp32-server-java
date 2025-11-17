import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

/**
 * 文件上传管理 Composable
 * 统一管理文件上传、验证、进度等功能
 */

export interface UseFileUploadOptions {
  /**
   * 接受的文件类型
   * 例如: '.pdf,.docx' 或 'image/*' 或 'application/pdf'
   */
  accept?: string
  
  /**
   * 最大文件大小（MB）
   */
  maxSize?: number
  
  /**
   * 是否支持多文件上传
   */
  multiple?: boolean
  
  /**
   * 自动上传（选择文件后立即上传）
   */
  autoUpload?: boolean
  
  /**
   * 上传回调函数
   * @param files 要上传的文件列表
   * @returns 上传是否成功
   */
  onUpload?: (files: File[]) => Promise<boolean | void>
  
  /**
   * 文件变化回调
   */
  onChange?: (files: File[]) => void
  
  /**
   * 上传进度回调
   */
  onProgress?: (progress: number) => void
  
  /**
   * 验证回调（自定义验证逻辑）
   * @returns true 表示验证通过，false 或错误消息表示验证失败
   */
  customValidate?: (file: File) => boolean | string
}

export interface FileItem {
  uid: string
  file: File
  name: string
  size: number
  type: string
  status: 'ready' | 'uploading' | 'success' | 'error'
  progress: number
  url?: string
  error?: string
  response?: unknown  // 上传响应数据
}

export function useFileUpload(options: UseFileUploadOptions = {}) {
  const { t } = useI18n()
  
  // 上传状态
  const uploading = ref(false)
  
  // 文件列表
  const fileList = ref<FileItem[]>([])
  
  // 总进度
  const totalProgress = ref(0)
  
  // 文件数量
  const fileCount = computed(() => fileList.value.length)
  
  // 是否有文件
  const hasFiles = computed(() => fileCount.value > 0)
  
  // 是否全部上传成功
  const allSuccess = computed(() => {
    if (fileCount.value === 0) return false
    return fileList.value.every(item => item.status === 'success')
  })
  
  // 是否有错误
  const hasError = computed(() => {
    return fileList.value.some(item => item.status === 'error')
  })
  
  /**
   * 验证文件类型
   */
  const validateFileType = (file: File): boolean => {
    if (!options.accept) return true
    
    const acceptTypes = options.accept.split(',').map(t => t.trim())
    const fileExt = '.' + file.name.split('.').pop()?.toLowerCase()
    const fileType = file.type.toLowerCase()
    
    const isValid = acceptTypes.some(type => {
      // 通配符匹配，如 image/*
      if (type.includes('*')) {
        const [mainType] = type.split('/')
        if (!mainType) return false
        return fileType.startsWith(mainType)
      }
      // 扩展名匹配
      if (type.startsWith('.')) {
        return type === fileExt
      }
      // MIME 类型匹配
      return type === fileType
    })
    
    if (!isValid) {
      message.error(t('upload.invalidFileType', { types: options.accept }))
    }
    
    return isValid
  }
  
  /**
   * 验证文件大小
   */
  const validateFileSize = (file: File): boolean => {
    if (!options.maxSize) return true
    
    const maxBytes = options.maxSize * 1024 * 1024
    const isValid = file.size <= maxBytes
    
    if (!isValid) {
      message.error(t('upload.fileTooLarge', { size: options.maxSize }))
    }
    
    return isValid
  }
  
  /**
   * 验证文件
   */
  const validateFile = (file: File): boolean => {
    // 基础验证
    if (!validateFileType(file)) return false
    if (!validateFileSize(file)) return false
    
    // 自定义验证
    if (options.customValidate) {
      const result = options.customValidate(file)
      if (result === false) {
        message.error(t('upload.validationFailed'))
        return false
      }
      if (typeof result === 'string') {
        message.error(result)
        return false
      }
    }
    
    return true
  }
  
  /**
   * 创建文件项
   */
  const createFileItem = (file: File): FileItem => {
    return {
      uid: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      file,
      name: file.name,
      size: file.size,
      type: file.type,
      status: 'ready',
      progress: 0
    }
  }
  
  /**
   * 处理文件选择
   */
  const handleFileChange = async (files: File[] | FileList) => {
    const fileArray = Array.from(files)

    // 验证文件
    const validFiles = fileArray.filter(validateFile)
    if (validFiles.length === 0) return
    
    // 单文件模式：替换文件列表
    // 多文件模式：追加文件
    const newFileItems = validFiles.map(createFileItem)
    
    if (options.multiple) {
      fileList.value.push(...newFileItems)
    } else {
      fileList.value = newFileItems
    }
    
    // 触发变化回调
    options.onChange?.(validFiles)
    
    // 自动上传
    if (options.autoUpload && options.onUpload) {
      await upload(validFiles)
    }
  }
  
  /**
   * 上传文件
   */
  const upload = async (files?: File[]): Promise<boolean> => {
    if (!options.onUpload) {
      console.warn('未配置上传函数')
      return false
    }
    
    // 如果没有指定文件，上传所有未上传的文件
    const filesToUpload = files || fileList.value
      .filter(item => item.status === 'ready' || item.status === 'error')
      .map(item => item.file)
    
    if (filesToUpload.length === 0) {
      message.warning(t('upload.noFilesToUpload'))
      return false
    }
    
    uploading.value = true
    totalProgress.value = 0
    
    try {
      // 更新文件状态为上传中
      filesToUpload.forEach(file => {
        const item = fileList.value.find(f => f.file === file)
        if (item) {
          item.status = 'uploading'
          item.progress = 0
        }
      })
      
      // 执行上传
      const result = await options.onUpload(filesToUpload)
      
      // 上传成功
      if (result !== false) {
        filesToUpload.forEach(file => {
          const item = fileList.value.find(f => f.file === file)
          if (item) {
            item.status = 'success'
            item.progress = 100
          }
        })
        totalProgress.value = 100
        message.success(t('upload.success'))
        return true
      } else {
        // 上传失败
        filesToUpload.forEach(file => {
          const item = fileList.value.find(f => f.file === file)
          if (item) {
            item.status = 'error'
            item.error = t('upload.failed')
          }
        })
        return false
      }
    } catch (error: any) {
      console.error('上传失败:', error)
      
      // 标记为失败
      filesToUpload.forEach(file => {
        const item = fileList.value.find(f => f.file === file)
        if (item) {
          item.status = 'error'
          item.error = error.message || t('upload.failed')
        }
      })
      
      message.error(error.message || t('upload.failed'))
      return false
    } finally {
      uploading.value = false
    }
  }
  
  /**
   * 更新文件进度
   */
  const updateProgress = (fileUid: string, progress: number) => {
    const item = fileList.value.find(f => f.uid === fileUid)
    if (item) {
      item.progress = progress
      
      // 计算总进度
      const total = fileList.value.reduce((sum, f) => sum + f.progress, 0)
      totalProgress.value = Math.round(total / fileList.value.length)
      
      options.onProgress?.(totalProgress.value)
    }
  }
  
  /**
   * 移除文件
   */
  const removeFile = (fileUid: string) => {
    const index = fileList.value.findIndex(f => f.uid === fileUid)
    if (index !== -1) {
      fileList.value.splice(index, 1)
    }
  }
  
  /**
   * 清空文件列表
   */
  const clearFiles = () => {
    fileList.value = []
    totalProgress.value = 0
  }
  
  /**
   * 重试上传失败的文件
   */
  const retryFailed = async () => {
    const failedFiles = fileList.value
      .filter(item => item.status === 'error')
      .map(item => item.file)
    
    if (failedFiles.length > 0) {
      await upload(failedFiles)
    }
  }
  
  return {
    // 状态
    uploading,
    fileList,
    totalProgress,
    fileCount,
    hasFiles,
    allSuccess,
    hasError,
    
    // 方法
    handleFileChange,
    upload,
    updateProgress,
    removeFile,
    clearFiles,
    retryFailed,
    validateFile
  }
}
