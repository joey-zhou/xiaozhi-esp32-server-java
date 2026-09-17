import { ref } from 'vue'
import { defineStore } from 'pinia'
import { i18n } from '@/locales'

/**
 * 全屏遮罩 loading，按 showLoading/hideLoading 成对计数
 * 与 composables/useLoadingState 的 withLoading 职责正交（那个是页内多路 key 的局部 loading），不要混用
 */
export const useLoadingStore = defineStore('loading', () => {
  // 全局 loading 状态
  const isLoading = ref(false)
  const loadingText = ref(i18n.global.t('common.loading'))

  // 请求计数器（处理多个并发请求）
  const requestCount = ref(0)

  // 最低显示时间（毫秒）- 防止快速操作时的闪烁
  const MIN_DISPLAY_TIME = 300

  // 记录显示时间
  let showTime = 0
  let hideTimer: ReturnType<typeof setTimeout> | null = null

  // 显示 loading
  const showLoading = (text = i18n.global.t('common.loading')) => {
    // 清除待执行的隐藏定时器：必须在任何提前 return 之前做，
    // 否则上一轮遗留的定时器会把这一轮的遮罩掐掉
    if (hideTimer) {
      clearTimeout(hideTimer)
      hideTimer = null
    }

    requestCount.value++
    loadingText.value = text

    // 如果已经在显示，只更新文字
    if (isLoading.value) {
      return
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

  return {
    isLoading,
    loadingText,
    showLoading,
    hideLoading,
    awaitMinDisplay,
  }
})
