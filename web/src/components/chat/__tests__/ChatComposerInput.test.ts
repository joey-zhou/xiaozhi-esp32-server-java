import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ChatComposer from '../ChatComposer.vue'

/** jsdom 不实现 innerText，用 textContent 代理，让组件的读写路径可测 */
function stubInnerText(el: HTMLElement) {
  Object.defineProperty(el, 'innerText', {
    configurable: true,
    get(this: HTMLElement) {
      return this.textContent ?? ''
    },
    set(this: HTMLElement, value: string) {
      this.textContent = value
    },
  })
}

function mountComposer(modelValue = '你好') {
  const wrapper = mount(ChatComposer, { props: { modelValue } })
  stubInnerText(wrapper.get('.field').element as HTMLElement)
  return wrapper
}

/** 取某个事件最后一次触发的参数；lib 是 ES2020，用不了 Array.prototype.at */
function lastEmitted(wrapper: { emitted: (name: string) => unknown[][] | undefined }, name: string) {
  const calls = wrapper.emitted(name)
  return calls?.[calls.length - 1]
}

describe('ChatComposer 输入行为', () => {
  it('组字中的回车不发送（isComposing）', async () => {
    const wrapper = mountComposer()

    await wrapper.get('.field').trigger('keydown', { key: 'Enter', isComposing: true })

    expect(wrapper.emitted('send')).toBeUndefined()
  })

  it('组字中的回车不发送（keyCode 229）', async () => {
    const wrapper = mountComposer()

    await wrapper.get('.field').trigger('keydown', { key: 'Enter', keyCode: 229 })

    expect(wrapper.emitted('send')).toBeUndefined()
  })

  it('组字结束后的回车正常发送', async () => {
    const wrapper = mountComposer()

    await wrapper.get('.field').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('send')).toHaveLength(1)
  })

  it('Shift+Enter 不发送，留给换行', async () => {
    const wrapper = mountComposer()

    await wrapper.get('.field').trigger('keydown', { key: 'Enter', shiftKey: true })

    expect(wrapper.emitted('send')).toBeUndefined()
  })

  it('取值走 innerText，多行内容不会被拼成一行', async () => {
    const wrapper = mountComposer('')
    const field = wrapper.get('.field')
    ;(field.element as HTMLElement).innerText = '第一行\n第二行'

    await field.trigger('input')

    expect(lastEmitted(wrapper, 'update:modelValue')).toEqual(['第一行\n第二行'])
  })

  it('粘贴只取 text/plain，富文本标记不进编辑区', async () => {
    const wrapper = mountComposer('')
    const field = wrapper.get('.field')

    await field.trigger('paste', {
      clipboardData: {
        getData: (type: string) => (type === 'text/plain' ? '纯文本' : '<b>纯文本</b>'),
      },
    })

    expect(lastEmitted(wrapper, 'update:modelValue')).toEqual(['纯文本'])
    expect(wrapper.get('.field').html()).not.toContain('<b>')
  })
})
