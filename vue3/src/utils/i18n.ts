import { i18n } from '@/locales'

/**
 * 获取路由标题的翻译
 * @param routeName 路由名称
 * @returns 翻译后的标题
 */
export function getRouteTitle(routeName: string): string {
  const routeTitleMap: Record<string, string> = {
    'login': 'router.title.login',
    'register': 'router.title.register',
    'forget': 'router.title.forget',
    'dashboard': 'router.title.dashboard',
    'user': 'router.title.user',
    'device': 'router.title.device',
    'message': 'router.title.message',
    'role': 'router.title.role',
    'template': 'router.title.template',
    'config-model': 'router.title.modelConfig',
    'config-agent': 'router.title.agent',
    'config-stt': 'router.title.sttConfig',
    'config-tts': 'router.title.ttsConfig',
    'setting-account': 'router.title.account',
    'setting-config': 'router.title.personalConfig',
    '403': 'router.title.error403',
    '404': 'router.title.error404',
  }

  const translationKey = routeTitleMap[routeName]
  if (translationKey) {
    return i18n.global.t(translationKey)
  }
  
  // 如果没有找到对应的翻译，返回原始名称
  return routeName
}

/**
 * 获取父级菜单的翻译
 * @param parentName 父级名称
 * @returns 翻译后的父级名称
 */
export function getParentTitle(parentName: string): string {
  const parentTitleMap: Record<string, string> = {
    '角色管理': 'router.parent.roleManagement',
    '配置管理': 'router.parent.configManagement',
    '设置': 'router.parent.settings',
  }

  const translationKey = parentTitleMap[parentName]
  if (translationKey) {
    return i18n.global.t(translationKey)
  }
  
  return parentName
}
