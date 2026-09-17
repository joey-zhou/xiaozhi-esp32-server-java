import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { effectScope } from 'vue'
import { message } from 'ant-design-vue'
import type { ApiResponse } from '@/types/api'

// Mock loading store（避免依赖 pinia 实例）
const loadingMock = vi.hoisted(() => {
  const store = { showLoading: vi.fn(), hideLoading: vi.fn() }
  return { store, useLoadingStore: vi.fn(() => store) }
})
vi.mock('@/store/loading', () => ({ useLoadingStore: loadingMock.useLoadingStore }))

// 需要在 mock 之后引入
import { useRequest, runSafely, runWithGlobalMask } from '../useRequest'

// 后端统一响应信封
function ok<T>(data: T): ApiResponse<T> {
  return { code: 200, data, message: 'success' }
}

function fail<T = null>(text: string, code = 500): ApiResponse<T> {
  return { code, data: null as unknown as T, message: text }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useRequest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('execute - 业务码判定', () => {
    it('code=200 时解包返回 res.data 并触发 onSuccess', async () => {
      const { execute } = useRequest()
      const onSuccess = vi.fn()
      const requestFn = vi.fn().mockResolvedValue(ok({ id: 1, name: 'test' }))

      const result = await execute(requestFn, { onSuccess })

      expect(result).toEqual({ id: 1, name: 'test' })
      expect(onSuccess).toHaveBeenCalledWith({ id: 1, name: 'test' })
    })

    it('code!=200 时返回 undefined、弹 res.message、触发 onError', async () => {
      const { execute } = useRequest()
      const onSuccess = vi.fn()
      const onError = vi.fn()
      const response = fail('角色不存在')
      const requestFn = vi.fn().mockResolvedValue(response)

      const result = await execute(requestFn, { onSuccess, onError })

      expect(result).toBeUndefined()
      expect(onSuccess).not.toHaveBeenCalled()
      expect(message.error).toHaveBeenCalledWith('角色不存在')
      expect(onError).toHaveBeenCalledWith(response)
    })

    it('code!=200 且 showSuccess=true 时不弹成功提示', async () => {
      const { execute } = useRequest()
      const requestFn = vi.fn().mockResolvedValue(fail('保存失败'))

      await execute(requestFn, { showSuccess: true })

      expect(message.success).not.toHaveBeenCalled()
    })

    it('业务失败且 res.message 为空时用 errorText 兜底', async () => {
      const { execute } = useRequest()
      const requestFn = vi.fn().mockResolvedValue(fail(''))

      await execute(requestFn, { errorText: '删除设备失败' })

      expect(message.error).toHaveBeenCalledWith('删除设备失败')
    })

    it('业务失败且无任何文案时用 common.operationFailed 兜底', async () => {
      const { execute } = useRequest()
      const requestFn = vi.fn().mockResolvedValue(fail(''))

      await execute(requestFn)

      expect(message.error).toHaveBeenCalledWith('common.operationFailed')
    })

    it('showError=false 时业务失败不弹提示', async () => {
      const { execute } = useRequest()
      const requestFn = vi.fn().mockResolvedValue(fail('保存失败'))

      await execute(requestFn, { showError: false })

      expect(message.error).not.toHaveBeenCalled()
    })

    it('showSuccess=true 时弹成功提示，默认文案取 common.success', async () => {
      const { execute } = useRequest()
      const requestFn = vi.fn().mockResolvedValue(ok('ok'))

      await execute(requestFn, { showSuccess: true })

      expect(message.success).toHaveBeenCalledWith('common.success')
    })
  })

  describe('execute - 传输层错误', () => {
    it('抛异常时返回 undefined 并触发 onError', async () => {
      const { execute } = useRequest()
      const onError = vi.fn()
      const error = new Error('Request failed with status code 500')
      const requestFn = vi.fn().mockRejectedValue(error)

      const result = await execute(requestFn, { onError })

      expect(result).toBeUndefined()
      expect(onError).toHaveBeenCalledWith(error)
    })

    it('未给 errorText 时不弹提示（拦截器已弹过一条）', async () => {
      const { execute } = useRequest()
      const requestFn = vi.fn().mockRejectedValue(new Error('Request failed with status code 500'))

      await execute(requestFn)

      expect(message.error).not.toHaveBeenCalled()
    })

    it('给了 errorText 时复用 request-error 这个 key 覆盖拦截器的提示', async () => {
      const { execute } = useRequest()
      const requestFn = vi.fn().mockRejectedValue(new Error('Request failed with status code 500'))

      await execute(requestFn, { errorText: '保存配置失败' })

      expect(message.error).toHaveBeenCalledWith({ content: '保存配置失败', key: 'request-error' })
    })

    it('静默错误（请求取消）不弹提示但仍触发 onError', async () => {
      const { execute } = useRequest()
      const onError = vi.fn()
      const cancelError = Object.assign(new Error('canceled'), { code: 'ERR_CANCELED' })
      const requestFn = vi.fn().mockRejectedValue(cancelError)

      const result = await execute(requestFn, { errorText: '加载失败', onError })

      expect(result).toBeUndefined()
      expect(message.error).not.toHaveBeenCalled()
      expect(onError).toHaveBeenCalledWith(cancelError)
    })
  })

  describe('execute - loading', () => {
    it('请求过程中 loading 为 true，结束后归 false', async () => {
      const { execute, loading } = useRequest()
      const pending = deferred<ApiResponse<string>>()

      const task = execute(() => pending.promise)
      expect(loading.value).toBe(true)

      pending.resolve(ok('data'))
      await task
      expect(loading.value).toBe(false)
    })

    it('并发时先结束的请求不会提前把 loading 置 false', async () => {
      const { execute, loading } = useRequest()
      const first = deferred<ApiResponse<string>>()
      const second = deferred<ApiResponse<string>>()

      const firstTask = execute(() => first.promise)
      const secondTask = execute(() => second.promise)

      first.resolve(ok('first'))
      await firstTask
      expect(loading.value).toBe(true)

      second.resolve(ok('second'))
      await secondTask
      expect(loading.value).toBe(false)
    })

    it('业务失败与抛异常后 loading 都归 false', async () => {
      const { execute, loading } = useRequest()

      await execute(vi.fn().mockResolvedValue(fail('失败')))
      expect(loading.value).toBe(false)

      await execute(vi.fn().mockRejectedValue(new Error('boom')))
      expect(loading.value).toBe(false)
    })

    it('showLoading=true 时才显示全局遮罩', async () => {
      const { execute } = useRequest()
      const requestFn = vi.fn().mockResolvedValue(ok('ok'))

      await execute(requestFn, { showLoading: true, loadingText: '保存中...' })

      expect(loadingMock.store.showLoading).toHaveBeenCalledWith('保存中...')
      expect(loadingMock.store.hideLoading).toHaveBeenCalled()
    })

    it('showLoading=false 时不取 loading store', async () => {
      const { execute } = useRequest()
      const requestFn = vi.fn().mockResolvedValue(ok('ok'))

      await execute(requestFn)

      expect(loadingMock.useLoadingStore).not.toHaveBeenCalled()
      expect(loadingMock.store.showLoading).not.toHaveBeenCalled()
    })
  })

  describe('createDebouncedRequest', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('连续调用只发最后一次并透传参数', async () => {
      const { createDebouncedRequest } = useRequest()
      const requestFn = vi.fn((keyword: string) => Promise.resolve(ok([keyword])))
      const onSuccess = vi.fn()
      const search = createDebouncedRequest(requestFn, 300, { onSuccess })

      search('a')
      search('ab')
      search('abc')
      await vi.advanceTimersByTimeAsync(300)

      expect(requestFn).toHaveBeenCalledTimes(1)
      expect(requestFn).toHaveBeenCalledWith('abc')
      expect(onSuccess).toHaveBeenCalledWith(['abc'])
    })

    it('cancel() 后不再发请求', async () => {
      const { createDebouncedRequest } = useRequest()
      const requestFn = vi.fn().mockResolvedValue(ok('data'))
      const search = createDebouncedRequest(requestFn, 300)

      search()
      search.cancel()
      await vi.advanceTimersByTimeAsync(300)

      expect(requestFn).not.toHaveBeenCalled()
    })

    it('作用域销毁后不再发请求', async () => {
      const requestFn = vi.fn().mockResolvedValue(ok('data'))
      const scope = effectScope()
      let search!: () => void

      scope.run(() => {
        const { createDebouncedRequest } = useRequest()
        search = createDebouncedRequest(requestFn, 300)
      })

      search()
      scope.stop()
      await vi.advanceTimersByTimeAsync(300)

      expect(requestFn).not.toHaveBeenCalled()
    })
  })
})

describe('runSafely', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('成功时返回结果', async () => {
    const result = await runSafely(() => Promise.resolve('data'), '操作失败')

    expect(result).toBe('data')
    expect(message.error).not.toHaveBeenCalled()
  })

  it('失败时返回 undefined 并用调用方文案覆盖拦截器的提示', async () => {
    const result = await runSafely(
      () => Promise.reject(new Error('Request failed with status code 500')),
      '自定义错误消息',
    )

    expect(result).toBeUndefined()
    expect(message.error).toHaveBeenCalledWith({ content: '自定义错误消息', key: 'request-error' })
  })

  it('非 Error 抛出时同样用调用方文案', async () => {
    const result = await runSafely(() => Promise.reject(42), '自定义错误消息')

    expect(result).toBeUndefined()
    expect(message.error).toHaveBeenCalledWith({ content: '自定义错误消息', key: 'request-error' })
  })

  it('忽略请求取消错误', async () => {
    const cancelError = Object.assign(new Error('canceled'), { code: 'ERR_CANCELED' })
    const result = await runSafely(() => Promise.reject(cancelError), '自定义错误消息')

    expect(result).toBeUndefined()
    expect(message.error).not.toHaveBeenCalled()
  })
})

describe('runWithGlobalMask', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('成功时显示并隐藏遮罩', async () => {
    const result = await runWithGlobalMask(() => Promise.resolve('data'), '加载中...', '加载失败')

    expect(result).toBe('data')
    expect(loadingMock.store.showLoading).toHaveBeenCalledWith('加载中...')
    expect(loadingMock.store.hideLoading).toHaveBeenCalled()
  })

  it('失败时隐藏遮罩并返回 undefined', async () => {
    const result = await runWithGlobalMask(
      () => Promise.reject(new Error('boom')),
      '加载中...',
      '加载失败',
    )

    expect(result).toBeUndefined()
    expect(message.error).toHaveBeenCalledWith({ content: '加载失败', key: 'request-error' })
    expect(loadingMock.store.hideLoading).toHaveBeenCalled()
  })
})

describe('useRequest.executeOk', () => {
  // 本文件的 mock 清理写在各 describe 内部，新 describe 必须自带一份，否则会继承上面用例的调用计数
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('写接口成功但没有 data 时仍判成功', async () => {
    const scope = effectScope()
    // 后端写接口返回 ApiResponse<Void>。当前 Jackson 未配 NON_NULL，实际发的是 data: null；
    // 一旦有人开了 NON_NULL，data 字段会整个消失 —— 两种形态都必须判成成功
    const withNullData = { code: 200, data: null, message: '操作成功' } as unknown as ApiResponse<void>
    const withoutDataField = { code: 200, message: '操作成功' } as unknown as ApiResponse<void>

    await scope.run(async () => {
      const { execute, executeOk } = useRequest()

      expect(await executeOk(async () => withNullData)).toBe(true)
      expect(await executeOk(async () => withoutDataField)).toBe(true)

      // 对照：execute 的返回值区分不出「成功但没数据」和「失败」，
      // 省略 data 字段时它给的 undefined 与失败时完全一样，所以写接口不能用它判成败
      expect(await execute(async () => withNullData)).toBeNull()
      expect(await execute(async () => withoutDataField)).toBeUndefined()
      expect(await execute(async () => fail('boom'), { showError: false })).toBeUndefined()
    })

    scope.stop()
  })

  it('业务码非 200 时返回 false', async () => {
    const scope = effectScope()
    let succeeded: boolean | undefined

    await scope.run(async () => {
      const { executeOk } = useRequest()
      succeeded = await executeOk(async () => fail('重名'), { showError: false })
    })

    expect(succeeded).toBe(false)
    scope.stop()
  })

  it('抛异常时返回 false 且不吞掉 onError', async () => {
    const scope = effectScope()
    const onError = vi.fn()
    let succeeded: boolean | undefined

    await scope.run(async () => {
      const { executeOk } = useRequest()
      succeeded = await executeOk(
        async () => {
          throw new Error('boom')
        },
        { showError: false, onError },
      )
    })

    expect(succeeded).toBe(false)
    expect(onError).toHaveBeenCalledTimes(1)
    scope.stop()
  })
})

describe('useRequest.executeAll', () => {
  // 本文件的 mock 清理写在各 describe 内部，新 describe 必须自带一份，否则会继承上面用例的调用计数
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('三路里第 2 路失败：只弹一条提示，且能拿到是哪一路', async () => {
    const scope = effectScope()
    const onError = vi.fn()

    await scope.run(async () => {
      const { executeAll } = useRequest()
      const result = await executeAll(
        [
          async () => ok(1),
          async () => fail<number>('第二路挂了'),
          async () => ok('c'),
        ] as const,
        { errorText: '批量加载失败', onError },
      )

      expect(result.ok).toBe(false)
      expect(result.failed).toEqual([1])
      // 成功那两路的数据仍按入参顺序对齐，失败那一路是 undefined
      expect(result.data[0]).toBe(1)
      expect(result.data[1]).toBeUndefined()
      expect(result.data[2]).toBe('c')
    })

    // 三路里错一路，只能弹一条 —— 包在 Promise.all 里逐路弹会弹成灾
    expect(message.error).toHaveBeenCalledTimes(1)
    expect(message.error).toHaveBeenCalledWith({ content: '批量加载失败', key: 'request-error' })
    expect(onError).toHaveBeenCalledWith([1])
    scope.stop()
  })

  it('全部成功时 ok 为 true 且不弹提示', async () => {
    const scope = effectScope()

    await scope.run(async () => {
      const { executeAll } = useRequest()
      const result = await executeAll([async () => ok('a'), async () => ok(2)] as const)

      expect(result.ok).toBe(true)
      expect(result.failed).toEqual([])
      expect(result.data).toEqual(['a', 2])
    })

    expect(message.error).not.toHaveBeenCalled()
    scope.stop()
  })

  it('多路只占用一次全局遮罩', async () => {
    const scope = effectScope()

    await scope.run(async () => {
      const { executeAll } = useRequest()
      await executeAll([async () => ok(1), async () => ok(2), async () => ok(3)] as const, {
        showLoading: true,
      })
    })

    expect(loadingMock.store.showLoading).toHaveBeenCalledTimes(1)
    expect(loadingMock.store.hideLoading).toHaveBeenCalledTimes(1)
    scope.stop()
  })
})
