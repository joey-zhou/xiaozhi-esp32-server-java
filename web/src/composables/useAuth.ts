import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { useStorage } from '@vueuse/core'
import { useUserStore } from '@/store/user'
import type { LoginResponse } from '@/types/user'
import { login as loginApi, logout as logoutApi, register as registerApi, resetPassword as resetPasswordApi, telLogin as telLoginApi } from '@/services/user'
import { STORAGE_REMEMBER_ME, STORAGE_USERNAME } from '@/constants/storage'
import { ROUTES, defaultRouteFor } from '@/router/routes'

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

  // 只记住用户名，密码一律不落盘
  const rememberedUsername = useStorage(STORAGE_USERNAME, '', localStorage)

  // 把登录响应写进 store
  const applyLoginSession = (data: LoginResponse) => {
    userStore.setUserInfo(data.user)
    userStore.setPermissions(data.permissions)
    userStore.setAuthRole(data.authRole)
    userStore.setToken(data.token)
    userStore.setRefreshToken(data.refreshToken)
  }

  // 计算登录后要落地的路径：无权访问 query.redirect 时退回默认页
  const resolveLandingRoute = (isAdmin: boolean): string => {
    const defaultRoute = defaultRouteFor(isAdmin)
    const redirect = router.currentRoute.value.query.redirect as string | undefined

    if (!redirect || redirect === defaultRoute) {
      return defaultRoute
    }

    const targetRoute = router.resolve(redirect)
    if (!targetRoute?.meta) {
      return redirect
    }

    if (targetRoute.meta.isAdmin && !isAdmin) {
      return defaultRoute
    }
    if (targetRoute.meta.permission && !userStore.hasPermission(targetRoute.meta.permission)) {
      return defaultRoute
    }
    if (
      targetRoute.meta.permissions?.length &&
      !userStore.hasAnyPermission(targetRoute.meta.permissions)
    ) {
      return defaultRoute
    }

    return redirect
  }

  // 登录
  const login = async (form: LoginForm) => {
    loading.value = true
    try {
      const res = await loginApi({
        username: form.username,
        password: form.password,
      })

      if (res.code === 200) {
        applyLoginSession(res.data)

        rememberedUsername.value = form.rememberMe ? form.username : ''

        message.success(t('auth.loginSuccess'))

        router.push(resolveLandingRoute(res.data.user?.isAdmin === '1'))
        return true
      }

      message.error(res.message || t('auth.loginFailed'))
      return false
    } catch {
      // HTTP 错误已由全局响应拦截器统一提示，避免重复弹窗
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
          router.push(ROUTES.LOGIN)
        }, 500)
        return true
      } else {
        message.error(res.message || t('common.error'))
        return false
      }
    } catch {
      // HTTP 错误已由全局响应拦截器统一提示，避免重复弹窗
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
          router.push(ROUTES.LOGIN)
        }, 500)
        return true
      } else {
        message.error(res.message || t('common.error'))
        return false
      }
    } catch {
      // HTTP 错误已由全局响应拦截器统一提示，避免重复弹窗
      return false
    } finally {
      loading.value = false
    }
  }

  const getRememberedCredentials = () => {
    localStorage.removeItem(STORAGE_REMEMBER_ME)

    return {
      username: rememberedUsername.value,
      rememberMe: !!rememberedUsername.value,
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
        applyLoginSession(res.data)

        message.success(t('auth.loginSuccess'))

        router.push(resolveLandingRoute(res.data.user?.isAdmin === '1'))
        return true
      }

      if (res.code === 201) {
        // 未注册的手机号
        message.warning(res.message)
        router.push(ROUTES.REGISTER)
        return false
      }

      message.error(res.message || t('auth.loginFailed'))
      return false
    } catch {
      // HTTP 错误已由全局响应拦截器统一提示，避免重复弹窗
      return false
    } finally {
      loading.value = false
    }
  }

  const logout = async () => {
    try {
      // 先让服务端注销 Sa-Token 会话，否则旧 token 在服务端仍然有效
      await logoutApi()
    } catch (error) {
      // 服务端注销失败也要把本地登录态清干净
      console.error('logout failed:', error)
    }

    userStore.clearUserInfo()
    userStore.clearToken()
    router.push(ROUTES.LOGIN)
  }

  return {
    loading,
    login,
    telLogin,
    register,
    resetPassword,
    getRememberedCredentials,
    logout,
  }
}
