import type { AxiosProgressEvent } from 'axios'
import { http } from './request'
import api from './api'
import { i18n } from '@/locales'

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

export interface UploadOptions {
  onProgress?: (percent: number) => void
  fullResponse?: boolean
}

// axios 实例默认 30s 超时，固件这类大文件会被中途打断，上传单独放宽到 5 分钟
const UPLOAD_TIMEOUT = 300000

/**
 * 通用文件上传方法
 * @param file 要上传的文件
 * @param type 文件类型: avatar 等
 * @param options 上传配置选项
 * @returns 默认返回可入库的路径，fullResponse=true 时返回完整响应
 */
export function uploadFile(
  file: File,
  type: string,
  options: UploadOptions & { fullResponse: true }
): Promise<UploadResponse>
export function uploadFile(
  file: File,
  type?: string,
  options?: UploadOptions & { fullResponse?: false }
): Promise<string>
export async function uploadFile(
  file: File,
  type: string = 'avatar',
  options?: UploadOptions
): Promise<string | UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', type)

  const raw = await http.postMultipart<UploadData>(api.upload, formData, {
    timeout: UPLOAD_TIMEOUT,
    onUploadProgress: (event: AxiosProgressEvent) => {
      if (!options?.onProgress || !event.total) {
        return
      }
      options.onProgress(Math.round((event.loaded * 100) / event.total))
    },
  })

  if (raw.code !== 200 || !raw.data) {
    throw new Error(raw.message || i18n.global.t('upload.uploadFailed'))
  }

  // 将 data 展开到顶层，保持 UploadResponse 的形状
  const response: UploadResponse = {
    code: raw.code,
    message: raw.message,
    ...raw.data,
  }

  // 默认返回可入库的路径：本地存储给相对路径（避免把主机名写死进库），
  // 云存储无 relativePath，返回签名 URL，后端入库时剥签名、下发时重签
  return options?.fullResponse ? response : (response.relativePath || response.url)
}
