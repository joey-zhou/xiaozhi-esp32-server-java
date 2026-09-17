import { useI18n } from 'vue-i18n'
import { useRequest } from '@/composables/useRequest'
import { queryPlatformConfig } from '@/services/config'
import type { PlatformConfig } from '@/types/agent'

export interface UsePlatformConfigModalOptions {
  /** 查到已有配置时，以编辑模式打开弹窗 */
  onEdit: (existing: PlatformConfig) => void | Promise<void>
  /** 没有配置时，以新增模式打开弹窗 */
  onCreate: () => void | Promise<void>
}

/**
 * 平台配置弹窗的打开流程：按 configType + provider 查询是否已有配置，
 * 有则以编辑模式打开，没有则以新增模式打开。AgentView 用这段流程，
 * 各自的表单字段映射与提交逻辑仍由调用方通过 useModal 处理
 *
 * loading 只是这次查询本身的状态，绑定到触发按钮即可，不用全局遮罩挡住整个页面
 */
export function usePlatformConfigModal(options: UsePlatformConfigModalOptions) {
  const { t } = useI18n()
  const { loading, execute } = useRequest()

  const openPlatformModal = async (configType: string, provider: string) => {
    const data = await execute(() => queryPlatformConfig(configType, provider), {
      errorText: t('common.getPlatformConfigFailed')
    })

    // 请求失败时 execute 已经弹过错误提示，这里直接放弃本次打开
    if (!data) {
      return
    }

    const existing = data.list[0]
    if (existing) {
      await options.onEdit(existing)
    } else {
      await options.onCreate()
    }
  }

  return {
    loading,
    openPlatformModal
  }
}
