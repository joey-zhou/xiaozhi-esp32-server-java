import api from './api'
import { useUserStore } from '@/store/user'

export interface UploadResponse {
  code: number
  message: string
  url: string
  fileName?: string
  newFileName?: string
  fileHash?: string
  hash?: string
}

export interface UploadOptions {
  onProgress?: (percent: number) => void
  fullResponse?: boolean
}

/**
 * 通用文件上传方法
 * @param file 要上传的文件
 * @param type 文件类型: avatar, firmware 等
 * @param options 上传配置选项
 * @returns 默认返回URL，fullResponse=true时返回完整响应
 */
export function uploadFile(
  file: File,
  type: string = 'avatar',
  options?: UploadOptions
): Promise<string | UploadResponse> {
  return new Promise((resolve, reject) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('type', type)

    const xhr = new XMLHttpRequest()
    // 使用完整的 API URL，避免相对路径解析到前端域名
    const baseURL = import.meta.env.VITE_API_BASE_URL || ''
    const uploadUrl = `${baseURL}${api.upload}`
    xhr.open('POST', uploadUrl, true)

    // 添加认证 token（使用 Bearer 格式）
    const userStore = useUserStore()
    if (userStore.token) {
      xhr.setRequestHeader('Authorization', `Bearer ${userStore.token}`)
    }

    if (options?.onProgress) {
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          const percent = Math.round((event.loaded / event.total) * 100)
          options.onProgress!(percent)
        }
      }
    }

    xhr.onload = function () {
      if (xhr.status === 200) {
        try {
          const response: UploadResponse = JSON.parse(xhr.responseText)
          if (response.code === 200) {
            resolve(options?.fullResponse ? response : response.url)
          } else {
            reject(new Error(response.message || '上传失败'))
          }
        } catch (error) {
          reject(new Error('响应解析失败'))
        }
      } else {
        reject(new Error(`上传失败，状态码: ${xhr.status}`))
      }
    }

    xhr.onerror = function () {
      reject(new Error('网络错误'))
    }

    xhr.send(formData)
  })
}
