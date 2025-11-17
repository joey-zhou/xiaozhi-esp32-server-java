<script setup lang="ts">
// 通用页面骨架屏组件
</script>

<template>
  <div class="page-skeleton">
    <!-- 搜索框骨架 -->
    <a-card :bordered="false" class="skeleton-card">
      <a-skeleton-input :active="true" size="large" style="width: 100%; max-width: 400px" />
    </a-card>

    <!-- 表格骨架 -->
    <a-card :bordered="false" class="skeleton-card" style="margin-top: 20px">
      <a-skeleton 
        :active="true" 
        :paragraph="{ rows: 8 }"
        :title="{ width: '30%' }"
      />
    </a-card>
  </div>
</template>

<style scoped lang="scss">
.page-skeleton {
  padding: 24px;
  max-width: 1600px;
  margin: 0 auto;
}

.skeleton-card {
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  :deep(.ant-card-body) {
    padding: 24px;
  }
}

@media (max-width: 768px) {
  .page-skeleton {
    padding: 16px;
  }
}
</style>

