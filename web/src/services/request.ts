import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import qs from 'qs'
import { useUserStore } from '@/store/user'
import type { 
  ApiResponse, 
  PageResponse, 
  ListResponse, 
  EmptyResponse, 
  DataResponse,
  PageQueryParams,
  ListQueryParams
} from '@/types/api'

// 创建 axios 实例
const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
  },
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 1. 添加 Token 到请求头
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }

    // 2. 如果是 POST/PUT/PATCH 请求且数据存在，转换为 form-urlencoded 格式
    if (
      config.data &&
      (config.method === 'post' || config.method === 'put' || config.method === 'patch') &&
      config.headers['Content-Type'] === 'application/x-www-form-urlencoded;charset=UTF-8'
    ) {
      config.data = qs.stringify(config.data)
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
    const { data } = response

    // 处理业务错误码
    if (data.code === 403 || data.code === 401) {
      // 清空用户信息和token
      const userStore = useUserStore()
      userStore.clearUserInfo()
      userStore.clearToken()
      
      message.error({
        content: '登录过期，请重新登录！',
        key: 'auth-error',
        onClose: () => {
          window.location.href = '/login'
        },
      })
      return Promise.reject(new Error(data.message || '未授权'))
    }

    // 返回数据部分，而不是整个 response
    return data as unknown as AxiosResponse<ApiResponse>
  },
  (error) => {
    // 判断是否是请求取消错误（快速切换页面导致）
    if (error.code === 'ERR_CANCELED' || error.message?.includes('canceled') || error.message?.includes('aborted')) {
      // 请求被取消是正常行为，不显示错误提示
      console.debug('请求已取消:', error.config?.url)
      return Promise.reject(error)
    }

    // HTTP 错误处理
    if (error.response) {
      const { status } = error.response
      if (status === 401 || status === 403) {
        // 清空用户信息和token
        const userStore = useUserStore()
        userStore.clearUserInfo()
        userStore.clearToken()
        
        // 使用相同的key避免重复显示
        message.error({
          content: '登录过期，请重新登录！',
          key: 'auth-error',
          onClose: () => {
            window.location.href = '/login'
          },
        })
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
 */
export const http = {
  /**
   * GET 请求 - 通用数据响应
   */
  get<T = unknown>(url: string, params?: Record<string, unknown>): Promise<DataResponse<T>> {
    return request.get(url, { params })
  },

  /**
   * POST 请求（form-urlencoded）- 通用数据响应
   */
  post<T = unknown>(url: string, data?: Record<string, unknown>): Promise<DataResponse<T>> {
    return request.post(url, data)
  },

  /**
   * PUT 请求（form-urlencoded）- 通用数据响应
   */
  put<T = unknown>(url: string, data?: Record<string, unknown>): Promise<DataResponse<T>> {
    return request.put(url, data)
  },

  /**
   * DELETE 请求 - 通用数据响应
   */
  delete<T = unknown>(url: string, params?: Record<string, unknown>): Promise<DataResponse<T>> {
    return request.delete(url, { params })
  },

  /**
   * POST 请求（JSON 格式）- 通用数据响应
   */
  postJSON<T = unknown>(url: string, data?: Record<string, unknown>): Promise<DataResponse<T>> {
    return request.post(url, data, {
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
      },
    })
  },

  /**
   * PUT 请求（JSON 格式）- 通用数据响应
   */
  putJSON<T = unknown>(url: string, data?: Record<string, unknown>): Promise<DataResponse<T>> {
    return request.put(url, data, {
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
      },
    })
  },

  /**
   * 分页查询（GET）- 自动推断为分页响应
   */
  getPage<T = unknown>(
    url: string,
    params?: PageQueryParams
  ): Promise<PageResponse<T>> {
    return request.get(url, { params })
  },

  /**
   * 列表查询（GET，不带分页）- 自动推断为列表响应
   */
  getList<T = unknown>(url: string, params?: ListQueryParams): Promise<ListResponse<T>> {
    return request.get(url, { params })
  },
}
