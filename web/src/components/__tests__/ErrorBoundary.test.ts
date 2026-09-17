import { defineComponent, h, nextTick, onMounted } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const { push } = vi.hoisted(() => ({ push: vi.fn(() => Promise.resolve()) }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

import ErrorBoundary from '../ErrorBoundary.vue'

const stubs = {
  AButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
}

/** 只在首次挂载时抛错，重置后再挂载就正常渲染 */
function makeFlakyChild() {
  let shouldThrow = true
  return defineComponent({
    setup() {
      onMounted(() => {
        if (shouldThrow) {
          shouldThrow = false
          throw new Error('boom')
        }
      })
      return () => h('div', { class: 'child-ok' }, 'ok')
    },
  })
}

describe('ErrorBoundary', () => {
  it('捕获子组件错误后展示错误页，「返回首页」走路由跳转并清掉错误态', async () => {
    const Child = makeFlakyChild()
    const wrapper = mount(ErrorBoundary, {
      slots: { default: () => h(Child) },
      global: { stubs },
    })

    await nextTick()
    expect(wrapper.text()).toContain('component.errorBoundary.title')
    expect(wrapper.find('.child-ok').exists()).toBe(false)

    const buttons = wrapper.findAll('button')
    expect(buttons.map((button) => button.text())).toEqual([
      'component.errorBoundary.retry',
      'component.errorBoundary.goHome',
    ])

    // 返回首页只跳路由，不整页刷新
    await buttons[1]!.trigger('click')
    await nextTick()

    expect(push).toHaveBeenCalledWith('/')
    expect(wrapper.text()).not.toContain('component.errorBoundary.title')
    expect(wrapper.find('.child-ok').exists()).toBe(true)
  })
})
