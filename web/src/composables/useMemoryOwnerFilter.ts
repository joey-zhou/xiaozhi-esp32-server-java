import { computed, ref } from 'vue'
import { useSelectLoadMore } from '@/composables/useSelectLoadMore'
import { useRequest } from '@/composables/useRequest'
import { queryRoles } from '@/services/role'
import { queryDevices } from '@/services/device'
import type { Role } from '@/types/role'
import type { Device } from '@/types/device'

/**
 * 记忆相关页面的「设备 + 角色」筛选。两个下拉都滚动加载，选中值用 0 / 空串表示「全部」。
 */
export function useMemoryOwnerFilter() {
  const {
    list: roles,
    loading: rolesLoading,
    load: loadRoles,
    onPopupScroll: onRolePopupScroll,
  } = useSelectLoadMore<Role>(queryRoles)

  const {
    list: devices,
    loading: devicesLoading,
    load: loadDevices,
    onPopupScroll: onDevicePopupScroll,
  } = useSelectLoadMore<Device>(queryDevices)

  const { execute: executeRouteDevice } = useRequest()

  const selectedRoleId = ref<number>(0)
  const selectedDeviceId = ref<string>('')

  // 路由带来的设备未必落在下拉已加载的那几页里，单查回来的那台挂在这里补进选项
  const routeDeviceOption = ref<Device | null>(null)

  // 补进来的设备一旦随翻页进了 devices，就不再单独占一项，避免出现重复 key
  const deviceOptions = computed<Device[]>(() => {
    const extra = routeDeviceOption.value
    const loaded = devices.value
    if (!extra || loaded.some((d) => d.deviceId === extra.deviceId)) {
      return loaded
    }
    return [extra, ...loaded]
  })

  /**
   * 按 deviceId 单查一台设备，补进下拉选项
   * 下拉按页拉取，路由传来的设备可能不在已加载的页里，不补就会被静默换成「全部」
   */
  async function loadRouteDeviceOption(deviceId: string) {
    if (devices.value.some((d) => d.deviceId === deviceId)) {
      return
    }
    // 补不到就保持不补，不打扰用户
    await executeRouteDevice(() => queryDevices({ pageNo: 1, pageSize: 1, deviceId }), {
      showError: false,
      onSuccess: (data) => {
        routeDeviceOption.value = data?.list?.[0] ?? null
      },
    })
  }

  /**
   * 加载两个下拉并按路由参数回填选中项，没有路由参数时保持「全部」
   */
  async function initOwner(routeRoleId: number, routeDeviceId: string) {
    await Promise.all([loadRoles(), loadDevices()])

    if (routeRoleId) {
      selectedRoleId.value = routeRoleId
    }

    if (routeDeviceId) {
      await loadRouteDeviceOption(routeDeviceId)
      if (deviceOptions.value.some((d) => d.deviceId === routeDeviceId)) {
        selectedDeviceId.value = routeDeviceId
      }
    }
  }

  /**
   * 角色筛选函数（按 a-select-option 上显式声明的 label 匹配）
   */
  function filterRoleOption(input: string, option: { label?: string }) {
    return (option.label ?? '').toLowerCase().includes(input.toLowerCase())
  }

  return {
    roles,
    rolesLoading,
    onRolePopupScroll,
    deviceOptions,
    devicesLoading,
    onDevicePopupScroll,
    selectedRoleId,
    selectedDeviceId,
    initOwner,
    filterRoleOption,
  }
}
