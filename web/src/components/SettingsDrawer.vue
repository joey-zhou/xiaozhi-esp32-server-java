<template>
  <a-drawer
    v-model:open="visible"
    :title="t('component.settings.title')"
    placement="right"
    :width="320"
    :closable="true"
  >
    <a-space direction="vertical" :size="24" style="width: 100%">
      <!-- 主题设置 -->
      <div>
        <h4 style="margin-bottom: 12px">
          <BulbOutlined /> {{ t('component.settings.theme.title') }}
        </h4>
        <a-radio-group v-model:value="themeMode" button-style="solid" style="width: 100%">
          <a-radio-button value="light" style="width: 33.33%">
            <span style="font-size: 16px">☀️</span> {{ t('component.settings.theme.light') }}
          </a-radio-button>
          <a-radio-button value="dark" style="width: 33.33%">
            <span style="font-size: 16px">🌙</span> {{ t('component.settings.theme.dark') }}
          </a-radio-button>
          <a-radio-button value="auto" style="width: 33.33%">
            <span style="font-size: 16px">🔄</span> {{ t('component.settings.theme.auto') }}
          </a-radio-button>
        </a-radio-group>
        <div style="margin-top: 8px; font-size: 12px; color: var(--ant-color-text-tertiary)">
          {{ t('component.settings.theme.current') }}: {{ themeName }} ({{ t('component.settings.theme.actual') }}: {{ actualTheme }})
        </div>
      </div>

      <a-divider style="margin: 0" />

      <!-- 语言设置 -->
      <div>
        <h4 style="margin-bottom: 12px">
          <GlobalOutlined /> {{ t('component.settings.language.title') }}
        </h4>
        <a-radio-group v-model:value="currentLocale" button-style="solid" style="width: 100%">
          <a-radio-button
            v-for="locale in availableLocales"
            :key="locale"
            :value="locale"
            style="width: 50%"
          >
            {{ localeNames[locale] }}
          </a-radio-button>
        </a-radio-group>
        <div style="margin-top: 8px; font-size: 12px; color: var(--ant-color-text-tertiary)">
          {{ t('component.settings.language.current') }}: {{ localeName }}
        </div>
      </div>

      <a-divider style="margin: 0" />

      <!-- 快捷操作 -->
      <div>
        <h4 style="margin-bottom: 12px">
          <ThunderboltOutlined /> {{ t('component.settings.quickActions.title') }}
        </h4>
        <a-space direction="vertical" style="width: 100%">
          <a-button block @click="toggleTheme">
            {{ themeIcon }} {{ t('component.settings.quickActions.resetSettings') }}
          </a-button>
          <a-button block @click="toggleLocale">
            <GlobalOutlined /> {{ t('component.settings.quickActions.clearCache') }}
          </a-button>
        </a-space>
      </div>

      <a-divider style="margin: 0" />

      <!-- 系统信息 -->
      <div>
        <h4 style="margin-bottom: 12px">
          <InfoCircleOutlined /> 系统信息
        </h4>
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="主题">{{ themeName }}</a-descriptions-item>
          <a-descriptions-item label="语言">{{ localeName }}</a-descriptions-item>
          <a-descriptions-item label="版本">Vue 3.0</a-descriptions-item>
        </a-descriptions>
      </div>
    </a-space>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  BulbOutlined,
  GlobalOutlined,
  ThunderboltOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons-vue'
import { useLocale } from '@/composables/useLocale'
import { useAntdTheme } from '@/composables/useAntdTheme'

const { t } = useI18n()

const visible = defineModel<boolean>('open', { default: false })

const {
  currentLocale,
  localeName,
  toggleLocale,
  availableLocales,
  localeNames,
} = useLocale()

const {
  themeMode,
  actualTheme,
  themeName,
  themeIcon,
  toggleTheme,
} = useAntdTheme()
</script>

<style scoped>
h4 {
  font-weight: 600;
  color: var(--ant-color-primary);
}

:deep(.ant-radio-button-wrapper) {
  text-align: center;
}
</style>
