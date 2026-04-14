import type { Router } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ROUTES } from '@/router/routes'
import { cancelPendingRequests } from '@/services/request'
import { i18n } from '@/locales'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

// 配置 NProgress
NProgress.configure({ showSpinner: false, speed: 500 })

// 不需要登录的白名单
const whiteList: string[] = [ROUTES.LOGIN, ROUTES.REGISTER, ROUTES.FORGET]

export function setupRouterGuards(router: Router) {
  // 前置守卫 - 页面跳转前执行
  router.beforeEach((to, from, next) => {
    // 取消上一个页面所有进行中的请求
    cancelPendingRequests()

    // 开始进度条
    NProgress.start()

    // 设置页面标题
    const baseTitle = import.meta.env.VITE_APP_TITLE || 'Connect Ai-智能物联网管理平台'
    if (to.meta.title) {
      // 如果是翻译键，则进行翻译
      const title = to.meta.title.startsWith('router.')
        ? i18n.global.t(to.meta.title)
        : to.meta.title
      document.title = `${title} - ${baseTitle}`
    } else {
      document.title = baseTitle
    }

    const userStore = useUserStore()
    const hasToken = !!userStore.token
    const { isAdmin } = userStore

    // 1. 未登录处理
    if (!hasToken) {
      if (whiteList.includes(to.path)) {
        // 在白名单中，直接访问
        next()
      } else {
        // 不在白名单中，跳转到登录页
        next(`${ROUTES.LOGIN}?redirect=${to.path}`)
        NProgress.done()
      }
      return
    }

    // 2. 已登录处理
    if (to.path === ROUTES.LOGIN) {
      // 如果已登录，访问登录页则跳转到首页
      next({ path: ROUTES.DASHBOARD })
      NProgress.done()
      return
    }

    // 2.1 处理根路径重定向（根据用户类型跳转到不同首页）
    if (to.path === '/') {
      const defaultPath = isAdmin ? ROUTES.DASHBOARD : ROUTES.DEVICE
      next({ path: defaultPath })
      NProgress.done()
      return
    }

    // 3. 权限检查
    if (to.meta.requiresAuth) {
      // 检查是否需要管理员权限
      if (to.meta.isAdmin && !isAdmin) {
        console.warn(`用户无权限访问: ${to.path}`)
        next(ROUTES.ERROR_403)
        NProgress.done()
        return
      }

      // 检查特定权限
      if (to.meta.permission) {
        const hasPermission = userStore.hasPermission(to.meta.permission)
        if (!hasPermission) {
          console.warn(`用户无权限访问: ${to.path}, 需要权限: ${to.meta.permission}`)
          next(ROUTES.ERROR_403)
          NProgress.done()
          return
        }
      }

      // 检查多个权限（任一即可）
      if (to.meta.permissions && to.meta.permissions.length > 0) {
        const hasAnyPermission = userStore.hasAnyPermission(to.meta.permissions)
        if (!hasAnyPermission) {
          console.warn(`用户无权限访问: ${to.path}, 需要权限之一: ${to.meta.permissions.join(', ')}`)
          next(ROUTES.ERROR_403)
          NProgress.done()
          return
        }
      }
    }

    // 4. 放行
    next()
  })

  // 后置守卫 - 页面跳转后执行
  router.afterEach(() => {
    // 结束进度条
    NProgress.done()
  })

  // 错误处理
  router.onError((error) => {
    console.error('路由错误:', error)
    NProgress.done()

    // 检测动态导入失败（chunk 加载失败）
    if (
      error.message?.includes('Failed to fetch dynamically imported module') ||
      error.message?.includes('Importing a module script failed') ||
      (error.message?.includes('Failed to fetch') && error.message?.match(/\.js/))
    ) {
      console.warn('路由模块加载失败，页面版本可能已更新，即将刷新页面')

      // 延迟一小段时间后刷新，避免立即刷新造成的闪烁
      setTimeout(() => {
        window.location.reload()
      }, 100)
    }
  })
}

// 使用示例：
// 在 main.ts 中：
// import { setupRouterGuards } from './router/guards'
// setupRouterGuards(router)
