<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  content: string
  done?: boolean
  expanded?: boolean
  durationMs?: number
}>()

defineEmits<{
  (e: 'toggle'): void
}>()

const { t } = useI18n()
const viewportRef = ref<HTMLDivElement>()
const fade = reactive({ top: false, bottom: true })

const SENT_H = 40
const GAP = 4
const MAX_H = 180
const FADE = 16

const elapsedSeconds = computed(() => Math.max(1, Math.round((props.durationMs || 0) / 1000)))
const isExpanded = computed(() => (props.done ? Boolean(props.expanded) : true))
const sentences = computed(() => {
  const matches = props.content
    .replace(/\r/g, '')
    .match(/[^。！？.!?\n]+[。！？.!?]?|[^\n]+/g)
    ?.map((line) => line.trim())
    .filter(Boolean)
  return matches?.length ? matches : [props.content]
})
const contentH = computed(() => {
  const count = sentences.value.length
  return count > 0 ? count * SENT_H + (count - 1) * GAP : 0
})
const capped = computed(() => contentH.value > MAX_H)
const viewH = computed(() => (capped.value ? MAX_H : contentH.value))
const scrollable = computed(() => Boolean(props.done && props.expanded))
const translate = computed(() =>
  scrollable.value ? 0 : capped.value ? MAX_H - FADE - contentH.value : 0
)
const showTop = computed(() => (scrollable.value ? fade.top : capped.value))
const showBottom = computed(() => (scrollable.value ? fade.bottom : capped.value))
const mask = computed(() =>
  capped.value
    ? `linear-gradient(to bottom, transparent 0, #000 ${showTop.value ? FADE : 0}px, #000 calc(100% - ${showBottom.value ? FADE : 0}px), transparent 100%)`
    : 'none'
)

function onScroll() {
  const element = viewportRef.value
  if (!element) return
  fade.top = element.scrollTop > 1
  fade.bottom = element.scrollTop + element.clientHeight < element.scrollHeight - 1
}
</script>

<template>
  <div class="tr" :class="{ 'is-done': done }">
    <button
      type="button"
      class="tr-header"
      :class="{ 'is-clickable': done }"
      :aria-expanded="isExpanded"
      :aria-label="t('chat.thinkingDone')"
      @click="done && $emit('toggle')"
    >
      <span v-if="done" class="tr-label">
        <span class="tr-verb">{{ t('chat.thought') }}</span>{{ t('chat.thoughtDuration', { seconds: elapsedSeconds }) }}
      </span>
      <span v-else class="tr-label tr-shimmer">{{ t('chat.thinkingInProgress') }}</span>
      <svg
        v-if="done"
        class="tr-chevron"
        viewBox="0 0 24 24"
        width="12"
        height="12"
        aria-hidden="true"
      >
        <path
          d="m4.5 15.75 7.5-7.5 7.5 7.5"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <div class="tr-collapsible" :class="{ 'is-collapsed': !isExpanded }">
      <div class="tr-inner">
        <div
          ref="viewportRef"
          class="tr-viewport"
          :class="{ 'is-scroll': scrollable }"
          :style="{ height: `${viewH}px`, maskImage: mask, WebkitMaskImage: mask }"
          @scroll="onScroll"
        >
          <div class="tr-stream" :style="{ transform: `translateY(${translate}px)` }">
            <p v-for="(line, index) in sentences" :key="index" class="tr-sentence">
              {{ line }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tr {
  display: flex;
  flex-direction: column;
  width: 360px;
  max-width: 100%;
  min-height: 206px;
  font-family: 'Inter', system-ui, sans-serif;
  animation: tr-block-in 320ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.tr.is-done {
  min-height: 20px;
}

@keyframes tr-block-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.tr-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  min-height: 20px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: default;
}

.tr-header.is-clickable {
  cursor: pointer;
}

.tr-label {
  font-size: 13px;
  line-height: 18px;
  font-weight: 500;
  color: color-mix(in srgb, #a1a1a1 68%, transparent);
  letter-spacing: -0.005em;
}

.tr-verb {
  color: #a1a1a1;
}

.tr-chevron {
  color: #a1a1a1;
  transition: transform 280ms cubic-bezier(0.22, 1, 0.36, 1);
  transform: rotate(180deg);
}

.tr-header[aria-expanded='true'] .tr-chevron {
  transform: rotate(0deg);
}

.tr-header.is-clickable:hover .tr-chevron {
  color: #a1a1a1;
}

.tr-collapsible {
  display: grid;
  grid-template-rows: 1fr;
  opacity: 1;
  transition:
    grid-template-rows 320ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 220ms ease;
}

.tr-collapsible.is-collapsed {
  grid-template-rows: 0fr;
  opacity: 0;
  pointer-events: none;
}

.tr-inner {
  min-height: 0;
  overflow: hidden;
}

.tr-viewport {
  margin-top: 6px;
  overflow: hidden;
  transition: height 360ms cubic-bezier(0.22, 1, 0.36, 1);
}

.tr-viewport.is-scroll {
  overflow-y: auto;
  scrollbar-width: none;
}

.tr-viewport.is-scroll::-webkit-scrollbar {
  display: none;
}

.tr-stream {
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: transform 560ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform;
}

.tr-sentence {
  margin: 0;
  height: 40px;
  line-height: 20px;
  font-size: 13px;
  font-weight: 425;
  color: #a1a1a1;
  letter-spacing: -0.005em;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  animation: tr-sentence-in 420ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes tr-sentence-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.tr-shimmer {
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
  animation: tr-shine 2.25s cubic-bezier(0.25, 0.1, 0.25, 1) infinite;
}

@keyframes tr-shine {
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
