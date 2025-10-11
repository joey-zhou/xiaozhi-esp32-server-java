import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { TablePaginationConfig } from 'ant-design-vue'
import { useDebounceFn } from '@vueuse/core'

/**
 * 表格分页管理 Composable（增强版）
 */
export function useTable<T = any>() {
  const { t } = useI18n()
  const loading = ref<boolean>(false)
  const data = ref<T[]>([])

  // 分页配置
  const pagination = reactive<TablePaginationConfig>({
    current: 1,
    pageSize: 10,
    total: 0,
    showTotal: (total: number) => t('table.total', { total }),
    showSizeChanger: true,
    showQuickJumper: true,
    pageSizeOptions: ['10', '30', '50', '100', '1000'],
  })

  /**
   * 处理分页变化
   */
  const handleTableChange = (pag: TablePaginationConfig) => {
    pagination.current = pag.current
    pagination.pageSize = pag.pageSize
  }

  /**
   * 重置到第一页
   */
  const resetPagination = () => {
    pagination.current = 1
  }

  /**
   * 加载数据（带错误处理）
   */
  const loadData = async (
    fetchFn: (params: { start: number; limit: number }) => Promise<{ code: number; data?: { list: T[]; total: number; pageNum: number; pageSize: number }; message?: string }>,
    options?: {
      showError?: boolean
      onSuccess?: () => void
      onError?: (error: unknown) => void
    }
  ) => {
    const { showError = true, onSuccess, onError } = options || {}

    try {
      loading.value = true
      const res = await fetchFn({
        start: pagination.current || 1,
        limit: pagination.pageSize || 10,
      })

      if (res.code === 200) {
        data.value = res.data?.list || []
        pagination.total = res.data?.total || 0
        onSuccess?.()
      } else {
        if (showError) {
          message.error(res.message || '获取数据失败')
        }
        onError?.(res)
      }
    } catch (error: unknown) {
      console.error('Error loading data:', error)
      if (showError) {
        const errorMessage = error instanceof Error ? error.message : '获取数据失败'
        message.error(errorMessage)
      }
      onError?.(error)
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建防抖的搜索函数
   */
  const createDebouncedSearch = (searchFn: () => void, delay = 500) => {
    return useDebounceFn(() => {
      resetPagination()
      searchFn()
    }, delay)
  }

  return {
    loading,
    data,
    pagination,
    handleTableChange,
    resetPagination,
    loadData,
    createDebouncedSearch,
  }
}

