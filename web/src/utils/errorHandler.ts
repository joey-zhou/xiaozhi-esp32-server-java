import type { App } from 'vue'
import { message } from 'ant-design-vue'
import { useEventListener } from '@vueuse/core'
import { i18n } from '@/locales'

// 错误类型
interface ErrorInfo {
  message: string
  stack?: string
  componentName?: string
  propKeys?: string[]
  url?: string
  line?: number
  column?: number
}

// 错误日志收集，只在内存里保留最近 MAX_ERROR_LOGS 条
const MAX_ERROR_LOGS = 50
const errorLogs: ErrorInfo[] = []

/** 落到内存日志并打印；没有服务端上报通道，不要按名字以为它发了请求 */
function recordError(errorInfo: ErrorInfo) {
  errorLogs.push(errorInfo)
  while (errorLogs.length > MAX_ERROR_LOGS) {
    errorLogs.shift()
  }
  console.error('捕获到错误:', errorInfo)
}

// Vue 错误处理器
export function setupErrorHandler(app: App) {
  // 1. Vue 组件错误处理
  app.config.errorHandler = (err: unknown, instance, info) => {
    const error = err instanceof Error ? err : new Error(String(err))
    const errorInfo: ErrorInfo = {
      message: error.message || i18n.global.t('error.unknown'),
      stack: error.stack,
      componentName: instance?.$options.name || instance?.$options.__name,
      // 只记 prop 名：存 $props 本身会把出错组件的整棵数据钉在内存里不让回收
      propKeys: instance?.$props ? Object.keys(instance.$props) : undefined,
    }

    recordError(errorInfo)

    // 固定 key：同类错误连发时只留最后一条，不叠成一屏
    message.error({
      content: i18n.global.t('error.componentError', { message: errorInfo.message }),
      key: 'component-error',
      duration: 3,
    })

    console.error('Vue 错误:', err, info)
  }

  if (import.meta.env.DEV) {
    app.config.warnHandler = (msg, _instance, trace) => {
      console.warn('Vue 警告:', msg, trace)
    }
  }

  useEventListener(window, 'unhandledrejection', (event) => {
    event.preventDefault()

    const reason = event.reason

    if (
      reason?.name === 'CanceledError' ||
      reason?.code === 'ERR_CANCELED' ||
      reason?.code === 'ERR_AUTH_EXPIRED' ||
      reason?.isSilent === true ||
      reason?.isAuthExpired === true ||
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

    // 忽略音频解码失败（音频过短或格式问题，已在组件内处理）
    if (reason?.message?.includes('Unable to decode audio data')) {
      return
    }

    // 检测动态导入失败（通常是因为部署了新版本，旧文件已被删除）
    if (
      reason?.message?.includes('Failed to fetch dynamically imported module') ||
      (reason?.message?.includes('Failed to fetch') && reason?.message?.match(/\.js/))
    ) {
      console.warn('动态模块加载失败，可能是页面版本过期:', reason.message)

      message.warning({
        content: i18n.global.t('error.pageUpdated'),
        duration: 2,
        onClose: () => {
          window.location.reload()
        }
      })

      // 2秒后自动刷新页面
      setTimeout(() => {
        window.location.reload()
      }, 2000)

      return
    }

    const errorInfo: ErrorInfo = {
      message: reason?.message || i18n.global.t('error.unknown'),
      stack: reason?.stack,
    }

    recordError(errorInfo)

    message.error({
      content: i18n.global.t('error.promiseError', { message: errorInfo.message }),
      key: 'promise-error',
      duration: 3,
    })
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

    recordError(errorInfo)

    message.error({
      content: i18n.global.t('error.scriptError', { message: errorInfo.message }),
      key: 'script-error',
      duration: 3,
    })
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

        recordError(errorInfo)
      }
    },
    { capture: true }
  )
}

// 获取错误日志（返回副本，调用方改不到内部数组）
export function getErrorLogs(): ErrorInfo[] {
  return [...errorLogs]
}

// 清空错误日志
export function clearErrorLogs() {
  errorLogs.length = 0
}
