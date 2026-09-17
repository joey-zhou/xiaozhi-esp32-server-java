import { describe, expect, it } from 'vitest'
import { mapWithLimit } from '../concurrency'

/** 挂起的 Promise 与它的 resolve，用来手工控制每一项什么时候完成 */
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('mapWithLimit', () => {
  it('返回值按入参下标对齐，与完成顺序无关', async () => {
    const gates = [deferred<string>(), deferred<string>(), deferred<string>()]

    const running = mapWithLimit([0, 1, 2], 3, index => gates[index]!.promise)
    // 倒序完成，结果仍必须是入参顺序
    gates[2]!.resolve('c')
    gates[1]!.resolve('b')
    gates[0]!.resolve('a')

    await expect(running).resolves.toEqual(['a', 'b', 'c'])
  })

  it('同时在途的任务数不超过 limit', async () => {
    const gates = Array.from({ length: 5 }, () => deferred<number>())
    let inFlight = 0
    let peak = 0

    const running = mapWithLimit(gates, 2, async gate => {
      inFlight++
      peak = Math.max(peak, inFlight)
      const value = await gate.promise
      inFlight--
      return value
    })

    // 先让前两个占满额度，确认没有第三个被启动
    await Promise.resolve()
    expect(peak).toBe(2)

    gates.forEach((gate, index) => gate.resolve(index))
    await running
    expect(peak).toBe(2)
  })

  it('任一项抛错时整体拒绝', async () => {
    const boom = new Error('boom')

    await expect(
      mapWithLimit([1, 2, 3], 2, async value => {
        if (value === 2) throw boom
        return value
      }),
    ).rejects.toBe(boom)
  })

  it('空入参直接返回空数组，不启动任何 runner', async () => {
    let calls = 0

    await expect(
      mapWithLimit<number, number>([], 3, async value => {
        calls++
        return value
      }),
    ).resolves.toEqual([])
    expect(calls).toBe(0)
  })
})
