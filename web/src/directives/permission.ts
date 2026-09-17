import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/store/user'

type PermissionBindingValue = string | string[]

const DISPLAY_KEY = 'permissionOriginalDisplay'
const SPACE_ITEM_DISPLAY_KEY = 'permissionOriginalSpaceItemDisplay'

/**
 * a-space 把每个子项单独包进一层 `.ant-space-item`，只隐藏子项本身会剩下一个空包装格，
 * 表现为操作列里多出一段 gap。只认直接父节点：那一层包装格是这个元素专属的，
 * 再往上找可能连带隐藏同一格里的其他内容。
 */
function findSpaceItem(el: HTMLElement): HTMLElement | null {
  const parent = el.parentElement
  return parent?.classList.contains('ant-space-item') ? parent : null
}

/** 首次隐藏时把原始 display 存进 dataset，恢复时原样写回 */
function hideElement(el: HTMLElement, storeKey: string) {
  if (el.dataset[storeKey] === undefined) {
    el.dataset[storeKey] = el.style.display
  }
  el.style.display = 'none'
}

/** dataset 里没有记录说明这个元素没被本指令隐藏过，不去动它的 display */
function showElement(el: HTMLElement, storeKey: string) {
  const originalDisplay = el.dataset[storeKey]
  if (originalDisplay === undefined) {
    return
  }
  el.style.display = originalDisplay
  delete el.dataset[storeKey]
}

function updateElementVisibility(el: HTMLElement, visible: boolean) {
  const spaceItem = findSpaceItem(el)

  if (visible) {
    showElement(el, DISPLAY_KEY)
    if (spaceItem) {
      showElement(spaceItem, SPACE_ITEM_DISPLAY_KEY)
    }
    return
  }

  hideElement(el, DISPLAY_KEY)
  if (spaceItem) {
    hideElement(spaceItem, SPACE_ITEM_DISPLAY_KEY)
  }
}

function applyPermission(el: HTMLElement, binding: DirectiveBinding<PermissionBindingValue>) {
  if (!binding.value) {
    return
  }

  const userStore = useUserStore()
  const permissions = Array.isArray(binding.value) ? binding.value : [binding.value]
  const hasPermission = permissions.some((item) => userStore.hasPermission(item))

  updateElementVisibility(el, hasPermission)
}

/**
 * 权限指令
 * 用法：
 * - v-permission="'system:device:create'"
 * - v-permission="['system:device:create', 'system:device:update']"
 */
export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<PermissionBindingValue>) {
    applyPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding<PermissionBindingValue>) {
    applyPermission(el, binding)
  }
}
