import { shallowRef, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { shouldIgnoreRequestError } from '@/services/request'

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
      // 切页/组件卸载导致的请求取消不是真错误，不弹提示
      if (shouldIgnoreRequestError(error)) {
        if (error instanceof Error) {
          options.onError?.(error)
        }
        return
      }
      console.error('加载数据失败:', error)
      const errorMessage = error instanceof Error
        ? error.message
        : t('common.loadDataFailed')
      // 传输层错误由 request.ts 的拦截器统一弹提示，这里用同一个 key 覆盖，不叠第二条
      message.error({ content: errorMessage, key: 'request-error' })

      // 触发错误回调
      if (error instanceof Error) {
        options.onError?.(error)
      }
    } finally {
      loading.value = false
    }
  }
  
  // 如果设置了立即加载，则自动加载数据
  if (options.immediate !== false) {
    loadData()
  }

  return {
    loading,
    filteredItems,
    skeletonCount,
    isEmpty,
    hasData,
    loadData
  }
}
