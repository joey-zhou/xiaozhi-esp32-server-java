<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goBack() {
  router.back()
}

function goHome() {
  router.push('/dashboard')
}
</script>

<template>
  <div class="exception-page">
    <a-result
      status="404"
      title="404"
      sub-title="抱歉，您访问的页面不存在。"
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

