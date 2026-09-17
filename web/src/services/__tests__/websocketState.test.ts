import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * connectToServer 的成败判定与 stopAutoReconnect 的状态更新，都读 connectionStatus 这个字段。
 * 这组用例把「哪些状态算连接失败、哪些算重连中」钉死，改动状态机时不能悄悄漂移。
 */

interface CloseInfo {
  wasClean: boolean
  code: number
  reason: string
}

class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3

  readyState = FakeWebSocket.CONNECTING
  binaryType = 'blob'
  onopen: (() => void) | null = null
  onclose: ((event: CloseInfo) => void) | null = null
  onerror: (() => void) | null = null
  onmessage: ((event: unknown) => void) | null = null
  send = vi.fn()
  close = vi.fn(() => {
    this.readyState = FakeWebSocket.CLOSED
  })

  constructor(public url: string) {
    FakeWebSocket.instances.push(this)
  }

  static get last() {
    return FakeWebSocket.instances[FakeWebSocket.instances.length - 1]!
  }

  emitOpen() {
    this.readyState = FakeWebSocket.OPEN
    this.onopen?.()
  }

  emitError() {
    this.onerror?.()
  }

  emitClose(wasClean: boolean) {
    this.readyState = FakeWebSocket.CLOSED
    this.onclose?.({ wasClean, code: wasClean ? 1000 : 1006, reason: '' })
  }
}

const config = { url: 'ws://localhost:8092/ws', deviceId: 'dev-1' }

async function loadModule() {
  vi.resetModules()
  return import('../websocket')
}

beforeEach(() => {
  FakeWebSocket.instances = []
  vi.useFakeTimers()
  vi.stubGlobal('WebSocket', FakeWebSocket)
  vi.spyOn(console, 'log').mockImplementation(() => {})
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.spyOn(console, 'debug').mockImplementation(() => {})
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('connectToServer 的成败判定', () => {
  it('连接建立后返回 true 并置为已连接', async () => {
    const ws = await loadModule()
    const pending = ws.connectToServer(config)

    FakeWebSocket.last.emitOpen()
    await vi.advanceTimersByTimeAsync(200)

    await expect(pending).resolves.toBe(true)
    expect(ws.getConnectionStatus().isConnected).toBe(true)
  })

  // onerror 之后必须尽快判失败，否则调用方要一直干等到 5 秒超时
  it('连接出错时不等超时就返回 false', async () => {
    const ws = await loadModule()
    const pending = ws.connectToServer(config)

    FakeWebSocket.last.emitError()
    await vi.advanceTimersByTimeAsync(300)

    await expect(pending).resolves.toBe(false)
    expect(ws.getConnectionStatus().isConnected).toBe(false)
  })

  it('一直没有响应时按超时返回 false', async () => {
    const ws = await loadModule()
    const pending = ws.connectToServer(config)

    await vi.advanceTimersByTimeAsync(5200)

    await expect(pending).resolves.toBe(false)
  })

  // 意外断开会进入重连流程，这一路不能被当成「连接失败」立刻判死，
  // 否则重连还没开始调用方就收到 false 了
  it('意外断开进入重连排队，不会立刻判成失败', async () => {
    const ws = await loadModule()
    let outcome: boolean | 'pending' = 'pending'
    const pending = ws.connectToServer(config).then((result) => {
      outcome = result
      return result
    })

    FakeWebSocket.last.emitClose(false)
    await vi.advanceTimersByTimeAsync(300)

    // 断开后进入 2 秒重连倒计时。这期间不能已经收口——
    // 一旦提前判死，调用方还没等到重连就拿到 false 了
    expect(outcome).toBe('pending')
    expect(ws.getConnectionStatus().connectionStatus).toBe('reconnecting')
    // 倒计时秒数要一并交出去，视图层的「N 秒后重连」靠它插值
    expect(ws.getConnectionStatus().reconnectSeconds).toBe(2)

    await vi.advanceTimersByTimeAsync(5200)
    await expect(pending).resolves.toBe(false)
  })
})

describe('stopAutoReconnect 的状态更新', () => {
  it('正在重连时停掉会切到已停止重连', async () => {
    const ws = await loadModule()
    const pending = ws.connectToServer(config)
    FakeWebSocket.last.emitClose(false)
    await vi.advanceTimersByTimeAsync(50)

    // 断开后进入重连排队，此刻的状态属于「重连中」
    expect(ws.stopAutoReconnect()).toBe(true)
    expect(ws.getConnectionStatus().connectionStatus).toBe('reconnectStopped')

    await vi.advanceTimersByTimeAsync(5200)
    await pending
  })

  it('没有在重连时停掉不会改写当前状态', async () => {
    const ws = await loadModule()
    const pending = ws.connectToServer(config)
    FakeWebSocket.last.emitOpen()
    await vi.advanceTimersByTimeAsync(200)
    await pending

    const before = ws.getConnectionStatus().connectionStatus
    ws.stopAutoReconnect()

    expect(ws.getConnectionStatus().connectionStatus).toBe(before)
    expect(before).not.toBe('reconnectStopped')
  })
})

describe('状态码不掺文案', () => {
  // 状态既显示给用户也参与控制流判定。一旦有人把中文写回这个字段，
  // TERMINAL_FAILURE_KEYS / RECONNECT_KEYS 的集合判定就会静默失配
  it('连接过程中的状态取值始终是英文状态码', async () => {
    const ws = await loadModule()
    const seen = new Set<string>()
    ws.registerStatusChangeCallback((status) => {
      seen.add(status.connectionStatus)
    })

    const pending = ws.connectToServer(config)
    FakeWebSocket.last.emitClose(false)
    await vi.advanceTimersByTimeAsync(5200)
    await pending

    expect(seen.size).toBeGreaterThan(0)
    for (const key of seen) {
      expect(key).toMatch(/^[a-zA-Z]+$/)
    }
  })
})
