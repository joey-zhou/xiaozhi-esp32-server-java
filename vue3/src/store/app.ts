import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useStorage, usePreferredDark } from '@vueuse/core'

/**
 * 主题类型
 */
export type Theme = 'light' | 'dark' | 'auto'

/**
 * 语言类型
 */
export type Locale = 'zh-CN' | 'en-US'

/**
 * 应用全局状态 Store
 * 管理主题、语言、布局等全局配置
 */
export const useAppStore = defineStore('app', () => {
  // ========== 主题管理 ==========
  const themeMode = useStorage<Theme>('theme-mode', 'auto')
  const prefersDark = usePreferredDark()

  // 计算实际应用的主题
  const actualTheme = computed(() => {
    if (themeMode.value === 'auto') {
      return prefersDark.value ? 'dark' : 'light'
    }
    return themeMode.value
  })

  const setTheme = (theme: Theme) => {
    themeMode.value = theme
  }

  // ========== 布局管理 ==========
  // 侧边栏折叠状态
  const sidebarCollapsed = ref(false)
  
  // 是否移动端
  const isMobile = ref(false)

  // 导航风格
  const navigationStyle = useStorage<'tabs' | 'sidebar'>('navigation-style', 'sidebar')

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const setSidebarCollapsed = (collapsed: boolean) => {
    sidebarCollapsed.value = collapsed
  }

  const setMobile = (mobile: boolean) => {
    isMobile.value = mobile
  }

  const setNavigationStyle = (style: 'tabs' | 'sidebar') => {
    navigationStyle.value = style
  }

  // ========== 页面设置 ==========
  const pageTitle = ref<string>('')

  const setPageTitle = (title: string) => {
    pageTitle.value = title
    const baseTitle = import.meta.env.VITE_APP_TITLE || 'Connect Ai'
    document.title = title ? `${title} - ${baseTitle}` : baseTitle
  }

  return {
    // 主题
    themeMode,
    actualTheme,
    setTheme,
    
    // 布局
    sidebarCollapsed,
    isMobile,
    navigationStyle,
    toggleSidebar,
    setSidebarCollapsed,
    setMobile,
    setNavigationStyle,
    
    // 页面
    pageTitle,
    setPageTitle,
  }
})

