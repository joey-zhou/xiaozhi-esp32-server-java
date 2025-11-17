import { ref, computed, nextTick, onMounted, onBeforeUnmount, type Ref } from 'vue'

/**
 * 滚动管理 Composable
 * 用于管理容器的滚动行为
 */

export interface UseScrollOptions {
  /**
   * 滚动容器引用
   */
  container?: Ref<HTMLElement | undefined>
  
  /**
   * 滚动到底部的阈值（像素），小于此值认为在底部
   */
  bottomThreshold?: number
  
  /**
   * 滚动到顶部的阈值（像素），小于此值认为在顶部
   */
  topThreshold?: number
  
  /**
   * 是否启用滚动监听
   */
  enableScrollListener?: boolean
  
  /**
   * 滚动事件回调
   */
  onScroll?: (scrollInfo: ScrollInfo) => void
  
  /**
   * 到达底部回调
   */
  onReachBottom?: () => void
  
  /**
   * 到达顶部回调
   */
  onReachTop?: () => void
}

export interface ScrollInfo {
  scrollTop: number
  scrollLeft: number
  scrollHeight: number
  scrollWidth: number
  clientHeight: number
  clientWidth: number
}

export function useScroll(options: UseScrollOptions = {}) {
  const containerRef = options.container || ref<HTMLElement>()
  const bottomThreshold = options.bottomThreshold || 50
  const topThreshold = options.topThreshold || 10
  
  // 滚动信息
  const scrollTop = ref(0)
  const scrollLeft = ref(0)
  const scrollHeight = ref(0)
  const scrollWidth = ref(0)
  const clientHeight = ref(0)
  const clientWidth = ref(0)
  
  // 是否在底部
  const isAtBottom = computed(() => {
    if (!containerRef.value) return false
    return scrollHeight.value - scrollTop.value - clientHeight.value <= bottomThreshold
  })
  
  // 是否在顶部
  const isAtTop = computed(() => {
    return scrollTop.value <= topThreshold
  })
  
  // 是否可以滚动
  const isScrollable = computed(() => {
    return scrollHeight.value > clientHeight.value
  })
  
  // 滚动百分比
  const scrollPercentage = computed(() => {
    if (!isScrollable.value) return 0
    return Math.round((scrollTop.value / (scrollHeight.value - clientHeight.value)) * 100)
  })
  
  /**
   * 更新滚动信息
   */
  const updateScrollInfo = () => {
    if (!containerRef.value) return
    
    const el = containerRef.value
    scrollTop.value = el.scrollTop
    scrollLeft.value = el.scrollLeft
    scrollHeight.value = el.scrollHeight
    scrollWidth.value = el.scrollWidth
    clientHeight.value = el.clientHeight
    clientWidth.value = el.clientWidth
    
    // 触发回调
    if (options.onScroll) {
      options.onScroll({
        scrollTop: scrollTop.value,
        scrollLeft: scrollLeft.value,
        scrollHeight: scrollHeight.value,
        scrollWidth: scrollWidth.value,
        clientHeight: clientHeight.value,
        clientWidth: clientWidth.value
      })
    }
    
    // 触发到达底部回调
    if (isAtBottom.value && options.onReachBottom) {
      options.onReachBottom()
    }
    
    // 触发到达顶部回调
    if (isAtTop.value && options.onReachTop) {
      options.onReachTop()
    }
  }
  
  /**
   * 滚动到底部
   */
  const scrollToBottom = (smooth = true) => {
    nextTick(() => {
      if (!containerRef.value) return
      
      containerRef.value.scrollTo({
        top: containerRef.value.scrollHeight,
        behavior: smooth ? 'smooth' : 'auto'
      })
      
      updateScrollInfo()
    })
  }
  
  /**
   * 滚动到顶部
   */
  const scrollToTop = (smooth = true) => {
    nextTick(() => {
      if (!containerRef.value) return
      
      containerRef.value.scrollTo({
        top: 0,
        behavior: smooth ? 'smooth' : 'auto'
      })
      
      updateScrollInfo()
    })
  }
  
  /**
   * 滚动到指定位置
   */
  const scrollTo = (options: { top?: number; left?: number; smooth?: boolean }) => {
    nextTick(() => {
      if (!containerRef.value) return
      
      containerRef.value.scrollTo({
        top: options.top,
        left: options.left,
        behavior: options.smooth !== false ? 'smooth' : 'auto'
      })
      
      updateScrollInfo()
    })
  }
  
  /**
   * 滚动到指定元素
   */
  const scrollToElement = (
    selector: string | HTMLElement,
    options: { block?: ScrollLogicalPosition; inline?: ScrollLogicalPosition; smooth?: boolean } = {}
  ) => {
    nextTick(() => {
      if (!containerRef.value) return
      
      let element: HTMLElement | null = null
      
      if (typeof selector === 'string') {
        element = containerRef.value.querySelector(selector)
      } else {
        element = selector
      }
      
      if (!element) {
        console.warn('找不到目标元素:', selector)
        return
      }
      
      element.scrollIntoView({
        block: options.block || 'start',
        inline: options.inline || 'nearest',
        behavior: options.smooth !== false ? 'smooth' : 'auto'
      })
      
      updateScrollInfo()
    })
  }
  
  /**
   * 滚动指定距离
   */
  const scrollBy = (options: { top?: number; left?: number; smooth?: boolean }) => {
    nextTick(() => {
      if (!containerRef.value) return
      
      containerRef.value.scrollBy({
        top: options.top || 0,
        left: options.left || 0,
        behavior: options.smooth !== false ? 'smooth' : 'auto'
      })
      
      updateScrollInfo()
    })
  }
  
  // 滚动事件处理
  const handleScroll = () => {
    updateScrollInfo()
  }
  
  // 挂载时初始化
  onMounted(() => {
    if (options.enableScrollListener !== false && containerRef.value) {
      containerRef.value.addEventListener('scroll', handleScroll)
      updateScrollInfo()
    }
  })
  
  // 卸载时清理
  onBeforeUnmount(() => {
    if (containerRef.value) {
      containerRef.value.removeEventListener('scroll', handleScroll)
    }
  })
  
  return {
    // 容器引用
    containerRef,
    
    // 滚动信息
    scrollTop,
    scrollLeft,
    scrollHeight,
    scrollWidth,
    clientHeight,
    clientWidth,
    
    // 计算属性
    isAtBottom,
    isAtTop,
    isScrollable,
    scrollPercentage,
    
    // 方法
    scrollToBottom,
    scrollToTop,
    scrollTo,
    scrollToElement,
    scrollBy,
    updateScrollInfo
  }
}

