import type { App } from 'vue'
import { message } from 'ant-design-vue'
import { useEventListener } from '@vueuse/core'

// 错误类型
interface ErrorInfo {
  message: string
  stack?: string
  componentName?: string
  propsData?: Record<string, unknown>
  url?: string
  line?: number
  column?: number
}

// 错误日志收集
const errorLogs: ErrorInfo[] = []

// 上报错误到服务器（可选）
function reportError(error: ErrorInfo) {
  // 这里可以调用后端 API 上报错误
  console.error('错误上报:', error)

  // 示例：发送到后端
  // fetch('/api/error/report', {
  //   method: 'POST',
  //   body: JSON.stringify(error)
  // })
}

// Vue 错误处理器
export function setupErrorHandler(app: App) {
  // 1. Vue 组件错误处理
  app.config.errorHandler = (err: unknown, instance, info) => {
    const error = err instanceof Error ? err : new Error(String(err))
    const errorInfo: ErrorInfo = {
      message: error.message || '未知错误',
      stack: error.stack,
      componentName: instance?.$options.name || instance?.$options.__name,
      propsData: instance?.$props as Record<string, unknown>,
    }

    // 保存错误日志
    errorLogs.push(errorInfo)

    // 上报错误
    reportError(errorInfo)

    // 显示错误提示
    message.error({
      content: `组件错误: ${errorInfo.message}`,
      duration: 5,
    })

    console.error('Vue 错误:', err, info)
  }

  if (import.meta.env.DEV) {
    app.config.warnHandler = (msg, instance, trace) => {
      console.warn('Vue 警告:', msg, trace)
    }
  }

  useEventListener(window, 'unhandledrejection', (event) => {
    event.preventDefault()

    const reason = event.reason
    
    if (
      reason?.name === 'CanceledError' || 
      reason?.code === 'ERR_CANCELED' ||
      reason?.message?.includes('canceled') ||
      reason?.message?.includes('aborted') ||
      reason?.message?.includes('signal is aborted')
    ) {
      console.debug('请求已取消（正常行为）:', reason.message)
      return
    }

    // 忽略音频文件加载失败的错误（404）
    if (
      reason?.message?.includes('Failed to fetch') &&
      reason?.message?.includes('/audio/') &&
      reason?.message?.includes('404')
    ) {
      console.debug('音频文件不存在（正常行为）:', reason.message)
      return
    }

    const errorInfo: ErrorInfo = {
      message: reason?.message || '未处理的 Promise 错误',
      stack: reason?.stack,
    }

    errorLogs.push(errorInfo)
    reportError(errorInfo)

    message.error({
      content: `Promise 错误: ${errorInfo.message}`,
      duration: 5,
    })

    console.error('未处理的 Promise 错误:', reason)
  })

  useEventListener(window, 'error', (event) => {
    if (
      event.message.includes('ResizeObserver loop') ||
      event.message.includes('ResizeObserver loop completed with undelivered notifications')
    ) {
      event.preventDefault()
      return
    }

    const errorInfo: ErrorInfo = {
      message: event.message,
      url: event.filename,
      line: event.lineno,
      column: event.colno,
      stack: event.error?.stack,
    }

    errorLogs.push(errorInfo)
    reportError(errorInfo)

    message.error({
      content: `脚本错误: ${errorInfo.message}`,
      duration: 5,
    })

    console.error('全局错误:', event.error)
  })

  useEventListener(
    window,
    'error',
    (event) => {
      const target = event.target as HTMLElement
      if (target.tagName === 'IMG' || target.tagName === 'SCRIPT' || target.tagName === 'LINK') {
        const resourceUrl = target instanceof HTMLImageElement || target instanceof HTMLScriptElement 
          ? target.src 
          : target instanceof HTMLLinkElement 
          ? target.href 
          : ''
        
        const errorInfo: ErrorInfo = {
          message: `资源加载失败: ${resourceUrl}`,
        }

        errorLogs.push(errorInfo)
        reportError(errorInfo)

        console.error('资源加载错误:', target)
      }
    },
    { capture: true }
  )
}

// 获取错误日志
export function getErrorLogs() {
  return errorLogs
}

// 清空错误日志
export function clearErrorLogs() {
  errorLogs.length = 0
}
