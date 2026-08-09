import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import StreamingText from '../StreamingText.vue'

describe('StreamingText', () => {
  it('renders a caret only while real stream content is arriving', async () => {
    const wrapper = mount(StreamingText, {
      props: {
        content: '正在生成',
        streaming: true,
      },
    })

    expect(wrapper.text()).toContain('正在生成')
    expect(wrapper.find('.caret').exists()).toBe(true)
    expect(wrapper.find('.caret').classes()).toContain('caret-steady')

    await wrapper.setProps({ streaming: false })
    expect(wrapper.find('.caret').exists()).toBe(true)
    expect(wrapper.find('.caret').classes()).not.toContain('caret-steady')
  })
})
