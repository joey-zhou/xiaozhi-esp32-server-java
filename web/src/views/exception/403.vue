<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

function goBack() {
  router.back()
}

function goHome() {
  // 清除用户信息和token，避免因权限不足导致的循环跳转
  userStore.clearUserInfo()
  userStore.clearToken()
  router.push('/login')
}
</script>

<template>
  <div class="exception-page">
    <a-result
      status="403"
      title="403"
      sub-title="抱歉，您没有权限访问此页面。"
    >
      <template #extra>
        <a-space>
          <a-button type="primary" @click="() => goHome()">返回首页</a-button>
          <a-button @click="() => goBack()">返回上一页</a-button>
        </a-space>
      </template>
    </a-result>
  </div>
</template>

<style scoped lang="scss">
.exception-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f0f2f5;
}
</style>

