import './assets/main.css'
import './assets/theme.css'
import 'ant-design-vue/dist/reset.css'
import 'nprogress/nprogress.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'

import App from './App.vue'
import router from './router'
import { setupRouterGuards } from './router/guards'
import { setupErrorHandler } from './utils/errorHandler'
import { i18n } from './locales'

const app = createApp(App)

// 1. 设置全局错误处理
setupErrorHandler(app)

// 2. 使用插件
app.use(createPinia())
app.use(router)
app.use(Antd)
app.use(i18n)

// 3. 设置路由守卫（登录验证、页面标题、进度条）
setupRouterGuards(router)

app.mount('#app')
