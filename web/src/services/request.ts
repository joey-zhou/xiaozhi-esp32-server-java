import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import qs from 'qs'
import { useUserStore } from '@/store/user'
import { ROUTES } from '@/router/routes'
import type {
  ApiResponse,
  PageResponse,
  ListResponse,
  EmptyResponse,
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
}

// 创建 axios 实例
const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
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

function handleAuthExpired(authMessage = '登录过期，请重新登录！') {
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
        window.location.replace(ROUTES.LOGIN)
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
        createRequestError(data.message || '未授权', {
          code: 'ERR_AUTH_EXPIRED',
          isSilent: true,
          isAuthExpired: true,
        })
      )
    }

    if (data.code === 403) {
      return Promise.reject(
        createRequestError(data.message || '权限不足', {
          code: 'ERR_FORBIDDEN',
          isForbidden: true,
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
        content: '请求超时',
        key: 'timeout-error',
      })
      return Promise.reject(error)
    }

    // HTTP 错误处理
    if (error.response) {
      const { status } = error.response
      if (status === 401) {
        handleAuthExpired()
        const requestError = toRequestError(error, '登录过期，请重新登录！')
        requestError.code = 'ERR_AUTH_EXPIRED'
        requestError.isSilent = true
        requestError.isAuthExpired = true
        return Promise.reject(requestError)
      } else if (status === 403) {
        const requestError = toRequestError(error, error.response.data?.message || '权限不足')
        requestError.code = 'ERR_FORBIDDEN'
        requestError.isForbidden = true
        return Promise.reject(requestError)
      } else {
        message.error({
          content: error.response.data?.message || `请求失败 (${status})`,
          key: 'request-error',
        })
      }
    } else if (error.request) {
      message.error({
        content: '网络错误，请检查网络连接',
        key: 'network-error',
      })
    } else {
      // 其他错误（如请求配置错误等）
      message.error({
        content: error.message || '请求失败',
        key: 'unknown-error',
      })
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
