import { describe, it, expect, vi, beforeEach } from 'vitest'
import { message } from 'ant-design-vue'
import type { UploadRequestOption } from 'ant-design-vue/es/vc-upload/interface'
import type { FileValidator } from '@/utils/fileValidators'
import { useFileUpload } from '../useFileUpload'

interface StubResponse {
  url: string
}

/** jsdom 下无法造大文件，size 直接覆写 */
function sizedFile(name: string, size = 1024): File {
  const file = new File(['x'], name)
  Object.defineProperty(file, 'size', { value: size })
  return file
}

/** 只按扩展名判定的最小校验器，避免测试依赖真实规则表的阈值 */
function stubValidator(allowed: string, errorKey = 'stub.invalid'): FileValidator {
  return {
    accept: allowed,
    validate: (file: File) => (file.name.endsWith(allowed) ? true : errorKey),
  }
}

function requestOption(
  file: File | Blob,
  handlers: Partial<Pick<UploadRequestOption<StubResponse>, 'onSuccess' | 'onError' | 'onProgress'>>
): UploadRequestOption<StubResponse> {
  return { file, action: '/upload', method: 'POST', ...handlers }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useFileUpload accept', () => {
  it('默认取校验器自带的 accept 串', () => {
    const { accept } = useFileUpload({ validator: stubValidator('.bin') })
    expect(accept.value).toBe('.bin')
  })

  it('显式 accept 覆盖校验器的那份', () => {
    const { accept } = useFileUpload({ validator: stubValidator('.bin'), accept: '.hex' })
    expect(accept.value).toBe('.hex')
  })

  it('两者都没有时给空串，a-upload 不做过滤', () => {
    const { accept } = useFileUpload()
    expect(accept.value).toBe('')
  })
})

describe('useFileUpload beforeUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('没有校验器时一律放行', () => {
    const { beforeUpload } = useFileUpload()
    expect(beforeUpload(sizedFile('a.zip'))).toBe(true)
    expect(message.error).not.toHaveBeenCalled()
  })

  it('校验通过返回 true，交给 customRequest 自动上传', () => {
    const { beforeUpload } = useFileUpload({ validator: stubValidator('.bin') })
    expect(beforeUpload(sizedFile('fw.bin'))).toBe(true)
    expect(message.error).not.toHaveBeenCalled()
  })

  it('校验失败弹出校验器返回的 i18n key', () => {
    const { beforeUpload } = useFileUpload({
      validator: stubValidator('.wav', 'common.audioFormatError'),
    })
    expect(beforeUpload(sizedFile('fw.zip'))).toBe(false)
    expect(message.error).toHaveBeenCalledWith('common.audioFormatError')
  })

  // false 是「别自动上传」，不是「校验没过」，两条路径都会返回它
  it('autoUpload=false 时校验通过也返回 false 且不报错', () => {
    const { beforeUpload } = useFileUpload({
      validator: stubValidator('.bin'),
      autoUpload: false,
    })
    expect(beforeUpload(sizedFile('fw.bin'))).toBe(false)
    expect(message.error).not.toHaveBeenCalled()
  })

  it('同步返回结论，异步会让 a-upload 先把文件塞进列表', () => {
    const { beforeUpload } = useFileUpload({ validator: stubValidator('.bin') })
    expect(beforeUpload(sizedFile('fw.bin'))).not.toBeInstanceOf(Promise)
  })
})

describe('useFileUpload customRequest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('成功时把 request 的返回值原样交给 onSuccess', async () => {
    const response: StubResponse = { url: '/uploads/fw.bin' }
    const { customRequest } = useFileUpload<StubResponse>({
      request: () => Promise.resolve(response),
    })

    const onSuccess = vi.fn()
    await customRequest(requestOption(sizedFile('fw.bin'), { onSuccess }))

    expect(onSuccess).toHaveBeenCalledWith(response)
  })

  // 进度条是 a-upload 唯一的进度来源，百分比必须包成 { percent } 才认
  it('进度回调包装成 a-upload 的 { percent } 形状', async () => {
    const { customRequest } = useFileUpload<StubResponse>({
      request: async (_file, onProgress) => {
        onProgress(42)
        return { url: '/uploads/fw.bin' }
      },
    })

    const onProgress = vi.fn()
    await customRequest(requestOption(sizedFile('fw.bin'), { onProgress }))

    expect(onProgress).toHaveBeenCalledWith({ percent: 42 })
  })

  it('失败时走 onError 而不是抛出去', async () => {
    const { customRequest } = useFileUpload<StubResponse>({
      request: () => Promise.reject(new Error('boom')),
    })

    const onSuccess = vi.fn()
    const onError = vi.fn()
    await customRequest(requestOption(sizedFile('fw.bin'), { onSuccess, onError }))

    expect(onSuccess).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith(expect.any(Error))
  })

  it('拿到 Blob 这类非真实文件时直接报错', async () => {
    const request = vi.fn()
    const { customRequest } = useFileUpload<StubResponse>({ request })

    const onError = vi.fn()
    await customRequest(requestOption(new Blob(['x']), { onError }))

    expect(request).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith(expect.any(Error))
  })
})

describe('useFileUpload uploading', () => {
  it('上传期间为 true，结束后回落', async () => {
    const gate = deferred<StubResponse>()
    const { customRequest, uploading } = useFileUpload<StubResponse>({
      request: () => gate.promise,
    })

    const pending = customRequest(requestOption(sizedFile('fw.bin'), {}))
    expect(uploading.value).toBe(true)

    gate.resolve({ url: '/uploads/fw.bin' })
    await pending
    expect(uploading.value).toBe(false)
  })

  // 多文件同时在传，先回来的那个不能把状态提前关掉
  it('并发上传时只有全部结束才回落', async () => {
    const first = deferred<StubResponse>()
    const second = deferred<StubResponse>()
    const gates = [first, second]
    let index = 0
    const { customRequest, uploading } = useFileUpload<StubResponse>({
      request: () => gates[index++]!.promise,
    })

    const pendingFirst = customRequest(requestOption(sizedFile('a.bin'), {}))
    const pendingSecond = customRequest(requestOption(sizedFile('b.bin'), {}))

    first.resolve({ url: '/uploads/a.bin' })
    await pendingFirst
    expect(uploading.value).toBe(true)

    second.reject(new Error('boom'))
    await pendingSecond
    expect(uploading.value).toBe(false)
  })

  it('未配置 request 时 runUpload 抛错而不是静默成功', async () => {
    const { runUpload } = useFileUpload()
    await expect(runUpload(sizedFile('fw.bin'))).rejects.toThrow(/request/)
  })
})

describe('useFileUpload fileList', () => {
  it('默认空列表，clearFiles / removeFile 按 uid 收拾', () => {
    const { fileList, hasFiles, clearFiles, removeFile } = useFileUpload<StubResponse>()

    expect(fileList.value).toEqual([])
    expect(hasFiles.value).toBe(false)

    fileList.value = [
      { uid: 'a', name: 'a.bin', status: 'done' },
      { uid: 'b', name: 'b.bin', status: 'done' },
    ]
    expect(hasFiles.value).toBe(true)

    removeFile('a')
    expect(fileList.value.map(item => item.uid)).toEqual(['b'])

    clearFiles()
    expect(fileList.value).toEqual([])
    expect(hasFiles.value).toBe(false)
  })
})
