<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <span class="shimmer">{{ t('chat.thinkingInProgress') }}</span>
</template>

<style scoped>
.shimmer {
  font-size: 13px;
  line-height: 18px;
  font-weight: 500;
  color: transparent;
  -webkit-text-fill-color: transparent;
  background: linear-gradient(
    90deg,
    #a1a1a1 0%,
    #a1a1a1 30%,
    rgba(161, 161, 161, 0.45) 45%,
    rgba(161, 161, 161, 0.45) 55%,
    #a1a1a1 70%,
    #a1a1a1 100%
  );
  background-size: 300% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  animation: label-shine 2.25s cubic-bezier(0.25, 0.1, 0.25, 1) infinite;
}

@keyframes label-shine {
  0%,
  18% {
    background-position: 100% 0;
  }
  82%,
  100% {
    background-position: 0% 0;
  }
}

</style>
