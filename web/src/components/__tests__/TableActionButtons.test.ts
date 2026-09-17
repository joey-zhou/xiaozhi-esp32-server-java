import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import TableActionButtons from '../TableActionButtons.vue'
import { useUserStore } from '@/store/user'
import type { PermissionTreeNode } from '@/types/authRole'

/** ant 组件是全局注册的，测试里换成能触发事件的最小替身 */
const stubs = {
  ASpace: { template: '<div><slot /></div>' },
  ADivider: { template: '<i class="divider" />' },
  AButton: { template: '<button type="button"><slot /></button>' },
  APopconfirm: {
    emits: ['confirm'],
    template: '<span class="popconfirm" @click="$emit(\'confirm\')"><slot /></span>',
  },
  ADropdown: { template: '<div><slot /><slot name="overlay" /></div>' },
  AMenu: { emits: ['click'], template: '<ul><slot /></ul>' },
  AMenuItem: { template: '<li class="menu-item"><slot /></li>' },
}

const record = { roleId: 7, roleName: '客服' }

function buttonPermission(permissionKey: string): PermissionTreeNode {
  return { permissionId: 1, name: permissionKey, permissionKey, permissionType: 'button' }
}

describe('TableActionButtons', () => {
  let pinia: ReturnType<typeof createPinia>

  /** 组件 setup 里要取 user store，挂载时把同一个 pinia 装进去 */
  function globalOptions() {
    return { plugins: [pinia], stubs }
  }

  beforeEach(() => {
    localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    useUserStore().setUserInfo({ userId: '1', isAdmin: '1' })
  })

  // store 里的 useStorage 会监听 storage 事件，上一个用例的实例不销毁就会把旧身份同步回来
  afterEach(() => {
    useUserStore().$dispose()
  })

  it('按 show-* 渲染对应按钮，点击把当前行数据带回去', async () => {
    const wrapper = mount(TableActionButtons, {
      props: {
        record,
        showEdit: true,
        showView: true,
        showDownload: true,
        showCopy: true,
        showSetDefault: true,
        showDelete: true,
      },
      global: globalOptions(),
    })

    const links = wrapper.findAll('a')
    expect(links.map((link) => link.text())).toEqual([
      'common.edit',
      'common.view',
      'common.download',
      'common.copy',
      'common.setAsDefault',
    ])

    for (const link of links) {
      await link.trigger('click')
    }

    // 删除按钮是 a-button，deleteClass 仍要落到按钮上（配色靠它）
    const deleteButton = wrapper.get('.popconfirm button')
    expect(deleteButton.text()).toBe('common.delete')
    expect(deleteButton.classes()).toContain('delete-link')
    // 删除是 popconfirm 确认后才发事件，点击冒泡到替身上触发 confirm
    await deleteButton.trigger('click')

    expect(wrapper.emitted('edit')?.[0]).toEqual([record])
    expect(wrapper.emitted('view')?.[0]).toEqual([record])
    expect(wrapper.emitted('download')?.[0]).toEqual([record])
    expect(wrapper.emitted('copy')?.[0]).toEqual([record])
    expect(wrapper.emitted('setDefault')?.[0]).toEqual([record])
    expect(wrapper.emitted('delete')?.[0]).toEqual([record])
  })

  it('已是默认项时不渲染「设为默认」', () => {
    const wrapper = mount(TableActionButtons, {
      props: { record, showSetDefault: true, isDefault: true },
      global: globalOptions(),
    })

    expect(wrapper.text()).not.toContain('common.setAsDefault')
  })

  it('permissionPrefix 按 update/delete 后缀查按钮权限', () => {
    const store = useUserStore()
    store.setUserInfo({ userId: '2', isAdmin: '0' })
    store.setPermissions([buttonPermission('system:role:update')])

    const wrapper = mount(TableActionButtons, {
      props: {
        record,
        permissionPrefix: 'system:role',
        showEdit: true,
        showView: true,
        showDelete: true,
      },
      global: globalOptions(),
    })

    expect(wrapper.text()).toContain('common.edit')
    // view 不查按钮权限，只受页面访问控制
    expect(wrapper.text()).toContain('common.view')
    expect(wrapper.text()).not.toContain('common.delete')
  })

  it('permissions 显式覆盖优先于 permissionPrefix 推导', () => {
    const store = useUserStore()
    store.setUserInfo({ userId: '2', isAdmin: '0' })
    store.setPermissions([buttonPermission('system:role:update')])

    const wrapper = mount(TableActionButtons, {
      props: {
        record,
        permissionPrefix: 'system:role',
        showEdit: true,
        permissions: { edit: false },
      },
      global: globalOptions(),
    })

    expect(wrapper.text()).not.toContain('common.edit')
  })

  it('moreActions 过滤掉 visible=false 与无权限项', () => {
    const store = useUserStore()
    store.setUserInfo({ userId: '2', isAdmin: '0' })
    store.setPermissions([buttonPermission('system:role:memory')])

    const wrapper = mount(TableActionButtons, {
      props: {
        record,
        moreActions: [
          { key: 'memory', label: '记忆', permission: 'system:role:memory' },
          { key: 'export', label: '导出', permission: 'system:role:export' },
          { key: 'hidden', label: '隐藏', visible: false },
        ],
      },
      global: globalOptions(),
    })

    expect(wrapper.findAll('.menu-item').map((item) => item.text())).toEqual(['记忆'])
  })
})
