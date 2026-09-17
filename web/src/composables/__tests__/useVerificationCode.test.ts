import { describe, it, expect, vi, beforeEach } from 'vitest'

const userServiceMock = vi.hoisted(() => ({
  checkUser: vi.fn(),
  sendEmailCaptcha: vi.fn(),
  sendSmsCaptcha: vi.fn(),
}))

vi.mock('@/services/user', () => userServiceMock)
// 真实的 request.ts 会把 axios / router / store 一起拖进来，这里只给用到的导出
vi.mock('@/services/request', () => ({ shouldIgnoreRequestError: () => false }))

import { message } from 'ant-design-vue'

import { useVerificationCode } from '../useVerificationCode'

function ok() {
  return { code: 200, data: null, message: '操作成功' }
}

function fail(text: string) {
  return { code: 500, data: null, message: text }
}

/** 建实例并保证倒计时定时器不会跨用例继续跑 */
function createCode() {
  const api = useVerificationCode()
  instances.push(api)
  return api
}

let instances: ReturnType<typeof useVerificationCode>[] = []

describe('useVerificationCode', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    instances.forEach((api) => api.pause())
    instances = []
  })

  describe('sendRegisterCode', () => {
    it('成功时弹提示、开倒计时并返回 true', async () => {
      userServiceMock.checkUser.mockResolvedValue(ok())
      userServiceMock.sendEmailCaptcha.mockResolvedValue(ok())
      const api = createCode()

      expect(await api.sendRegisterCode('a@b.com', 'alice')).toBe(true)

      expect(userServiceMock.checkUser).toHaveBeenCalledWith({ username: 'alice', email: 'a@b.com' })
      expect(userServiceMock.sendEmailCaptcha).toHaveBeenCalledWith({ email: 'a@b.com', type: 'register' })
      expect(message.success).toHaveBeenCalledWith('auth.verificationCodeSent')
      expect(message.error).not.toHaveBeenCalled()
      expect(api.countdown.value).toBe(60)
      expect(api.canSendCode.value).toBe(false)
      expect(api.sendCodeLoading.value).toBe(false)
    })

    it('业务码失败时弹后端文案、不开倒计时并返回 false', async () => {
      userServiceMock.checkUser.mockResolvedValue(ok())
      userServiceMock.sendEmailCaptcha.mockResolvedValue(fail('验证码发送过于频繁'))
      const api = createCode()

      expect(await api.sendRegisterCode('a@b.com', 'alice')).toBe(false)

      expect(message.error).toHaveBeenCalledWith('验证码发送过于频繁')
      expect(message.success).not.toHaveBeenCalled()
      expect(api.countdown.value).toBe(0)
      expect(api.sendCodeLoading.value).toBe(false)
    })

    it('传输层异常时不再叠提示、返回 false', async () => {
      userServiceMock.checkUser.mockResolvedValue(ok())
      userServiceMock.sendEmailCaptcha.mockRejectedValue(new Error('Network Error'))
      const api = createCode()

      expect(await api.sendRegisterCode('a@b.com', 'alice')).toBe(false)

      // 拦截器已经弹过一条，这里不能再弹
      expect(message.error).not.toHaveBeenCalled()
      expect(api.countdown.value).toBe(0)
      expect(api.sendCodeLoading.value).toBe(false)
    })

    it('checkUser 抛 409 时直接返回 false，不再发验证码', async () => {
      userServiceMock.checkUser.mockRejectedValue(new Error('邮箱已注册'))
      const api = createCode()

      expect(await api.sendRegisterCode('a@b.com', 'alice')).toBe(false)

      expect(userServiceMock.sendEmailCaptcha).not.toHaveBeenCalled()
      expect(message.error).not.toHaveBeenCalled()
      expect(api.countdown.value).toBe(0)
    })

    it('没传用户名时跳过 checkUser', async () => {
      userServiceMock.sendEmailCaptcha.mockResolvedValue(ok())
      const api = createCode()

      expect(await api.sendRegisterCode('a@b.com')).toBe(true)
      expect(userServiceMock.checkUser).not.toHaveBeenCalled()
    })

    it('邮箱格式不合法时不发请求', async () => {
      const api = createCode()

      expect(await api.sendRegisterCode('not-an-email')).toBe(false)
      expect(message.error).toHaveBeenCalledWith('auth.enterValidEmail')
      expect(userServiceMock.sendEmailCaptcha).not.toHaveBeenCalled()
    })
  })

  describe('sendForgetCode', () => {
    it('成功时弹提示、开倒计时并返回 true', async () => {
      userServiceMock.sendEmailCaptcha.mockResolvedValue(ok())
      const api = createCode()

      expect(await api.sendForgetCode('a@b.com')).toBe(true)

      expect(userServiceMock.sendEmailCaptcha).toHaveBeenCalledWith({ email: 'a@b.com', type: 'forget' })
      expect(message.success).toHaveBeenCalledWith('auth.verificationCodeSent')
      expect(api.countdown.value).toBe(60)
      expect(api.sendCodeLoading.value).toBe(false)
    })

    it('业务码失败时弹后端文案并返回 false', async () => {
      userServiceMock.sendEmailCaptcha.mockResolvedValue(fail('邮箱未注册'))
      const api = createCode()

      expect(await api.sendForgetCode('a@b.com')).toBe(false)

      expect(message.error).toHaveBeenCalledWith('邮箱未注册')
      expect(api.countdown.value).toBe(0)
      expect(api.sendCodeLoading.value).toBe(false)
    })

    it('传输层异常时不再叠提示、返回 false', async () => {
      userServiceMock.sendEmailCaptcha.mockRejectedValue(new Error('Network Error'))
      const api = createCode()

      expect(await api.sendForgetCode('a@b.com')).toBe(false)

      expect(message.error).not.toHaveBeenCalled()
      expect(api.countdown.value).toBe(0)
      expect(api.sendCodeLoading.value).toBe(false)
    })
  })

  describe('sendSmsLoginCode', () => {
    it('成功时弹提示、开倒计时并返回 true', async () => {
      userServiceMock.sendSmsCaptcha.mockResolvedValue(ok())
      const api = createCode()

      expect(await api.sendSmsLoginCode('13800138000')).toBe(true)

      expect(userServiceMock.sendSmsCaptcha).toHaveBeenCalledWith({ tel: '13800138000', type: 'login' })
      expect(message.success).toHaveBeenCalledWith('auth.verificationCodeSent')
      expect(api.countdown.value).toBe(60)
      expect(api.sendCodeLoading.value).toBe(false)
    })

    it('业务码失败时弹后端文案并返回 false', async () => {
      userServiceMock.sendSmsCaptcha.mockResolvedValue(fail('短信通道不可用'))
      const api = createCode()

      expect(await api.sendSmsLoginCode('13800138000')).toBe(false)

      expect(message.error).toHaveBeenCalledWith('短信通道不可用')
      expect(api.countdown.value).toBe(0)
      expect(api.sendCodeLoading.value).toBe(false)
    })

    it('传输层异常时不再叠提示、返回 false', async () => {
      userServiceMock.sendSmsCaptcha.mockRejectedValue(new Error('Network Error'))
      const api = createCode()

      expect(await api.sendSmsLoginCode('13800138000')).toBe(false)

      expect(message.error).not.toHaveBeenCalled()
      expect(api.countdown.value).toBe(0)
      expect(api.sendCodeLoading.value).toBe(false)
    })

    it('手机号为空时不发请求', async () => {
      const api = createCode()

      expect(await api.sendSmsLoginCode('')).toBe(false)
      expect(message.error).toHaveBeenCalledWith('auth.enterMobilePhone')
      expect(userServiceMock.sendSmsCaptcha).not.toHaveBeenCalled()
    })
  })
})
