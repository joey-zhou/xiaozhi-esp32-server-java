<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{
  content: string
  streaming?: boolean
}>()

const showIdleCaret = ref(false)
let idleTimer: number | undefined

watch(
  () => props.streaming,
  (streaming, previous) => {
    if (previous && !streaming) {
      showIdleCaret.value = true
      window.clearTimeout(idleTimer)
      idleTimer = window.setTimeout(() => {
        showIdleCaret.value = false
      }, 1600)
    }
  }
)

onBeforeUnmount(() => window.clearTimeout(idleTimer))
</script>

<template>
  <p class="prose" aria-live="polite">
    {{ content
    }}<span
      v-if="streaming || showIdleCaret"
      class="caret"
      :class="{ 'caret-steady': streaming }"
      aria-hidden="true"
    />
  </p>
</template>

<style scoped>
.prose {
  margin: 0;
  font-size: 14px;
  line-height: 19px;
  color: #1a1a1a;
  white-space: pre-wrap;
}

.caret {
  display: inline-block;
  width: 8px;
  height: 1.05em;
  margin-left: 2px;
  background: #0b0d12;
  vertical-align: text-bottom;
  animation: caret-blink 1s step-end infinite;
}

.caret-steady {
  animation: none;
  opacity: 1;
}

@keyframes caret-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .caret {
    animation: none;
  }
}

</style>
