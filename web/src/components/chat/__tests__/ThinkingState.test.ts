import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ThinkingState from '../ThinkingState.vue'

describe('ThinkingState', () => {
  it('renders the AICSS shimmer state before any model token arrives', () => {
    const wrapper = mount(ThinkingState)

    expect(wrapper.get('.shimmer').text()).toBe('chat.thinkingInProgress')
  })

  // 在看图时换成对应的说法，动效不变
  it('says what the reply is waiting on', () => {
    expect(mount(ThinkingState, { props: { activity: 'vision' } }).get('.shimmer').text())
      .toBe('chat.activityVision')
  })
})
