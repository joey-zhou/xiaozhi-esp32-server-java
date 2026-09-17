<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
import { ROUTES } from '@/router/routes'
import FloatingChat from '@/components/FloatingChat.vue'

const router = useRouter()
const route = useRoute()

// 悬浮聊天是给非对话页准备的快捷入口，对话页本身已有完整会话界面，再浮一个是重复
const showFloatingChat = computed(() => route.name !== 'chat')
const userStore = useUserStore()

// 侧边栏宽度控制。宽度由折叠态推导，手动点折叠按钮时占位块才会跟着变
const SIDEBAR_WIDTH = 200
const SIDEBAR_COLLAPSED_WIDTH = 80
const isCollapsed = ref(false)
const sidebarWidth = computed(() => (isCollapsed.value ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_WIDTH))

// 用户信息
const userInfo = computed(() => userStore.userInfo)

// 供内容区固定定位元素避开侧边栏
const contentOffset = computed(() => `${sidebarWidth.value}px`)

/**
 * 处理断点变化（响应式布局）
 */
function handleBreakpoint(broken: boolean) {
  isCollapsed.value = broken
}

onMounted(() => {
  // 检查登录状态
  if (!userInfo.value) {
    router.push(ROUTES.LOGIN)
  }
})
</script>

<template>
  <div class="main-layout" :style="{ '--app-content-offset': contentOffset }">
    <a-layout>
      <!-- 占位 Sider - 用于保持内容区域位置 -->
      <div
        class="sider-placeholder"
        :style="{
          width: `${sidebarWidth}px`,
          flex: `0 0 ${sidebarWidth}px`,
          maxWidth: `${sidebarWidth}px`,
          minWidth: `${sidebarWidth}px`,
        }"
      />

      <!-- 固定侧边栏 -->
      <a-layout-sider
        v-model:collapsed="isCollapsed"
        theme="light"
        breakpoint="lg"
        :collapsed-width="SIDEBAR_COLLAPSED_WIDTH"
        :width="SIDEBAR_WIDTH"
        @breakpoint="handleBreakpoint"
        collapsible
        class="fixed-sidebar"
      >
        <AppSidebar :collapsed="isCollapsed" />
      </a-layout-sider>

      <!-- 主内容区域 -->
      <a-layout class="main-content-layout">
        <!-- 顶部栏 -->
        <a-layout-header class="layout-header">
          <AppHeader />
        </a-layout-header>

        <!-- 内容区 -->
        <a-layout-content class="layout-content">
          <!-- 按 fullPath 打 key，同一路由换查询参数时也重新挂载 -->
          <router-view v-slot="{ Component }">
            <component :is="Component" :key="$route.fullPath" />
          </router-view>
        </a-layout-content>

        <!-- 页脚 -->
        <a-layout-footer class="layout-footer">
          <AppFooter />
        </a-layout-footer>
      </a-layout>
    </a-layout>

    <!-- 浮动聊天组件 -->
    <FloatingChat v-if="showFloatingChat" />
  </div>
</template>

<style scoped lang="scss">
.main-layout {
  width: 100%;
  min-height: 100vh;
}

.sider-placeholder {
  overflow: hidden;
  transition: all 0.2s;
}

.fixed-sidebar {
  height: 100vh;
  z-index: 99;
  position: fixed;
  left: 0;
  overflow: auto;

  :deep(.ant-layout-sider-children) {
    display: flex;
    flex-direction: column;
  }
}

.main-content-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.layout-header {
  padding: 0;
  height: auto;
  line-height: normal;
  background: var(--ant-color-bg-container);
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03),
      0 1px 6px -1px rgba(0, 0, 0, 0.02),
      0 2px 4px 0 rgba(0, 0, 0, 0.02);
  flex-shrink: 0;
}

.layout-content {
  flex: 1;
  position: relative;
}


.layout-footer {
  padding: 0;
  background: transparent;
  flex-shrink: 0;
}
</style>

