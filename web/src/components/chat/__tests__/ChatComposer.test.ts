import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ChatComposer from '../ChatComposer.vue'

describe('ChatComposer', () => {
  it('uses the AICSS menu structure and emits send for a ready prompt', async () => {
    const wrapper = mount(ChatComposer, {
      props: {
        modelValue: '帮我总结这段内容',
        roleName: '默认助手',
      },
    })

    await wrapper.get('.plus').trigger('click')

    expect(wrapper.find('.menu').exists()).toBe(true)
    expect(wrapper.findAll('.menu > .menu-item')).toHaveLength(2)
    expect(wrapper.text()).toContain('默认助手')

    await wrapper.get('.send').trigger('click')
    expect(wrapper.emitted('send')).toHaveLength(1)
  })

  it('keeps the AICSS action button shape while exposing stop during generation', async () => {
    const wrapper = mount(ChatComposer, {
      props: {
        modelValue: '',
        sending: true,
      },
    })

    const action = wrapper.get('.send')
    expect(action.attributes('aria-label')).toBe('chat.composer.stop')
    await action.trigger('click')
    expect(wrapper.emitted('stop')).toHaveLength(1)
  })
})
