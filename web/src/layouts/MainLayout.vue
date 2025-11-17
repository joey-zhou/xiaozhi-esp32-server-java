<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/store/user'
import { useRouter } from 'vue-router'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
import FloatingChat from '@/components/FloatingChat.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'

const router = useRouter()
const userStore = useUserStore()

// 侧边栏宽度控制
const sidebarWidth = ref(200)
const isCollapsed = ref(false)

// 客户端宽度
const clientWidth = ref(document.body.clientWidth)

// 是否是移动端
const isMobile = computed(() => clientWidth.value < 768)

// 用户信息
const userInfo = computed(() => userStore.userInfo)

/**
 * 处理窗口大小变化
 */
function handleResize() {
  clientWidth.value = document.body.clientWidth
  userStore.setMobileType(isMobile.value)
}

/**
 * 处理断点变化（响应式布局）
 */
function handleBreakpoint(broken: boolean) {
  if (broken) {
    // 小屏幕 - 自动折叠侧边栏
    sidebarWidth.value = 80
    isCollapsed.value = true
  } else {
    // 大屏幕 - 展开侧边栏
    sidebarWidth.value = 200
    isCollapsed.value = false
  }
}

onMounted(() => {
  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
  handleResize()
  
  // 检查登录状态
  if (!userInfo.value) {
    router.push('/login')
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="main-layout">
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
        :collapsed-width="80"
        :width="200"
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
        <router-view v-slot="{ Component }">
          <Suspense>
            <template #default>
              <component :is="Component" :key="$route.fullPath" />
            </template>
            <template #fallback>
              <PageSkeleton />
            </template>
          </Suspense>
        </router-view>
        </a-layout-content>

        <!-- 页脚 -->
        <a-layout-footer class="layout-footer">
          <AppFooter />
        </a-layout-footer>
      </a-layout>
    </a-layout>

    <!-- 浮动聊天组件 -->
    <FloatingChat />
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
}


.layout-footer {
  padding: 0;
  background: transparent;
  flex-shrink: 0;
}
</style>

