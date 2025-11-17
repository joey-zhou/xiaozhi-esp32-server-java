import { reactive, toRefs } from 'vue'
import { useDebounceFn } from '@vueuse/core'

/**
 * 查询表单管理 Composable
 * 用于统一管理列表页面的查询表单
 */

export interface UseQueryFormOptions<T> {
  /**
   * 初始值
   */
  initialValues: T
  
  /**
   * 搜索回调函数
   */
  onSearch: () => void | Promise<void>
  
  /**
   * 重置回调函数
   */
  onReset?: () => void | Promise<void>
  
  /**
   * 防抖延迟（毫秒）
   */
  debounceDelay?: number
  
  /**
   * 是否在重置后自动搜索
   */
  searchAfterReset?: boolean
}

export function useQueryForm<T extends object>(
  options: UseQueryFormOptions<T>
) {
  // 查询表单数据
  const queryForm = reactive<T>({ ...options.initialValues } as T)
  
  /**
   * 执行搜索
   */
  const search = async () => {
    await options.onSearch()
  }
  
  /**
   * 防抖搜索
   */
  const debouncedSearch = useDebounceFn(
    search,
    options.debounceDelay || 500
  )
  
  /**
   * 重置表单
   */
  const reset = async () => {
    // 重置为初始值
    const formObj = queryForm as { [K in keyof T]: T[K] }
    Object.keys(queryForm).forEach(key => {
      const typedKey = key as keyof T
      formObj[typedKey] = options.initialValues[typedKey]
    })
    
    // 触发重置回调
    if (options.onReset) {
      await options.onReset()
    }
    
    // 如果设置了自动搜索，则执行搜索
    if (options.searchAfterReset !== false) {
      await search()
    }
  }
  
  /**
   * 设置表单值
   */
  const setValues = (values: Partial<T>) => {
    Object.assign(queryForm, values)
  }
  
  /**
   * 获取表单值（返回普通对象副本）
   */
  const getValues = (): T => {
    return JSON.parse(JSON.stringify(queryForm)) as T
  }
  
  /**
   * 清空表单（设置为空值，但不重置为初始值）
   */
  const clear = () => {
    const formObj = queryForm as { [K in keyof T]: T[K] }
    Object.keys(formObj).forEach(key => {
      const typedKey = key as keyof T
      const value = formObj[typedKey]
      if (typeof value === 'string') {
        formObj[typedKey] = '' as T[keyof T]
      } else if (typeof value === 'number') {
        formObj[typedKey] = 0 as T[keyof T]
      } else if (Array.isArray(value)) {
        formObj[typedKey] = [] as T[keyof T]
      } else if (value !== null && typeof value === 'object') {
        formObj[typedKey] = {} as T[keyof T]
      } else {
        formObj[typedKey] = undefined as T[keyof T]
      }
    })
  }
  
  /**
   * 判断表单是否为空（所有值都是空值）
   */
  const isEmpty = (): boolean => {
    return Object.values(queryForm).every(value => {
      if (value === null || value === undefined) return true
      if (typeof value === 'string') return value.trim() === ''
      if (typeof value === 'number') return value === 0
      if (Array.isArray(value)) return value.length === 0
      if (typeof value === 'object') return Object.keys(value).length === 0
      return false
    })
  }
  
  /**
   * 判断表单是否已修改
   */
  const isModified = (): boolean => {
    const formObj = queryForm as { [K in keyof T]: T[K] }
    return Object.keys(queryForm).some(key => {
      const typedKey = key as keyof T
      return formObj[typedKey] !== options.initialValues[typedKey]
    })
  }
  
  return {
    // 响应式表单数据
    queryForm,
    
    // 方法
    search,
    debouncedSearch,
    reset,
    setValues,
    getValues,
    clear,
    isEmpty,
    isModified,
    
    // 解构后的表单字段（方便直接使用）
    ...toRefs(queryForm)
  }
}

