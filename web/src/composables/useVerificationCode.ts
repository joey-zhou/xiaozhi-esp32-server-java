import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useIntervalFn } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import { checkUser, sendEmailCaptcha, checkCaptcha } from '@/services/user'

type VerificationType = 'register' | 'forget'

export function useVerificationCode() {
  const { t } = useI18n()
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
      // 全局响应拦截器已统一弹出后端提示（如“邮箱已注册”），此处无需再判断 code。
      if (username) {
        await checkUser({
          username,
          email,
        })
      }

      // 发送验证码
      const res = await sendEmailCaptcha({
        email,
        type: 'register',
      })

      if (res.code === 200) {
        message.success(t('auth.verificationCodeSent'))
        startCountdown()
        return true
      }

      message.error(res.message || t('auth.sendVerificationCodeFailed'))
      return false
    } catch (error) {
      // 校验/发送过程中抛出的 HTTP 错误已由全局拦截器统一提示，避免重复弹窗
      return false
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
      const res = await sendEmailCaptcha({
        email,
        type: 'forget',
      })

      if (res.code === 200) {
        message.success(t('auth.verificationCodeSent'))
        startCountdown()
        return true
      }

      message.error(res.message || t('auth.sendVerificationCodeFailed'))
      return false
    } catch (error) {
      // HTTP 错误已由全局拦截器统一提示，避免重复弹窗
      return false
    } finally {
      sendCodeLoading.value = false
    }
  }

  // 验证验证码
  const verifyCode = async (email: string, code: string, type: VerificationType) => {
    try {
      const res = await checkCaptcha({
        email,
        code,
        type,
      })

      if (res.code === 200) {
        message.success(t('auth.verificationSuccess'))
        return true
      }

      message.error(res.message || t('auth.verificationCodeError'))
      return false
    } catch (error) {
      // HTTP 错误已由全局拦截器统一提示，避免重复弹窗
      return false
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
    verifyCode,
    validateEmail,
    pause,
    resume
  }
}
