<script setup lang="ts">
import { ref, onErrorCaptured, provide, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from 'ant-design-vue'

const { t } = useI18n()

interface Props {
  /**
   * 是否在开发环境显示错误详情
   */
  showDetails?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showDetails: true,
})

// 错误状态
const hasError = ref(false)
const errorMessage = ref('')
const errorStack = ref('')

// 计算属性：是否为开发环境
const isDev = computed(() => import.meta.env.DEV)

/**
 * 捕获子组件错误
 */
onErrorCaptured((error, instance, info) => {
  hasError.value = true
  errorMessage.value = error.message || '未知错误'
  errorStack.value = error.stack || ''

  console.error('ErrorBoundary 捕获到错误:', {
    error,
    instance,
    info,
  })

  // 返回 false 阻止错误继续向上传播
  return false
})

/**
 * 重置错误状态
 */
const resetError = () => {
  hasError.value = false
  errorMessage.value = ''
  errorStack.value = ''
}

/**
 * 刷新页面
 */
const reloadPage = () => {
  window.location.reload()
}

// 提供重置方法给子组件
provide('resetError', resetError)
</script>

<template>
  <div class="error-boundary">
    <!-- 错误状态 -->
    <div v-if="hasError" class="error-container">
      <div class="error-content">
        <!-- 错误图标 -->
        <div class="error-icon">
          <svg
            viewBox="0 0 1024 1024"
            xmlns="http://www.w3.org/2000/svg"
            width="64"
            height="64"
          >
            <path
              d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z"
              fill="#ff4d4f"
            />
            <path
              d="M512 288c-22.1 0-40 17.9-40 40v264c0 22.1 17.9 40 40 40s40-17.9 40-40V328c0-22.1-17.9-40-40-40z"
              fill="#ff4d4f"
            />
            <circle cx="512" cy="728" r="40" fill="#ff4d4f" />
          </svg>
        </div>

        <!-- 错误标题 -->
        <h2 class="error-title">{{ t('component.errorBoundary.title') }}</h2>

        <!-- 错误信息 -->
        <p class="error-message">{{ t('component.errorBoundary.description') }}</p>

        <!-- 操作按钮 -->
        <div class="error-actions">
          <a-button type="primary" @click="resetError">{{ t('component.errorBoundary.retry') }}</a-button>
          <a-button @click="reloadPage">{{ t('component.errorBoundary.goHome') }}</a-button>
        </div>

        <!-- 错误详情（开发环境） -->
        <div
          v-if="props.showDetails && isDev && errorStack"
          class="error-details"
        >
          <details>
            <summary>错误详情</summary>
            <pre>{{ errorStack }}</pre>
          </details>
        </div>
      </div>
    </div>

    <!-- 正常内容 -->
    <slot v-else />
  </div>
</template>

<style scoped lang="scss">
.error-boundary {
  width: 100%;
  height: 100%;
}

.error-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 40px 20px;
}

.error-content {
  text-align: center;
  max-width: 600px;
}

.error-icon {
  margin-bottom: 24px;
  
  svg {
    display: inline-block;
    animation: shake 0.5s ease-in-out;
  }
}

.error-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--ant-color-text);
  margin-bottom: 16px;
}

.error-message {
  font-size: 16px;
  color: var(--ant-color-text-secondary);
  margin-bottom: 32px;
  word-break: break-word;
}

.error-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-bottom: 24px;
}

.error-details {
  margin-top: 32px;
  text-align: left;

  details {
    background: #f5f5f5;
    padding: 16px;
    border-radius: 4px;
    cursor: pointer;

    summary {
      font-weight: 600;
      margin-bottom: 12px;
      user-select: none;
      
      &:hover {
        color: var(--ant-color-primary);
      }
    }

    pre {
      margin: 0;
      padding: 12px;
      background: #fff;
      border-radius: 4px;
      overflow-x: auto;
      font-size: 12px;
      line-height: 1.5;
      color: #c41d7f;
    }
  }
}

@keyframes shake {
  0%, 100% {
    transform: translateX(0);
  }
  10%, 30%, 50%, 70%, 90% {
    transform: translateX(-10px);
  }
  20%, 40%, 60%, 80% {
    transform: translateX(10px);
  }
}
</style>

