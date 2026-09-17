import { beforeEach, describe, expect, it, vi } from 'vitest'
import { message } from 'ant-design-vue'

import { useSelectLoadMore } from '../useSelectLoadMore'

type Row = { id: number }

const PAGE_SIZE = 50

function rows(from: number, count: number): Row[] {
  return Array.from({ length: count }, (_, index) => ({ id: from + index }))
}

/** 后端 PageResult 的真实形状：只有 list/total/pageNo/pageSize */
function pageOk(list: Row[], total: number, pageNo: number) {
  return { code: 200, message: 'ok', data: { list, total, pageNo, pageSize: PAGE_SIZE } }
}

describe('useSelectLoadMore 翻页判定', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('总数大于已加载条数时可以继续加载下一页', async () => {
    const fetchFn = vi.fn()
    fetchFn.mockResolvedValueOnce(pageOk(rows(1, PAGE_SIZE), 120, 1))
    fetchFn.mockResolvedValueOnce(pageOk(rows(51, PAGE_SIZE), 120, 2))

    const { list, hasNextPage, load, loadMore } = useSelectLoadMore<Row>(fetchFn)

    await load()
    expect(list.value).toHaveLength(PAGE_SIZE)
    expect(hasNextPage.value).toBe(true)

    await loadMore()
    expect(fetchFn).toHaveBeenLastCalledWith({ pageNo: 2, pageSize: PAGE_SIZE })
    expect(list.value).toHaveLength(PAGE_SIZE * 2)
    expect(list.value[50]).toEqual({ id: 51 })
  })

  it('已加载条数达到总数后不再请求', async () => {
    const fetchFn = vi.fn()
    fetchFn.mockResolvedValue(pageOk(rows(1, 20), 20, 1))

    const { hasNextPage, load, loadMore } = useSelectLoadMore<Row>(fetchFn)

    await load()
    expect(hasNextPage.value).toBe(false)

    await loadMore()
    expect(fetchFn).toHaveBeenCalledTimes(1)
  })

  it('返回空页时停止翻页，避免总数不准时空转', async () => {
    const fetchFn = vi.fn()
    fetchFn.mockResolvedValueOnce(pageOk(rows(1, PAGE_SIZE), 999, 1))
    fetchFn.mockResolvedValueOnce(pageOk([], 999, 2))

    const { hasNextPage, load, loadMore } = useSelectLoadMore<Row>(fetchFn)

    await load()
    await loadMore()
    expect(hasNextPage.value).toBe(false)

    await loadMore()
    expect(fetchFn).toHaveBeenCalledTimes(2)
  })

  it('重新 load 时丢掉上一次的结果', async () => {
    const fetchFn = vi.fn()
    fetchFn.mockResolvedValueOnce(pageOk(rows(1, PAGE_SIZE), 120, 1))
    fetchFn.mockResolvedValueOnce(pageOk(rows(51, PAGE_SIZE), 120, 2))
    fetchFn.mockResolvedValueOnce(pageOk(rows(1, 3), 3, 1))

    const { list, hasNextPage, load, loadMore } = useSelectLoadMore<Row>(fetchFn)

    await load()
    await loadMore()
    await load()

    expect(list.value).toHaveLength(3)
    expect(hasNextPage.value).toBe(false)
  })

  it('请求失败时不把异常抛给调用方，弹一条本地化提示', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new Error('boom'))
    vi.spyOn(console, 'error').mockImplementation(() => {})

    const { list, loading, load } = useSelectLoadMore<Row>(fetchFn)

    await expect(load()).resolves.toBeUndefined()
    expect(list.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(message.error).toHaveBeenCalledWith({
      content: 'common.loadDataFailed',
      key: 'request-error',
    })
  })

  it('请求被取消时不弹提示', async () => {
    const canceled = Object.assign(new Error('canceled'), { isRequestCanceled: true })
    const fetchFn = vi.fn().mockRejectedValue(canceled)

    const { load } = useSelectLoadMore<Row>(fetchFn)

    await expect(load()).resolves.toBeUndefined()
    expect(message.error).not.toHaveBeenCalled()
  })

  it('滚动到底部才触发加载', async () => {
    const fetchFn = vi.fn()
    fetchFn.mockResolvedValue(pageOk(rows(1, PAGE_SIZE), 120, 1))

    const { load, onPopupScroll } = useSelectLoadMore<Row>(fetchFn)
    await load()

    const target = { scrollTop: 0, clientHeight: 300, scrollHeight: 1000 }
    onPopupScroll({ target } as unknown as Event)
    expect(fetchFn).toHaveBeenCalledTimes(1)

    target.scrollTop = 700
    onPopupScroll({ target } as unknown as Event)
    await Promise.resolve()
    expect(fetchFn).toHaveBeenCalledTimes(2)
  })
})
