<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="home-container">
    <a-card title="欢迎使用小智 ESP32 管理系统" :bordered="false">
      <p>这是首页，您可以在这里查看系统概览。</p>
      <a-space>
        <a-button type="primary" @click="router.push('/dashboard')">前往仪表板</a-button>
        <a-button @click="router.push('/about')">关于系统</a-button>
      </a-space>
    </a-card>
  </div>
</template>

<style scoped>
.home-container {
  padding: 24px;
}
</style>