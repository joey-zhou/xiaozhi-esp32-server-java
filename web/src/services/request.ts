import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import qs from 'qs'
import { REQUEST_TIMEOUT } from '@/constants/api'
import { useUserStore } from '@/store/user'
import { ROUTES } from '@/router/routes'
import { i18n } from '@/locales'
import type {
  ApiResponse,
  PageResponse,
  ListResponse,
  DataResponse,
  PageQueryParams,
  BaseQueryParams
} from '@/types/api'

export interface RequestError extends Error {
  code?: string
  isSilent?: boolean
  isAuthExpired?: boolean
  isRequestCanceled?: boolean
  isForbidden?: boolean
  /** 拦截器已经弹过一条提示，全局的兜底错误处理器不用再弹第二条 */
  isToasted?: boolean
}

/** 接口前缀，全站唯一来源；SSE / keepalive fetch 这类绕开 axios 的链路也从这里取 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || ''

// 创建 axios 实例
const request: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
})

// 全局请求取消控制器：路由切换时自动取消所有进行中的请求
let globalController = new AbortController()
let authExpiredHandling = false

/**
 * 取消所有进行中的请求（路由守卫自动调用）
 */
export function cancelPendingRequests() {
  globalController.abort()
  globalController = new AbortController()
}

function createRequestError(messageText: string, extras: Partial<RequestError> = {}): RequestError {
  const error = new Error(messageText) as RequestError
  Object.assign(error, extras)
  return error
}

function toRequestError(error: unknown, fallbackMessage: string): RequestError {
  if (error instanceof Error) {
    return error as RequestError
  }
  return createRequestError(fallbackMessage)
}

export function isRequestCanceledError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false
  }
  const requestError = error as RequestError
  return requestError.isRequestCanceled === true ||
    requestError.code === 'ERR_CANCELED' ||
    requestError.message.includes('canceled') ||
    requestError.message.includes('aborted')
}

export function isAuthExpiredError(error: unknown): boolean {
  return error instanceof Error && (error as RequestError).isAuthExpired === true
}

export function isForbiddenError(error: unknown): boolean {
  return error instanceof Error && (error as RequestError).isForbidden === true
}

export function shouldIgnoreRequestError(error: unknown): boolean {
  return isRequestCanceledError(error) || isAuthExpiredError(error) ||
    (error instanceof Error && (error as RequestError).isSilent === true)
}

/**
 * 清空登录态并跳登录页。
 * SSE 等绕开拦截器的链路（services/chat.ts）拿到 401 时也要调它，否则 token 失效后页面只会静默卡住
 */
export function handleAuthExpired(authMessage = i18n.global.t('error.sessionExpired')) {
  const userStore = useUserStore()
  userStore.clearUserInfo()
  userStore.clearToken()
  cancelPendingRequests()

  if (authExpiredHandling) {
    return
  }

  authExpiredHandling = true
  message.destroy('auth-error')
  message.error({
    content: authMessage,
    key: 'auth-error',
    duration: 2,
    onClose: () => {
      authExpiredHandling = false
      if (window.location.pathname !== ROUTES.LOGIN) {
        // redirect 参数的写法与 router/guards.ts 保持一致，登录成功后由登录页决定是否跳回
        const target = `${window.location.pathname}${window.location.search}`
        window.location.replace(`${ROUTES.LOGIN}?redirect=${encodeURIComponent(target)}`)
      }
    },
  })
}

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 添加 Token 到请求头
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }

    // 自动挂载全局取消信号（如果请求未自行指定 signal）
    if (!config.signal) {
      config.signal = globalController.signal
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    // 如果是 blob 类型的响应，直接返回，不做业务处理
    if (response.config.responseType === 'blob') {
      return response
    }

    const { data } = response

    // 处理业务错误码
    if (data.code === 401) {
      handleAuthExpired()
      return Promise.reject(
        createRequestError(data.message || i18n.global.t('error.unauthorized'), {
          code: 'ERR_AUTH_EXPIRED',
          isSilent: true,
          isAuthExpired: true,
        })
      )
    }

    if (data.code === 403) {
      // 权限不足要当场告诉用户，否则只会被调用方的兜底文案盖成「加载失败」
      const forbiddenMessage = data.message || i18n.global.t('error.forbidden')
      message.error({ content: forbiddenMessage, key: 'request-error' })
      return Promise.reject(
        createRequestError(forbiddenMessage, {
          code: 'ERR_FORBIDDEN',
          isForbidden: true,
          isToasted: true,
        })
      )
    }

    // 返回数据部分，而不是整个 response
    return data as unknown as AxiosResponse<ApiResponse>
  },
  (error) => {
    // 判断是否是请求取消错误（快速切换页面导致）
    if (error.code === 'ERR_CANCELED' || error.message?.includes('canceled') || error.message?.includes('aborted')) {
      // 请求被取消是正常行为，不显示错误提示
      console.debug('请求已取消:', error.config?.url)
      const requestError = toRequestError(error, '请求已取消')
      requestError.code = 'ERR_CANCELED'
      requestError.isSilent = true
      requestError.isRequestCanceled = true
      return Promise.reject(requestError)
    }

    if (error.code === 'ECONNABORTED' || error.message?.toLowerCase?.().includes('timeout')) {
      message.error({
        content: i18n.global.t('error.timeout'),
        key: 'timeout-error',
      })
      error.isToasted = true
      return Promise.reject(error)
    }

    // HTTP 错误处理
    if (error.response) {
      const { status } = error.response
      if (status === 401) {
        handleAuthExpired()
        const requestError = toRequestError(error, i18n.global.t('error.sessionExpired'))
        requestError.code = 'ERR_AUTH_EXPIRED'
        requestError.isSilent = true
        requestError.isAuthExpired = true
        return Promise.reject(requestError)
      } else if (status === 403) {
        const forbiddenMessage = error.response.data?.message || i18n.global.t('error.forbidden')
        message.error({ content: forbiddenMessage, key: 'request-error' })
        const requestError = toRequestError(error, forbiddenMessage)
        requestError.code = 'ERR_FORBIDDEN'
        requestError.isForbidden = true
        requestError.isToasted = true
        return Promise.reject(requestError)
      } else {
        message.error({
          content: error.response.data?.message || `${i18n.global.t('error.serverError')} (${status})`,
          key: 'request-error',
        })
        error.isToasted = true
      }
    } else if (error.request) {
      message.error({
        content: i18n.global.t('error.networkError'),
        key: 'network-error',
      })
      error.isToasted = true
    } else {
      // 其他错误（如请求配置错误等）
      message.error({
        content: error.message || i18n.global.t('error.unknown'),
        key: 'unknown-error',
      })
      error.isToasted = true
    }
    return Promise.reject(error)
  },
)

// 导出请求方法
export default request

/**
 * HTTP 请求便捷方法
 * 默认使用 JSON 格式提交数据，如需表单格式请使用 postForm
 * 路由切换时自动取消所有进行中的请求，无需手动处理
 */
export const http = {
  /**
   * GET 请求
   */
  get<T = unknown>(url: string, params?: Record<string, unknown>): Promise<DataResponse<T>> {
    return request.get(url, { params })
  },

  /**
   * POST 请求（JSON 格式）
   */
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<DataResponse<T>> {
    return request.post(url, data, config)
  },

  /**
   * POST 请求（multipart/form-data）
   */
  postMultipart<T = unknown>(url: string, data: FormData, config?: AxiosRequestConfig): Promise<DataResponse<T>> {
    return request.post(url, data, {
      ...config,
      headers: {
        ...config?.headers,
        'Content-Type': 'multipart/form-data',
      },
    })
  },

  /**
   * POST 请求（form-urlencoded 格式）
   */
  postForm<T = unknown>(url: string, data?: Record<string, unknown>): Promise<DataResponse<T>> {
    return request.post(url, qs.stringify(data), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
      },
    })
  },

  /**
   * PUT 请求（JSON 格式）
   */
  put<T = unknown>(url: string, data?: unknown): Promise<DataResponse<T>> {
    return request.put(url, data)
  },

  /**
   * PATCH 请求（JSON 格式）
   */
  patch<T = unknown>(url: string, data?: unknown): Promise<DataResponse<T>> {
    return request.patch(url, data)
  },

  /**
   * DELETE 请求（查询参数方式）
   */
  delete<T = unknown>(url: string, params?: Record<string, unknown>): Promise<DataResponse<T>> {
    return request.delete(url, { params })
  },

  /**
   * DELETE 请求（带 JSON 请求体）
   */
  deleteBody<T = unknown>(url: string, data?: Record<string, unknown> | unknown[]): Promise<DataResponse<T>> {
    return request.delete(url, { data })
  },

  /**
   * 分页查询（GET）
   */
  getPage<T = unknown>(
    url: string,
    params?: PageQueryParams
  ): Promise<PageResponse<T>> {
    return request.get(url, { params })
  },

  /**
   * 列表查询（GET，不带分页）
   */
  getList<T = unknown>(url: string, params?: BaseQueryParams): Promise<ListResponse<T>> {
    return request.get(url, { params })
  },
}
