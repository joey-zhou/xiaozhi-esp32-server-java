import { beforeEach, describe, expect, it, vi } from 'vitest'

const httpMock = vi.hoisted(() => ({
  http: {
    get: vi.fn(() => Promise.resolve({ code: 200, message: '', data: {} })),
    post: vi.fn(() => Promise.resolve({ code: 200, message: '', data: {} })),
  },
}))

vi.mock('../request', () => httpMock)

import { getDisabledTools, updateToolsStatus } from '../role'

describe('MCP 相关接口的 roleId 校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // roleId=0 是「取全局禁用列表」的合法取值，不能被当成空值拦掉
  it('roleId 为 0 时照常发请求', () => {
    getDisabledTools(0)
    expect(httpMock.http.get).toHaveBeenCalledWith('/mcpTool/role/0/disabled-tools')
  })

  it('roleId 合法时按真实路径请求', () => {
    updateToolsStatus(7, ['a'])
    expect(httpMock.http.post).toHaveBeenCalledWith('/mcpTool/role/7/exclude-tools', {
      roleId: 7,
      excludeTools: ['a'],
    })
  })

  // 以前非法 roleId 会被伪造成 code:200 的空结果，调用方看不出请求根本没发出去
  it('roleId 非法时抛错而不是伪造成功响应', () => {
    const invalid = Number.NaN

    expect(() => getDisabledTools(invalid)).toThrow(/roleId/)
    expect(() => updateToolsStatus(invalid, [])).toThrow(/roleId/)

    expect(httpMock.http.get).not.toHaveBeenCalled()
    expect(httpMock.http.post).not.toHaveBeenCalled()
  })
})
