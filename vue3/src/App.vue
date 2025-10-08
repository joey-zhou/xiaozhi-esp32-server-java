<script setup lang="ts">
import { RouterView } from 'vue-router'
import GlobalLoading from './components/GlobalLoading.vue'
import ErrorBoundary from './components/ErrorBoundary.vue'
import { useLocale } from './composables/useLocale'
import { useAntdTheme } from './composables/useAntdTheme'

const { antdLocale } = useLocale()
const { antdTheme } = useAntdTheme()
</script>

<template>
  <a-config-provider :locale="antdLocale" :theme="antdTheme">
    <div id="app">
      <!-- 全局 Loading 组件 -->
      <GlobalLoading />
      
      <!-- 错误边界 -->
      <ErrorBoundary>
        <!-- 路由视图 -->
        <RouterView />
      </ErrorBoundary>
    </div>
  </a-config-provider>
</template>

<style>
#app {
  width: 100%;
  height: 100%;
}
</style>
