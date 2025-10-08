import { useLocalStorage, usePreferredDark } from '@vueuse/core'
import { computed, watch } from 'vue'

export type Theme = 'light' | 'dark' | 'auto'

export function useTheme() {
  // 使用 VueUse 的 useLocalStorage 自动持久化主题设置
  const themeMode = useLocalStorage<Theme>('theme-mode', 'auto')

  // 检测系统是否偏好暗色模式
  const prefersDark = usePreferredDark()

  // 计算实际应用的主题
  const actualTheme = computed(() => {
    if (themeMode.value === 'auto') {
      return prefersDark.value ? 'dark' : 'light'
    }
    return themeMode.value
  })

  // 应用主题到 HTML 元素
  const applyTheme = (theme: 'light' | 'dark') => {
    if (theme === 'dark') {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  // 监听主题变化并应用
  watch(
    actualTheme,
    (newTheme) => {
      applyTheme(newTheme)
    },
    { immediate: true }
  )

  // 切换主题
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
  const setTheme = (theme: Theme) => {
    themeMode.value = theme
  }

  return {
    themeMode,
    actualTheme,
    toggleTheme,
    setTheme,
  }
}

// 使用示例：
// const { themeMode, actualTheme, toggleTheme } = useTheme()
// 
// <button @click="toggleTheme">
//   当前主题: {{ actualTheme }}
// </button>
