<script setup lang="ts">
import { h, type VNodeChild } from 'vue'
import { useI18n } from 'vue-i18n'
import SimpleEmptyImg from 'ant-design-vue/es/empty/simple'

interface Props {
  /** 加载失败说明，为空表示这一页是真的没有数据 */
  error?: string | null
}

defineProps<Props>()

defineEmits<{
  (e: 'retry'): void
}>()

const { t } = useI18n()

// 与 a-table 默认空态用同一张简版插图，只有失败时才换文案。
// a-empty 内部按「函数返回 VNode」识别简版插图并套用紧凑排版，但类型声明里 image 只写了 VueNode，故断言一次
const simpleImage = (() => h(SimpleEmptyImg)) as unknown as VNodeChild
</script>

<template>
  <a-empty v-if="error" :image="simpleImage">
    <!-- a-empty 会把 description 塞进段落元素里，这里只能用行内元素再靠样式撑成块 -->
    <template #description>
      <span class="empty-title">{{ t('table.loadFailed') }}</span>
      <span class="empty-detail">{{ error }}</span>
    </template>
    <a-button size="small" @click="$emit('retry')">{{ t('table.retry') }}</a-button>
  </a-empty>
  <a-empty v-else :image="simpleImage" />
</template>

<style scoped lang="scss">
.empty-title {
  display: block;
  color: var(--ant-color-text);
}

.empty-detail {
  display: block;
  margin-top: 4px;
  color: var(--ant-color-text-tertiary);
  font-size: 12px;
  word-break: break-word;
}
</style>
