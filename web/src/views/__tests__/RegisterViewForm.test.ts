import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'

const authMock = vi.hoisted(() => ({
  register: vi.fn(),
}))

const verificationMock = vi.hoisted(() => ({
  sendRegisterCode: vi.fn(),
}))

vi.mock('@/composables/useAuth', () => ({
  useAuth: () => ({
    loading: ref(false),
    register: authMock.register,
  }),
}))

vi.mock('@/composables/useVerificationCode', () => ({
  useVerificationCode: () => ({
    sendCodeLoading: ref(false),
    canSendCode: ref(true),
    buttonText: ref('auth.sendVerificationCode'),
    sendRegisterCode: verificationMock.sendRegisterCode,
  }),
}))

import RegisterView from '../RegisterView.vue'

interface AgreeTermsRule {
  validator: (rule: unknown, value: boolean) => Promise<void>
}

/** 只保留被测代码用到的那个方法的 FormInstance 替身 */
interface FormRefStub {
  validateFields: (fields: string[]) => Promise<unknown>
}

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface RegisterViewState {
  formData: {
    name: string
    username: string
    email: string
    tel: string
    password: string
    confirmPassword: string
    verifyCode: string
    agreeTerms: boolean
  }
  formRef: FormRefStub | undefined
  showVerificationInput: boolean
  agreeTermsRules: AgreeTermsRule[]
  handleSendCode: () => Promise<void>
  handleSubmit: () => Promise<void>
}

/**
 * a-form 在 shallowMount 下是 stub，模板里的 ref="formRef" 拿不到真的 FormInstance，
 * `formRef.value?.validateFields()` 会被可选链短路掉。每个用例都显式塞受控替身，
 * 否则「校验不过就不发码」会因为压根没校验而假绿。
 */
function mountView(validateFields: FormRefStub['validateFields']) {
  const wrapper = shallowMount(RegisterView, {
    global: { directives: { permission: {} } },
  })
  const view = wrapper.vm as unknown as RegisterViewState
  view.formRef = { validateFields }
  return { view, wrapper }
}

/**
 * 表单项在 shallowMount 下渲染成未注册的 <a-form-item> 元素，name 属性仍可读，
 * 用它来钉住 v-if 这条模板接线。
 * 注意 @click / @finish 覆盖不到：发码按钮在 a-input 的 suffix 插槽里、提交挂在 a-form 上，
 * 而 ant-design-vue 在 src/__tests__/setup.ts 里被整体 mock 成只有 message 和 theme，
 * 组件都没注册，插槽内容不会渲染。
 */
function formItemNames(wrapper: ReturnType<typeof mountView>['wrapper']) {
  return wrapper.findAll('a-form-item').map((item) => item.attributes('name'))
}

function fillForm(view: RegisterViewState) {
  Object.assign(view.formData, {
    name: 'Joey',
    username: 'joey',
    email: 'joey@example.com',
    tel: '13800138000',
    password: 'secret123',
    confirmPassword: 'secret123',
    verifyCode: '654321',
    agreeTerms: true,
  })
}

describe('RegisterView 发送验证码', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('邮箱、账号没过校验时不发验证码，验证码输入框保持收起', async () => {
    const validateFields = vi.fn().mockRejectedValue(new Error('invalid'))
    const { view } = mountView(validateFields)
    fillForm(view)

    await view.handleSendCode()

    expect(validateFields).toHaveBeenCalled()
    expect(verificationMock.sendRegisterCode).not.toHaveBeenCalled()
    expect(view.showVerificationInput).toBe(false)
  })

  it('发码成功后展开验证码输入框，发码前只校验邮箱和账号两项', async () => {
    const validateFields = vi.fn().mockResolvedValue(undefined)
    verificationMock.sendRegisterCode.mockResolvedValue(true)
    const { view } = mountView(validateFields)
    fillForm(view)

    await view.handleSendCode()

    expect(validateFields).toHaveBeenCalledWith(['email', 'username'])
    expect(verificationMock.sendRegisterCode).toHaveBeenCalledWith('joey@example.com', 'joey')
    expect(view.showVerificationInput).toBe(true)
  })

  it('发码失败时验证码输入框保持收起', async () => {
    const validateFields = vi.fn().mockResolvedValue(undefined)
    verificationMock.sendRegisterCode.mockResolvedValue(false)
    const { view } = mountView(validateFields)
    fillForm(view)

    await view.handleSendCode()

    expect(verificationMock.sendRegisterCode).toHaveBeenCalledTimes(1)
    expect(view.showVerificationInput).toBe(false)
  })
})

describe('RegisterView 用户协议校验', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('没勾用户协议时 validator 拒绝并给出提示文案', async () => {
    const { view } = mountView(vi.fn().mockResolvedValue(undefined))
    const { validator } = view.agreeTermsRules[0]!

    await expect(validator(null, false)).rejects.toBe('auth.agreeTermsRequired')
  })

  it('勾了用户协议时 validator 通过', async () => {
    const { view } = mountView(vi.fn().mockResolvedValue(undefined))
    const { validator } = view.agreeTermsRules[0]!

    await expect(validator(null, true)).resolves.toBeUndefined()
  })
})

describe('RegisterView 提交注册', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 钉住表单字段到 register 入参的映射，将来加字段时不会漏传
  it('把表单里的八个字段原样交给 register', async () => {
    authMock.register.mockResolvedValue(true)
    const { view } = mountView(vi.fn().mockResolvedValue(undefined))
    fillForm(view)

    await view.handleSubmit()

    expect(authMock.register).toHaveBeenCalledWith({
      name: 'Joey',
      username: 'joey',
      email: 'joey@example.com',
      tel: '13800138000',
      password: 'secret123',
      confirmPassword: 'secret123',
      verifyCode: '654321',
      agreeTerms: true,
    })
  })
})

describe('RegisterView 验证码字段的显隐', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('发码成功前表单里没有验证码字段，成功后才渲染出来', async () => {
    verificationMock.sendRegisterCode.mockResolvedValue(true)
    const { view, wrapper } = mountView(vi.fn().mockResolvedValue(undefined))
    fillForm(view)

    expect(formItemNames(wrapper)).not.toContain('verifyCode')

    await view.handleSendCode()
    await nextTick()

    expect(formItemNames(wrapper)).toContain('verifyCode')
  })

  it('发码失败时验证码字段不会被渲染出来', async () => {
    verificationMock.sendRegisterCode.mockResolvedValue(false)
    const { view, wrapper } = mountView(vi.fn().mockResolvedValue(undefined))
    fillForm(view)

    await view.handleSendCode()
    await nextTick()

    expect(formItemNames(wrapper)).not.toContain('verifyCode')
  })
})
