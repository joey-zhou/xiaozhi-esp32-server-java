import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useStorage } from '@vueuse/core'
import { useUserStore } from '@/store/user'
import { login as loginApi, register as registerApi, resetPassword as resetPasswordApi } from '@/services/user'
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

export function useAuth() {
  const router = useRouter()
  const userStore = useUserStore()
  const loading = ref(false)

  const rememberedUsername = useStorage('username', '', localStorage)
  const rememberedPassword = useStorage('rememberMe', '', localStorage)

  // 登录
  const login = async (form: LoginForm) => {
    loading.value = true
    try {
      const res = await loginApi({
        username: form.username,
        password: form.password,
      })

      if (res.code === 200) {
        userStore.setUserInfo(res.data)

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

        message.success('登录成功！')
        router.push('/dashboard')
        return true
      } else {
        message.error(res.message || '登录失败')
        return false
      }
    } catch (error) {
      message.error('登录失败，请稍后重试')
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
        message.success('注册成功！即将跳转到登录页面...')
        setTimeout(() => {
          router.push('/login')
        }, 500)
        return true
      } else {
        message.error(res.message || '注册失败')
        return false
      }
    } catch (error) {
      message.error('注册失败，请稍后重试')
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
        message.success('密码重置成功！即将跳转到登录页面...')
        setTimeout(() => {
          router.push('/login')
        }, 500)
        return true
      } else {
        message.error(res.message || '密码重置失败')
        return false
      }
    } catch (error) {
      message.error('密码重置失败，请稍后重试')
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

  const logout = () => {
    userStore.clearUserInfo()
    router.push('/login')
  }

  return {
    loading,
    login,
    register,
    resetPassword,
    getRememberedCredentials,
    logout,
  }
}
