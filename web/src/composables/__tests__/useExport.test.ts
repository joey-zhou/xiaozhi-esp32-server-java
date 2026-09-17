import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useExport, type ExportColumn } from '../useExport'

type Row = { name: string; total: number | null }

function csvRows(data: Row[], columns?: ExportColumn<Row>[]): string[] {
  return useExport().convertToCSV(data, columns).split('\n')
}

describe('convertToCSV 公式注入转义', () => {
  it('以 = + @ 开头的文本前置单引号，Excel 打开时当文本而不是公式', () => {
    const [, ...rows] = csvRows([
      { name: '=1+1', total: 1 },
      { name: '+SUM(A1)', total: 2 },
      { name: '@import', total: 3 },
      { name: `-2+3+cmd|'/C calc'!A0`, total: 4 },
    ])

    expect(rows).toEqual([
      `"'=1+1","1"`,
      `"'+SUM(A1)","2"`,
      `"'@import","3"`,
      `"'-2+3+cmd|'/C calc'!A0","4"`,
    ])
  })

  it('制表符开头的值同样前置单引号', () => {
    const [, ...rows] = csvRows([{ name: '\tcmd', total: 1 }])

    expect(rows).toEqual([`"'\tcmd","1"`])
  })

  it('负数不加单引号，数值列不会变成文本', () => {
    const [, ...rows] = csvRows([{ name: 'a', total: -5 }])

    expect(rows).toEqual([`"a","-5"`])
  })

  it('引号翻倍，空值输出空字段', () => {
    const [, ...rows] = csvRows([{ name: 'a"b', total: null }])

    expect(rows).toEqual([`"a""b",""`])
  })

  it('列标题也走同一套转义', () => {
    const [header] = csvRows([{ name: 'a', total: 1 }], [{ key: 'name', title: '=name' }])

    expect(header).toBe(`"'=name"`)
  })

  it('format 的返回值参与转义，而不是原始值', () => {
    const [, ...rows] = csvRows(
      [{ name: 'a', total: 1 }],
      [{ key: 'total', title: '状态', format: (value) => (value === 1 ? '=OK' : 'NG') }],
    )

    expect(rows).toEqual([`"'=OK"`])
  })
})

describe('parseCSVToTable', () => {
  it('单元格内容按文本转义，标签不会被当标记渲染', () => {
    const html = useExport().parseCSVToTable('name\n<img src=x onerror=alert(1)>')

    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;')
    expect(html).not.toContain('<img')
  })
})

describe('下载内容的 BOM', () => {
  const blobs: Blob[] = []
  const originalCreate = URL.createObjectURL
  const originalRevoke = URL.revokeObjectURL

  beforeEach(() => {
    blobs.length = 0
    URL.createObjectURL = vi.fn((obj: Blob | MediaSource) => {
      blobs.push(obj as Blob)
      return 'blob:mock'
    })
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  afterEach(() => {
    URL.createObjectURL = originalCreate
    URL.revokeObjectURL = originalRevoke
    vi.restoreAllMocks()
  })

  // BOM 是给 Excel 认 UTF-8 用的，JSON 带上它会让 JSON.parse 直接失败
  it('JSON 导出不带 BOM，CSV 仍带', async () => {
    const data = [{ name: 'a', total: 1 }]

    await useExport().exportToJSON(data, { showLoading: false })
    const json = await blobs[0]!.text()
    expect(json).toBe(JSON.stringify(data, null, 2))
    expect(() => JSON.parse(json)).not.toThrow()

    // 必须按字节看：Blob.text() 走 UTF-8 解码，标准行为会把开头的 BOM 当标记剥掉，用它永远验证不到
    await useExport().exportToCSV(data, { showLoading: false })
    const csvBytes = new Uint8Array(await blobs[1]!.arrayBuffer())
    expect([csvBytes[0], csvBytes[1], csvBytes[2]]).toEqual([0xef, 0xbb, 0xbf])
  })
})
