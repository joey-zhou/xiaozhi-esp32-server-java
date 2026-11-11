import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'

const configApiMock = vi.hoisted(() => ({
  queryConfigs: vi.fn(),
  addConfig: vi.fn(),
  updateConfig: vi.fn(),
  deleteConfig: vi.fn(),
  testConfig: vi.fn(),
  queryPlatformConfig: vi.fn(),
  addPlatformConfig: vi.fn(),
  updatePlatformConfig: vi.fn(),
}))

vi.mock('@/services/config', () => configApiMock)

import ConfigManager from '../ConfigManager.vue'
import TableActionButtons from '../TableActionButtons.vue'

/** 按行按列渲染 bodyCell 插槽，操作列里的按钮组才会出现 */
const TableStub = defineComponent({
  props: { columns: { type: Array, default: () => [] }, dataSource: { type: Array, default: () => [] } },
  template: `<div>
    <template v-for="record in dataSource">
      <template v-for="column in columns"><slot name="bodyCell" :column="column" :record="record" /></template>
    </template>
  </div>`,
})

describe('ConfigManager 删除按钮', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  // 删默认配置的规则在后端：仍被角色使用会拒，删当前生效的对象存储会先要二次确认，前端不再另藏一道
  it('默认配置也给删除按钮', async () => {
    configApiMock.queryConfigs.mockResolvedValue({
      code: 200,
      message: '',
      data: {
        list: [
          { configId: 1, configType: 'oss', provider: 'aliyun', configName: '当前存储', isDefault: '1' },
          { configId: 2, configType: 'oss', provider: 'tencent', configName: '备用存储', isDefault: '0' },
        ],
        total: 2,
      },
    })

    const wrapper = shallowMount(ConfigManager, {
      props: { configType: 'oss' },
      global: { directives: { permission: {} }, stubs: { ATable: TableStub } },
    })
    await flushPromises()

    const actions = wrapper.findAllComponents(TableActionButtons)
    expect(actions).toHaveLength(2)
    expect(actions.map((action) => action.props('showDelete'))).toEqual([true, true])
  })
})
