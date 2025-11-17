import { shallowRef, computed } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

/**
 * 卡片列表管理 Composable
 * 用于管理卡片式列表的搜索、加载、骨架屏等功能
 */

export interface UseCardListOptions<T extends object> {
  /**
   * 获取数据的函数
   */
  fetchData: () => Promise<T[]>
  
  /**
   * 搜索字段（支持多字段搜索）
   */
  searchFields: (keyof T)[]
  
  /**
   * 默认骨架屏数量
   */
  defaultSkeletonCount?: number
  
  /**
   * 防抖延迟（毫秒）
   */
  debounceDelay?: number
  
  /**
   * 是否在初始化时自动加载数据
   */
  immediate?: boolean
  
  /**
   * 错误处理
   */
  onError?: (error: Error) => void
}

export function useCardList<T extends object>(options: UseCardListOptions<T>) {
  const { t } = useI18n()
  
  // 加载状态
  const loading = shallowRef(false)
  
  // 搜索关键词
  const searchQuery = shallowRef('')
  
  // 所有数据（使用 shallowRef 避免深层响应式）
  const allItems = shallowRef<T[]>([])
  
  // 过滤后的数据
  const filteredItems = computed(() => {
    if (!searchQuery.value.trim()) {
      return allItems.value
    }
    
    const query = searchQuery.value.toLowerCase()
    return allItems.value.filter(item => {
      return options.searchFields.some(field => {
        const value = item[field as keyof T]
        if (value === null || value === undefined) {
          return false
        }
        return String(value).toLowerCase().includes(query)
      })
    })
  })
  
  // 骨架屏数量（动态计算）
  const skeletonCount = computed(() => {
    // 如果正在加载且没有数据，显示默认数量
    if (loading.value && allItems.value.length === 0) {
      return options.defaultSkeletonCount || 6
    }
    // 如果有数据，根据实际数量显示（最多6个）
    return Math.max(1, Math.min(allItems.value.length, 6))
  })
  
  // 是否为空
  const isEmpty = computed(() => {
    return !loading.value && filteredItems.value.length === 0
  })
  
  // 是否有数据
  const hasData = computed(() => {
    return filteredItems.value.length > 0
  })
  
  /**
   * 加载数据
   */
  const loadData = async () => {
    loading.value = true
    try {
      const data = await options.fetchData()
      allItems.value = data
    } catch (error) {
      console.error('加载数据失败:', error)
      const errorMessage = error instanceof Error 
        ? error.message 
        : t('common.loadDataFailed')
      message.error(errorMessage)
      
      // 触发错误回调
      if (error instanceof Error) {
        options.onError?.(error)
      }
    } finally {
      loading.value = false
    }
  }
  
  /**
   * 刷新数据
   */
  const refresh = async () => {
    await loadData()
  }
  
  /**
   * 防抖搜索
   */
  const debouncedSearch = useDebounceFn(() => {
    // filteredItems 会自动更新，这里不需要额外逻辑
    // 可以在这里添加搜索分析等
  }, options.debounceDelay || 300)
  
  /**
   * 重置搜索
   */
  const resetSearch = () => {
    searchQuery.value = ''
  }
  
  /**
   * 添加项到列表
   */
  const addItem = (item: T) => {
    allItems.value = [...allItems.value, item]
  }
  
  /**
   * 更新项
   */
  const updateItem = (predicate: (item: T) => boolean, newItem: Partial<T>) => {
    const items = allItems.value
    const index = items.findIndex(item => predicate(item))
    if (index !== -1) {
      const updatedItems = [...items]
      updatedItems[index] = { ...items[index], ...newItem } as T
      allItems.value = updatedItems
    }
  }
  
  /**
   * 删除项
   */
  const removeItem = (predicate: (item: T) => boolean) => {
    const items = allItems.value
    allItems.value = items.filter(item => !predicate(item))
  }
  
  /**
   * 清空数据
   */
  const clear = () => {
    allItems.value = []
    searchQuery.value = ''
  }
  
  // 如果设置了立即加载，则自动加载数据
  if (options.immediate !== false) {
    loadData()
  }
  
  return {
    // 状态
    loading,
    searchQuery,
    allItems,
    filteredItems,
    skeletonCount,
    isEmpty,
    hasData,
    
    // 方法
    loadData,
    refresh,
    debouncedSearch,
    resetSearch,
    addItem,
    updateItem,
    removeItem,
    clear
  }
}
