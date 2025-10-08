/**
 * 设备信息接口
 */
export interface Device {
  createTime?: string | null
  updateTime?: string | null
  roleId?: number | null
  avatar?: string | null
  roleName?: string | null
  roleDesc?: string | null
  voiceName?: string | null
  state?: string | null
  ttsId?: number | null
  modelId?: number | null
  modelName?: string | null
  sttId?: number | null
  temperature?: number | null
  topP?: number | null
  vadEnergyTh?: number | null
  vadSpeechTh?: number | null
  vadSilenceTh?: number | null
  vadSilenceMs?: number | null
  modelProvider?: string | null
  ttsProvider?: string | null
  isDefault?: string | null
  totalDevice?: number | null
  deviceId: string
  sessionId?: string | null
  deviceName?: string | null
  totalMessage?: number | null
  audioPath?: string | null
  lastLogin?: string | null
  wifiName?: string | null
  ip?: string | null
  chipModelName?: string | null
  type?: string | null
  version?: string | null
  functionNames?: string | null
  location?: string | null
  editable?: boolean // 表格编辑状态
}

/**
 * 设备查询参数
 */
export interface DeviceQueryParams {
  start: number
  limit: number
  deviceId?: string
  deviceName?: string
  roleName?: string
  state?: string | number
}

/**
 * 设备列表响应
 */
export interface DeviceListResponse {
  list: Device[]
  total: number
  pageNum: number
  pageSize: number
  size: number
  startRow: number
  endRow: number
  pages: number
  prePage: number
  nextPage: number
  isFirstPage: boolean
  isLastPage: boolean
  hasPreviousPage: boolean
  hasNextPage: boolean
  navigatePages: number
  navigatepageNums: number[]
  navigateFirstPage: number
  navigateLastPage: number
}

/**
 * 角色信息接口
 */
export interface Role {
  roleId: string
  roleName: string
  roleDesc?: string
  prompt?: string
  model?: string
  ttsVoice?: string
  createTime?: string
  updateTime?: string
}

/**
 * 角色列表响应
 */
export interface RoleListResponse {
  list: Role[]
  total: number
  pageNum: number
  pageSize: number
  size: number
  startRow: number
  endRow: number
  pages: number
  prePage: number
  nextPage: number
  isFirstPage: boolean
  isLastPage: boolean
  hasPreviousPage: boolean
  hasNextPage: boolean
  navigatePages: number
  navigatepageNums: number[]
  navigateFirstPage: number
  navigateLastPage: number
}

