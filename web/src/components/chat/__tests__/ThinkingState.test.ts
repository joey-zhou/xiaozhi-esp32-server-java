import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ThinkingState from '../ThinkingState.vue'

describe('ThinkingState', () => {
  it('renders the AICSS shimmer state before any model token arrives', () => {
    const wrapper = mount(ThinkingState)

    expect(wrapper.get('.shimmer').text()).toBe('chat.thinkingInProgress')
  })
})
