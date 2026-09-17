import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TableEmptyState from '../TableEmptyState.vue'

/** ant 组件是全局注册的，测试里换成保留插槽结构的最小替身 */
const stubs = {
  AEmpty: {
    template:
      '<div class="empty"><p class="desc"><slot name="description" /></p><div class="footer"><slot /></div></div>',
  },
  AButton: { template: '<button type="button"><slot /></button>' },
}

describe('TableEmptyState', () => {
  it('没有失败态时只渲染空提示，不给重试入口', () => {
    const wrapper = mount(TableEmptyState, { global: { stubs } })

    expect(wrapper.find('.desc').text()).toBe('')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  // 失败后只显示「暂无数据」的话，用户分不清是请求挂了还是真的没数据
  it('有失败态时给出失败说明与重试按钮', async () => {
    const wrapper = mount(TableEmptyState, {
      props: { error: '连接超时' },
      global: { stubs },
    })

    expect(wrapper.find('.desc').text()).toContain('table.loadFailed')
    expect(wrapper.find('.desc').text()).toContain('连接超时')

    const retry = wrapper.get('.footer button')
    expect(retry.text()).toBe('table.retry')

    await retry.trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
