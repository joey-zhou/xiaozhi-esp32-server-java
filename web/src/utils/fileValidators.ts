/**
 * 文件上传校验规则表
 *
 * 校验器只做判定，不弹提示：合法返回 true，非法返回 i18n key，由调用方翻译后展示。
 * accept 与 validate 共用同一份扩展名清单，避免选择器放行的格式在校验这一步被拦
 */
import {
  ALLOWED_AUDIO_TYPES,
  ALLOWED_IMAGE_TYPES,
  MAX_AUDIO_SIZE,
  MAX_IMAGE_SIZE
} from '@/constants/api'

/**
 * 文件验证器
 */
export interface FileValidator {
  /**
   * 传给 <a-upload> / input[type=file] 的 accept 串，只影响选择器过滤
   */
  accept: string

  /**
   * 验证文件是否合法
   * @param file 要验证的文件
   * @returns 合法返回 true，否则返回错误消息的 i18n key
   */
  validate: (file: File) => true | string
}

/** 音频扩展名白名单，与后端 FileUploadController 的允许列表一致 */
const AUDIO_EXTENSIONS = ['.wav', '.mp3', '.m4a', '.flac', '.ogg', '.opus', '.aac']

/** 图片扩展名白名单，与 ALLOWED_IMAGE_TYPES 的 MIME 列表一一对应 */
const IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp']

/** 扩展名判定统一走小写比对，浏览器给的文件名大小写不固定 */
function hasExtension(file: File, extensions: string[]): boolean {
  const name = file.name.toLowerCase()
  return extensions.some(ext => name.endsWith(ext))
}

/**
 * 预定义的文件验证器
 */
export const fileValidators = {
  /**
   * 音频文件验证器（10MB 限制）
   */
  audio: {
    accept: AUDIO_EXTENSIONS.join(','),

    validate: (file: File) => {
      // 浏览器对部分音频（如 .m4a）不给 type 或给的不是标准 MIME，回退按扩展名判定
      const isAudio = ALLOWED_AUDIO_TYPES.includes(file.type) || hasExtension(file, AUDIO_EXTENSIONS)

      if (!isAudio) {
        return 'common.audioFormatError'
      }

      if (file.size >= MAX_AUDIO_SIZE) {
        return 'common.audioSizeError'
      }

      return true
    }
  } as FileValidator,

  /**
   * 图片文件验证器（2MB 限制）
   */
  image: {
    accept: IMAGE_EXTENSIONS.join(','),

    validate: (file: File) => {
      // 浏览器对部分来源（如剪贴板粘贴）不给标准 MIME，回退按扩展名判定
      const isImage = ALLOWED_IMAGE_TYPES.includes(file.type) || hasExtension(file, IMAGE_EXTENSIONS)

      if (!isImage) {
        return 'common.onlyImageFiles'
      }

      if (file.size >= MAX_IMAGE_SIZE) {
        return 'common.imageSizeLimit'
      }

      return true
    }
  } as FileValidator
}
