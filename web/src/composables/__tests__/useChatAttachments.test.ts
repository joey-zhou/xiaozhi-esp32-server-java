import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { message } from 'ant-design-vue'

const chatMock = vi.hoisted(() => ({ uploadChatAttachment: vi.fn() }))
const imageMock = vi.hoisted(() => ({ fitImage: vi.fn((file: File) => Promise.resolve(file)) }))

vi.mock('@/services/chat', () => chatMock)
vi.mock('@/utils/image', () => imageMock)

import { useChatAttachments } from '../useChatAttachments'
import { CHAT_ATTACHMENT_MAX_COUNT } from '@/constants/api'

type Attachments = ReturnType<typeof useChatAttachments>

function mountAttachments() {
  let attachments!: Attachments
  const wrapper = mount(
    defineComponent({
      setup() {
        attachments = useChatAttachments()
        return () => h('div')
      },
    })
  )
  return { attachments, wrapper }
}

function image(name = '花.png') {
  return new File(['x'], name, { type: 'image/png' })
}

describe('useChatAttachments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:1'), revokeObjectURL: vi.fn() })
    chatMock.uploadChatAttachment.mockResolvedValue({
      code: 200,
      message: '',
      data: { attachmentId: 'a-1', kind: 'image', name: '花.png' },
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('选好的图上传后可以发，发出去就从输入框移走', async () => {
    const { attachments } = mountAttachments()

    await attachments.add([image()])

    expect(attachments.attachments.value).toEqual([
      { localId: 1, name: '花.png', previewUrl: 'blob:1', status: 'ready', attachmentId: 'a-1' },
    ])
    expect(attachments.uploading.value).toBe(false)
    expect(attachments.takeReady().map((a) => a.attachmentId)).toEqual(['a-1'])
    expect(attachments.attachments.value).toEqual([])
  })

  it('上传失败的不留在输入框里，失败原因由执行器提示', async () => {
    chatMock.uploadChatAttachment.mockResolvedValue({ code: 400, message: '图片尺寸超过限制', data: null })
    const { attachments } = mountAttachments()

    await attachments.add([image()])

    expect(attachments.attachments.value).toEqual([])
    expect(message.error).toHaveBeenCalledWith('图片尺寸超过限制')
  })

  it('不是图片的文件不收', async () => {
    const { attachments } = mountAttachments()

    await attachments.add([new File(['x'], 'a.txt', { type: 'text/plain' })])

    expect(chatMock.uploadChatAttachment).not.toHaveBeenCalled()
    expect(attachments.attachments.value).toEqual([])
  })

  it('超过上限的部分不传并提示', async () => {
    const { attachments } = mountAttachments()
    const files = Array.from({ length: CHAT_ATTACHMENT_MAX_COUNT + 1 }, (_, i) => image(`${i}.png`))

    await attachments.add(files)

    expect(chatMock.uploadChatAttachment).toHaveBeenCalledTimes(CHAT_ATTACHMENT_MAX_COUNT)
    expect(message.warning).toHaveBeenCalled()
  })

  it('上传中移除的，传完也不再出现', async () => {
    let finish!: () => void
    chatMock.uploadChatAttachment.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = () => resolve({ code: 200, message: '', data: { attachmentId: 'a-1', kind: 'image', name: '花.png' } })
        })
    )
    const { attachments } = mountAttachments()

    const adding = attachments.add([image()])
    await vi.waitUntil(() => attachments.uploading.value)
    attachments.remove(1)
    finish()
    await adding

    expect(attachments.attachments.value).toEqual([])
  })

  it('页面卸载时释放本地预览地址', async () => {
    const { attachments, wrapper } = mountAttachments()
    await attachments.add([image()])
    attachments.takeReady()

    wrapper.unmount()

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1')
  })
})
