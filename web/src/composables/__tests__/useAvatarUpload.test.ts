import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { message } from 'ant-design-vue'
import { useAvatarUpload } from '../useAvatarUpload'
import type { UploadProps } from 'ant-design-vue'

const uploadFileMock = vi.hoisted(() => vi.fn())
vi.mock('@/services/upload', () => ({ uploadFile: uploadFileMock }))

/** antd 的 beforeUpload 收的是带 uid/lastModifiedDate 的 RcFile，不是原生 File */
type UploadBeforeFile = Parameters<NonNullable<UploadProps['beforeUpload']>>[0]

/** jsdom 下无法造大文件，size 直接改写 */
function sizedFile(name: string, size = 1024, type = 'image/png'): UploadBeforeFile {
  const file = new File(['x'], name, { type }) as UploadBeforeFile
  Object.defineProperty(file, 'size', { value: size })
  file.uid = name
  Object.defineProperty(file, 'lastModifiedDate', { value: new Date(0) })
  return file
}

describe('useAvatarUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('accept 取自 fileValidators.image，两处不再各写一份扩展名清单', () => {
    const { avatarAccept } = useAvatarUpload({ onUploaded: vi.fn() })
    expect(avatarAccept).toContain('.png')
    expect(avatarAccept).toContain('.jpg')
  })

  it('非图片直接拒绝，不触发上传', () => {
    const onUploaded = vi.fn()
    const { beforeAvatarUpload, avatarLoading } = useAvatarUpload({ onUploaded })

    const result = beforeAvatarUpload(sizedFile('a.pdf', 1024, 'application/pdf'), [])

    expect(result).toBe(false)
    expect(message.error).toHaveBeenCalledWith('common.onlyImageFiles')
    expect(uploadFileMock).not.toHaveBeenCalled()
    expect(avatarLoading.value).toBe(false)
  })

  it('超过 2MB 直接拒绝，不触发上传', () => {
    const { beforeAvatarUpload } = useAvatarUpload({ onUploaded: vi.fn() })

    beforeAvatarUpload(sizedFile('a.png', 2 * 1024 * 1024, 'image/png'), [])

    expect(message.error).toHaveBeenCalledWith('common.imageSizeLimit')
    expect(uploadFileMock).not.toHaveBeenCalled()
  })

  it('校验通过后立即上传，期间 loading 为 true，成功后把可入库路径交给 onUploaded 并熄灭 loading', async () => {
    let resolveUpload!: (value: { code: number; message: string; url: string; relativePath: string }) => void
    uploadFileMock.mockImplementation(
      () => new Promise(resolve => { resolveUpload = resolve })
    )
    const onUploaded = vi.fn()
    const { beforeAvatarUpload, avatarLoading } = useAvatarUpload({ onUploaded })

    const result = beforeAvatarUpload(sizedFile('a.png'), [])

    // 返回 false 挡掉 a-upload 自己的自动上传，上传由这里手动接管
    expect(result).toBe(false)
    expect(avatarLoading.value).toBe(true)

    resolveUpload({ code: 200, message: '', url: '', relativePath: 'avatar/1.png' })
    await flushPromises()

    expect(onUploaded).toHaveBeenCalledWith('avatar/1.png')
    expect(avatarLoading.value).toBe(false)
  })

  it('本地存储无 relativePath 时退回签名 URL', async () => {
    uploadFileMock.mockResolvedValue({ code: 200, message: '', url: 'https://cdn/x.png' })
    const onUploaded = vi.fn()
    const { beforeAvatarUpload } = useAvatarUpload({ onUploaded })

    beforeAvatarUpload(sizedFile('a.png'), [])
    await flushPromises()

    expect(onUploaded).toHaveBeenCalledWith('https://cdn/x.png')
  })

  it('onUploaded 是异步的场景：loading 要等它跑完才熄灭', async () => {
    uploadFileMock.mockResolvedValue({ code: 200, message: '', url: '', relativePath: 'avatar/1.png' })
    let resolveOnUploaded!: () => void
    const onUploaded = vi.fn(() => new Promise<void>(resolve => { resolveOnUploaded = resolve }))
    const { beforeAvatarUpload, avatarLoading } = useAvatarUpload({ onUploaded })

    beforeAvatarUpload(sizedFile('a.png'), [])
    await flushPromises()
    // uploadFile 已经完成，但 onUploaded 还没跑完，遮罩不能先收起来
    expect(avatarLoading.value).toBe(true)

    resolveOnUploaded()
    await flushPromises()
    expect(avatarLoading.value).toBe(false)
  })

  it('上传失败弹错误提示并熄灭 loading，onUploaded 不会被调用', async () => {
    uploadFileMock.mockRejectedValue(new Error('network down'))
    const onUploaded = vi.fn()
    const { beforeAvatarUpload, avatarLoading } = useAvatarUpload({ onUploaded })

    beforeAvatarUpload(sizedFile('a.png'), [])
    await flushPromises()

    expect(onUploaded).not.toHaveBeenCalled()
    expect(avatarLoading.value).toBe(false)
    expect(message.error).toHaveBeenCalledWith(expect.stringContaining('common.avatarUploadFailed'))
  })
})
