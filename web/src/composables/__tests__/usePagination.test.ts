import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
  }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

import { toRaw } from 'vue'

import { PAGE_SIZE_OPTIONS } from '@/constants/api'

import { usePagination } from '../usePagination'

describe('usePagination 分页配置', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // vc-pagination 改每页条数时会先后触发 onShowSizeChange 与 onChange，
  // 两个回调都挂在 config 上会让「重置到第一页」被后一次覆盖
  it('config 上不挂 onChange / onShowSizeChange，只留 handleTableChange 一条通道', () => {
    const { pagination } = usePagination()
    expect(pagination.onChange).toBeUndefined()
    expect(pagination.onShowSizeChange).toBeUndefined()
  })

  it('默认每页条数选项取自 PAGE_SIZE_OPTIONS 且是副本，避免共享常量被就地改写', () => {
    const { pagination } = usePagination()
    expect(pagination.pageSizeOptions).toEqual(PAGE_SIZE_OPTIONS)
    expect(toRaw(pagination).pageSizeOptions).not.toBe(PAGE_SIZE_OPTIONS)
  })

  it('默认每页条数为 10，可被 options 覆盖', () => {
    expect(usePagination().pagination.pageSize).toBe(10)
    expect(usePagination({ defaultPageSize: 30 }).pagination.pageSize).toBe(30)
  })

  it('totalFormat 返回空串时按空串渲染，不回落到默认文案', () => {
    const { pagination } = usePagination({ totalFormat: () => '' })
    expect(pagination.showTotal?.(5, [0, 0])).toBe('')
  })

  it('未提供 totalFormat 时用 i18n 默认文案', () => {
    const { pagination } = usePagination()
    expect(pagination.showTotal?.(5, [0, 0])).toBe('table.total:{"total":5}')
  })

  it('showTotal 为 false 时不给 showTotal', () => {
    const { pagination } = usePagination({ showTotal: false })
    expect(pagination.showTotal).toBeUndefined()
  })
})

describe('usePagination 页码钳制', () => {
  it('setTotal 变小后把 current 拉回最后一页', () => {
    const pg = usePagination()
    pg.setTotal(41)
    pg.goToPage(5)
    expect(pg.currentPage.value).toBe(5)

    pg.setTotal(40)
    expect(pg.currentPage.value).toBe(4)
  })

  // 旧写法只在 setTotal 里钳制，直接写 total computed 会绕过
  it('直接写可写的 total computed 一样被钳制', () => {
    const pg = usePagination()
    pg.setTotal(100)
    pg.goToPage(10)

    pg.total.value = 20
    expect(pg.currentPage.value).toBe(2)
  })

  // 旧写法把可变的 pagination 原样导出，调用方直接改 total 也绕过钳制
  it('直接写导出的 pagination.total 一样被钳制', () => {
    const pg = usePagination()
    pg.setTotal(100)
    pg.pagination.current = 10

    pg.pagination.total = 5
    expect(pg.pagination.current).toBe(1)
  })

  it('直接写 pagination.current 超界时被拉回', () => {
    const pg = usePagination()
    pg.setTotal(25)
    pg.pagination.current = 99
    expect(pg.pagination.current).toBe(3)
  })

  it('调大 pageSize 使总页数变少时 current 被拉回', () => {
    const pg = usePagination()
    pg.setTotal(100)
    pg.goToPage(10)

    pg.pagination.pageSize = 100
    expect(pg.pagination.current).toBe(1)
  })

  it('总数为 0 时 current 固定在第一页', () => {
    const pg = usePagination()
    pg.setTotal(0)
    pg.pagination.current = 7
    expect(pg.pagination.current).toBe(1)
    expect(pg.totalPages.value).toBe(1)
  })
})

describe('usePagination goToPage', () => {
  it('拒绝 NaN 与 Infinity，保持原页码', () => {
    const pg = usePagination()
    pg.setTotal(100)
    pg.goToPage(3)

    pg.goToPage(Number.NaN)
    expect(pg.currentPage.value).toBe(3)

    pg.goToPage(Number.POSITIVE_INFINITY)
    expect(pg.currentPage.value).toBe(3)
  })

  it('小数向下取整', () => {
    const pg = usePagination()
    pg.setTotal(100)
    pg.goToPage(3.9)
    expect(pg.currentPage.value).toBe(3)
  })

  it('越界入参被夹到合法区间', () => {
    const pg = usePagination()
    pg.setTotal(25)
    pg.goToPage(0)
    expect(pg.currentPage.value).toBe(1)
    pg.goToPage(99)
    expect(pg.currentPage.value).toBe(3)
  })
})

describe('usePagination 派生量', () => {
  it('总数为 0 时数据范围是 0-0', () => {
    const pg = usePagination()
    pg.setTotal(0)
    expect(pg.dataRange.value).toEqual({ start: 0, end: 0 })
  })

  it('有数据时数据范围按当前页计算', () => {
    const pg = usePagination()
    pg.setTotal(25)
    expect(pg.dataRange.value).toEqual({ start: 1, end: 10 })
    pg.goToPage(3)
    expect(pg.dataRange.value).toEqual({ start: 21, end: 25 })
  })

  it('首末页判定', () => {
    const pg = usePagination()
    pg.setTotal(25)
    expect(pg.isFirstPage.value).toBe(true)
    expect(pg.hasPrev.value).toBe(false)
    expect(pg.hasNext.value).toBe(true)

    pg.lastPage()
    expect(pg.currentPage.value).toBe(3)
    expect(pg.isLastPage.value).toBe(true)
    expect(pg.hasNext.value).toBe(false)
  })

  it('上下页在边界处不越界', () => {
    const pg = usePagination()
    pg.setTotal(25)
    pg.prevPage()
    expect(pg.currentPage.value).toBe(1)
    pg.nextPage()
    pg.nextPage()
    pg.nextPage()
    expect(pg.currentPage.value).toBe(3)
  })
})

describe('usePagination 请求参数与操作', () => {
  // offset 会被 axios 原样序列化进 query，后端并不认这个参数
  it('请求参数只有 pageNo 与 pageSize', () => {
    const pg = usePagination()
    pg.setTotal(100)
    pg.goToPage(3)
    expect(pg.getRequestParams()).toEqual({ pageNo: 3, pageSize: 10 })
  })

  it('setPageSize 会回到第一页', () => {
    const pg = usePagination()
    pg.setTotal(1000)
    pg.goToPage(5)
    pg.setPageSize(30)
    expect(pg.pagination.pageSize).toBe(30)
    expect(pg.currentPage.value).toBe(1)
  })

  it('handleTableChange 同时接收页码与每页条数', () => {
    const pg = usePagination()
    pg.setTotal(1000)
    pg.handleTableChange({ current: 4, pageSize: 30 })
    expect(pg.currentPage.value).toBe(4)
    expect(pg.pageSize.value).toBe(30)
  })

  it('handleTableChange 忽略缺省字段', () => {
    const pg = usePagination()
    pg.setTotal(1000)
    pg.goToPage(4)
    pg.handleTableChange({})
    expect(pg.currentPage.value).toBe(4)
    expect(pg.pageSize.value).toBe(10)
  })

  it('reset 回到第一页，且不再另有同码的 firstPage', () => {
    const pg = usePagination()
    pg.setTotal(1000)
    pg.goToPage(6)
    pg.reset()
    expect(pg.currentPage.value).toBe(1)
    expect('firstPage' in pg).toBe(false)
  })
})
