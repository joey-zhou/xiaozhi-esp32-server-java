import { http } from './request'
import api from './api'

/**
 * 文件上传服务
 */
export interface UploadResponse {
  code: number
  message: string
  url: string
}

/**
 * 上传文件
 * @param file 要上传的文件
 * @param type 文件类型，默认为 'avatar'
 * @returns Promise<string> 返回文件URL
 */
export function uploadFile(file: File, type: string = 'avatar'): Promise<string> {
  return new Promise((resolve, reject) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('type', type)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', api.upload, true)

    xhr.onload = function () {
      if (xhr.status === 200) {
        try {
          const response: UploadResponse = JSON.parse(xhr.responseText)
          if (response.code === 200) {
            resolve(response.url)
          } else {
            reject(new Error(response.message || '上传失败'))
          }
        } catch (error) {
          reject(new Error('响应解析失败'))
        }
      } else {
        reject(new Error('上传失败，状态码: ' + xhr.status))
      }
    }

    xhr.onerror = function () {
      reject(new Error('网络错误'))
    }

    xhr.send(formData)
  })
}

/**
 * 上传头像文件
 * @param file 头像文件
 * @returns Promise<string> 返回头像URL
 */
export function uploadAvatar(file: File): Promise<string> {
  return uploadFile(file, 'avatar')
}
