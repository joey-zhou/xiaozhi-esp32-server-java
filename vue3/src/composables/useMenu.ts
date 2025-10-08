import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import type { MenuItem } from '@/types/menu'

/**
 * 菜单管理 Composable
 * 处理侧边栏菜单的展开、选中、权限过滤等逻辑
 */
export function useMenu() {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  const { t } = useI18n()

  // 展开的菜单keys（默认展开配置管理）
  const openKeys = ref<string[]>(['router.parent.configManagement'])
  
  // 根级子菜单keys（用于手风琴模式）
  const rootSubmenuKeys = ['router.parent.roleManagement', 'router.parent.configManagement', 'router.parent.settings']

  // 获取所有菜单项（从路由配置中获取）
  const menuItems = computed<MenuItem[]>(() => {
    // 找到主布局路由
    const mainRoute = router.getRoutes().find(r => r.path === '/' && r.children)
    if (!mainRoute?.children) return []
    
    // 菜单映射，用于组织父子关系
    const menuMap = new Map<string, MenuItem>()
    const rootMenus: MenuItem[] = []
    
    // 遍历路由，构建菜单
    mainRoute.children
      .filter(route => route.meta?.title && !route.meta?.hideInMenu)
      .forEach(route => {
        const menuItem: MenuItem = {
          path: `/${route.path}`,
          name: route.name as string,
          meta: route.meta as any,
          children: []
        }
        
        // 如果有 parent，说明是子菜单
        if (route.meta?.parent) {
          const parentKey = route.meta.parent as string
          
          // 查找或创建父菜单
          if (!menuMap.has(parentKey)) {
            const parentMenu: MenuItem = {
              path: parentKey,
              name: parentKey,
              meta: {
                title: parentKey, // 这里已经是多语言键了
                icon: route.meta.icon || 'SettingOutlined',
                isAdmin: route.meta.isAdmin
              },
              children: []
            }
            menuMap.set(parentKey, parentMenu)
            rootMenus.push(parentMenu)
          }
          
          // 添加到父菜单的子菜单中
          const parentMenu = menuMap.get(parentKey)!
          if (!parentMenu.children) parentMenu.children = []
          parentMenu.children.push(menuItem)
        } else {
          // 没有 parent，是根菜单
          rootMenus.push(menuItem)
          menuMap.set(menuItem.path, menuItem)
        }
      })
    
    return rootMenus
  })

  // 是否是管理员
  const isAdmin = computed(() => {
    return String(userStore.userInfo?.isAdmin) === '1'
  })

  // 过滤后的菜单（根据权限）
  const filteredMenuItems = computed(() => {
    return filterMenuByPermission(menuItems.value)
  })

  // 当前选中的菜单key
  const selectedKeys = computed(() => {
    return [route.path]
  })

  /**
   * 根据权限过滤菜单
   */
  function filterMenuByPermission(items: MenuItem[]): MenuItem[] {
    return items.filter(item => {
      // 检查是否需要管理员权限
      if (item.meta.isAdmin && !isAdmin.value) {
        return false
      }
      
      // 递归过滤子菜单
      if (item.children) {
        item.children = filterMenuByPermission(item.children)
      }
      
      return true
    })
  }

  /**
   * 处理菜单展开变化（手风琴模式）
   */
  function handleOpenChange(keys: string[]) {
    const latestOpenKey = keys.find(key => !openKeys.value.includes(key))
    
    if (latestOpenKey && rootSubmenuKeys.includes(latestOpenKey)) {
      // 如果打开的是根级菜单，只保留这一个
      openKeys.value = [latestOpenKey]
    } else {
      // 否则保留所有打开的菜单
      openKeys.value = keys
    }
  }

  /**
   * 菜单点击处理
   */
  function handleMenuClick(path: string) {
    router.push(path)
  }

  return {
    openKeys,
    selectedKeys,
    menuItems: filteredMenuItems,
    isAdmin,
    handleOpenChange,
    handleMenuClick,
  }
}


