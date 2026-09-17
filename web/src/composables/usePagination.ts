import { reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TablePaginationConfig } from 'ant-design-vue'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/api'

/**
 * 分页器增强 Composable
 * 用于统一管理分页逻辑
 *
 * 分页变化只走 handleTableChange 一条通道：config 上不挂 onChange / onShowSizeChange，
 * 否则 vc-pagination 改每页条数时会先后触发两者，后一次会覆盖掉前一次的重置结果。
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
}

export function usePagination(options: UsePaginationOptions = {}) {
  const { t } = useI18n()

  // 分页配置
  const pagination = reactive<TablePaginationConfig>({
    current: 1,
    pageSize: options.defaultPageSize ?? DEFAULT_PAGE_SIZE,
    total: 0,
    showSizeChanger: options.showSizeChanger !== false,
    showQuickJumper: options.showQuickJumper !== false,
    pageSizeOptions: options.pageSizeOptions ?? [...PAGE_SIZE_OPTIONS],
    showTotal: options.showTotal !== false
      ? (total: number) => (options.totalFormat ? options.totalFormat(total) : t('table.total', { total }))
      : undefined,
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
    get: () => pagination.pageSize || DEFAULT_PAGE_SIZE,
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

  // 总页数，无数据时仍有第 1 页
  const totalPages = computed(() => {
    return Math.ceil(total.value / pageSize.value) || 1
  })

  /**
   * 页码越界钳制的唯一入口
   * 无论从 pagination 直接改、从可写 computed 改还是走方法改，最终都在这里收口
   */
  watch(
    [() => pagination.current, () => pagination.pageSize, () => pagination.total],
    () => {
      const raw = Math.trunc(pagination.current ?? 1) || 1
      const clamped = Math.min(Math.max(raw, 1), totalPages.value)
      if (clamped !== pagination.current) {
        pagination.current = clamped
      }
    },
    { immediate: true, flush: 'sync' }
  )

  // 是否是第一页
  const isFirstPage = computed(() => currentPage.value === 1)

  // 是否是最后一页
  const isLastPage = computed(() => currentPage.value >= totalPages.value)

  // 是否有上一页
  const hasPrev = computed(() => !isFirstPage.value)

  // 是否有下一页
  const hasNext = computed(() => !isLastPage.value)

  // 当前页的数据范围，无数据时为 0-0
  const dataRange = computed(() => {
    if (total.value <= 0) {
      return { start: 0, end: 0 }
    }
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
   * 跳转到指定页，非有限数直接忽略，小数向下取整
   */
  const goToPage = (page: number) => {
    if (!Number.isFinite(page)) {
      return
    }
    pagination.current = Math.min(Math.max(Math.trunc(page), 1), totalPages.value)
  }

  /**
   * 上一页
   */
  const prevPage = () => {
    if (hasPrev.value) {
      pagination.current = currentPage.value - 1
    }
  }

  /**
   * 下一页
   */
  const nextPage = () => {
    if (hasNext.value) {
      pagination.current = currentPage.value + 1
    }
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
  }

  /**
   * 设置每页条数并回到第一页
   */
  const setPageSize = (val: number) => {
    pagination.pageSize = val
    pagination.current = 1
  }

  /**
   * 获取请求参数（用于 API 请求）
   */
  const getRequestParams = () => {
    return {
      pageNo: currentPage.value,
      pageSize: pageSize.value,
    }
  }

  /**
   * 处理表格变化（直接传给 ant-design-vue 的 Table 组件）
   */
  const handleTableChange = (pag: TablePaginationConfig) => {
    if (pag.pageSize !== undefined) {
      pagination.pageSize = pag.pageSize
    }
    if (pag.current !== undefined) {
      pagination.current = pag.current
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
    lastPage,
    setTotal,
    setPageSize,
    getRequestParams,
    handleTableChange
  }
}
