import { useClipboard as useVueUseClipboard } from '@vueuse/core'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

/**
 * 剪贴板复制 Composable
 * 在 @vueuse/core 的 useClipboard 之上包一层，补上项目统一的成功/失败提示
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
}

export function useClipboard(options: UseClipboardOptions = {}) {
  const { t } = useI18n()

  // legacy: true 表示 Clipboard API 不可用（非安全上下文等）时降级用 execCommand
  const { copy: copyToClipboard, isSupported } = useVueUseClipboard({ legacy: true })

  /**
   * 复制文本到剪贴板
   */
  const copy = async (
    text: string,
    customOptions?: Partial<UseClipboardOptions>
  ): Promise<boolean> => {
    const mergedOptions = { ...options, ...customOptions }
    const showMessage = mergedOptions.showMessage !== false

    try {
      await copyToClipboard(text)

      if (showMessage) {
        message.success(mergedOptions.successMessage || t('clipboard.copySuccess'))
      }

      return true
    } catch (error) {
      console.error('复制失败:', error)

      if (showMessage) {
        message.error(mergedOptions.errorMessage || t('clipboard.copyFailed'))
      }

      return false
    }
  }

  return {
    isSupported,
    copy,
  }
}
