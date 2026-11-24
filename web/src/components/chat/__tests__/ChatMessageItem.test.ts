import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ChatMessageItem from '../ChatMessageItem.vue'

describe('ChatMessageItem', () => {
  it('用户消息带的图显示在文字上方', () => {
    const wrapper = mount(ChatMessageItem, {
      props: {
        roleName: '小智',
        msg: {
          id: 1,
          role: 'user',
          content: '这是什么花',
          timestamp: new Date(),
          attachments: [{ kind: 'image', name: '花.png', url: 'blob:1' }],
        },
      },
    })

    const image = wrapper.get('.message-images a-image')
    expect(image.attributes('src')).toBe('blob:1')
    expect(image.attributes('alt')).toBe('花.png')
    expect(wrapper.text()).toContain('这是什么花')
  })
})
