import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/store/user'

type PermissionBindingValue = string | string[]

function updateElementVisibility(el: HTMLElement, visible: boolean) {
  const displayKey = 'permissionOriginalDisplay'
  if (visible) {
    el.style.display = el.dataset[displayKey] ?? ''
    delete el.dataset[displayKey]
    return
  }

  if (!el.dataset[displayKey]) {
    el.dataset[displayKey] = el.style.display || ''
  }
  el.style.display = 'none'
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
