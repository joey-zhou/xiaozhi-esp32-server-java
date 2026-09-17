<script setup lang="ts">
import { UserOutlined } from '@ant-design/icons-vue'
import { useI18n } from 'vue-i18n'
import RobotAvatar from '@/components/RobotAvatar.vue'
import ThinkingState from '@/components/chat/ThinkingState.vue'
import ThinkingBlock from '@/components/chat/ThinkingBlock.vue'
import StreamingText from '@/components/chat/StreamingText.vue'
import type { ChatMessage } from '@/types/chat'

defineProps<{
  msg: ChatMessage
  /** 助手消息展示用的名称，取自当前选中角色 */
  roleName: string
  /** 助手消息展示用的头像，未配置时回退到默认机器人图标 */
  roleAvatar?: string
  /** 该条消息的思考区域是否展开 */
  thinkingExpanded?: boolean
}>()

defineEmits<{
  (e: 'toggle-thinking'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="message-row" :class="msg.role">
    <a-avatar v-if="msg.role === 'user'" :size="36" :style="{ background: '#1677ff', flexShrink: 0 }">
      <template #icon><UserOutlined /></template>
    </a-avatar>
    <a-avatar v-else-if="roleAvatar" :size="36" :src="roleAvatar" :style="{ flexShrink: 0 }" />
    <RobotAvatar v-else :size="36" />
    <div class="message-content" :class="msg.role">
      <a-typography-text type="secondary" :style="{ fontSize: '13px', padding: '0 4px', marginBottom: '4px' }">
        {{ msg.role === 'user' ? t('chat.me') : roleName }}
      </a-typography-text>
      <div class="message-bubble" :class="msg.role">
        <ThinkingState v-if="msg.streaming && !msg.content && !msg.thinking" />
        <template v-else>
          <ThinkingBlock
            v-if="msg.thinking"
            :content="msg.thinking"
            :done="msg.thinkingDone"
            :expanded="thinkingExpanded"
            :duration-ms="msg.thinkingDurationMs"
            @toggle="$emit('toggle-thinking')"
          />
          <StreamingText
            v-if="msg.content"
            :content="msg.content"
            :streaming="msg.streaming"
          />
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-row {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-content {
  display: flex;
  flex-direction: column;
  max-width: 80%;
}

.message-content.user {
  align-items: flex-end;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.message-bubble.user {
  background: var(--ant-color-primary-bg);
  border-top-right-radius: 4px;
}

.message-bubble.assistant {
  padding: 4px 0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
}

@media (max-width: 600px) {
  .message-row {
    gap: 10px;
  }

  .message-content {
    max-width: calc(100% - 46px);
  }
}
</style>
