import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ChatComposer from '../ChatComposer.vue'
import type { ChatDraftAttachment } from '@/types/chat'

const ready: ChatDraftAttachment = { localId: 1, name: '花.png', previewUrl: 'blob:1', status: 'ready', attachmentId: 'a-1' }

describe('ChatComposer 附件', () => {
  it('菜单里选图后把文件交出去', async () => {
    const wrapper = mount(ChatComposer, { props: { modelValue: '' } })
    const file = new File(['x'], '花.png', { type: 'image/png' })

    await wrapper.get('.plus').trigger('click')
    await wrapper.get('[data-action="add-image"]').trigger('click')
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')

    expect(wrapper.emitted('attach')?.[0]).toEqual([[file]])
  })

  it('粘贴图片时交出文件，不当文字插入', async () => {
    const wrapper = mount(ChatComposer, { props: { modelValue: '' } })
    const file = new File(['x'], 'paste.png', { type: 'image/png' })

    await wrapper.get('.field').trigger('paste', {
      clipboardData: { files: [file], getData: () => '' },
    })

    expect(wrapper.emitted('attach')?.[0]).toEqual([[file]])
  })

  it('只有图没有字也能发，上传中不能发', async () => {
    const wrapper = mount(ChatComposer, { props: { modelValue: '', attachments: [ready] } })
    expect(wrapper.get('.send').attributes('disabled')).toBeUndefined()

    await wrapper.setProps({ attachments: [ready, { ...ready, localId: 2, status: 'uploading' }] })
    expect(wrapper.get('.send').attributes('disabled')).toBeDefined()
  })

  it('显示缩略图，点移除交出本地 ID', async () => {
    const wrapper = mount(ChatComposer, { props: { modelValue: '', attachments: [ready] } })

    expect(wrapper.get('.attachment img').attributes('src')).toBe('blob:1')
    await wrapper.get('.attachment-remove').trigger('click')

    expect(wrapper.emitted('remove-attachment')?.[0]).toEqual([1])
  })
})
