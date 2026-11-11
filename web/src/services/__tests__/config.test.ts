import { beforeEach, describe, expect, it, vi } from 'vitest'

const httpMock = vi.hoisted(() => ({
  http: {
    delete: vi.fn(() => Promise.resolve({ code: 200, message: '', data: null })),
  },
}))

vi.mock('../request', () => httpMock)

import { deleteConfig } from '../config'

describe('deleteConfig', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // http.delete 的第二参本身就是 query；再包一层 params 会发成 ?params[confirmStorageSwitch]=true，
  // 后端永远拿到 false，删当前生效的对象存储配置时确认框循环弹、永远删不掉
  it('确认换存储的标记作为 query 参数发出', () => {
    deleteConfig(5, true)

    expect(httpMock.http.delete).toHaveBeenCalledWith('/config/5', { confirmStorageSwitch: true })
  })
})
