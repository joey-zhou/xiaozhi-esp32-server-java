import { useStorage, usePreferredDark } from '@vueuse/core'
import { computed, watch } from 'vue'
import { theme } from 'ant-design-vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

export type ThemeMode = 'light' | 'dark' | 'auto'

// Ant Design Vue 暗色主题配置
const darkTheme: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#1890ff',
  },
}

// Ant Design Vue 亮色主题配置
const lightTheme: ThemeConfig = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#1890ff',
  },
}

export function useAntdTheme() {
  const themeMode = useStorage<ThemeMode>('theme-mode', 'auto')
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

  // 应用主题到 HTML 元素（用于自定义 CSS）
  const applyThemeClass = (theme: 'light' | 'dark') => {
    const html = document.documentElement
    if (theme === 'dark') {
      html.classList.add('dark')
      html.setAttribute('data-theme', 'dark')
    } else {
      html.classList.remove('dark')
      html.setAttribute('data-theme', 'light')
    }
  }

  // 监听主题变化并应用
  watch(
    actualTheme,
    (newTheme) => {
      applyThemeClass(newTheme)
    },
    { immediate: true }
  )

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

  // 获取主题显示名称
  const themeName = computed(() => {
    switch (themeMode.value) {
      case 'light':
        return '亮色模式'
      case 'dark':
        return '暗色模式'
      case 'auto':
        return '跟随系统'
      default:
        return '亮色模式'
    }
  })

  return {
    themeMode,
    actualTheme,
    antdTheme,
    toggleTheme,
    setTheme,
    themeIcon,
    themeName,
  }
}

// 使用示例：
// const { themeMode, actualTheme, antdTheme, toggleTheme } = useAntdTheme()
// 
// <a-config-provider :theme="antdTheme">
//   <button @click="toggleTheme">
//     {{ themeIcon }} {{ themeName }}
//   </button>
// </a-config-provider>
