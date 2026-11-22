import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  clearLogs,
  getLogs,
  log,
  registerBinaryHandler,
  resolveWsUrl,
  setLogLevel,
  unregisterBinaryHandler,
} from '../websocket'

describe('日志', () => {
  beforeEach(() => {
    vi.spyOn(console, 'debug').mockImplementation(() => {})
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    vi.spyOn(console, 'log').mockImplementation(() => {})
    clearLogs()
  })

  afterEach(() => {
    setLogLevel('debug')
    clearLogs()
    vi.restoreAllMocks()
  })

  // 下行音频每帧都会打日志，级别不生效等于把 console 和内存日志刷爆
  it('低于当前级别的日志既不落内存也不打印', () => {
    setLogLevel('warning')

    log('每帧音频', 'debug')
    log('普通信息', 'info')
    expect(getLogs()).toHaveLength(0)
    expect(console.debug).not.toHaveBeenCalled()

    log('要留下的告警', 'warning')
    expect(getLogs()).toHaveLength(1)
  })

  it('内存日志固定只留最近 500 条', () => {
    setLogLevel('debug')

    for (let i = 0; i < 520; i++) {
      log(`m${i}`, 'debug')
    }

    const logs = getLogs()
    expect(logs).toHaveLength(500)
    expect(logs[0]?.message).toBe('m20')
    expect(logs[499]?.message).toBe('m519')
  })

  it('getLogs 返回副本，改不到内部数组', () => {
    setLogLevel('debug')
    log('a', 'debug')

    getLogs().length = 0
    expect(getLogs()).toHaveLength(1)
  })
})

describe('二进制处理函数注册', () => {
  beforeEach(() => {
    vi.spyOn(console, 'log').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('注册后能反注册，反注册别人的处理函数不生效', () => {
    const handler = vi.fn()
    const other = vi.fn()

    registerBinaryHandler(handler)
    expect(unregisterBinaryHandler(other)).toBe(false)
    expect(unregisterBinaryHandler(handler)).toBe(true)
    expect(unregisterBinaryHandler(handler)).toBe(false)
  })
})

/**
 * 地址每次按环境变量或当前页面推导、不持久化：原来存进 localStorage 后永远读回旧值，
 * 早期默认值曾是 API 进程地址，访问过旧版的浏览器会一直连错
 */
describe('resolveWsUrl', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('配了 VITE_WS_URL 就用它', () => {
    vi.stubEnv('VITE_WS_URL', 'ws://10.0.0.2:8092/ws/xiaozhi/v1')

    expect(resolveWsUrl({ protocol: 'https:', host: 'chat.example.com' })).toBe('ws://10.0.0.2:8092/ws/xiaozhi/v1')
  })

  it('没配时按当前页面推导，https 页面用 wss', () => {
    vi.stubEnv('VITE_WS_URL', '')

    expect(resolveWsUrl({ protocol: 'https:', host: 'chat.example.com' })).toBe('wss://chat.example.com/ws/xiaozhi/v1/')
    expect(resolveWsUrl({ protocol: 'http:', host: '192.168.1.5:8084' })).toBe('ws://192.168.1.5:8084/ws/xiaozhi/v1/')
  })

  it('不往浏览器存储里写', () => {
    vi.stubEnv('VITE_WS_URL', '')
    localStorage.clear()

    resolveWsUrl()

    expect(localStorage.length).toBe(0)
  })
})
