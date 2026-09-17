import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  shouldIgnoreRequestError: vi.fn(() => false),
  isForbiddenError: vi.fn(() => false),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

vi.mock('@/services/request', () => requestMock)

// 防抖在单测里直接透传，只验「防抖搜索会先回到第一页」这一语义
vi.mock('@vueuse/core', () => ({
  useDebounceFn: (fn: () => void) => fn,
}))

import { message } from 'ant-design-vue'

import { useTable } from '../useTable'

type Row = { id: number }

function pageOk(list: Row[], total: number) {
  return { code: 200, message: 'ok', data: { list, total } }
}

describe('useTable 返回契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMock.shouldIgnoreRequestError.mockReturnValue(false)
  })

  // 12 个 view 直接解构这些字段，改名即破坏调用点
  it('保留原有 7 个字段名，派生量只在其后追加', () => {
    const table = useTable<Row>()
    expect(Object.keys(table)).toEqual([
      'loading',
      'data',
      'pagination',
      'handleTableChange',
      'resetPagination',
      'loadData',
      'createDebouncedSearch',
      'fetchData',
      'onTableChange',
      'debouncedSearch',
      'loadError',
      'retryLoad',
      'currentPage',
      'pageSize',
      'total',
      'totalPages',
      'isFirstPage',
      'isLastPage',
      'hasPrev',
      'hasNext',
      'dataRange',
      'goToPage',
      'prevPage',
      'nextPage',
      'lastPage',
      'setPageSize',
    ])
  })

  it('分页初值与 antd 配置保持现状', () => {
    const { pagination } = useTable<Row>()
    expect(pagination.current).toBe(1)
    expect(pagination.pageSize).toBe(10)
    expect(pagination.total).toBe(0)
    expect(pagination.showSizeChanger).toBe(true)
    expect(pagination.showQuickJumper).toBe(true)
    expect(pagination.pageSizeOptions).toEqual(['10', '30', '50', '100', '1000'])
    expect(typeof pagination.showTotal).toBe('function')
  })

  it('pagination 仍可被调用方直接写（ConfigManager/FirmwareView/MemoryManagementView 依赖）', () => {
    const { pagination } = useTable<Row>()
    pagination.total = 100
    pagination.current = 3
    pagination.pageSize = 30
    expect(pagination.current).toBe(3)
    expect(pagination.pageSize).toBe(30)
  })
})

describe('useTable loadData', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMock.shouldIgnoreRequestError.mockReturnValue(false)
  })

  it('按当前分页取参并回填列表与总数', async () => {
    const table = useTable<Row>()
    table.pagination.total = 100
    table.pagination.current = 2
    const fetchFn = vi.fn().mockResolvedValue(pageOk([{ id: 1 }], 100))

    await table.loadData(fetchFn)

    expect(fetchFn).toHaveBeenCalledWith({ pageNo: 2, pageSize: 10 })
    expect(table.data.value).toEqual([{ id: 1 }])
    expect(table.pagination.total).toBe(100)
    expect(table.loading.value).toBe(false)
  })

  it('请求参数只有 pageNo 与 pageSize，不夹带 offset', async () => {
    const table = useTable<Row>()
    const fetchFn = vi.fn().mockResolvedValue(pageOk([], 0))

    await table.loadData(fetchFn)

    expect(Object.keys(fetchFn.mock.calls[0]![0]).sort()).toEqual(['pageNo', 'pageSize'])
  })

  // 用户可见 bug：最后一页删掉最后一条后总数变小，旧写法不钳制 current，
  // 分页器因为 page === stateCurrent 不再 emit change，点了没反应
  it('总数变小后把 current 钳回最后一页', async () => {
    const table = useTable<Row>()
    table.pagination.total = 41
    table.pagination.current = 5
    const fetchFn = vi.fn().mockResolvedValue(pageOk([], 40))

    await table.loadData(fetchFn)

    expect(table.pagination.total).toBe(40)
    expect(table.pagination.current).toBe(4)
  })

  it('总数归零时回到第一页', async () => {
    const table = useTable<Row>()
    table.pagination.total = 41
    table.pagination.current = 5
    const fetchFn = vi.fn().mockResolvedValue(pageOk([], 0))

    await table.loadData(fetchFn)

    expect(table.pagination.current).toBe(1)
  })

  it('业务码非 200 时报错并回调 onError', async () => {
    const table = useTable<Row>()
    const onError = vi.fn()
    const fetchFn = vi.fn().mockResolvedValue({ code: 500, message: 'boom', data: null })

    await table.loadData(fetchFn, { onError })

    expect(message.error).toHaveBeenCalledWith('boom')
    expect(onError).toHaveBeenCalled()
    expect(table.loading.value).toBe(false)
  })

  // 传输层错误已由 request.ts 拦截器用 key 'request-error' 弹过一次，
  // 这里必须复用同一个 key 覆盖，否则用户会看到第二条 axios 英文原文
  it('传输层错误只用同一个 message key 覆盖，不弹 axios 原文', async () => {
    const table = useTable<Row>()
    const fetchFn = vi.fn().mockRejectedValue(new Error('Request failed with status code 500'))

    await table.loadData(fetchFn)

    expect(message.error).toHaveBeenCalledTimes(1)
    expect(message.error).toHaveBeenCalledWith({
      content: 'common.loadDataFailed',
      key: 'request-error',
    })
  })

  it('被忽略的请求错误不报 message', async () => {
    requestMock.shouldIgnoreRequestError.mockReturnValue(true)
    const table = useTable<Row>()
    const onError = vi.fn()
    const fetchFn = vi.fn().mockRejectedValue(new Error('canceled'))

    await table.loadData(fetchFn, { onError })

    expect(message.error).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalled()
  })
})

describe('useTable 传入 fetchFn 后的三件套', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMock.shouldIgnoreRequestError.mockReturnValue(false)
  })

  it('fetchData 按当前分页拉数据', async () => {
    const fetchFn = vi.fn().mockResolvedValue(pageOk([{ id: 1 }], 1))
    const table = useTable<Row>(fetchFn)

    await table.fetchData()

    expect(fetchFn).toHaveBeenCalledWith({ pageNo: 1, pageSize: 10 })
    expect(table.data.value).toEqual([{ id: 1 }])
  })

  it('onTableChange 先记录分页再按新分页拉数据', async () => {
    const fetchFn = vi.fn().mockResolvedValue(pageOk([], 1000))
    const table = useTable<Row>(fetchFn)
    table.pagination.total = 1000

    table.onTableChange({ current: 3, pageSize: 30 })
    await vi.waitFor(() => expect(fetchFn).toHaveBeenCalled())

    expect(table.pagination.current).toBe(3)
    expect(fetchFn).toHaveBeenCalledWith({ pageNo: 3, pageSize: 30 })
  })

  it('debouncedSearch 先回第一页再拉数据', async () => {
    const fetchFn = vi.fn().mockResolvedValue(pageOk([], 1000))
    const table = useTable<Row>(fetchFn)
    table.pagination.total = 1000
    table.pagination.current = 6

    table.debouncedSearch()
    await vi.waitFor(() => expect(fetchFn).toHaveBeenCalled())

    expect(table.pagination.current).toBe(1)
    expect(fetchFn).toHaveBeenCalledWith({ pageNo: 1, pageSize: 10 })
  })

  it('未传 fetchFn 时 fetchData 不发请求', async () => {
    const table = useTable<Row>()
    await table.fetchData()
    expect(table.loading.value).toBe(false)
  })
})

// 查询条件塞不进 TableFetchFn 的签名，是九个列表页各写一遍 onTableChange 的直接原因
describe('useTable 查询条件', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMock.shouldIgnoreRequestError.mockReturnValue(false)
  })

  it('getQuery 的返回值与分页参数一起传给 fetchFn', async () => {
    const fetchFn = vi.fn().mockResolvedValue(pageOk([], 0))
    const query = { keyword: 'abc' }
    const table = useTable<Row, { keyword: string }>(fetchFn, () => query)

    await table.fetchData()

    expect(fetchFn).toHaveBeenCalledWith({ pageNo: 1, pageSize: 10, keyword: 'abc' })
  })

  it('每次请求前重新求值，取的是最新的查询条件', async () => {
    const fetchFn = vi.fn().mockResolvedValue(pageOk([], 0))
    let keyword = 'first'
    const table = useTable<Row, { keyword: string }>(fetchFn, () => ({ keyword }))

    await table.fetchData()
    keyword = 'second'
    await table.fetchData()

    expect(fetchFn).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 10, keyword: 'second' })
  })
})

// 没有失败态时，请求失败后表格只剩「暂无数据」，与真的没数据分不开
describe('useTable 失败态与重试', () => {
  // clearAllMocks 不会还原 mockReturnValue，两个判定都得逐个复位
  beforeEach(() => {
    vi.clearAllMocks()
    requestMock.shouldIgnoreRequestError.mockReturnValue(false)
    requestMock.isForbiddenError.mockReturnValue(false)
  })

  afterEach(() => {
    requestMock.isForbiddenError.mockReturnValue(false)
  })

  it('初始没有失败态', () => {
    expect(useTable<Row>().loadError.value).toBeNull()
  })

  it('业务码非 200 时把后端原文暴露成失败态', async () => {
    const table = useTable<Row>()
    await table.loadData(vi.fn().mockResolvedValue({ code: 500, message: 'boom', data: null }))
    expect(table.loadError.value).toBe('boom')
  })

  it('传输层错误落成本地化的加载失败文案', async () => {
    const table = useTable<Row>()
    await table.loadData(vi.fn().mockRejectedValue(new Error('Network Error')))
    expect(table.loadError.value).toBe('common.loadDataFailed')
  })

  it('403 用「无权限访问」而不是笼统的加载失败', async () => {
    requestMock.isForbiddenError.mockReturnValue(true)
    const table = useTable<Row>()
    await table.loadData(vi.fn().mockRejectedValue(new Error('forbidden')))
    expect(table.loadError.value).toBe('error.forbidden')
  })

  // 请求被取消或登录过期时页面正在被替换，标失败态只会闪一下红字
  it('被忽略的请求错误不进失败态', async () => {
    requestMock.shouldIgnoreRequestError.mockReturnValue(true)
    const table = useTable<Row>()
    await table.loadData(vi.fn().mockRejectedValue(new Error('canceled')))
    expect(table.loadError.value).toBeNull()
  })

  it('重新加载成功后清掉失败态', async () => {
    const table = useTable<Row>()
    const fetchFn = vi
      .fn()
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValue(pageOk([{ id: 1 }], 1))

    await table.loadData(fetchFn)
    expect(table.loadError.value).toBe('common.loadDataFailed')

    await table.loadData(fetchFn)
    expect(table.loadError.value).toBeNull()
  })

  it('retryLoad 用同一组参数重发请求并清掉失败态', async () => {
    const fetchFn = vi
      .fn()
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValue(pageOk([{ id: 9 }], 1))
    const table = useTable<Row, { keyword: string }>(fetchFn, () => ({ keyword: 'kw' }))
    table.pagination.total = 100
    table.pagination.current = 2

    await table.fetchData()
    expect(table.loadError.value).toBe('common.loadDataFailed')

    await table.retryLoad()

    expect(fetchFn).toHaveBeenCalledTimes(2)
    expect(fetchFn).toHaveBeenLastCalledWith({ pageNo: 2, pageSize: 10, keyword: 'kw' })
    expect(table.loadError.value).toBeNull()
    expect(table.data.value).toEqual([{ id: 9 }])
  })

  // loadData 直接调用的页面（ConfigManager 等）也要能重试，重试重放的是最后那次 loadData
  it('retryLoad 重放最近一次 loadData 而不是默认 fetchFn', async () => {
    const defaultFetch = vi.fn().mockResolvedValue(pageOk([], 0))
    const adHocFetch = vi.fn().mockRejectedValue(new Error('boom'))
    const table = useTable<Row>(defaultFetch)

    await table.loadData(adHocFetch)
    await table.retryLoad()

    expect(adHocFetch).toHaveBeenCalledTimes(2)
    expect(defaultFetch).not.toHaveBeenCalled()
  })
})

describe('useTable 分页操作', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('handleTableChange 透传页码与每页条数', () => {
    const { pagination, handleTableChange } = useTable<Row>()
    pagination.total = 1000
    handleTableChange({ current: 3, pageSize: 30 })
    expect(pagination.current).toBe(3)
    expect(pagination.pageSize).toBe(30)
  })

  it('resetPagination 回到第一页', () => {
    const { pagination, resetPagination } = useTable<Row>()
    pagination.total = 1000
    pagination.current = 6
    resetPagination()
    expect(pagination.current).toBe(1)
  })

  it('createDebouncedSearch 先重置页码再执行搜索', () => {
    const { pagination, createDebouncedSearch } = useTable<Row>()
    pagination.total = 1000
    pagination.current = 6
    const searchFn = vi.fn(() => {
      expect(pagination.current).toBe(1)
    })

    createDebouncedSearch(searchFn)()

    expect(searchFn).toHaveBeenCalledTimes(1)
    expect(pagination.current).toBe(1)
  })
})

// 防抖搜索与翻页并发时，先发出的请求可能后返回，旧列表会盖掉新列表
describe('useTable 并发请求', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestMock.shouldIgnoreRequestError.mockReturnValue(false)
  })

  it('后发起的请求先返回时，旧响应不覆盖新数据', async () => {
    const table = useTable<Row>()
    let resolveSlow: (value: unknown) => void = () => {}
    const slow = new Promise((resolve) => {
      resolveSlow = resolve
    })

    const stale = table.loadData(() => slow as Promise<ReturnType<typeof pageOk>>)
    const fresh = table.loadData(async () => pageOk([{ id: 2 }], 1))
    await fresh

    resolveSlow(pageOk([{ id: 1 }], 99))
    await stale

    expect(table.data.value).toEqual([{ id: 2 }])
    expect(table.pagination.total).toBe(1)
  })

  it('旧请求失败时既不弹提示也不把 loading 关掉后再乱改', async () => {
    const table = useTable<Row>()
    let rejectStale: (reason: unknown) => void = () => {}
    const stalePromise = new Promise((_resolve, reject) => {
      rejectStale = reject
    })

    const stale = table.loadData(() => stalePromise as Promise<ReturnType<typeof pageOk>>)
    const fresh = table.loadData(async () => pageOk([{ id: 2 }], 1))
    await fresh

    rejectStale(new Error('boom'))
    await stale

    expect(message.error).not.toHaveBeenCalled()
    expect(table.data.value).toEqual([{ id: 2 }])
    expect(table.loading.value).toBe(false)
  })
})
