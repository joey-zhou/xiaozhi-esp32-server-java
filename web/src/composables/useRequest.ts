/**
 * 请求处理 Composable
 * 统一处理业务码判定、全局 Loading、错误提示与防抖
 */
import { computed, onScopeDispose, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { useLoadingStore } from '@/store/loading'
import { shouldIgnoreRequestError } from '@/services/request'
import type { ApiResponse } from '@/types/api'

export interface RequestOptions<T = unknown> {
  /** 是否显示全局 loading 遮罩 */
  showLoading?: boolean
  /** 全局 loading 遮罩文案，默认 t('common.loading') */
  loadingText?: string
  /** 失败时是否弹错误提示 */
  showError?: boolean
  /** 成功时是否弹成功提示 */
  showSuccess?: boolean
  /** 成功提示文案，默认 t('common.success') */
  successText?: string
  /** 错误提示文案：业务码失败时作为 res.message 的兜底，传输层错误时覆盖拦截器已弹出的那条 */
  errorText?: string
  /** 成功回调，入参是解包后的 res.data */
  onSuccess?: (data: T) => void
  /** 失败回调，入参是失败的响应体或抛出的错误 */
  onError?: (error: unknown) => void
}

/** 返回后端统一信封的请求函数 */
type ApiThunk<T> = () => Promise<ApiResponse<T>>

/** 把一组请求函数的元组映射成各自的数据类型，失败那一路是 undefined */
type ThunkData<T> = { [K in keyof T]: T[K] extends ApiThunk<infer R> ? R | undefined : never }

export interface RequestAllOptions {
  /** 是否显示全局 loading 遮罩 */
  showLoading?: boolean
  /** 全局 loading 遮罩文案，默认 t('common.loading') */
  loadingText?: string
  /** 有任意一路失败时是否弹提示，整批只弹一条 */
  showError?: boolean
  /** 失败提示文案 */
  errorText?: string
  /** 失败回调，入参是失败请求在入参数组里的下标 */
  onError?: (failedIndexes: number[]) => void
}

/**
 * 请求处理 Hook
 */
export function useRequest() {
  const { t } = useI18n()
  // 在途请求数，并发时任一请求未结束 loading 都为 true
  const pending = ref(0)
  const loading = computed(() => pending.value > 0)

  /**
   * 请求的唯一实现。成败与数据分开返回，因为写接口的成功响应体是 ApiResponse<Void>、
   * data 恒为 null，只看 data 无法区分「成功但没数据」和「失败」。
   */
  const run = async <T = unknown>(
    requestFn: () => Promise<ApiResponse<T>>,
    options: RequestOptions<T> = {}
  ): Promise<{ ok: boolean; data?: T }> => {
    const {
      showLoading = false,
      loadingText = t('common.loading'),
      showError = true,
      showSuccess = false,
      successText = t('common.success'),
      errorText,
      onSuccess,
      onError,
    } = options

    // 只在真的要遮罩时才取 store，避免无谓地绑定 pinia
    const loadingStore = showLoading ? useLoadingStore() : null
    pending.value += 1
    loadingStore?.showLoading(loadingText)

    try {
      const res = await requestFn()

      if (res.code !== 200) {
        if (showError) {
          message.error(res.message || errorText || t('common.operationFailed'))
        }
        onError?.(res)
        return { ok: false }
      }

      if (showSuccess) {
        message.success(successText)
      }

      onSuccess?.(res.data)
      return { ok: true, data: res.data }
    } catch (error: unknown) {
      if (shouldIgnoreRequestError(error)) {
        onError?.(error)
        return { ok: false }
      }

      console.error('Request error:', error)

      // 传输层错误由 request.ts 的拦截器统一弹提示，这里只在调用方给了文案时用同一个 key 覆盖，不叠第二条
      if (showError && errorText) {
        message.error({ content: errorText, key: 'request-error' })
      }

      onError?.(error)
      return { ok: false }
    } finally {
      pending.value -= 1
      loadingStore?.hideLoading()
    }
  }

  /**
   * 取数据用：成功返回解包后的 res.data，失败返回 undefined。
   * 仅适用于「成功一定带 data」的读接口；写接口请用 executeOk。
   */
  const execute = async <T = unknown>(
    requestFn: () => Promise<ApiResponse<T>>,
    options: RequestOptions<T> = {}
  ): Promise<T | undefined> => (await run(requestFn, options)).data

  /**
   * 判成败用：只回 res.code === 200 与否，与 data 是否为空无关。
   * 写接口（新增/修改/删除/状态切换）的成功响应体是 ApiResponse<Void>，
   * 用 execute 的返回值判会把成功误判成失败。
   */
  const executeOk = async <T = unknown>(
    requestFn: () => Promise<ApiResponse<T>>,
    options: RequestOptions<T> = {}
  ): Promise<boolean> => (await run(requestFn, options)).ok

  /**
   * 并行聚合：多路请求共用一次遮罩，失败合并成一条提示，返回值按入参顺序一一对应。
   * <p>
   * 不能用 Promise.all 包 execute 代替：包在外面丢掉「哪一路失败」，包在里面 N 路失败会弹 N 条提示。
   * 这里让每一路静默执行，失败下标收集起来由调用方决定怎么用。
   */
  const executeAll = async <T extends readonly ApiThunk<unknown>[]>(
    requestFns: T,
    options: RequestAllOptions = {}
  ): Promise<{ ok: boolean; data: ThunkData<T>; failed: number[] }> => {
    const {
      showLoading = false,
      loadingText = t('common.loading'),
      showError = true,
      errorText,
      onError,
    } = options

    const loadingStore = showLoading ? useLoadingStore() : null
    loadingStore?.showLoading(loadingText)

    try {
      const settled = await Promise.all(requestFns.map(fn => run(fn, { showError: false })))
      const failed = settled.flatMap((result, index) => (result.ok ? [] : [index]))

      if (failed.length > 0) {
        if (showError) {
          // 与拦截器共用一个 key，传输层失败时覆盖它那条而不是叠加
          message.error({ content: errorText ?? t('common.operationFailed'), key: 'request-error' })
        }
        onError?.(failed)
      }

      // 元组下标与类型的对应关系没法在值层面表达，这里的断言由 ThunkData 的映射类型保证
      return { ok: failed.length === 0, data: settled.map(r => r.data) as ThunkData<T>, failed }
    } finally {
      loadingStore?.hideLoading()
    }
  }

  /**
   * 创建防抖请求函数：连续调用只发最后一次，参数透传给 requestFn
   * 返回的函数带 cancel()，所在作用域销毁时也会自动取消
   */
  const createDebouncedRequest = <T = unknown, A extends unknown[] = []>(
    requestFn: (...args: A) => Promise<ApiResponse<T>>,
    delay = 500,
    options: RequestOptions<T> = {}
  ) => {
    let timer: ReturnType<typeof setTimeout> | null = null
    let disposed = false

    const cancel = () => {
      if (timer !== null) {
        clearTimeout(timer)
        timer = null
      }
    }

    // 结果只走 onSuccess / onError，不返回值，避免与「失败返回 undefined」混淆
    const run = (...args: A): void => {
      cancel()
      timer = setTimeout(() => {
        timer = null
        if (disposed) {
          return
        }
        void execute(() => requestFn(...args), options)
      }, delay)
    }

    onScopeDispose(() => {
      disposed = true
      cancel()
    }, true)

    return Object.assign(run, { cancel })
  }

  return {
    loading,
    execute,
    executeOk,
    executeAll,
    createDebouncedRequest,
  }
}

/**
 * 带全局遮罩执行任意异步操作，失败返回 undefined
 */
export async function runWithGlobalMask<T = unknown>(
  requestFn: () => Promise<T>,
  loadingText: string,
  errorText: string
): Promise<T | undefined> {
  const loadingStore = useLoadingStore()

  loadingStore.showLoading(loadingText)
  try {
    return await runSafely(requestFn, errorText)
  } finally {
    loadingStore.hideLoading()
  }
}

/**
 * 吞掉异常执行任意异步操作，失败弹 errorText 并返回 undefined
 */
export async function runSafely<T = unknown>(
  requestFn: () => Promise<T>,
  errorText: string
): Promise<T | undefined> {
  try {
    return await requestFn()
  } catch (error: unknown) {
    if (shouldIgnoreRequestError(error)) {
      return undefined
    }

    console.error('Request error:', error)
    // 拦截器已经用同一个 key 弹过传输层提示，这里覆盖而不是叠第二条
    message.error({ content: errorText, key: 'request-error' })
    return undefined
  }
}
