import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

/**
 * 剪贴板操作 Composable
 * 用于复制、粘贴等剪贴板操作
 */

export interface UseClipboardOptions {
  /**
   * 复制成功的消息
   */
  successMessage?: string
  
  /**
   * 复制失败的消息
   */
  errorMessage?: string
  
  /**
   * 是否显示消息提示
   */
  showMessage?: boolean
  
  /**
   * 复制成功回调
   */
  onSuccess?: (text: string) => void
  
  /**
   * 复制失败回调
   */
  onError?: (error: any) => void
}

export function useClipboard(options: UseClipboardOptions = {}) {
  const { t } = useI18n()
  
  // 复制状态
  const copying = ref(false)
  
  // 已复制的文本
  const copiedText = ref<string>('')
  
  // 是否支持剪贴板 API
  const isSupported = ref(
    typeof navigator !== 'undefined' && 'clipboard' in navigator
  )
  
  /**
   * 复制文本到剪贴板
   */
  const copy = async (
    text: string,
    customOptions?: Partial<UseClipboardOptions>
  ): Promise<boolean> => {
    const mergedOptions = { ...options, ...customOptions }
    const showMessage = mergedOptions.showMessage !== false
    
    if (!isSupported.value) {
      // 降级处理：使用 document.execCommand
      return copyFallback(text, mergedOptions, showMessage)
    }
    
    copying.value = true
    
    try {
      await navigator.clipboard.writeText(text)
      copiedText.value = text
      
      if (showMessage) {
        const successMsg = mergedOptions.successMessage || t('clipboard.copySuccess')
        message.success(successMsg)
      }
      
      mergedOptions.onSuccess?.(text)
      return true
    } catch (error: any) {
      console.error('复制失败:', error)
      
      if (showMessage) {
        const errorMsg = mergedOptions.errorMessage || t('clipboard.copyFailed')
        message.error(errorMsg)
      }
      
      mergedOptions.onError?.(error)
      return false
    } finally {
      copying.value = false
    }
  }
  
  /**
   * 降级方案：使用 execCommand
   */
  const copyFallback = (
    text: string,
    mergedOptions: Partial<UseClipboardOptions>,
    showMessage: boolean
  ): boolean => {
    copying.value = true
    
    try {
      // 创建临时 textarea
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.top = '0'
      textarea.style.left = '-9999px'
      textarea.style.opacity = '0'
      
      document.body.appendChild(textarea)
      textarea.select()
      
      const successful = document.execCommand('copy')
      document.body.removeChild(textarea)
      
      if (successful) {
        copiedText.value = text
        
        if (showMessage) {
          const successMsg = mergedOptions.successMessage || t('clipboard.copySuccess')
          message.success(successMsg)
        }
        
        mergedOptions.onSuccess?.(text)
        return true
      } else {
        throw new Error('execCommand failed')
      }
    } catch (error: any) {
      console.error('复制失败（降级方案）:', error)
      
      if (showMessage) {
        const errorMsg = mergedOptions.errorMessage || t('clipboard.copyFailed')
        message.error(errorMsg)
      }
      
      mergedOptions.onError?.(error)
      return false
    } finally {
      copying.value = false
    }
  }
  
  /**
   * 读取剪贴板内容
   */
  const paste = async (): Promise<string | null> => {
    if (!isSupported.value) {
      message.warning(t('clipboard.pasteNotSupported'))
      return null
    }
    
    try {
      const text = await navigator.clipboard.readText()
      return text
    } catch (error: any) {
      console.error('读取剪贴板失败:', error)
      message.error(t('clipboard.pasteFailed'))
      return null
    }
  }
  
  /**
   * 清空剪贴板
   */
  const clear = async (): Promise<boolean> => {
    if (!isSupported.value) {
      return false
    }
    
    try {
      await navigator.clipboard.writeText('')
      copiedText.value = ''
      return true
    } catch (error: any) {
      console.error('清空剪贴板失败:', error)
      return false
    }
  }
  
  /**
   * 复制对象为 JSON 字符串
   */
  const copyJSON = async (
    obj: any,
    pretty = true,
    customOptions?: Partial<UseClipboardOptions>
  ): Promise<boolean> => {
    try {
      const json = pretty ? JSON.stringify(obj, null, 2) : JSON.stringify(obj)
      return await copy(json, customOptions)
    } catch (error: any) {
      console.error('JSON 序列化失败:', error)
      message.error(t('clipboard.jsonError'))
      return false
    }
  }
  
  /**
   * 复制 HTML
   */
  const copyHTML = async (
    html: string,
    plainText?: string
  ): Promise<boolean> => {
    if (!isSupported.value) {
      // 降级：复制纯文本
      return copy(plainText || html.replace(/<[^>]*>/g, ''))
    }
    
    copying.value = true
    
    try {
      const blob = new Blob([html], { type: 'text/html' })
      const textBlob = new Blob([plainText || html], { type: 'text/plain' })
      
      const clipboardItem = new ClipboardItem({
        'text/html': blob,
        'text/plain': textBlob
      })
      
      await navigator.clipboard.write([clipboardItem])
      
      if (options.showMessage !== false) {
        message.success(options.successMessage || t('clipboard.copySuccess'))
      }
      
      return true
    } catch (error: any) {
      console.error('复制 HTML 失败:', error)
      
      // 降级：复制纯文本
      return copy(plainText || html.replace(/<[^>]*>/g, ''))
    } finally {
      copying.value = false
    }
  }
  
  /**
   * 复制图片
   */
  const copyImage = async (blob: Blob): Promise<boolean> => {
    if (!isSupported.value) {
      message.warning(t('clipboard.imageNotSupported'))
      return false
    }
    
    copying.value = true
    
    try {
      const clipboardItem = new ClipboardItem({
        [blob.type]: blob
      })
      
      await navigator.clipboard.write([clipboardItem])
      
      if (options.showMessage !== false) {
        message.success(options.successMessage || t('clipboard.copySuccess'))
      }
      
      return true
    } catch (error: any) {
      console.error('复制图片失败:', error)
      
      if (options.showMessage !== false) {
        message.error(options.errorMessage || t('clipboard.copyFailed'))
      }
      
      return false
    } finally {
      copying.value = false
    }
  }
  
  return {
    // 状态
    copying,
    copiedText,
    isSupported,
    
    // 方法
    copy,
    paste,
    clear,
    copyJSON,
    copyHTML,
    copyImage
  }
}

