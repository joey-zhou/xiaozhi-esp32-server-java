import api from './api'
import { useUserStore } from '@/store/user'

export interface UploadData {
  url: string
  fileName?: string
  newFileName?: string
  relativePath?: string
  fileHash?: string
  hash?: string
}

export interface UploadResponse extends UploadData {
  code: number
  message: string
}

// 后端实际返回结构：{ code, message, data: { url, ... } }
interface UploadRawResponse {
  code: number
  message: string
  data?: UploadData
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
          const raw: UploadRawResponse = JSON.parse(xhr.responseText)
          if (raw.code === 200 && raw.data) {
            // 将 data 展开到顶层，保持 UploadResponse 的向后兼容
            const response: UploadResponse = {
              code: raw.code,
              message: raw.message,
              ...raw.data
            }
            resolve(options?.fullResponse ? response : response.url)
          } else {
            reject(new Error(raw.message || '上传失败'))
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
