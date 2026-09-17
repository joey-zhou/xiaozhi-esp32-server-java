import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { message } from 'ant-design-vue'
import type { UploadOptions, UploadResponse } from '@/services/upload'

const userApiMock = vi.hoisted(() => ({
  updateUser: vi.fn(),
}))

vi.mock('@/services/user', () => userApiMock)

vi.mock('@/services/upload', () => ({
  uploadFile: vi.fn(),
}))

import AccountView from '../setting/AccountView.vue'
import { useUserStore } from '@/store/user'
import type { User } from '@/types/user'

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface AccountViewState {
  formData: {
    name: string
    tel: string
    email: string
    oldPassword: string
    password: string
    confirmPassword: string
  }
  formRef: { validate: () => Promise<void> } | undefined
  avatarLoading: boolean
  handleSubmit: () => Promise<void>
  updateUserAvatar: (avatarPath: string) => Promise<void>
  beforeAvatarUpload: (file: File) => boolean
}

describe('AccountView 保存账号资料', () => {
  let pinia: ReturnType<typeof createPinia>

  /** a-form 在测试里没被解析，formRef 拿到的是 DOM 节点，这里换成只有 validate 的替身 */
  function mountView(validate = vi.fn().mockResolvedValue(undefined)) {
    const wrapper = shallowMount(AccountView, {
      global: { plugins: [pinia], directives: { permission: {} } },
    })
    const view = wrapper.vm as unknown as AccountViewState
    view.formRef = { validate }
    return view
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => {})
    localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    useUserStore().setUserInfo({ userId: 1, username: 'joey', name: 'Joey' } as User)
  })

  // store 里的 useStorage 会监听 storage 事件，上一个用例的实例不销毁就会把旧身份同步回来
  afterEach(() => {
    useUserStore().$dispose()
    vi.restoreAllMocks()
  })

  it('保存成功即使响应 data 为 null 也判成功，并清空两个密码框', async () => {
    // 写接口的成功响应体是 ApiResponse<Void>，data 恒为 null
    userApiMock.updateUser.mockResolvedValue({ code: 200, message: '', data: null })
    const view = mountView()
    view.formData.oldPassword = 'oldpass1'
    view.formData.password = 'newpass1'
    view.formData.confirmPassword = 'newpass1'

    await view.handleSubmit()
    await flushPromises()

    // 原密码要跟着一起提交，后端凭它校验，漏传等于登录态一被窃就能改密码
    expect(userApiMock.updateUser).toHaveBeenCalledWith(
      expect.objectContaining({ userId: 1, oldPassword: 'oldpass1', password: 'newpass1' }),
    )
    expect(message.success).toHaveBeenCalledWith('account.updateSuccess')
    expect(view.formData.oldPassword).toBe('')
    expect(view.formData.password).toBe('')
    expect(view.formData.confirmPassword).toBe('')
  })

  it('保存失败时原样弹后端提示，密码框保持不清空', async () => {
    userApiMock.updateUser.mockResolvedValue({ code: 500, message: '邮箱已注册' })
    const view = mountView()
    view.formData.oldPassword = 'oldpass1'
    view.formData.password = 'newpass1'
    view.formData.confirmPassword = 'newpass1'

    await view.handleSubmit()
    await flushPromises()

    expect(message.error).toHaveBeenCalledWith('邮箱已注册')
    expect(message.success).not.toHaveBeenCalled()
    expect(view.formData.oldPassword).toBe('oldpass1')
    expect(view.formData.password).toBe('newpass1')
    expect(view.formData.confirmPassword).toBe('newpass1')
  })

  it('表单校验不过就不发请求', async () => {
    const view = mountView(vi.fn().mockRejectedValue(new Error('confirmPassword')))

    await view.handleSubmit()
    await flushPromises()

    expect(userApiMock.updateUser).not.toHaveBeenCalled()
    expect(message.success).not.toHaveBeenCalled()
  })

  it('头像入库请求抛错时也要熄灭上传遮罩', async () => {
    // avatarLoading 现在由 useAvatarUpload 统一收起，走完整的 beforeAvatarUpload 流程才能验到
    const { uploadFile } = await import('@/services/upload')
    // uploadFile 是重载函数，vi.mocked 只会挑到返回路径字符串的那条；这里走的是 fullResponse: true 那条
    const uploadFullResponse = uploadFile as (
      file: File, type: string, options: UploadOptions & { fullResponse: true }
    ) => Promise<UploadResponse>
    vi.mocked(uploadFullResponse).mockResolvedValue({
      code: 200,
      message: '',
      url: '',
      relativePath: 'avatar/1.png',
    })
    userApiMock.updateUser.mockRejectedValue(new Error('network down'))
    const view = mountView()

    const file = new File([], 'avatar.png', { type: 'image/png' })
    view.beforeAvatarUpload(file)
    await flushPromises()

    expect(view.avatarLoading).toBe(false)
    expect(message.success).not.toHaveBeenCalled()
  })
})
