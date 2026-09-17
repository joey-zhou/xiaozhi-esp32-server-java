<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  UserOutlined,
  LogoutOutlined,
  GlobalOutlined,
  BgColorsOutlined,
  BulbOutlined,
  DesktopOutlined
} from '@ant-design/icons-vue'
import { useUserStore } from '@/store/user'
import { useAvatar } from '@/composables/useAvatar'
import { useLocale } from '@/composables/useLocale'
import { useAntdTheme } from '@/composables/useAntdTheme'
import { useAuth } from '@/composables/useAuth'
import { ROUTES } from '@/router/routes'

const { t } = useI18n()

const router = useRouter()
const userStore = useUserStore()
const { getAvatarUrl } = useAvatar()
const { logout } = useAuth()
const { currentLocale, localeName, setLocale, availableLocales, localeNames } = useLocale()
const { themeMode, actualTheme, setTheme } = useAntdTheme()

// 用户信息
const user = computed(() => userStore.userInfo)

// 头像URL
const avatarUrl = computed(() => getAvatarUrl(user.value?.avatar))
// 主题图标
const themeIcon = computed(() => {
  switch (actualTheme.value) {
    case 'light':
      return BgColorsOutlined
    case 'dark':
      return BulbOutlined
    default:
      return DesktopOutlined
  }
})

// 主题文本
const themeText = computed(() => {
  switch (themeMode.value) {
    case 'light':
      return t('component.settings.theme.light')
    case 'dark':
      return t('component.settings.theme.dark')
    case 'auto':
      return t('component.settings.theme.auto')
    default:
      return t('component.settings.theme.title')
  }
})

/**
 * 跳转到个人中心
 */
function goToAccount() {
  router.push(ROUTES.SETTING_ACCOUNT)
}

/**
 * 切换语言
 */
function handleLocaleChange(locale: string) {
  setLocale(locale as 'zh-CN' | 'en-US')
}

/**
 * 切换主题
 */
function handleThemeChange(theme: string) {
  setTheme(theme as 'light' | 'dark' | 'auto')
}
</script>

<template>
  <div class="app-header">
    <div class="header-left">
      <!-- 预留左侧空间，可以放置面包屑等 -->
    </div>
    
    <div class="header-right">
      <!-- 语言选择 -->
      <a-dropdown class="locale-dropdown">
        <a-button type="text" class="header-btn">
          <GlobalOutlined />
          <span class="btn-text">{{ localeName }}</span>
        </a-button>
        
        <template #overlay>
          <a-menu @click="({ key }: { key: string }) => handleLocaleChange(key)">
            <a-menu-item 
              v-for="locale in availableLocales" 
              :key="locale"
              :class="{ 'ant-menu-item-selected': currentLocale === locale }"
            >
              {{ localeNames[locale] }}
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>

      <!-- 主题切换 -->
      <a-dropdown class="theme-dropdown">
        <a-button type="text" class="header-btn">
          <component :is="themeIcon" />
          <span class="btn-text">{{ themeText }}</span>
        </a-button>
        
        <template #overlay>
          <a-menu @click="({ key }: { key: string }) => handleThemeChange(key)">
            <a-menu-item 
              key="light"
              :class="{ 'ant-menu-item-selected': themeMode === 'light' }"
            >
              <BgColorsOutlined />
              <span class="menu-text">{{ t('component.settings.theme.light') }}</span>
            </a-menu-item>
            <a-menu-item 
              key="dark"
              :class="{ 'ant-menu-item-selected': themeMode === 'dark' }"
            >
              <BulbOutlined />
              <span class="menu-text">{{ t('component.settings.theme.dark') }}</span>
            </a-menu-item>
            <a-menu-item 
              key="auto"
              :class="{ 'ant-menu-item-selected': themeMode === 'auto' }"
            >
              <DesktopOutlined />
              <span class="menu-text">{{ t('component.settings.theme.auto') }}</span>
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>

      <!-- 用户信息下拉菜单：trigger 用按钮而不是 div，键盘 Tab 能聚焦、Enter/Space 能触发 -->
      <a-dropdown class="user-dropdown" :trigger="['hover', 'click']">
        <a-button type="text" class="header-btn">
          <a-avatar
            :src="avatarUrl"
            size="small"
            class="user-avatar"
          >
            <template #icon>
              <UserOutlined />
            </template>
          </a-avatar>
          <span class="user-name">{{ user?.name || t('common.user') }}</span>
        </a-button>

        <template #overlay>
          <a-menu>
            <a-menu-item @click="() => goToAccount()">
              <UserOutlined />
              <span class="menu-text">{{ t('common.personalCenter') }}</span>
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item @click="() => logout()">
              <LogoutOutlined />
              <span class="menu-text">{{ t('common.logout') }}</span>
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-header {
  box-shadow: 0 2px 8px var(--ant-color-border-secondary);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  z-index: 10;
  height: 48px;
  padding: 0 40px;
}

.header-left {
  flex: 1;
}

.header-right {
  display: flex;
  align-items: center;
  height: 100%;
  gap: 8px;
  
  .header-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    height: 32px;
    padding: 0 8px;
    border-radius: 6px;
    transition: all 0.2s;
    
    &:hover {
      background-color: var(--ant-color-fill-tertiary);
    }
    
    .btn-text {
      font-size: 14px;
      color: var(--ant-color-text);
    }
  }
  
  .locale-dropdown,
  .theme-dropdown {
    .header-btn {
      min-width: 80px;
    }
  }
  
  // a-dropdown 的 class 会合并到触发按钮自身，所以这里直接作用在按钮上，里面的元素才能嵌套写
  .user-dropdown {
    padding: 0 12px;
    gap: 10px;

    .user-name {
      color: var(--ant-color-text);
    }
  }
}

.menu-text {
  margin-left: 8px;
  color: var(--ant-color-text-secondary);
}
</style>

