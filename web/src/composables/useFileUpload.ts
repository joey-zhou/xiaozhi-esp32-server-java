import { computed, ref, shallowRef } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { UploadFile } from 'ant-design-vue'
import type { UploadRequestOption } from 'ant-design-vue/es/vc-upload/interface'
import type { FileValidator } from '@/utils/fileValidators'

/**
 * <a-upload> 适配器
 *
 * 只负责把「校验规则表 + 一个上传实现」拼成 a-upload 需要的三样东西：
 * accept 串、同步的 beforeUpload、customRequest。
 * 文件状态（uploading / done / error / percent）由 a-upload 自己维护在 fileList 上，这里不再重复一套
 */
export interface UseFileUploadOptions<T> {
  /**
   * 文件校验规则表，同时提供 accept 串
   */
  validator?: FileValidator

  /**
   * 覆盖 accept 串；不给就用校验器自带的那份
   */
  accept?: string

  /**
   * 校验通过后是否立即交给 customRequest 上传。
   * 关掉时 beforeUpload 返回 false，文件留在列表里等调用方手动提交
   */
  autoUpload?: boolean

  /**
   * 真正把文件送出去的实现，返回值原样交给 a-upload 的 onSuccess
   * @param file 要上传的文件
   * @param onProgress 进度回调，百分比整数
   */
  request?: (file: File, onProgress: (percent: number) => void) => Promise<T>
}

export function useFileUpload<T = unknown>(options: UseFileUploadOptions<T> = {}) {
  const { t } = useI18n()

  /** 与 <a-upload v-model:file-list> 双向绑定；a-upload 每次都整体换新数组，浅层响应即可 */
  const fileList = shallowRef<UploadFile<T>[]>([])

  /** 在途请求数：一个列表可能同时传多个文件，用计数而不是布尔量 */
  const pendingCount = ref(0)
  const uploading = computed(() => pendingCount.value > 0)

  const accept = computed(() => options.accept ?? options.validator?.accept ?? '')
  const hasFiles = computed(() => fileList.value.length > 0)

  /**
   * a-upload 的 beforeUpload：必须同步给出结论，异步会让 a-upload 先把文件塞进列表
   *
   * 返回 true 放行自动上传；返回 false 表示「不要自动上传」——
   * 校验失败和 autoUpload=false 都走这个返回值，它不是「校验失败」的专用信号
   */
  const beforeUpload = (file: File): boolean => {
    const result = options.validator?.validate(file)
    if (typeof result === 'string') {
      message.error(t(result))
      return false
    }

    return options.autoUpload !== false
  }

  /**
   * 执行一次上传，期间维持 uploading。拖拽这类不经过 a-upload 的入口也走这里
   */
  const runUpload = async (file: File, onProgress?: (percent: number) => void): Promise<T> => {
    if (!options.request) {
      throw new Error('useFileUpload: 未配置 request')
    }

    pendingCount.value += 1
    try {
      return await options.request(file, percent => onProgress?.(percent))
    } finally {
      pendingCount.value -= 1
    }
  }

  /**
   * a-upload 的 customRequest：异常吞进 onError，由上传列表展示失败态
   */
  const customRequest = async (option: UploadRequestOption<T>) => {
    const { file, onProgress, onSuccess, onError } = option

    // customRequest 的 file 声明上允许 Blob/string，这里只接真实文件
    if (!(file instanceof File)) {
      onError?.(new Error('unsupported file'))
      return
    }

    try {
      const response = await runUpload(file, percent => onProgress?.({ percent }))
      onSuccess?.(response)
    } catch (error) {
      const failure = error instanceof Error ? error : new Error(String(error))
      console.error('文件上传失败:', failure)
      onError?.(failure)
    }
  }

  const clearFiles = () => {
    fileList.value = []
  }

  const removeFile = (uid: string) => {
    fileList.value = fileList.value.filter(item => item.uid !== uid)
  }

  return {
    // 状态
    fileList,
    uploading,
    accept,
    hasFiles,

    // a-upload 接口
    beforeUpload,
    customRequest,

    // 方法
    runUpload,
    clearFiles,
    removeFile,
  }
}
