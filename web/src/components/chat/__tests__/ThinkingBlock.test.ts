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

  it('思考中复用 ThinkingState 的 shimmer，展开阅读时放开固定行高', async () => {
    const wrapper = mount(ThinkingBlock, {
      props: {
        content: '这是一句很长的思考内容'.repeat(20),
        done: false,
        expanded: false,
      },
    })

    expect(wrapper.find('.shimmer').text()).toBe('chat.thinkingInProgress')
    // 流式滚动时按每句固定高度算版面
    expect(wrapper.get('.tr-viewport').attributes('style')).toContain('height: 40px')

    await wrapper.setProps({ done: true, expanded: true, durationMs: 1000 })

    const viewport = wrapper.get('.tr-viewport')
    expect(viewport.classes()).toContain('is-scroll')
    // 展开后只限制最大高度，长句不再被钉死成两行
    expect(viewport.attributes('style')).toContain('max-height: 180px')
    expect(viewport.attributes('style')).not.toContain('height: 40px')
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
