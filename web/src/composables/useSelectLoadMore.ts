import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { shouldIgnoreRequestError } from '@/services/request'
import type { PageResponse } from '@/types/api'

const PAGE_SIZE = 50

/**
 * 下拉框滚动加载更多 Composable
 * 支持初始加载 + 滚动到底部自动加载下一页
 */
export function useSelectLoadMore<T extends object>(
  fetchFn: (params: { pageNo: number; pageSize: number }) => Promise<PageResponse<T>>
) {
  const { t } = useI18n()

  const list = ref<T[]>([])
  const loading = ref(false)
  const currentPage = ref(1)
  const hasNextPage = ref(true)

  async function loadPage(page: number) {
    if (loading.value) return
    loading.value = true
    try {
      const res = await fetchFn({ pageNo: page, pageSize: PAGE_SIZE })
      if (res.code === 200 && res.data) {
        const pageList = (res.data.list ?? []) as T[]
        list.value = page === 1 ? pageList : ([...list.value, ...pageList] as T[])
        // 后端 PageResult 只有 list/total/pageNo/pageSize，没有 hasNextPage，只能按已加载条数判断
        hasNextPage.value = pageList.length > 0 && list.value.length < (res.data.total ?? 0)
        currentPage.value = page
      }
    } catch (error) {
      // 路由切换会取消在途请求，这类中断不弹提示；
      // 传输层错误由 request.ts 拦截器统一弹，这里用同一个 key 覆盖成本地化文案，不叠第二条
      if (!shouldIgnoreRequestError(error)) {
        console.error('Error loading options:', error)
        message.error({ content: t('common.loadDataFailed'), key: 'request-error' })
      }
    } finally {
      loading.value = false
    }
  }

  async function load() {
    currentPage.value = 1
    hasNextPage.value = true
    list.value = []
    await loadPage(1)
  }

  async function loadMore() {
    if (!hasNextPage.value || loading.value) return
    await loadPage(currentPage.value + 1)
  }

  function onPopupScroll(e: Event) {
    const target = e.target as HTMLElement
    if (target.scrollTop + target.clientHeight >= target.scrollHeight - 20) {
      loadMore()
    }
  }

  return { list, loading, hasNextPage, load, loadMore, onPopupScroll }
}
