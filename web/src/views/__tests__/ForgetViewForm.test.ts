import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import type { Rule } from 'ant-design-vue/es/form'

// 只替掉 composable 边界：view 层要断言的是它把哪些参数、在什么条件下交出去
const authMock = vi.hoisted(() => ({ resetPassword: vi.fn() }))
const codeMock = vi.hoisted(() => ({ sendForgetCode: vi.fn() }))

vi.mock('@/composables/useAuth', () => ({
  useAuth: () => ({
    loading: ref(false),
    resetPassword: authMock.resetPassword,
  }),
}))

vi.mock('@/composables/useVerificationCode', () => ({
  useVerificationCode: () => ({
    sendCodeLoading: ref(false),
    canSendCode: ref(true),
    buttonText: ref('auth.sendVerificationCode'),
    sendForgetCode: codeMock.sendForgetCode,
  }),
}))

import ForgetView from '../ForgetView.vue'
import { useFormValidation } from '@/composables/useFormValidation'

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface ForgetViewState {
  formRef: { validateFields: (names: string[]) => Promise<unknown> } | undefined
  showVerificationInput: boolean
  formData: {
    email: string
    verificationCode: string
    newPassword: string
    confirmPassword: string
  }
  rules: {
    email: Rule[]
    verificationCode: Rule[]
    newPassword: Rule[]
    confirmPassword: Rule[]
  }
  handleSendCode: () => Promise<void>
  handleSubmit: () => Promise<void>
}

/**
 * shallowMount 下 a-form 是 stub，模板上的 ref="formRef" 拿不到真的 FormInstance，
 * `formRef.value?.validateFields()` 会短路成 undefined 而直接放行。
 * 所以每个用例都显式塞一个受控替身，别让校验分支空转。
 */
function mountView(validateFields: () => Promise<unknown>) {
  const wrapper = shallowMount(ForgetView, {
    global: { stubs: { 'router-link': true } },
  })
  const view = wrapper.vm as unknown as ForgetViewState
  view.formRef = { validateFields }
  return { view, wrapper }
}

/**
 * 表单项在 shallowMount 下渲染成未注册的 <a-form-item> 元素，name 属性仍可读，
 * 用它来钉住 v-if 这条模板接线。@click / @finish 覆盖不到：
 * ant-design-vue 在 src/__tests__/setup.ts 里被整体 mock，组件没注册、插槽不渲染。
 */
function formItemNames(wrapper: ReturnType<typeof mountView>['wrapper']) {
  return wrapper.findAll('a-form-item').map((item) => item.attributes('name'))
}

const validatePass = () => vi.fn().mockResolvedValue(undefined)
const validateFail = () => vi.fn().mockRejectedValue(new Error('invalid'))

describe('ForgetView 发送验证码', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('邮箱校验没过时不发验证码，验证码框也不展开', async () => {
    const { view } = mountView(validateFail())
    view.formData.email = 'not-an-email'

    await view.handleSendCode()

    expect(codeMock.sendForgetCode).not.toHaveBeenCalled()
    expect(view.showVerificationInput).toBe(false)
  })

  it('发码成功才展开验证码与新密码输入，且这一步只校验邮箱一个字段', async () => {
    const validateFields = validatePass()
    codeMock.sendForgetCode.mockResolvedValue(true)
    const { view } = mountView(validateFields)
    view.formData.email = 'joey@example.com'

    await view.handleSendCode()

    // 此时验证码/新密码都还没填，一起校验会必然失败
    expect(validateFields).toHaveBeenCalledWith(['email'])
    expect(codeMock.sendForgetCode).toHaveBeenCalledWith('joey@example.com')
    expect(view.showVerificationInput).toBe(true)
  })

  it('发码失败时保持不展开，避免用户对着空验证码提交', async () => {
    codeMock.sendForgetCode.mockResolvedValue(false)
    const { view } = mountView(validatePass())
    view.formData.email = 'joey@example.com'

    await view.handleSendCode()

    expect(codeMock.sendForgetCode).toHaveBeenCalledWith('joey@example.com')
    expect(view.showVerificationInput).toBe(false)
  })
})

describe('ForgetView 提交重置', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMock.resetPassword.mockResolvedValue(true)
  })

  it('把四个表单字段原样交给 resetPassword', async () => {
    const { view } = mountView(validatePass())
    view.formData.email = 'joey@example.com'
    view.formData.verificationCode = '123456'
    view.formData.newPassword = 'NewPass123'
    view.formData.confirmPassword = 'NewPass123'

    await view.handleSubmit()

    expect(authMock.resetPassword).toHaveBeenCalledWith({
      email: 'joey@example.com',
      verificationCode: '123456',
      newPassword: 'NewPass123',
      confirmPassword: 'NewPass123',
    })
  })
})

describe('ForgetView 表单规则装配', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('四个字段各接到对应的规则集', () => {
    const { emailRules, verificationCodeRules, passwordRules } = useFormValidation()
    const { view } = mountView(validatePass())

    expect(view.rules.email).toEqual(emailRules)
    expect(view.rules.verificationCode).toEqual(verificationCodeRules)
    expect(view.rules.newPassword).toEqual(passwordRules)
  })

  it('确认密码规则绑的是当前 formData.newPassword，改了新密码后规则跟着改判', async () => {
    const { view } = mountView(validatePass())
    // a-form 每次校验都重新读 rules，这里照它的用法取
    const validateConfirm = (value: string) => {
      const rule = view.rules.confirmPassword[0]!
      return rule.validator!(rule, value, () => {})
    }

    view.formData.newPassword = 'NewPass123'
    await expect(validateConfirm('NewPass123')).resolves.toBeUndefined()

    // 用户回头改了新密码：旧值改判为不匹配，新值才算匹配
    view.formData.newPassword = 'Changed456'
    await expect(validateConfirm('NewPass123')).rejects.toBe('validation.confirmPassword')
    await expect(validateConfirm('Changed456')).resolves.toBeUndefined()
  })
})

describe('ForgetView 重置字段的显隐', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 验证码、新密码、确认密码三项共用同一个 showVerificationInput 开关，
  // 漏掉任何一个都会让用户在没发码的情况下就能填新密码
  it('发码成功前三个字段都不渲染，成功后一并出现', async () => {
    codeMock.sendForgetCode.mockResolvedValue(true)
    const { view, wrapper } = mountView(validatePass())
    view.formData.email = 'joey@example.com'

    const gated = ['verificationCode', 'newPassword', 'confirmPassword']
    expect(formItemNames(wrapper).filter((name) => gated.includes(name ?? ''))).toEqual([])

    await view.handleSendCode()
    await nextTick()

    expect(formItemNames(wrapper)).toEqual(expect.arrayContaining(gated))
  })

  it('发码失败时三个字段都不会被渲染出来', async () => {
    codeMock.sendForgetCode.mockResolvedValue(false)
    const { view, wrapper } = mountView(validatePass())
    view.formData.email = 'joey@example.com'

    await view.handleSendCode()
    await nextTick()

    const gated = ['verificationCode', 'newPassword', 'confirmPassword']
    expect(formItemNames(wrapper).filter((name) => gated.includes(name ?? ''))).toEqual([])
  })
})
