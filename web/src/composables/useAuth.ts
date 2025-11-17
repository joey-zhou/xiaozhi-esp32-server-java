import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { useStorage } from '@vueuse/core'
import { useUserStore } from '@/store/user'
import { useCountdown } from '@/composables/useCountdown'
import { login as loginApi, register as registerApi, resetPassword as resetPasswordApi, telLogin as telLoginApi, sendSmsCaptcha } from '@/services/user'
import { encrypt, decrypt } from '@/utils/jsencrypt'

interface LoginForm {
  username: string
  password: string
  rememberMe?: boolean
}

interface RegisterForm {
  name: string
  username: string
  email: string
  tel?: string
  password: string
  confirmPassword: string
  verifyCode: string
  agreeTerms: boolean
}

interface ForgetPasswordForm {
  email: string
  verificationCode: string
  newPassword: string
  confirmPassword: string
}

interface MobileLoginForm {
  tel: string
  code: string
}

export function useAuth() {
  const router = useRouter()
  const userStore = useUserStore()
  const { t } = useI18n()
  const loading = ref(false)
  const sendCodeLoading = ref(false)

  const rememberedUsername = useStorage('username', '', localStorage)
  const rememberedPassword = useStorage('rememberMe', '', localStorage)

  // 使用倒计时 composable
  const {
    count: countdown,
    counting,
    countdownText,
    start: startCountdown
  } = useCountdown({
    initialCount: 60
  })

  // 登录
  const login = async (form: LoginForm) => {
    loading.value = true
    try {
      const res = await loginApi({
        username: form.username,
        password: form.password,
      })

      if (res.code === 200) {
        // 设置用户信息
        userStore.setUserInfo(res.data.user)
        // 设置权限信息
        userStore.setPermissions(res.data.permissions)
        // 设置角色信息
        userStore.setRole(res.data.role)
        // 设置token
        userStore.setToken(res.data.token)
        userStore.setRefreshToken(res.data.refreshToken)

        if (form.rememberMe) {
          rememberedUsername.value = form.username
          const encryptedPassword = encrypt(form.password)
          if (encryptedPassword) {
            rememberedPassword.value = encryptedPassword
          }
        } else {
          rememberedUsername.value = ''
          rememberedPassword.value = ''
        }

        message.success(t('auth.loginSuccess'))
        router.push('/dashboard')
        return true
      } else {
        message.error(res.message || t('auth.loginFailed'))
        return false
      }
    } catch (error) {
      message.error(t('auth.loginFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  // 注册
  const register = async (form: RegisterForm) => {
    loading.value = true
    try {
      const res = await registerApi({
        name: form.name,
        username: form.username,
        email: form.email,
        tel: form.tel,
        password: form.password,
        verifyCode: form.verifyCode,
      })

      if (res.code === 200) {
        message.success(t('auth.registerSuccess'))
        setTimeout(() => {
          router.push('/login')
        }, 500)
        return true
      } else {
        message.error(res.message || t('common.error'))
        return false
      }
    } catch (error) {
      message.error(t('common.error'))
      return false
    } finally {
      loading.value = false
    }
  }

  // 重置密码
  const resetPassword = async (form: ForgetPasswordForm) => {
    loading.value = true
    try {
      const res = await resetPasswordApi({
        email: form.email,
        code: form.verificationCode,
        password: form.newPassword,
      })

      if (res.code === 200) {
        message.success(t('auth.passwordReset'))
        setTimeout(() => {
          router.push('/login')
        }, 500)
        return true
      } else {
        message.error(res.message || t('common.error'))
        return false
      }
    } catch (error) {
      message.error(t('common.error'))
      return false
    } finally {
      loading.value = false
    }
  }

  const getRememberedCredentials = () => {
    const username = rememberedUsername.value
    const encryptedPassword = rememberedPassword.value
    const password = encryptedPassword ? decrypt(encryptedPassword) : ''

    return {
      username,
      password: typeof password === 'string' ? password : '',
      rememberMe: !!encryptedPassword,
    }
  }

  // 手机号验证码登录
  const telLogin = async (form: MobileLoginForm) => {
    loading.value = true
    try {
      const res = await telLoginApi({
        tel: form.tel,
        code: form.code,
      })

      if (res.code === 200) {
        // 设置用户信息
        userStore.setUserInfo(res.data.user)
        // 设置权限信息
        userStore.setPermissions(res.data.permissions)
        // 设置角色信息
        userStore.setRole(res.data.role)
        // 设置token
        userStore.setToken(res.data.token)
        userStore.setRefreshToken(res.data.refreshToken)

        message.success(t('auth.loginSuccess'))
        router.push('/dashboard')
        return true
      } else if (res.code === 201) {
        // 未注册的手机号
        message.warning(res.message)
        router.push('/register')
        return false
      } else {
        message.error(res.message || t('auth.loginFailed'))
        return false
      }
    } catch (error: any) {
      if (error && error.code === 201) {
        message.warning(error.message)
        router.push('/register')
      } else {
        message.error(error?.message || t('auth.loginFailed'))
      }
      return false
    } finally {
      loading.value = false
    }
  }

  // 发送短信验证码
  const sendVerificationCode = async (tel: string) => {
    if (!tel) {
      message.error(t('auth.enterMobilePhone'))
      return false
    }

    if (sendCodeLoading.value || counting.value) {
      return false
    }

    sendCodeLoading.value = true
    try {
      const res = await sendSmsCaptcha({
        tel,
        type: 'login',
      })

      if (res.code === 200) {
        message.success(t('auth.verificationCodeSent'))
        // 开始倒计时（60秒）
        startCountdown(60)
        return true
      } else {
        message.error(res.message || t('auth.sendVerificationCodeFailed'))
        return false
      }
    } catch (error) {
      message.error(t('auth.sendVerificationCodeFailed'))
      return false
    } finally {
      sendCodeLoading.value = false
    }
  }

  const logout = () => {
    userStore.clearUserInfo()
    userStore.clearToken()
    router.push('/login')
  }

  return {
    loading,
    sendCodeLoading,
    countdown,
    counting,
    countdownText,
    login,
    telLogin,
    register,
    resetPassword,
    sendVerificationCode,
    getRememberedCredentials,
    logout,
  }
}
