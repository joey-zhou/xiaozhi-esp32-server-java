import { ref } from 'vue'
import { defineStore } from 'pinia'
import { i18n } from '@/locales'

/**
 * 全屏遮罩 loading，按 showLoading/hideLoading 成对计数
 * 与 composables/useLoadingState 的 withLoading 职责正交（那个是页内多路 key 的局部 loading），不要混用
 *
 * 采用「延迟显示」策略而非「最小显示时间」策略：
 * showLoading 后不会立刻挂遮罩，先等 SHOW_DELAY，快速结束的操作（在延迟内 hideLoading）
 * 遮罩根本不会出现；只有真正显示出来的遮罩才应用 MIN_DISPLAY_TIME 防止一闪而过
 */
export const useLoadingStore = defineStore('loading', () => {
  // 全局 loading 状态（遮罩是否真的挂出来了）
  const isLoading = ref(false)
  const loadingText = ref(i18n.global.t('common.loading'))

  // 请求计数器（处理多个并发请求）
  const requestCount = ref(0)

  // 延迟显示时间（毫秒）- 短操作在这段时间内结束就不会挂遮罩
  const SHOW_DELAY = 250
  // 最低显示时间（毫秒）- 遮罩一旦显示出来，至少保留这么久，防止闪烁
  const MIN_DISPLAY_TIME = 200

  // 遮罩真正显示出来的时间点，仅在 isLoading 为 true 时有意义
  let shownAt = 0
  // 等待展示遮罩的延迟定时器（遮罩还没显示）
  let showTimer: ReturnType<typeof setTimeout> | null = null
  // 等待达到最低显示时间后再隐藏的定时器（遮罩已经显示）
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

    // 遮罩已经显示，只更新文字
    if (isLoading.value) {
      return
    }

    // 已经在等待展示期内，沿用同一个延迟定时器，不重复排期
    if (showTimer) {
      return
    }

    // 进入延迟展示期：这段时间内请求结束的话遮罩根本不会出现
    showTimer = setTimeout(() => {
      showTimer = null
      shownAt = Date.now()
      isLoading.value = true
    }, SHOW_DELAY)
  }

  // 隐藏 loading
  const hideLoading = () => {
    requestCount.value--
    if (requestCount.value > 0) {
      return
    }

    requestCount.value = 0

    // 还在延迟展示期内就结束了，遮罩全程都不会出现
    if (showTimer) {
      clearTimeout(showTimer)
      showTimer = null
      return
    }

    if (!isLoading.value) {
      return
    }

    // 计算已显示时间，不足最低时间则延迟隐藏
    const displayedTime = Date.now() - shownAt
    const remainingTime = MIN_DISPLAY_TIME - displayedTime

    if (remainingTime > 0) {
      hideTimer = setTimeout(() => {
        isLoading.value = false
        hideTimer = null
      }, remainingTime)
    } else {
      isLoading.value = false
    }
  }

  return {
    isLoading,
    loadingText,
    showLoading,
    hideLoading,
  }
})
