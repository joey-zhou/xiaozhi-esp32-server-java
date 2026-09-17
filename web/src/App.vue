<script setup lang="ts">
import { watch } from 'vue'
import { RouterView } from 'vue-router'
import dayjs from 'dayjs'
import GlobalLoading from './components/GlobalLoading.vue'
import ErrorBoundary from './components/ErrorBoundary.vue'
import { useLocale } from './composables/useLocale'
import { useAntdTheme } from './composables/useAntdTheme'

const { antdLocale, currentLocale } = useLocale()
const { antdTheme } = useAntdTheme()

// 把界面语言同步给 dayjs，日期选择器的星期行/月份名与每周首日跟随语言切换
watch(
  currentLocale,
  (locale) => {
    dayjs.locale(locale === 'zh-CN' ? 'zh-cn' : 'en')
  },
  { immediate: true }
)
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
