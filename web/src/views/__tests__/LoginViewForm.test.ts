import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import type { Rule } from 'ant-design-vue/es/form'

// composable 层行为已由 composables/__tests__ 覆盖，这里只留装配用的桩
const authApi = vi.hoisted(() => ({
  login: vi.fn(),
  telLogin: vi.fn(),
  getRememberedCredentials: vi.fn(),
}))

const codeApi = vi.hoisted(() => ({
  sendSmsLoginCode: vi.fn(),
}))

vi.mock('@/composables/useAuth', async () => {
  const { ref } = await import('vue')
  const loading = ref(false)
  return { useAuth: () => ({ loading, ...authApi }) }
})

vi.mock('@/composables/useVerificationCode', async () => {
  const { ref, computed } = await import('vue')
  const sendCodeLoading = ref(false)
  return {
    useVerificationCode: () => ({
      sendCodeLoading,
      canSendCode: computed(() => true),
      buttonText: computed(() => 'auth.sendVerificationCode'),
      ...codeApi,
    }),
  }
})

// useFormValidation 用真的：mobileRules 是否真补上了必填，只有对着真规则比才有意义
import { useFormValidation } from '@/composables/useFormValidation'
import LoginView from '../LoginView.vue'

const { usernameRules, passwordRules, telRules, verificationCodeRules } = useFormValidation()

// <script setup> 的内部状态不对外暴露，测试里按实际形状声明后取用
interface LoginViewState {
  loginType: 'account' | 'mobile'
  formState: { username: string; password: string; rememberMe: boolean }
  mobileFormState: { tel: string; code: string }
  rules: Record<string, Rule[]>
  switchLoginType: (type: 'account' | 'mobile') => void
  handleSendCode: () => void
  handleSubmit: () => Promise<void>
}

async function mountView() {
  const wrapper = shallowMount(LoginView, {
    global: { stubs: { 'router-link': true } },
  })
  await flushPromises()
  return wrapper.vm as unknown as LoginViewState
}

describe('LoginView 校验规则跟随登录方式切换', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authApi.getRememberedCredentials.mockReturnValue({ username: '', rememberMe: false })
  })

  it('账号模式下用的是用户名与密码规则', async () => {
    const view = await mountView()

    expect(view.rules).toEqual({ username: usernameRules, password: passwordRules })
  })

  it('切到手机号模式后换成手机号与验证码规则', async () => {
    const view = await mountView()

    view.switchLoginType('mobile')
    await flushPromises()

    expect(Object.keys(view.rules).sort()).toEqual(['code', 'tel'])
    expect(view.rules.code).toEqual(verificationCodeRules)
    expect(view.rules.tel).toEqual([
      { required: true, message: 'auth.enterMobilePhone', trigger: 'blur' },
      ...telRules,
    ])
  })

  // telRules 只有格式校验，必填是 LoginView 自己补的；
  // 谁把这条补丁删了（比如误以为 useFormValidation 里已经有），手机号就会静默变成选填
  it('手机号规则里必须有一条 required，光有格式校验不算', async () => {
    const view = await mountView()

    view.switchLoginType('mobile')
    await flushPromises()

    expect((view.rules.tel ?? []).some((rule) => rule.required === true)).toBe(true)
    expect(telRules.some((rule) => rule.required === true)).toBe(false)
  })
})

describe('LoginView 提交按登录方式分流', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authApi.getRememberedCredentials.mockReturnValue({ username: '', rememberMe: false })
  })

  it('账号模式提交走 login，实参是账号表单', async () => {
    const view = await mountView()
    view.formState.username = 'joey'
    view.formState.password = 'pwd123'
    view.formState.rememberMe = true

    await view.handleSubmit()

    expect(authApi.login).toHaveBeenCalledWith({
      username: 'joey',
      password: 'pwd123',
      rememberMe: true,
    })
    expect(authApi.telLogin).not.toHaveBeenCalled()
  })

  it('手机号模式提交走 telLogin，实参是手机号表单', async () => {
    const view = await mountView()
    view.switchLoginType('mobile')
    view.mobileFormState.tel = '13800138000'
    view.mobileFormState.code = '123456'

    await view.handleSubmit()

    expect(authApi.telLogin).toHaveBeenCalledWith({ tel: '13800138000', code: '123456' })
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('发验证码把当前输入的手机号传下去', async () => {
    const view = await mountView()
    view.switchLoginType('mobile')
    view.mobileFormState.tel = '13800138000'

    view.handleSendCode()

    expect(codeApi.sendSmsLoginCode).toHaveBeenCalledWith('13800138000')
  })
})

describe('LoginView 挂载时回填记住的凭据', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authApi.getRememberedCredentials.mockReturnValue({ username: '', rememberMe: false })
  })

  it('记住的用户名与勾选状态写进 formState，密码位保持空', async () => {
    authApi.getRememberedCredentials.mockReturnValue({ username: 'joey', rememberMe: true })

    const view = await mountView()

    expect(authApi.getRememberedCredentials).toHaveBeenCalledTimes(1)
    expect(view.formState).toEqual({ username: 'joey', password: '', rememberMe: true })
  })

  it('回填后的凭据原样带进 login，记住我状态不会在提交时丢掉', async () => {
    authApi.getRememberedCredentials.mockReturnValue({ username: 'joey', rememberMe: true })

    const view = await mountView()
    view.formState.password = 'pwd123'
    await view.handleSubmit()

    expect(authApi.login).toHaveBeenCalledWith({
      username: 'joey',
      password: 'pwd123',
      rememberMe: true,
    })
  })

  it('没有记住过时表单保持空，不会凭空塞进用户名', async () => {
    const view = await mountView()

    expect(view.formState).toEqual({ username: '', password: '', rememberMe: false })
  })
})
