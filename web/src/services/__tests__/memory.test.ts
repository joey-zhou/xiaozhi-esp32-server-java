import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  http: {
    getPage: vi.fn((_url?: string, _params?: Record<string, unknown>) =>
      Promise.resolve({ code: 200, message: '', data: { list: [], total: 0 } }),
    ),
  },
}))

vi.mock('../request', () => requestMock)

import api from '../api'
import { queryChatMemory } from '../memory'

describe('queryChatMemory 把短参数形状转换成消息分页查询', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('只查普通消息，并带上角色、设备与时间范围', async () => {
    await queryChatMemory({
      roleId: 7,
      deviceId: 'dev-1',
      pageNo: 2,
      pageSize: 20,
      startTime: '2026-01-01 00:00:00',
      endTime: '2026-01-02 00:00:00',
    })

    expect(requestMock.http.getPage).toHaveBeenCalledWith(api.message.root, {
      pageNo: 2,
      pageSize: 20,
      deviceId: 'dev-1',
      roleId: 7,
      messageType: 'NORMAL',
      startTime: '2026-01-01 00:00:00',
      endTime: '2026-01-02 00:00:00',
    })
  })

  it('不传 startTime/endTime 时不会带上这两个字段', async () => {
    await queryChatMemory({ roleId: 7, deviceId: 'dev-1' })

    const calledWith = requestMock.http.getPage.mock.calls[0]?.[1]
    expect(calledWith).not.toHaveProperty('startTime')
    expect(calledWith).not.toHaveProperty('endTime')
    expect(calledWith).toMatchObject({ pageNo: 1, pageSize: 10, roleId: 7, deviceId: 'dev-1' })
  })
})
