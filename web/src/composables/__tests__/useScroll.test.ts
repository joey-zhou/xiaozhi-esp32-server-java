import { describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'

import { useScroll } from '../useScroll'

/** 容器放在 v-if 里：挂载那一刻还没有元素，晚一步才出现 */
const LateContainer = defineComponent({
  setup() {
    const visible = ref(false)
    const scroll = useScroll()
    return { visible, scroll }
  },
  render() {
    if (!this.visible) {
      return h('span')
    }
    return h('div', {
      ref: (el: unknown) => {
        this.scroll.containerRef.value = (el as HTMLElement | null) ?? undefined
      },
    })
  },
})

function stubMetrics(el: HTMLElement, scrollHeight: number, clientHeight: number) {
  Object.defineProperty(el, 'scrollHeight', { value: scrollHeight, configurable: true })
  Object.defineProperty(el, 'clientHeight', { value: clientHeight, configurable: true })
}

describe('useScroll 滚动监听绑定时机', () => {
  it('容器在 v-if 内晚出现时也能绑上滚动监听', async () => {
    const wrapper = mount(LateContainer)
    const scroll = wrapper.vm.scroll

    wrapper.vm.visible = true
    await nextTick()

    const el = wrapper.get('div').element as HTMLElement
    stubMetrics(el, 300, 100)
    el.scrollTop = 200
    el.dispatchEvent(new Event('scroll'))

    expect(scroll.scrollTop.value).toBe(200)
    expect(scroll.isAtBottom.value).toBe(true)

    wrapper.unmount()
  })

  it('容器被移除后旧元素的滚动事件不再回写状态', async () => {
    const wrapper = mount(LateContainer)
    const scroll = wrapper.vm.scroll

    wrapper.vm.visible = true
    await nextTick()

    const el = wrapper.get('div').element as HTMLElement
    stubMetrics(el, 300, 100)
    el.scrollTop = 120
    el.dispatchEvent(new Event('scroll'))
    expect(scroll.scrollTop.value).toBe(120)

    wrapper.vm.visible = false
    await nextTick()

    el.scrollTop = 260
    el.dispatchEvent(new Event('scroll'))
    expect(scroll.scrollTop.value).toBe(120)

    wrapper.unmount()
  })
})
