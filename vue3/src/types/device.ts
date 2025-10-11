/**
 * 设备信息接口
 */
export interface Device {
  createTime?: string
  updateTime?: string
  roleId?: number
  avatar?: string
  roleName?: string
  roleDesc?: string
  voiceName?: string
  state?: string
  ttsId?: number
  modelId?: number
  modelName?: string
  sttId?: number
  temperature?: number
  topP?: number
  vadEnergyTh?: number
  vadSpeechTh?: number
  vadSilenceTh?: number
  vadSilenceMs?: number
  modelProvider?: string
  ttsProvider?: string
  isDefault?: string
  totalDevice?: number
  deviceId: string
  sessionId?: string
  deviceName?: string
  totalMessage?: number
  audioPath?: string
  lastLogin?: string
  wifiName?: string
  ip?: string
  chipModelName?: string
  type?: string
  version?: string
  functionNames?: string
  location?: string
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
  roleId: number
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

