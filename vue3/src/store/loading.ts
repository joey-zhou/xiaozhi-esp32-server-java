import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useLoadingStore = defineStore('loading', () => {
  // 全局 loading 状态
  const isLoading = ref(false)
  const loadingText = ref('加载中...')

  // 请求计数器（处理多个并发请求）
  const requestCount = ref(0)

  // 显示 loading
  const showLoading = (text = '加载中...') => {
    requestCount.value++
    loadingText.value = text
    isLoading.value = true
  }

  // 隐藏 loading
  const hideLoading = () => {
    requestCount.value--
    if (requestCount.value <= 0) {
      requestCount.value = 0
      isLoading.value = false
    }
  }

  // 强制隐藏（用于错误情况）
  const forceHideLoading = () => {
    requestCount.value = 0
    isLoading.value = false
  }

  return {
    isLoading,
    loadingText,
    showLoading,
    hideLoading,
    forceHideLoading,
  }
})

// 使用示例：
// const loadingStore = useLoadingStore()
// loadingStore.showLoading('正在保存...')
// await saveData()
// loadingStore.hideLoading()
