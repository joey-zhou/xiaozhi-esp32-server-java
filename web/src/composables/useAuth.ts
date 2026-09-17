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
import { resolveAuthNavigation } from '@/router/authNavigation'
import { useRequest } from './useRequest'

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
  // 四条鉴权请求各自一个实例，失败文案互不干扰；
  // 传输层错误统一交给 request.ts 拦截器，这里一律 networkErrorText: null 不再覆盖
  const { executeFull: executeLogin } = useRequest()
  const { executeFull: executeTelLogin } = useRequest()
  const { executeOk: executeRegister } = useRequest()
  const { executeOk: executeResetPassword } = useRequest()

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

  // 计算登录后要落地的路径：把 query.redirect 交给全局唯一的权限判断（resolveAuthNavigation），
  // 没有 redirect、或 redirect 本就是默认页时直接用默认页，省一次路由解析
  const resolveLandingRoute = (isAdmin: boolean): string => {
    const defaultRoute = defaultRouteFor(isAdmin)
    const redirect = router.currentRoute.value.query.redirect as string | undefined

    if (!redirect || redirect === defaultRoute) {
      return defaultRoute
    }

    const targetRoute = router.resolve(redirect)
    const decision = resolveAuthNavigation(
      { path: targetRoute.path, fullPath: targetRoute.fullPath, meta: targetRoute.meta },
      {
        hasToken: true, // 走到这里说明刚登录/手机验证码登录成功，会话已写入
        isAdmin,
        hasPermission: userStore.hasPermission,
        hasAnyPermission: userStore.hasAnyPermission,
      },
    )

    // 允许则原样跳 redirect（保留其自带的 query），否则用统一判断给出的兜底目标
    return decision.action === 'allow' ? redirect : decision.to
  }

  // 登录
  const login = async (form: LoginForm) => {
    const { ok, data } = await executeLogin(
      () => loginApi({ username: form.username, password: form.password }),
      { loadingRef: loading, errorText: t('auth.loginFailed'), networkErrorText: null },
    )

    if (!ok || !data) {
      return false
    }

    applyLoginSession(data)

    rememberedUsername.value = form.rememberMe ? form.username : ''

    message.success(t('auth.loginSuccess'))

    router.push(resolveLandingRoute(data.user?.isAdmin === '1'))
    return true
  }

  // 注册
  const register = async (form: RegisterForm) => {
    const registered = await executeRegister(
      () => registerApi({
        name: form.name,
        username: form.username,
        email: form.email,
        tel: form.tel,
        password: form.password,
        verifyCode: form.verifyCode,
      }),
      { loadingRef: loading, errorText: t('common.error'), networkErrorText: null },
    )

    if (!registered) {
      return false
    }

    message.success(t('auth.registerSuccess'))
    setTimeout(() => {
      router.push(ROUTES.LOGIN)
    }, 500)
    return true
  }

  // 重置密码
  const resetPassword = async (form: ForgetPasswordForm) => {
    const reset = await executeResetPassword(
      () => resetPasswordApi({
        email: form.email,
        code: form.verificationCode,
        password: form.newPassword,
      }),
      { loadingRef: loading, errorText: t('common.error'), networkErrorText: null },
    )

    if (!reset) {
      return false
    }

    message.success(t('auth.passwordReset'))
    setTimeout(() => {
      router.push(ROUTES.LOGIN)
    }, 500)
    return true
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
    const { ok, data } = await executeTelLogin(
      () => telLoginApi({ tel: form.tel, code: form.code }),
      {
        loadingRef: loading,
        errorText: t('auth.loginFailed'),
        networkErrorText: null,
        // 201 是「手机号未注册」，不是错误，引导去注册页而不是弹红字
        onFailure: (res) => {
          if (res.code !== 201) {
            return false
          }
          message.warning(res.message)
          router.push(ROUTES.REGISTER)
          return true
        },
      },
    )

    if (!ok || !data) {
      return false
    }

    applyLoginSession(data)

    message.success(t('auth.loginSuccess'))

    router.push(resolveLandingRoute(data.user?.isAdmin === '1'))
    return true
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
