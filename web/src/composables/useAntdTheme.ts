import { useStorage, usePreferredDark } from '@vueuse/core'
import { computed, watch } from 'vue'
import { ConfigProvider, theme } from 'ant-design-vue'
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

/**
 * Modal.confirm / message 等静态方法不挂在 <a-config-provider> 的组件树下，拿不到它的 theme，
 * 暗色模式下会一直按亮色渲染。ant-design-vue 源码里这些静态方法会读 ConfigProvider.config()
 * 写入的全局配置，各自套一层 <ConfigProvider> 再渲染内容
 *（见 modal/confirm.js 的 Wrapper、vc-notification/Notification.js 的 newInstance），
 * 所以在主题切换时同步调这个全局配置，就能让它们跟着页面主题变暗/变亮。
 * 官方类型只认旧版 Theme（primaryColor 等几个色值），但运行时其实原样透传给内部的
 * <ConfigProvider>，新版 ThemeConfig（algorithm + token）一样生效，这里按真实签名断言类型。
 */
type ConfigProviderConfig = (params: { theme?: ThemeConfig }) => void

function syncStaticMethodsTheme(themeConfig: ThemeConfig) {
  ;(ConfigProvider.config as unknown as ConfigProviderConfig)({ theme: themeConfig })
}

// 主题是全局唯一的一份状态，放模块作用域：AudioPlayer 这类组件是按表格行渲染的，
// 若把状态和 watch 放进函数体，挂 N 行就注册 N 个 watch，切一次主题要重复注入 N 遍 CSS 变量
const themeMode = useStorage<ThemeMode>(STORAGE_THEME_MODE, 'auto')
const prefersDark = usePreferredDark()

const actualTheme = computed<'light' | 'dark'>(() => {
  if (themeMode.value === 'auto') {
    return prefersDark.value ? 'dark' : 'light'
  }
  return themeMode.value
})

const antdTheme = computed<ThemeConfig>(() => {
  return actualTheme.value === 'dark' ? darkTheme : lightTheme
})

// 监听主题变化，注入 CSS 变量，并把主题同步给 Modal.confirm / message 这类静态方法
watch(actualTheme, (mode) => {
  injectCssVariables(mode === 'dark')
  syncStaticMethodsTheme(mode === 'dark' ? darkTheme : lightTheme)
}, { immediate: true })

export function useAntdTheme() {
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

