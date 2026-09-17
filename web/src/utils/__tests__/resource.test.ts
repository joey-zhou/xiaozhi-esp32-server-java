import { describe, it, expect, afterEach, vi } from 'vitest'
import { getResourceUrl } from '../resource'

describe('getResourceUrl', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('空值返回 undefined', () => {
    expect(getResourceUrl()).toBeUndefined()
    expect(getResourceUrl('')).toBeUndefined()
  })

  it('已是完整 URL 时原样返回', () => {
    vi.stubEnv('VITE_BACKEND_URL', 'http://backend:8091')
    expect(getResourceUrl('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png')
    expect(getResourceUrl('http://cdn.example.com/a.png')).toBe('http://cdn.example.com/a.png')
  })

  it('拼接后端地址，且不产生双斜杠', () => {
    vi.stubEnv('VITE_BACKEND_URL', 'http://backend:8091/')
    expect(getResourceUrl('/uploads/a.png')).toBe('http://backend:8091/uploads/a.png')
    expect(getResourceUrl('uploads/a.png')).toBe('http://backend:8091/uploads/a.png')
  })

  // 生产漏配 VITE_BACKEND_URL 时不能静默指向 localhost，走相对路径由反向代理承载
  it('未配置后端地址时返回相对路径', () => {
    vi.stubEnv('VITE_BACKEND_URL', '')
    expect(getResourceUrl('/uploads/a.png')).toBe('/uploads/a.png')
    expect(getResourceUrl('uploads/a.png')).toBe('/uploads/a.png')
  })
})
