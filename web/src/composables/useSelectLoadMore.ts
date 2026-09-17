import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from '@/composables/useRequest'
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
  const { execute } = useRequest()

  const list = ref<T[]>([])
  const loading = ref(false)
  const currentPage = ref(1)
  const hasNextPage = ref(true)

  async function loadPage(page: number) {
    if (loading.value) return

    // 业务码失败只留空列表不弹提示；传输层错误由 useRequest 用 request-error 这个 key 覆盖成本地化文案，
    // 路由切换导致的取消在 useRequest 里已经被判成静默错误
    await execute(() => fetchFn({ pageNo: page, pageSize: PAGE_SIZE }), {
      loadingRef: loading,
      showError: false,
      networkErrorText: t('common.loadDataFailed'),
      onSuccess: (data) => {
        if (!data) return
        const pageList = (data.list ?? []) as T[]
        list.value = page === 1 ? pageList : ([...list.value, ...pageList] as T[])
        // 后端 PageResult 只有 list/total/pageNo/pageSize，没有 hasNextPage，只能按已加载条数判断
        hasNextPage.value = pageList.length > 0 && list.value.length < (data.total ?? 0)
        currentPage.value = page
      },
    })
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
