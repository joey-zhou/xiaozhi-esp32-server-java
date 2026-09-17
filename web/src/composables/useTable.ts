import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { useDebounceFn } from '@vueuse/core'
import type { TablePaginationConfig } from 'ant-design-vue'
import type { PageResponse } from '@/types/api'
import { isForbiddenError, shouldIgnoreRequestError } from '@/services/request'
import { DEBOUNCE_DELAY } from '@/constants/api'
import { usePagination } from './usePagination'

/** 列表请求函数，分页参数由 useTable 注入，其余查询条件由调用方闭包带入 */
export type TableFetchFn<T> = (params: { pageNo: number; pageSize: number }) => Promise<PageResponse<T>>

/**
 * 表格分页管理 Composable
 * 分页状态委托给 usePagination，页码越界钳制由其内部 watch 统一负责
 *
 * 传入 fetchFn 时直接用 fetchData / onTableChange / debouncedSearch；
 * 一次翻页要做多件事的场景仍可只用 loadData / handleTableChange / createDebouncedSearch 自己拼
 */
export function useTable<T = unknown>(defaultFetchFn?: TableFetchFn<T>) {
  const { t } = useI18n()
  const loading = ref<boolean>(false)
  const data = ref<T[]>([])

  const pg = usePagination()
  const { pagination } = pg

  /**
   * 处理分页变化
   */
  const handleTableChange = pg.handleTableChange

  /**
   * 重置到第一页
   */
  const resetPagination = pg.reset

  /** 已发起的请求序号，只认最后一次：防抖搜索与翻页并发时，先返回的旧响应不能覆盖新数据 */
  let loadSeq = 0

  /**
   * 加载数据（带错误处理）
   */
  const loadData = async (
    fetchFn: TableFetchFn<T>,
    options?: {
      showError?: boolean
      onSuccess?: () => void
      onError?: (error: unknown) => void
    }
  ) => {
    const { showError = true, onSuccess, onError } = options || {}
    const seq = ++loadSeq

    try {
      loading.value = true
      const res = await fetchFn(pg.getRequestParams())

      // 已有更新的请求在途，这一份结果连同它的报错一并丢弃
      if (seq !== loadSeq) {
        return
      }

      if (res.code === 200) {
        data.value = res.data?.list || []
        pg.setTotal(res.data?.total || 0)
        onSuccess?.()
      } else {
        if (showError) {
          message.error(res.message || t('common.loadDataFailed'))
        }
        onError?.(res)
      }
    } catch (error: unknown) {
      if (seq !== loadSeq) {
        return
      }
      if (shouldIgnoreRequestError(error)) {
        onError?.(error)
        return
      }
      // 403 的「权限不足」由 request.ts 弹出，这里再覆盖就只剩一句笼统的加载失败
      if (isForbiddenError(error)) {
        onError?.(error)
        return
      }
      console.error('Error loading data:', error)
      // 传输层错误由 request.ts 的拦截器统一弹提示，这里用同一个 key 覆盖成本地化文案，
      // 不叠第二条、也不把 axios 的英文原文弹给用户
      if (showError) {
        message.error({ content: t('common.loadDataFailed'), key: 'request-error' })
      }
      onError?.(error)
    } finally {
      if (seq === loadSeq) {
        loading.value = false
      }
    }
  }

  /**
   * 创建防抖的搜索函数
   */
  const createDebouncedSearch = (searchFn: () => void, delay = DEBOUNCE_DELAY) => {
    return useDebounceFn(() => {
      resetPagination()
      searchFn()
    }, delay)
  }

  /**
   * 按当前分页拉取数据，未传 fetchFn 时不做任何事
   */
  const fetchData = async () => {
    if (!defaultFetchFn) return
    await loadData(defaultFetchFn)
  }

  /**
   * 直接绑到 a-table 的 @change：先记录分页再重新拉数据
   */
  const onTableChange = (pag: TablePaginationConfig) => {
    handleTableChange(pag)
    void fetchData()
  }

  /**
   * 防抖搜索：回到第一页再重新拉数据
   */
  const debouncedSearch = createDebouncedSearch(() => {
    void fetchData()
  })

  return {
    loading,
    data,
    pagination,
    handleTableChange,
    resetPagination,
    loadData,
    createDebouncedSearch,

    // 传入 fetchFn 后可直接使用的三件套
    fetchData,
    onTableChange,
    debouncedSearch,

    // usePagination 的派生量与页码操作
    currentPage: pg.currentPage,
    pageSize: pg.pageSize,
    total: pg.total,
    totalPages: pg.totalPages,
    isFirstPage: pg.isFirstPage,
    isLastPage: pg.isLastPage,
    hasPrev: pg.hasPrev,
    hasNext: pg.hasNext,
    dataRange: pg.dataRange,
    goToPage: pg.goToPage,
    prevPage: pg.prevPage,
    nextPage: pg.nextPage,
    lastPage: pg.lastPage,
    setPageSize: pg.setPageSize,
  }
}
