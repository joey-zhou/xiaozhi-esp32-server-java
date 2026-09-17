import { watch } from 'vue'
import type { RouteLocationNormalized, Router } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import { ROUTES, defaultRouteFor } from '@/router/routes'
import { cancelPendingRequests, isRequestCanceledError } from '@/services/request'
import { checkToken } from '@/services/user'
import { i18n } from '@/locales'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

// 配置 NProgress
NProgress.configure({ showSpinner: false, speed: 500 })

// 不需要登录的白名单
const whiteList: string[] = [ROUTES.LOGIN, ROUTES.REGISTER, ROUTES.FORGET]

// chunk 加载失败自动刷新的熔断标记，两次刷新间隔小于该值就不再刷新
const CHUNK_RELOAD_KEY = 'chunk-reload-at'
const CHUNK_RELOAD_INTERVAL = 10000

// 本次会话是否已用 check-token 同步过权限树
let permissionsSynced = false

// 用后端实时权限覆盖本地缓存，不阻塞导航；失败时沿用 localStorage 里的旧值
function syncPermissions(userStore: ReturnType<typeof useUserStore>) {
  if (permissionsSynced) {
    return
  }
  permissionsSynced = true
  checkToken()
    .then((res) => {
      if (res.code !== 200 || !res.data) {
        return
      }
      userStore.setUserInfo(res.data.user)
      userStore.setPermissions(res.data.permissions)
      userStore.setAuthRole(res.data.authRole)
    })
    .catch((error) => {
      // 导航切换会取消在途请求，这种情况下次导航再同步一次
      if (isRequestCanceledError(error)) {
        permissionsSynced = false
      }
      // 其他失败按本地缓存放行，token 真失效时由响应拦截器统一登出
    })
}

// 写标签页标题：路由标题 + 应用名，两段都可能是翻译键
function applyDocumentTitle(route: RouteLocationNormalized) {
  const baseTitle = import.meta.env.VITE_APP_TITLE || i18n.global.t('common.appTitle')
  const routeTitle = route.meta?.title
  if (routeTitle) {
    const title = routeTitle.startsWith('router.') ? i18n.global.t(routeTitle) : routeTitle
    document.title = `${title} - ${baseTitle}`
  } else {
    document.title = baseTitle
  }
}

export function setupRouterGuards(router: Router) {
  // 切换语言后按当前路由重写标题，否则标签页会一直停在旧语言
  watch(i18n.global.locale, () => applyDocumentTitle(router.currentRoute.value))

  // 前置守卫 - 页面跳转前执行
  router.beforeEach((to, _from, next) => {
    // 取消上一个页面所有进行中的请求
    cancelPendingRequests()

    // 开始进度条
    NProgress.start()

    // 设置页面标题
    applyDocumentTitle(to)

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
        next(`${ROUTES.LOGIN}?redirect=${encodeURIComponent(to.fullPath)}`)
        NProgress.done()
      }
      return
    }

    // 2. 已登录处理
    syncPermissions(userStore)

    if (to.path === ROUTES.LOGIN) {
      // 如果已登录，访问登录页则跳转到默认落地页
      next({ path: defaultRouteFor(isAdmin) })
      NProgress.done()
      return
    }

    // 2.1 处理根路径重定向（根据用户类型跳转到不同首页）
    if (to.path === '/') {
      next({ path: defaultRouteFor(isAdmin) })
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
    // 导航成功说明 chunk 已可正常加载，清掉熔断标记
    sessionStorage.removeItem(CHUNK_RELOAD_KEY)
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
      const lastReloadAt = Number(sessionStorage.getItem(CHUNK_RELOAD_KEY) || 0)
      // 刚刷新过还是失败，说明不是版本更新导致的，停止自动刷新避免死循环
      if (Date.now() - lastReloadAt < CHUNK_RELOAD_INTERVAL) {
        message.error(i18n.global.t('error.chunkLoadFailed'))
        return
      }

      console.warn('路由模块加载失败，页面版本可能已更新，即将刷新页面')
      sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now()))

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
