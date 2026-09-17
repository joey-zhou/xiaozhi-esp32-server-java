import { beforeEach, describe, expect, it, vi } from 'vitest'

const httpMock = vi.hoisted(() => ({
  http: {
    postMultipart: vi.fn(),
  },
}))

vi.mock('../request', () => httpMock)

// 全局 setup 把 vue-i18n 的 createI18n 也 mock 了，真实 locales 模块在测试里无法求值
vi.mock('@/locales', () => ({
  i18n: { global: { t: (key: string) => key } },
}))

import { uploadFile, type UploadResponse } from '../upload'

function okResponse(data: Record<string, string>) {
  return { code: 200, message: '上传成功', data }
}

describe('uploadFile', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('走统一 http 封装并显式放宽超时，避免大文件被 30s 默认值打断', async () => {
    httpMock.http.postMultipart.mockResolvedValue(okResponse({ url: 'http://x/a.png' }))

    await uploadFile(new File([''], 'a.png'), 'avatar')

    const [url, formData, config] = httpMock.http.postMultipart.mock.calls[0]!
    expect(url).toBe('/file/upload')
    expect((formData as FormData).get('type')).toBe('avatar')
    expect((config as { timeout: number }).timeout).toBe(300000)
  })

  // 本地存储给 relativePath（避免把主机名写死进库），云存储只有签名 URL
  it('默认返回 relativePath，缺失时退回 url', async () => {
    httpMock.http.postMultipart.mockResolvedValue(
      okResponse({ url: 'http://x/a.png', relativePath: 'uploads/a.png' })
    )
    await expect(uploadFile(new File([''], 'a.png'))).resolves.toBe('uploads/a.png')

    httpMock.http.postMultipart.mockResolvedValue(okResponse({ url: 'http://x/a.png' }))
    await expect(uploadFile(new File([''], 'a.png'))).resolves.toBe('http://x/a.png')
  })

  // 重载让 fullResponse: true 直接推成 UploadResponse，调用方不需要再断言
  it('fullResponse 把 data 展开到顶层', async () => {
    httpMock.http.postMultipart.mockResolvedValue(
      okResponse({ url: 'http://x/a.png', relativePath: 'uploads/a.png' })
    )

    const res: UploadResponse = await uploadFile(new File([''], 'a.png'), 'avatar', {
      fullResponse: true,
    })

    expect(res.code).toBe(200)
    expect(res.message).toBe('上传成功')
    expect(res.url).toBe('http://x/a.png')
    expect(res.relativePath).toBe('uploads/a.png')
  })

  it('业务码非 200 时抛出后端文案', async () => {
    httpMock.http.postMultipart.mockResolvedValue({ code: 500, message: '文件过大', data: null })

    await expect(uploadFile(new File([''], 'a.png'))).rejects.toThrow('文件过大')
  })

  it('后端没给 message 时抛 i18n 兜底文案', async () => {
    httpMock.http.postMultipart.mockResolvedValue({ code: 500, message: '', data: null })

    await expect(uploadFile(new File([''], 'a.png'))).rejects.toThrow('upload.uploadFailed')
  })

  it('上传进度换算成百分比，total 缺失时不回调', async () => {
    httpMock.http.postMultipart.mockResolvedValue(okResponse({ url: 'http://x/a.png' }))
    const onProgress = vi.fn()

    await uploadFile(new File([''], 'a.png'), 'avatar', { onProgress })

    const config = httpMock.http.postMultipart.mock.calls[0]![2] as {
      onUploadProgress: (e: { loaded: number; total?: number }) => void
    }
    config.onUploadProgress({ loaded: 50, total: 200 })
    config.onUploadProgress({ loaded: 50 })

    expect(onProgress).toHaveBeenCalledTimes(1)
    expect(onProgress).toHaveBeenCalledWith(25)
  })
})
