import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ThinkingBlock from '../ThinkingBlock.vue'

describe('ThinkingBlock', () => {
  it('stays expanded while thinking and collapses into a duration summary when done', async () => {
    const wrapper = mount(ThinkingBlock, {
      props: {
        content: '正在检查上下文',
        done: false,
        expanded: false,
      },
    })

    expect(wrapper.find('.tr-collapsible').classes()).not.toContain('is-collapsed')
    expect(wrapper.get('.tr-header').attributes('aria-expanded')).toBe('true')

    await wrapper.setProps({ done: true, durationMs: 4200 })

    expect(wrapper.find('.tr-collapsible').classes()).toContain('is-collapsed')
    expect(wrapper.text()).toContain('chat.thought')
    expect(wrapper.text()).toContain('chat.thoughtDuration')

    await wrapper.get('.tr-header').trigger('click')
    expect(wrapper.emitted('toggle')).toHaveLength(1)
  })

  it('can be expanded again after thinking is complete', () => {
    const wrapper = mount(ThinkingBlock, {
      props: {
        content: '已完成的思考内容',
        done: true,
        expanded: true,
        durationMs: 1000,
      },
    })

    expect(wrapper.find('.tr-collapsible').classes()).not.toContain('is-collapsed')
    expect(wrapper.get('.tr-header').attributes('aria-expanded')).toBe('true')
  })
})
