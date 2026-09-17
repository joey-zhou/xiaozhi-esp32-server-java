/**
 * MCP 工具选择 Composable
 * 角色管理页用：加载系统工具、按禁用列表算默认勾选、算保存用的排除列表
 */

import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { getDisabledTools, getSystemGlobalTools } from '@/services/role'
import type { McpToolItem } from '@/types/mcpTool'

/** 系统工具描述兜底文案 key，工具名固定，不做动态拼接 */
const SYSTEM_TOOL_DESCRIPTION_KEYS: Record<string, string> = {
  func_playMusic: 'role.systemTool.playMusic',
  func_changeRole: 'role.systemTool.changeRole',
  func_playHuiBen: 'role.systemTool.playPictureBook',
  func_new_chat: 'role.systemTool.newChat',
  func_exitSession: 'role.systemTool.exitSession',
}

export function useMcpToolSelection() {
  const { t } = useI18n()

  // 状态
  const allMcpTools = ref<McpToolItem[]>([])
  const selectedToolNames = ref<string[]>([])
  const globalDisabledTools = ref<string[]>([])
  const mcpToolsLoading = ref(false)

  const getSystemToolDescription = (toolName: string): string => {
    return t(SYSTEM_TOOL_DESCRIPTION_KEYS[toolName] ?? 'role.systemTool.default')
  }

  /**
   * 加载工具列表
   * @param roleId 角色 ID，不传（新建角色）时禁用列表只取全局部分
   */
  const loadTools = async (roleId?: number) => {
    mcpToolsLoading.value = true
    try {
      const isEdit = typeof roleId === 'number' && roleId > 0
      const [systemRes, disabledRes] = await Promise.all([
        getSystemGlobalTools(),
        getDisabledTools(isEdit ? roleId : 0),
      ])

      const tools: McpToolItem[] = []

      if (systemRes.code === 200 && Array.isArray(systemRes.data)) {
        systemRes.data.forEach((tool) => {
          if (!tool.name) return
          tools.push({
            name: tool.name,
            description: tool.description || getSystemToolDescription(tool.name),
            inputSchema: '',
            inputSchemaData: [],
            enabled: true,
            source: 'system',
          })
        })
      }

      allMcpTools.value = tools

      if (disabledRes.code === 200 && disabledRes.data) {
        globalDisabledTools.value = disabledRes.data.globalDisabled || []
        const roleDisabled = isEdit ? (disabledRes.data.roleDisabled || []) : []
        selectedToolNames.value = tools
          .filter(tool => !roleDisabled.includes(tool.name) && !globalDisabledTools.value.includes(tool.name))
          .map(tool => tool.name)
      } else {
        globalDisabledTools.value = []
        selectedToolNames.value = tools.map(tool => tool.name)
      }
    } catch (error) {
      console.error('加载 MCP 工具失败:', error)
      message.error(t('role.mcpLoadToolsFailed'))
    } finally {
      mcpToolsLoading.value = false
    }
  }

  /** 重新加载工具列表，保留仍然存在的勾选 */
  const refreshTools = async (roleId?: number) => {
    const prevSelected = [...selectedToolNames.value]
    await loadTools(roleId)
    const availableNames = new Set(allMcpTools.value.map(tool => tool.name))
    selectedToolNames.value = prevSelected.filter(name => availableNames.has(name))
  }

  /**
   * 保存角色时提交的排除列表：全局禁用的工具不写进角色维度，
   * 否则解除全局禁用后该角色仍是禁用状态
   */
  const buildExcludeTools = (): string[] => {
    return allMcpTools.value
      .filter(tool => !globalDisabledTools.value.includes(tool.name))
      .filter(tool => !selectedToolNames.value.includes(tool.name))
      .map(tool => tool.name)
  }

  /** 可选工具，全局禁用的不出现在下拉里 */
  const availableTools = computed<McpToolItem[]>(() =>
    allMcpTools.value.filter(tool => !globalDisabledTools.value.includes(tool.name)))

  const availableToolGroups = computed(() => [
    { source: 'system', label: t('role.mcpSystemTools'), tools: availableTools.value },
  ].filter(group => group.tools.length > 0))

  /** 下拉搜索按工具名匹配，入参形状对齐 a-select 的 filterOption */
  const filterToolOption = (input: string, option?: { value?: string | number | null }) => {
    return String(option?.value ?? '').toLowerCase().includes(input.toLowerCase())
  }

  /** 去掉工具名前缀后展示 */
  const formatToolName = (toolName?: string): string => {
    if (!toolName) return ''
    return toolName
      .replace(/^func_/, '')
      .replace(/^XiaoZhi_MCP_Client_/, '')
  }

  return {
    allMcpTools,
    availableTools,
    availableToolGroups,
    buildExcludeTools,
    filterToolOption,
    formatToolName,
    globalDisabledTools,
    loadTools,
    mcpToolsLoading,
    refreshTools,
    selectedToolNames,
  }
}
