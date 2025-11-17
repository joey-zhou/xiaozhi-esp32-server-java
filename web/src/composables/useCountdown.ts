import { ref, computed, onBeforeUnmount } from 'vue'

/**
 * 倒计时 Composable
 * 用于验证码倒计时、限时活动等场景
 */

export interface UseCountdownOptions {
  /**
   * 初始倒计时秒数
   */
  initialCount?: number
  
  /**
   * 倒计时结束回调
   */
  onFinish?: () => void
  
  /**
   * 每秒回调
   */
  onTick?: (count: number) => void
  
  /**
   * 是否自动开始
   */
  autoStart?: boolean
  
  /**
   * 间隔时间（毫秒）
   */
  interval?: number
}

export function useCountdown(options: UseCountdownOptions = {}) {
  const initialCount = options.initialCount || 60
  const interval = options.interval || 1000
  
  // 当前倒计时值
  const count = ref(0)
  
  // 是否正在倒计时
  const counting = ref(false)
  
  // 定时器
  let timer: ReturnType<typeof setInterval> | null = null
  
  // 是否已完成
  const isFinished = computed(() => count.value === 0 && !counting.value)
  
  // 格式化时间显示（MM:SS）
  const formattedTime = computed(() => {
    const minutes = Math.floor(count.value / 60)
    const seconds = count.value % 60
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  })
  
  // 倒计时文本（常用于按钮）
  const countdownText = computed(() => {
    return counting.value ? `${count.value}s` : ''
  })
  
  /**
   * 开始倒计时
   */
  const start = (seconds: number = initialCount): boolean => {
    // 如果正在倒计时，不重复启动
    if (counting.value) {
      return false
    }
    
    count.value = seconds
    counting.value = true
    
    timer = setInterval(() => {
      count.value--
      
      // 触发每秒回调
      options.onTick?.(count.value)
      
      // 倒计时结束
      if (count.value <= 0) {
        stop()
        options.onFinish?.()
      }
    }, interval)
    
    return true
  }
  
  /**
   * 停止倒计时
   */
  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    counting.value = false
  }
  
  /**
   * 暂停倒计时
   */
  const pause = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    counting.value = false
  }
  
  /**
   * 恢复倒计时
   */
  const resume = () => {
    if (count.value > 0 && !counting.value) {
      counting.value = true
      
      timer = setInterval(() => {
        count.value--
        
        options.onTick?.(count.value)
        
        if (count.value <= 0) {
          stop()
          options.onFinish?.()
        }
      }, interval)
    }
  }
  
  /**
   * 重置倒计时
   */
  const reset = (seconds: number = initialCount) => {
    stop()
    count.value = seconds
  }
  
  /**
   * 重新开始倒计时
   */
  const restart = (seconds: number = initialCount) => {
    stop()
    start(seconds)
  }
  
  /**
   * 设置倒计时值
   */
  const setCount = (seconds: number) => {
    count.value = seconds
  }
  
  /**
   * 增加倒计时（延长时间）
   */
  const addTime = (seconds: number) => {
    count.value += seconds
  }
  
  /**
   * 减少倒计时（减少时间）
   */
  const reduceTime = (seconds: number) => {
    count.value = Math.max(0, count.value - seconds)
    
    if (count.value === 0 && counting.value) {
      stop()
      options.onFinish?.()
    }
  }
  
  // 自动开始
  if (options.autoStart) {
    start()
  }
  
  // 组件卸载时清理定时器
  onBeforeUnmount(() => {
    stop()
  })
  
  return {
    // 状态
    count,
    counting,
    isFinished,
    formattedTime,
    countdownText,
    
    // 方法
    start,
    stop,
    pause,
    resume,
    reset,
    restart,
    setCount,
    addTime,
    reduceTime
  }
}

