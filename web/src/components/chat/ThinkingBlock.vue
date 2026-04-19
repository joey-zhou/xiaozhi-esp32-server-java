<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ThunderboltOutlined, RightOutlined } from '@ant-design/icons-vue'

defineProps<{
  /** 思考内容文本 */
  content: string
  /** 思考是否已完成 */
  done?: boolean
  /** 是否展开 */
  expanded?: boolean
}>()

defineEmits<{
  (e: 'toggle'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="thinking-block">
    <div class="thinking-header" @click="$emit('toggle')">
      <RightOutlined class="thinking-arrow" :class="{ expanded }" />
      <ThunderboltOutlined :style="{ fontSize: '13px' }" />
      <span v-if="done">{{ t('chat.thinkingDone') }}</span>
      <span v-else class="thinking-loading">{{ t('chat.thinkingInProgress') }}</span>
    </div>
    <div v-show="expanded" class="thinking-content">{{ content }}</div>
  </div>
</template>

<style scoped>
.thinking-block {
  margin-bottom: 8px;
  border-radius: 8px;
  background: #f5f5f5;
  overflow: hidden;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  color: #8c8c8c;
  user-select: none;
  transition: color 0.2s;
}

.thinking-header:hover {
  color: #595959;
}

.thinking-arrow {
  font-size: 10px;
  transition: transform 0.2s;
}

.thinking-arrow.expanded {
  transform: rotate(90deg);
}

.thinking-loading {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.thinking-content {
  padding: 8px 12px 10px 30px;
  font-size: 13px;
  line-height: 1.6;
  color: #8c8c8c;
  white-space: pre-wrap;
  word-break: break-word;
  border-top: 1px solid #e8e8e8;
  margin: 0 12px;
}
</style>
