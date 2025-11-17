import { reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TablePaginationConfig } from 'ant-design-vue'

/**
 * 分页器增强 Composable
 * 用于统一管理分页逻辑
 */

export interface UsePaginationOptions {
  /**
   * 默认每页条数
   */
  defaultPageSize?: number
  
  /**
   * 每页条数选项
   */
  pageSizeOptions?: string[]
  
  /**
   * 是否显示每页条数选择器
   */
  showSizeChanger?: boolean
  
  /**
   * 是否显示快速跳转
   */
  showQuickJumper?: boolean
  
  /**
   * 是否显示总数
   */
  showTotal?: boolean
  
  /**
   * 总数文本格式化函数
   */
  totalFormat?: (total: number) => string
  
  /**
   * 分页变化回调
   */
  onChange?: (page: number, pageSize: number) => void
  
  /**
   * 每页条数变化回调
   */
  onShowSizeChange?: (page: number, pageSize: number) => void
}

export function usePagination(options: UsePaginationOptions = {}) {
  const { t } = useI18n()
  
  // 分页配置
  const pagination = reactive<TablePaginationConfig>({
    current: 1,
    pageSize: options.defaultPageSize || 10,
    total: 0,
    showSizeChanger: options.showSizeChanger !== false,
    showQuickJumper: options.showQuickJumper !== false,
    pageSizeOptions: options.pageSizeOptions || ['10', '20', '50', '100'],
    showTotal: options.showTotal !== false 
      ? (total: number) => options.totalFormat?.(total) || t('table.total', { total })
      : undefined,
    onChange: (page: number, pageSize: number) => {
      pagination.current = page
      pagination.pageSize = pageSize
      options.onChange?.(page, pageSize)
    },
    onShowSizeChange: (page: number, pageSize: number) => {
      pagination.current = 1 // 改变每页条数时，重置到第一页
      pagination.pageSize = pageSize
      options.onShowSizeChange?.(page, pageSize)
    }
  })
  
  // 当前页
  const currentPage = computed({
    get: () => pagination.current || 1,
    set: (val: number) => {
      pagination.current = val
    }
  })
  
  // 每页条数
  const pageSize = computed({
    get: () => pagination.pageSize || 10,
    set: (val: number) => {
      pagination.pageSize = val
    }
  })
  
  // 总条数
  const total = computed({
    get: () => pagination.total || 0,
    set: (val: number) => {
      pagination.total = val
    }
  })
  
  // 总页数
  const totalPages = computed(() => {
    return Math.ceil(total.value / pageSize.value) || 1
  })
  
  // 是否是第一页
  const isFirstPage = computed(() => currentPage.value === 1)
  
  // 是否是最后一页
  const isLastPage = computed(() => currentPage.value >= totalPages.value)
  
  // 是否有上一页
  const hasPrev = computed(() => !isFirstPage.value)
  
  // 是否有下一页
  const hasNext = computed(() => !isLastPage.value)
  
  // 当前页的数据范围
  const dataRange = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value + 1
    const end = Math.min(currentPage.value * pageSize.value, total.value)
    return { start, end }
  })
  
  /**
   * 重置到第一页
   */
  const reset = () => {
    pagination.current = 1
  }
  
  /**
   * 跳转到指定页
   */
  const goToPage = (page: number) => {
    if (page < 1) page = 1
    if (page > totalPages.value) page = totalPages.value
    pagination.current = page
  }
  
  /**
   * 上一页
   */
  const prevPage = () => {
    if (hasPrev.value) {
      pagination.current = (pagination.current || 1) - 1
    }
  }
  
  /**
   * 下一页
   */
  const nextPage = () => {
    if (hasNext.value) {
      pagination.current = (pagination.current || 1) + 1
    }
  }
  
  /**
   * 第一页
   */
  const firstPage = () => {
    pagination.current = 1
  }
  
  /**
   * 最后一页
   */
  const lastPage = () => {
    pagination.current = totalPages.value
  }
  
  /**
   * 设置总条数
   */
  const setTotal = (val: number) => {
    pagination.total = val
    
    // 如果当前页超出范围，自动跳转到最后一页
    if (currentPage.value > totalPages.value) {
      pagination.current = totalPages.value || 1
    }
  }
  
  /**
   * 设置每页条数
   */
  const setPageSize = (val: number) => {
    pagination.pageSize = val
    pagination.current = 1 // 重置到第一页
  }
  
  /**
   * 获取请求参数（用于 API 请求）
   */
  const getRequestParams = () => {
    return {
      page: currentPage.value,
      pageSize: pageSize.value,
      // 有些后端使用 start/limit 格式
      start: currentPage.value,
      limit: pageSize.value,
      // 有些后端使用 offset/limit 格式
      offset: (currentPage.value - 1) * pageSize.value,
    }
  }
  
  /**
   * 处理表格变化（直接传给 ant-design-vue 的 Table 组件）
   */
  const handleTableChange = (pag: TablePaginationConfig) => {
    if (pag.current !== undefined) {
      pagination.current = pag.current
    }
    if (pag.pageSize !== undefined) {
      pagination.pageSize = pag.pageSize
    }
  }
  
  return {
    // 分页配置对象（用于 Table 组件）
    pagination,
    
    // 计算属性
    currentPage,
    pageSize,
    total,
    totalPages,
    isFirstPage,
    isLastPage,
    hasPrev,
    hasNext,
    dataRange,
    
    // 方法
    reset,
    goToPage,
    prevPage,
    nextPage,
    firstPage,
    lastPage,
    setTotal,
    setPageSize,
    getRequestParams,
    handleTableChange
  }
}

