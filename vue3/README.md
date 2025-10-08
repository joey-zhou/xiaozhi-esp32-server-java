# 小智 ESP32 管理系统 - Vue 3

基于 Vue 3 + TypeScript + Vite 的现代化管理系统前端，已完成从 Vue 2 到 Vue 3 的全面迁移。

## 技术栈

- **Vue 3.4+** - 渐进式 JavaScript 框架（Composition API）
- **TypeScript 5.x** - JavaScript 的超集，提供类型安全
- **Vite 5.x** - 下一代前端构建工具
- **Ant Design Vue 4.x** - 企业级 UI 组件库
- **Pinia** - Vue 3 官方状态管理库
- **Vue Router 4.x** - Vue 官方路由管理器
- **VueUse** - Vue Composition API 工具集
- **Day.js** - 轻量级日期处理库
- **SCSS** - CSS 预处理器
- **Web Audio API** - 音频处理
- **WebSocket** - 实时通信

## 项目特性

### ✅ 核心特性

- **Vue 3 Composition API** - 全面使用 `<script setup>` 语法
- **TypeScript** - 完整的类型定义和类型安全
- **Async Setup + Suspense** - 页面级异步数据加载
- **服务化架构** - 统一的 Service 层管理 API 调用
- **响应式主题** - 支持浅色/深色主题切换
- **响应式布局** - 桌面端/移动端自适应

### 🎯 业务功能

- **用户认证系统** - 登录/注册/忘记密码，记住我，自动登录
- **仪表盘** - 用户信息、设备列表、聊天记录、每日一句
- **用户管理** - 用户列表、搜索、导出（CRUD 操作）
- **设备管理** - 设备列表、绑定、编辑、删除、清除记忆
- **对话管理** - 消息记录、搜索、音频播放
- **角色配置** - AI 角色管理、模型配置、音色配置、VAD 设置
- **智能体管理** - Agent 配置、工具管理、系统提示词
- **模板管理** - 提示词模板、分类管理、默认模板设置
- **个人设置** - 账号信息、密码修改、头像上传
- **浮动聊天** - WebSocket 实时聊天、语音对话、设置配置

### 🔧 技术亮点

- **统一的 Composables** - `useTable`、`useAuth`、`useAvatar`、`useWebSocket` 等
- **全局错误处理** - 统一的错误捕获和提示
- **全局 Loading** - 统一的加载状态管理
- **路由守卫** - 登录验证、页面标题、进度条
- **骨架屏** - 页面加载时的友好占位
- **防抖搜索** - 自动搜索优化
- **音频处理** - Opus 编解码、实时播放
- **深色模式** - CSS 变量驱动的主题系统
  kilopppppppppppppppppp0-99999999999
## 项目结构

```
vue3/
├── public/                  # 公共静态资源
│   └── libopus.js          # Opus 音频编解码库
├── src/
│   ├── assets/             # 静态资源
│   │   └── styles/         # 全局样式
│   ├── components/         # 全局组件
│   │   ├── layout/         # 布局组件
│   │   │   ├── AppSidebar.vue      # 侧边栏
│   │   │   ├── AppHeader.vue       # 顶部栏
│   │   │   └── AppFooter.vue       # 页脚
│   │   ├── FloatingChat.vue        # 浮动聊天
│   │   ├── PageSkeleton.vue        # 页面骨架屏
│   │   ├── GlobalLoading.vue       # 全局 Loading
│   │   └── DeviceEditDialog.vue    # 设备编辑对话框
│   ├── composables/        # 组合式函数
│   │   ├── useAuth.ts              # 认证逻辑
│   │   ├── useTable.ts             # 表格逻辑（分页、搜索）
│   │   ├── useMenu.ts              # 菜单逻辑
│   │   ├── useAvatar.ts            # 头像处理
│   │   ├── useAntdTheme.ts         # Ant Design 主题
│   │   ├── useWebSocket.ts         # WebSocket 封装
│   │   ├── useFormValidation.ts    # 表单验证规则
│   │   └── useVerificationCode.ts  # 验证码逻辑
│   ├── layouts/            # 页面布局
│   │   └── MainLayout.vue          # 主布局
│   ├── router/             # 路由配置
│   │   ├── index.ts                # 路由定义
│   │   └── guards.ts               # 路由守卫
│   ├── services/           # API 服务层
│   │   ├── api.ts                  # API 端点定义
│   │   ├── request.ts              # Axios 封装
│   │   ├── user.ts                 # 用户服务
│   │   ├── device.ts               # 设备服务
│   │   ├── message.ts              # 消息服务
│   │   ├── role.ts                 # 角色服务
│   │   ├── agent.ts                # 智能体服务
│   │   ├── template.ts             # 模板服务
│   │   ├── config.ts               # 配置服务
│   │   ├── websocket.ts            # WebSocket 服务
│   │   └── audio.ts                # 音频服务
│   ├── store/              # Pinia 状态管理
│   │   ├── loading.ts              # 全局 Loading 状态
│   │   └── user.ts                 # 用户状态（含 WebSocket 配置）
│   ├── types/              # TypeScript 类型定义
│   │   ├── user.ts                 # 用户类型
│   │   ├── device.ts               # 设备类型
│   │   ├── message.ts              # 消息类型
│   │   ├── role.ts                 # 角色类型
│   │   ├── agent.ts                # 智能体类型
│   │   ├── template.ts             # 模板类型
│   │   └── config.ts               # 配置类型
│   ├── utils/              # 工具函数
│   │   └── errorHandler.ts        # 全局错误处理
│   ├── views/              # 页面组件
│   │   ├── LoginView.vue           # 登录页
│   │   ├── RegisterView.vue        # 注册页
│   │   ├── ForgetView.vue          # 忘记密码页
│   │   ├── DashboardView.vue       # 仪表板
│   │   ├── UserView.vue            # 用户管理
│   │   ├── DeviceView.vue          # 设备管理
│   │   ├── MessageView.vue         # 对话管理
│   │   ├── RoleView.vue            # 角色配置
│   │   ├── TemplateView.vue        # 模板管理
│   │   ├── config/
│   │   │   └── AgentView.vue       # 智能体管理
│   │   └── setting/
│   │       └── AccountView.vue     # 个人设置
│   ├── App.vue             # 根组件
│   └── main.ts             # 入口文件
├── .env                    # 环境变量
├── vite.config.ts          # Vite 配置
├── tsconfig.json           # TypeScript 配置
└── package.json            # 项目依赖
```

## 快速开始

### 环境要求

- Node.js >= 18
- Bun >= 1.0 (推荐) 或 npm/yarn/pnpm

### 安装依赖

```bash
bun install
```

### 开发模式

```bash
bun run dev
```

访问: http://localhost:8084

### 构建生产版本

```bash
bun run build
```

### 预览生产版本

```bash
bun run preview
```

### 代码检查

```bash
bun run lint
```

### 代码格式化

```bash
bun run format
```

### 类型检查

```bash
bun run type-check
```

## 开发指南

### IDE 配置

推荐使用 [VS Code](https://code.visualstudio.com/) + 以下插件：

- [Vue - Official (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.volar)
- [TypeScript Vue Plugin](https://marketplace.visualstudio.com/items?itemName=Vue.vscode-typescript-vue-plugin)
- [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint)
- [Prettier](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode)

### 浏览器扩展

- Chrome/Edge: [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
- Firefox: [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)

### 编码规范

#### 1. 组件开发

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import type { User } from '@/types/user'

// 使用 TypeScript 类型注解
const user = ref<User | null>(null)

// 使用 computed 而不是方法
const fullName = computed(() => {
  return `${user.value?.firstName} ${user.value?.lastName}`
})
</script>

<template>
  <div>{{ fullName }}</div>
</template>

<style scoped lang="scss">
// 使用 scoped 样式
</style>
```

#### 2. 异步数据加载

**页面组件使用顶层 await：**

```typescript
// ✅ 正确 - 页面组件
<script setup lang="ts">
import { queryUsers } from '@/services/user'

// 顶层 await，配合 Suspense
await fetchData()

async function fetchData() {
  const res = await queryUsers({ start: 1, limit: 10 })
  // ...
}
</script>
```

**子组件使用 onMounted：**

```typescript
// ✅ 正确 - 子组件
<script setup lang="ts">
import { onMounted } from 'vue'

onMounted(async () => {
  await initComponent()
})
</script>
```

#### 3. API 调用

**统一使用 Service 层：**

```typescript
// ❌ 错误 - 直接调用 http
import { http } from '@/services/request'
const res = await http.get('/api/users')

// ✅ 正确 - 通过 Service 层
import { queryUsers } from '@/services/user'
const res = await queryUsers({ start: 1, limit: 10 })
```

#### 4. 状态管理

```typescript
// ✅ 使用 Pinia + VueUse
import { useUserStore } from '@/store/user'
import { useStorage } from '@vueuse/core'

const userStore = useUserStore()
const token = useStorage('token', '', localStorage)
```

#### 5. 类型定义

```typescript
// ✅ 统一在 types 目录定义类型
export interface User {
  userId: string
  username: string
  name: string
  email?: string
  avatar?: string
}

// ✅ 使用 type 而不是 interface（联合类型等场景）
export type MessageSender = 'user' | 'assistant'
```

## 环境变量

创建 `.env.local` 文件配置本地环境变量：

```env
# API 基础 URL
VITE_API_BASE_URL=http://localhost:8091

# WebSocket 地址
VITE_WS_URL=ws://localhost:8091/ws/xiaozhi/v1/
```

## 代理配置

开发环境下，`/api` 请求会被代理到后端服务器（默认 `http://localhost:8091`）。

修改 `vite.config.ts` 可以调整代理配置：

```typescript
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8091',
        changeOrigin: true
      }
    }
  }
})
```

## 核心功能详解

### 用户认证

- 登录/注册/忘记密码
- 记住我功能（加密存储到 localStorage）
- 邮箱验证码发送和验证
- 自动登录状态保持
- 密码强度检测

### 仪表盘

- 用户信息展示（头像、统计数据）
- 每日一句（中英文切换）
- 设备列表（分页、排序）
- 聊天记录（消息流）

### 数据管理

- **用户管理**：CRUD、搜索、导出
- **设备管理**：绑定、编辑、删除、清除记忆
- **对话管理**：消息列表、音频播放
- **角色配置**：AI 角色、模型选择、音色配置
- **智能体管理**：Agent 配置、工具管理
- **模板管理**：提示词模板、分类管理

### 浮动聊天

- WebSocket 实时通信
- 文本消息发送
- 语音对话（Opus 编解码）
- 连接状态显示
- 内置设置抽屉（WebSocket 地址、设备 ID）

### 主题系统

- 浅色/深色主题
- CSS 变量驱动
- 主题切换动画
- 持久化保存

## 迁移说明

### 从 Vue 2 迁移的主要变化

1. **Options API → Composition API**
   - 所有组件使用 `<script setup>`
   - `data/methods/computed` → `ref/function/computed`

2. **Vuex → Pinia**
   - 更简洁的状态管理
   - 完整的 TypeScript 支持

3. **Moment.js → Day.js**
   - 更轻量的日期库
   - API 兼容

4. **服务化架构**
   - 统一的 Service 层
   - 完整的类型定义

5. **异步数据加载**
   - 页面级：顶层 `await` + Suspense
   - 组件级：`onMounted`

## 性能优化

- **路由懒加载** - 所有页面组件按需加载
- **Suspense** - 优雅的异步加载体验
- **防抖搜索** - 减少不必要的 API 调用
- **虚拟滚动** - 大数据列表优化（计划中）
- **图片懒加载** - 图片按需加载
- **代码分割** - Vite 自动分割

## 浏览器支持

- Chrome >= 90
- Firefox >= 90
- Safari >= 14
- Edge >= 90

## 参考文档

- [Vue 3 文档](https://cn.vuejs.org/)
- [Vite 文档](https://cn.vitejs.dev/)
- [Ant Design Vue 文档](https://antdv.com/)
- [Pinia 文档](https://pinia.vuejs.org/zh/)
- [VueUse 文档](https://vueuse.org/)
- [TypeScript 文档](https://www.typescriptlang.org/zh/)

## 待办事项

- [ ] 单元测试（Vitest）
- [ ] E2E 测试（Playwright）
- [ ] 国际化（i18n）
- [ ] PWA 支持
- [ ] 性能监控
- [ ] 错误上报

## 更新日志

### v2.0.0 (2025-01)

- ✅ 完成 Vue 2 到 Vue 3 的全面迁移
- ✅ 重构为 TypeScript + Composition API
- ✅ 实现统一的 Service 层架构
- ✅ 完成所有页面组件迁移
- ✅ 集成 WebSocket 实时通信
- ✅ 实现浮动聊天功能
- ✅ 支持深色模式
- ✅ 优化移动端适配

## License

MIT

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

## 联系方式

如有问题，请通过 Issue 反馈。
