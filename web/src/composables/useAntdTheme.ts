import { useStorage, usePreferredDark } from '@vueuse/core'
import { computed, watch } from 'vue'
import { theme } from 'ant-design-vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import { STORAGE_THEME_MODE } from '@/constants/storage'

export type ThemeMode = 'light' | 'dark' | 'auto'

// Ant Design Vue 暗色主题配置
const darkTheme: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#1890ff',
    colorError: '#ff4d4f',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorBgBase: '#141414',
    colorBgContainer: '#1f1f1f',
    colorBgElevated: '#262626',
    colorBorder: '#434343',
    colorText: '#ffffff',
    colorTextSecondary: '#a6a6a6',
    colorTextTertiary: '#8c8c8c',
    colorTextQuaternary: '#595959',
    colorFillQuaternary: '#262626',
    colorFillTertiary: '#1f1f1f',
    colorErrorHover: '#ff7875',
  },
}

// Ant Design Vue 亮色主题配置
const lightTheme: ThemeConfig = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#1890ff',
    colorError: '#ff4d4f',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorBgBase: '#ffffff',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorBorder: '#d9d9d9',
    colorText: '#000000',
    colorTextSecondary: '#666666',
    colorTextTertiary: '#999999',
    colorTextQuaternary: '#cccccc',
    colorFillQuaternary: '#fafafa',
    colorFillTertiary: '#f5f5f5',
    colorErrorHover: '#ff7875',
  },
}

/**
 * 将主题 token 注入到 CSS 变量中
 * 这是为了兼容项目中使用的 var(--ant-xxx) 样式
 * 注意：Ant Design Vue 不像 React 版本会自动生成 CSS 变量，需要手动注入
 */
function injectCssVariables(isDark: boolean) {
  const root = document.documentElement
  const tokens = isDark ? darkTheme.token : lightTheme.token
  
  if (!tokens) return
  
  // 注入所有 token 作为 CSS 变量
  Object.entries(tokens).forEach(([key, value]) => {
    // 将 camelCase 转换为 kebab-case
    const cssVarName = key.replace(/([A-Z])/g, '-$1').toLowerCase()
    root.style.setProperty(`--ant-${cssVarName}`, String(value))
  })
  
  // 额外添加一些常用的派生变量
  root.style.setProperty('--ant-color-primary-hover', isDark ? '#40a9ff' : '#40a9ff')
  root.style.setProperty('--ant-color-primary-bg', isDark ? '#111d2c' : '#e6f7ff')
  root.style.setProperty('--ant-color-success-bg', isDark ? '#162312' : '#f6ffed')
  root.style.setProperty('--ant-color-error-bg', isDark ? '#2c1618' : '#fff1f0')
  root.style.setProperty('--ant-color-warning-bg', isDark ? '#2b2111' : '#fffbe6')
  root.style.setProperty('--ant-color-text-inverse', '#ffffff')
  root.style.setProperty('--ant-color-white', '#ffffff')
  root.style.setProperty('--ant-color-text-placeholder', isDark ? '#595959' : '#bfbfbf')
  root.style.setProperty('--ant-box-shadow', isDark 
    ? '0 3px 6px -4px rgba(0, 0, 0, 0.48), 0 6px 16px 0 rgba(0, 0, 0, 0.32), 0 9px 28px 8px rgba(0, 0, 0, 0.20)'
    : '0 3px 6px -4px rgba(0, 0, 0, 0.12), 0 6px 16px 0 rgba(0, 0, 0, 0.08), 0 9px 28px 8px rgba(0, 0, 0, 0.05)'
  )
  root.style.setProperty('--ant-color-border-secondary', isDark ? '#303030' : '#f0f0f0')
}

export function useAntdTheme() {
  const themeMode = useStorage<ThemeMode>(STORAGE_THEME_MODE, 'auto')
  const prefersDark = usePreferredDark()

  // 计算实际应用的主题
  const actualTheme = computed<'light' | 'dark'>(() => {
    if (themeMode.value === 'auto') {
      return prefersDark.value ? 'dark' : 'light'
    }
    return themeMode.value
  })

  // 获取 Ant Design Vue 的主题配置
  const antdTheme = computed<ThemeConfig>(() => {
    return actualTheme.value === 'dark' ? darkTheme : lightTheme
  })

  // 监听主题变化，注入 CSS 变量
  watch(actualTheme, (theme) => {
    injectCssVariables(theme === 'dark')
  }, { immediate: true })

  // 切换主题（循环切换：light -> dark -> auto）
  const toggleTheme = () => {
    if (themeMode.value === 'light') {
      themeMode.value = 'dark'
    } else if (themeMode.value === 'dark') {
      themeMode.value = 'auto'
    } else {
      themeMode.value = 'light'
    }
  }

  // 设置特定主题
  const setTheme = (theme: ThemeMode) => {
    themeMode.value = theme
  }

  // 获取主题图标
  const themeIcon = computed(() => {
    switch (themeMode.value) {
      case 'light':
        return '☀️'
      case 'dark':
        return '🌙'
      case 'auto':
        return '🔄'
      default:
        return '☀️'
    }
  })

  // 主题显示名称的多语言 key，交给组件里的 t() 渲染
  const themeNameKey = computed(() => `component.settings.theme.${themeMode.value}`)

  return {
    themeMode,
    actualTheme,
    antdTheme,
    toggleTheme,
    setTheme,
    themeIcon,
    themeNameKey,
  }
}

