<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="about-container">
    <a-card title="关于小智 ESP32 管理系统" :bordered="false">
      <a-descriptions :column="1" bordered>
        <a-descriptions-item label="系统名称">小智 ESP32 管理系统</a-descriptions-item>
        <a-descriptions-item label="版本">Vue 3.0</a-descriptions-item>
        <a-descriptions-item label="技术栈">
          Vue 3 + TypeScript + Vite + Ant Design Vue 4 + Pinia + VueUse
        </a-descriptions-item>
        <a-descriptions-item label="开发框架">
          <a-tag color="blue">Vue 3 Composition API</a-tag>
          <a-tag color="green">TypeScript</a-tag>
          <a-tag color="orange">Vite</a-tag>
        </a-descriptions-item>
      </a-descriptions>

      <a-divider />

      <a-space direction="vertical" :size="16" style="width: 100%">
        <a-alert
          message="现代化架构"
          description="采用 Vue 3 最新特性，使用 Composition API 和 TypeScript 提供更好的开发体验"
          type="success"
          show-icon
        />
        <a-alert
          message="VueUse 集成"
          description="集成 VueUse 工具库，提供响应式存储、防抖节流等实用功能"
          type="info"
          show-icon
        />
      </a-space>

      <div style="margin-top: 24px">
        <a-button type="primary" @click="router.push('/dashboard')">返回仪表板</a-button>
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.about-container {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}
</style>