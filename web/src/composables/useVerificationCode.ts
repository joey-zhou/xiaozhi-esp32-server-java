import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useIntervalFn } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import { useRequest } from '@/composables/useRequest'
import { checkUser, sendEmailCaptcha, sendSmsCaptcha } from '@/services/user'

export function useVerificationCode() {
  const { t } = useI18n()
  const { executeOk } = useRequest()
  // 按钮文案与倒计时都读它，和 useRequest 自带的 loading 是两个 ref，保持原样
  const sendCodeLoading = ref(false)
  const countdown = ref(0)

  const { pause, resume, isActive } = useIntervalFn(
    () => {
      countdown.value--
      if (countdown.value <= 0) {
        pause()
      }
    },
    1000,
    { immediate: false }
  )

  const canSendCode = computed(() => !sendCodeLoading.value && countdown.value === 0)

  const buttonText = computed(() => {
    if (sendCodeLoading.value) return t('auth.sending')
    if (countdown.value > 0) return t('auth.resendAfter', { seconds: countdown.value })
    return t('auth.sendVerificationCode')
  })

  const startCountdown = (seconds = 60) => {
    countdown.value = seconds
    resume()
  }

  // 验证邮箱格式
  const validateEmail = (email: string): boolean => {
    if (!email) {
      message.error(t('auth.enterEmailFirst'))
      return false
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(email)) {
      message.error(t('auth.enterValidEmail'))
      return false
    }

    return true
  }

  // 发送验证码（注册场景 - 需要先检查用户名和邮箱）
  const sendRegisterCode = async (email: string, username?: string) => {
    if (!validateEmail(email)) return false
    if (!canSendCode.value) return false

    sendCodeLoading.value = true

    try {
      // 先检查用户名和邮箱是否已存在
      // 注意：后端在已存在时返回 HTTP 409（走 axios 错误分支并抛异常），
      // 全局响应拦截器已统一弹出后端提示（如“邮箱已注册”），此处不能再弹第二条
      if (username) {
        const available = await executeOk(() => checkUser({ username, email }), { showError: false })
        if (!available) {
          return false
        }
      }

      // 发送验证码。不给 errorText：传输层失败时拦截器弹的是后端原文，覆盖成笼统文案反而更差
      return await executeOk(() => sendEmailCaptcha({ email, type: 'register' }), {
        showSuccess: true,
        successText: t('auth.verificationCodeSent'),
        onSuccess: () => startCountdown(),
      })
    } finally {
      sendCodeLoading.value = false
    }
  }

  // 发送验证码（忘记密码场景）
  const sendForgetCode = async (email: string) => {
    if (!validateEmail(email)) return false
    if (!canSendCode.value) return false

    sendCodeLoading.value = true

    try {
      return await executeOk(() => sendEmailCaptcha({ email, type: 'forget' }), {
        showSuccess: true,
        successText: t('auth.verificationCodeSent'),
        onSuccess: () => startCountdown(),
      })
    } finally {
      sendCodeLoading.value = false
    }
  }

  // 发送验证码（手机号登录场景）
  const sendSmsLoginCode = async (tel: string) => {
    if (!tel) {
      message.error(t('auth.enterMobilePhone'))
      return false
    }
    if (!canSendCode.value) return false

    sendCodeLoading.value = true

    try {
      return await executeOk(() => sendSmsCaptcha({ tel, type: 'login' }), {
        showSuccess: true,
        successText: t('auth.verificationCodeSent'),
        onSuccess: () => startCountdown(),
      })
    } finally {
      sendCodeLoading.value = false
    }
  }

  return {
    sendCodeLoading,
    countdown,
    canSendCode,
    buttonText,
    isActive,
    sendRegisterCode,
    sendForgetCode,
    sendSmsLoginCode,
    validateEmail,
    pause,
    resume
  }
}
