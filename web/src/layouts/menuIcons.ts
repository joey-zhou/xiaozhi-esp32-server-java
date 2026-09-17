import type { Component } from 'vue'
import {
  ApiOutlined,
  AudioOutlined,
  BookOutlined,
  CrownOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  GiftOutlined,
  MessageOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  ShopOutlined,
  SnippetsOutlined,
  SoundOutlined,
  TeamOutlined,
  UserAddOutlined,
} from '@ant-design/icons-vue'

/**
 * 菜单可用图标白名单
 * 只登记这里的图标能被 tree-shaking 保留，新增菜单图标必须同步加进来
 * 来源：router 路由 meta.icon、useMenu 的父菜单图标、sys_permission.icon
 */
const menuIcons: Record<string, Component> = {
  ApiOutlined,
  AudioOutlined,
  BookOutlined,
  CrownOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  GiftOutlined,
  MessageOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  ShopOutlined,
  SnippetsOutlined,
  SoundOutlined,
  TeamOutlined,
  UserAddOutlined,
}

// sys_permission.icon 存的是 kebab-case（user-add），路由里存的是组件名（UserAddOutlined）
function toComponentName(iconName: string): string {
  return (
    iconName
      .split('-')
      .filter(Boolean)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join('') + 'Outlined'
  )
}

export function resolveMenuIcon(iconName?: string): Component | null {
  if (!iconName) {
    return null
  }

  const icon = menuIcons[iconName] ?? menuIcons[toComponentName(iconName)]
  if (!icon) {
    if (import.meta.env.DEV) {
      console.warn(`菜单图标未登记到 menuIcons：${iconName}`)
    }
    return null
  }

  return icon
}
