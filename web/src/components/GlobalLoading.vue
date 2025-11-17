<script setup lang="ts">
import { useLoadingStore } from '@/store/loading'

const loadingStore = useLoadingStore()
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="loadingStore.isLoading" class="global-loading">
        <div class="loading-content">
          <a-spin size="large" />
          <div class="loading-text">{{ loadingStore.loadingText }}</div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.global-loading {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
  backdrop-filter: blur(2px);
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.loading-text {
  color: #ffffff;
  font-size: 16px;
  font-weight: 500;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

<!-- 
使用方法：
1. 在 App.vue 中添加：
<template>
  <GlobalLoading />
  <RouterView />
</template>

2. 在任何组件中使用：
<script setup>
import { useLoadingStore } from '@/store/loading'

const loadingStore = useLoadingStore()

const handleSave = async () => {
  loadingStore.showLoading('正在保存...')
  try {
    await saveData()
  } finally {
    loadingStore.hideLoading()
  }
}
</script>
-->
