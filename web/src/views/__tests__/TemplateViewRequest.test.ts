import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { message } from 'ant-design-vue'

const templateApiMock = vi.hoisted(() => ({
  queryTemplates: vi.fn(),
  addTemplate: vi.fn(),
  updateTemplate: vi.fn(),
  deleteTemplate: vi.fn(),
  setDefaultTemplate: vi.fn(),
}))

vi.mock('@/services/template', () => templateApiMock)

import TemplateView from '../TemplateView.vue'

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface TemplateViewState {
  handleCreate: () => void
  handleSubmit: () => Promise<void>
  handleDelete: (record: { templateId?: number }) => Promise<void>
  modal: { visible: { value: boolean } }
}

function pageResponse() {
  return { code: 200, data: { list: [{ templateId: 1, templateName: '客服' }], total: 1 } }
}

/** formRef 指向的是 a-form 的替身，得给出弹窗流程真正会调到的两个方法 */
const stubs = {
  AForm: {
    template: '<form><slot /></form>',
    methods: {
      validate: () => Promise.resolve(),
      resetFields: () => {},
    },
  },
}

async function mountView() {
  const wrapper = shallowMount(TemplateView, {
    global: { directives: { permission: {} }, stubs },
  })
  await flushPromises()
  return wrapper.vm as unknown as TemplateViewState
}

describe('TemplateView 写接口', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    templateApiMock.queryTemplates.mockResolvedValue(pageResponse())
  })

  it('新增成功时即便响应体没有 data 也判成功，关弹窗并刷新列表', async () => {
    // 写接口的成功响应是 ApiResponse<Void>，data 恒为 null
    templateApiMock.addTemplate.mockResolvedValue({ code: 200, data: null })

    const view = await mountView()
    view.handleCreate()
    await flushPromises()
    expect(view.modal.visible.value).toBe(true)

    await view.handleSubmit()
    await flushPromises()

    expect(templateApiMock.addTemplate).toHaveBeenCalledTimes(1)
    expect(message.success).toHaveBeenCalledWith('template.createSuccess')
    expect(message.error).not.toHaveBeenCalled()
    expect(view.modal.visible.value).toBe(false)
    // 首屏一次 + 成功后刷新一次
    expect(templateApiMock.queryTemplates).toHaveBeenCalledTimes(2)
  })

  it('新增业务失败时只弹后端文案，弹窗不关也不刷新列表', async () => {
    templateApiMock.addTemplate.mockResolvedValue({ code: 500, message: '模板名已存在' })

    const view = await mountView()
    view.handleCreate()
    await flushPromises()

    await view.handleSubmit()
    await flushPromises()

    expect(message.success).not.toHaveBeenCalled()
    expect(message.error).toHaveBeenCalledTimes(1)
    expect(message.error).toHaveBeenCalledWith('模板名已存在')
    expect(view.modal.visible.value).toBe(true)
    expect(templateApiMock.queryTemplates).toHaveBeenCalledTimes(1)
  })

  it('删除成功时刷新列表', async () => {
    templateApiMock.deleteTemplate.mockResolvedValue({ code: 200, data: null })

    const view = await mountView()
    await view.handleDelete({ templateId: 1 })
    await flushPromises()

    expect(templateApiMock.deleteTemplate).toHaveBeenCalledWith(1)
    expect(message.success).toHaveBeenCalledWith('template.deleteSuccess')
    expect(templateApiMock.queryTemplates).toHaveBeenCalledTimes(2)
  })

  it('删除请求抛异常时不刷新列表，只弹一条操作级文案', async () => {
    templateApiMock.deleteTemplate.mockRejectedValue(new Error('Network Error'))

    const view = await mountView()
    await view.handleDelete({ templateId: 1 })
    await flushPromises()

    expect(message.success).not.toHaveBeenCalled()
    expect(message.error).toHaveBeenCalledTimes(1)
    expect(message.error).toHaveBeenCalledWith('template.deleteFailed')
    expect(templateApiMock.queryTemplates).toHaveBeenCalledTimes(1)
  })
})
