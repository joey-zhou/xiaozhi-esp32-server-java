import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useLoadingStore = defineStore('loading', () => {
  // 全局 loading 状态
  const isLoading = ref(false)
  const loadingText = ref('加载中...')

  // 请求计数器（处理多个并发请求）
  const requestCount = ref(0)
  
  // 最低显示时间（毫秒）- 防止快速操作时的闪烁
  const MIN_DISPLAY_TIME = 1000
  
  // 记录显示时间
  let showTime = 0
  let hideTimer: ReturnType<typeof setTimeout> | null = null

  // 显示 loading
  const showLoading = (text = '加载中...') => {
    requestCount.value++
    loadingText.value = text
    
    // 如果已经在显示，只更新文字
    if (isLoading.value) {
      return
    }
    
    // 清除可能存在的隐藏定时器
    if (hideTimer) {
      clearTimeout(hideTimer)
      hideTimer = null
    }
    
    // 记录显示时间
    showTime = Date.now()
    isLoading.value = true
  }

  // 等待最小显示时间
  const awaitMinDisplay = (): Promise<void> => {
    return new Promise((resolve) => {
      const displayedTime = Date.now() - showTime
      const remainingTime = MIN_DISPLAY_TIME - displayedTime
      
      if (remainingTime > 0) {
        setTimeout(resolve, remainingTime)
      } else {
        resolve()
      }
    })
  }

  // 隐藏 loading
  const hideLoading = () => {
    requestCount.value--
    if (requestCount.value > 0) {
      return
    }
    
    requestCount.value = 0
    
    // 计算已显示时间
    const displayedTime = Date.now() - showTime
    const remainingTime = MIN_DISPLAY_TIME - displayedTime
    
    // 如果显示时间不足最低时间，延迟隐藏
    if (remainingTime > 0) {
      hideTimer = setTimeout(() => {
        isLoading.value = false
        hideTimer = null
      }, remainingTime)
    } else {
      // 已达到最低时间，立即隐藏
      isLoading.value = false
    }
  }
  
  // 执行带 loading 的异步操作（优化版）
  // 等待最小时间后再执行数据操作和关闭 loading
  const withLoading = async <T>(
    apiCall: () => Promise<T>,
    options?: {
      loadingText?: string
      onSuccess?: (data: T) => void | Promise<void>
      onError?: (error: any) => void
    }
  ): Promise<T | null> => {
    showLoading(options?.loadingText)
    
    try {
      // 并行执行：API 请求 和 等待最小时间
      const [data] = await Promise.all([
        apiCall(),
        awaitMinDisplay()
      ])
      
      // 此时最小时间已满足，立即执行数据操作
      if (options?.onSuccess) {
        await options.onSuccess(data)
      }
      
      // 关闭 loading
      requestCount.value = 0
      isLoading.value = false
      if (hideTimer) {
        clearTimeout(hideTimer)
        hideTimer = null
      }
      
      return data
    } catch (error) {
      // 出错时也要等待最小时间，避免闪烁
      await awaitMinDisplay()
      
      if (options?.onError) {
        options.onError(error)
      }
      
      // 关闭 loading
      requestCount.value = 0
      isLoading.value = false
      if (hideTimer) {
        clearTimeout(hideTimer)
        hideTimer = null
      }
      
      return null
    }
  }

  // 强制隐藏（用于错误情况）
  const forceHideLoading = () => {
    requestCount.value = 0
    if (hideTimer) {
      clearTimeout(hideTimer)
      hideTimer = null
    }
    isLoading.value = false
  }

  return {
    isLoading,
    loadingText,
    showLoading,
    hideLoading,
    forceHideLoading,
    withLoading,
    awaitMinDisplay,
  }
})
