import './assets/main.css'
import './assets/theme.css'
import 'ant-design-vue/dist/reset.css'
import 'nprogress/nprogress.css'
// dayjs 中文语言包，日期选择器的星期行与月份名需要它；en 是 dayjs 内置默认语言
import 'dayjs/locale/zh-cn'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd, { message } from 'ant-design-vue'

import App from './App.vue'
import router from './router'
import { setupRouterGuards } from './router/guards'
import { setupErrorHandler } from './utils/errorHandler'
import { setupDirectives } from './directives'
import { i18n } from './locales'

// 断网提示走固定 key，联网后按 key 收掉
const OFFLINE_MESSAGE_KEY = 'network-offline'

function showOfflineNotice() {
  message.error({
    content: i18n.global.t('error.networkError'),
    key: OFFLINE_MESSAGE_KEY,
    duration: 0,
  })
}

const app = createApp(App)

// 1. 设置全局错误处理
setupErrorHandler(app)

// 1.1 一次操作常会连带失败多个并发请求，不封顶提示条数会把整屏堆满
message.config({ maxCount: 3 })

// 1.2 断网期间每个请求都会各报一条网络错误，用一条常驻提示替代，让用户看得出根因
window.addEventListener('offline', showOfflineNotice)
window.addEventListener('online', () => message.destroy(OFFLINE_MESSAGE_KEY))
if (!navigator.onLine) {
  showOfflineNotice()
}

// 2. 使用插件
app.use(createPinia())
app.use(router)
app.use(Antd)
app.use(i18n)

// 2.1 注册自定义指令
setupDirectives(app)

// 3. 设置路由守卫（登录验证、页面标题、进度条）
setupRouterGuards(router)

app.mount('#app')
