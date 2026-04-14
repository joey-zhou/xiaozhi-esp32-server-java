/**
 * MCP工具相关类型定义
 */

/**
 * 系统全局工具摘要
 */
export interface SystemGlobalToolSummary {
  name: string
  description: string
}

/**
 * MCP工具项
 */
export interface McpToolItem {
  name: string
  description: string
  inputSchema: string
  inputSchemaData: McpToolSchemaProperty[]
  enabled: boolean
  source: string
}

/**
 * MCP工具参数属性
 */
export interface McpToolSchemaProperty {
  name: string
  type: string
  description: string
  required: boolean
}
