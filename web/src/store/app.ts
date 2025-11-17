import { ref } from 'vue'
import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'

/**
 * 语言类型
 */
export type Locale = 'zh-CN' | 'en-US'

/**
 * 应用全局状态 Store
 * 管理语言、布局等全局配置
 */
export const useAppStore = defineStore('app', () => {

  // ========== 布局管理 ==========
  // 侧边栏折叠状态
  const sidebarCollapsed = ref(false)
  
  // 是否移动端
  const isMobile = ref(false)

  // 导航风格
  const navigationStyle = useStorage<'tabs' | 'sidebar'>('navigation-style', 'sidebar')

  // 屏幕尺寸
  const screenWidth = ref(window.innerWidth)
  const screenHeight = ref(window.innerHeight)

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

  const updateScreenSize = () => {
    screenWidth.value = window.innerWidth
    screenHeight.value = window.innerHeight
    isMobile.value = window.innerWidth < 768
  }

  // ========== 页面设置 ==========
  const pageTitle = ref<string>('')

  const setPageTitle = (title: string) => {
    pageTitle.value = title
    const baseTitle = import.meta.env.VITE_APP_TITLE || 'Connect Ai'
    document.title = title ? `${title} - ${baseTitle}` : baseTitle
  }

  return {
    // 布局
    sidebarCollapsed,
    isMobile,
    navigationStyle,
    screenWidth,
    screenHeight,
    toggleSidebar,
    setSidebarCollapsed,
    setMobile,
    setNavigationStyle,
    updateScreenSize,
    
    // 页面
    pageTitle,
    setPageTitle,
  }
})

