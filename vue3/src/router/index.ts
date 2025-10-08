import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'

// 扩展 RouteMeta 类型
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    requiresAuth?: boolean
    isAdmin?: boolean
    parent?: string
    hideInMenu?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: {
      title: 'router.title.login',
      requiresAuth: false,
    },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: {
      title: 'router.title.register',
      requiresAuth: false,
    },
  },
  {
    path: '/forget',
    name: 'forget',
    component: () => import('../views/ForgetView.vue'),
    meta: {
      title: 'router.title.forget',
      requiresAuth: false,
    },
  },

  // 主应用路由
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: {
          title: 'router.title.dashboard',
          icon: 'DashboardOutlined',
          requiresAuth: true,
        },
      },
      {
        path: 'user',
        name: 'user',
        component: () => import('../views/UserView.vue'),
        meta: {
          title: 'router.title.user',
          icon: 'TeamOutlined',
          requiresAuth: true,
          isAdmin: true,
        },
      },
      {
        path: 'device',
        name: 'device',
        component: () => import('../views/DeviceView.vue'),
        meta: {
          title: 'router.title.device',
          icon: 'RobotOutlined',
          requiresAuth: true,
        },
      },
      {
        path: 'message',
        name: 'message',
        component: () => import('../views/MessageView.vue'),
        meta: {
          title: 'router.title.message',
          icon: 'MessageOutlined',
          requiresAuth: true,
        },
      },
      {
        path: 'role',
        name: 'role',
        component: () => import('../views/RoleView.vue'),
        meta: {
          title: 'router.title.role',
          icon: 'UserAddOutlined',
          requiresAuth: true,
        },
      },
      {
        path: 'template',
        name: 'template',
        component: () => import('../views/TemplateView.vue'),
        meta: {
          title: 'router.title.template',
          icon: 'SnippetsOutlined',
          parent: 'router.parent.roleManagement',
          requiresAuth: true,
          isAdmin: true,
          hideInMenu: true
        },
      },
      // 配置管理
      {
        path: 'config/model',
        name: 'config-model',
        component: () => import('../views/config/ModelConfigView.vue'),
        meta: {
          title: 'router.title.modelConfig',
          parent: 'router.parent.configManagement',
          requiresAuth: true,
          isAdmin: true,
        },
      },
      {
        path: 'config/agent',
        name: 'config-agent',
        component: () => import('../views/config/AgentView.vue'),
        meta: {
          title: 'router.title.agent',
          parent: 'router.parent.configManagement',
          requiresAuth: true,
          isAdmin: true,
        },
      },
      {
        path: 'config/stt',
        name: 'config-stt',
        component: () => import('../views/config/SttConfigView.vue'),
        meta: {
          title: 'router.title.sttConfig',
          parent: 'router.parent.configManagement',
          requiresAuth: true,
          isAdmin: true,
        },
      },
      {
        path: 'config/tts',
        name: 'config-tts',
        component: () => import('../views/config/TtsConfigView.vue'),
        meta: {
          title: 'router.title.ttsConfig',
          parent: 'router.parent.configManagement',
          requiresAuth: true,
          isAdmin: true,
        },
      },
      // 个人设置
      {
        path: 'setting/account',
        name: 'setting-account',
        component: () => import('../views/setting/AccountView.vue'),
        meta: {
          title: 'router.title.account',
          parent: 'router.parent.settings',
          requiresAuth: true,
        },
      },
      {
        path: 'setting/config',
        name: 'setting-config',
        component: () => import('../views/setting/ConfigView.vue'),
        meta: {
          title: 'router.title.personalConfig',
          parent: 'router.parent.settings',
          requiresAuth: true,
        },
      },
    ],
  },

  // 异常页面
  {
    path: '/403',
    name: '403',
    component: () => import('../views/exception/403.vue'),
    meta: {
      title: 'router.title.error403',
      requiresAuth: false,
    },
  },
  {
    path: '/404',
    name: '404',
    component: () => import('../views/exception/404.vue'),
    meta: {
      title: 'router.title.error404',
      requiresAuth: false,
    },
  },

  // 捕获所有未匹配的路由
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
