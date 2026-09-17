<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import { defaultRouteFor } from '@/router/routes'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

function goBack() {
  router.back()
}

function goHome() {
  router.replace(defaultRouteFor(userStore.isAdmin))
}
</script>

<template>
  <div class="exception-page">
    <a-result
      status="404"
      title="404"
      :sub-title="t('error.pageNotFound')"
    >
      <template #extra>
        <a-space>
          <a-button type="primary" @click="() => goHome()">{{ t('common.backHome') }}</a-button>
          <a-button @click="() => goBack()">{{ t('common.backPrevious') }}</a-button>
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
  background: var(--ant-color-fill-quaternary, #f0f2f5);
}
</style>
