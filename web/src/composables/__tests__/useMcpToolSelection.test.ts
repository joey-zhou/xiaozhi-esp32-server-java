import { beforeEach, describe, expect, it, vi } from 'vitest'

const roleServiceMock = vi.hoisted(() => ({
  getDisabledTools: vi.fn(),
  getSystemGlobalTools: vi.fn(),
}))

// vue-i18n 与 ant-design-vue 用 setup.ts 里的全局 mock：
// 这里的 composable 经 @/services/role -> services/request 间接引到 '@/locales'，
// 局部 mock 少一个 createI18n 就会在求值阶段炸
vi.mock('@/services/role', () => roleServiceMock)

import { message } from 'ant-design-vue'

import { useMcpToolSelection } from '../useMcpToolSelection'

const systemResponse = () => ({
  code: 200,
  message: '',
  data: [
    { name: 'func_playMusic', description: '' },
    { name: 'func_exitSession', description: '退出' },
    { name: 'func_changeRole', description: '切换角色' },
  ],
})

describe('useMcpToolSelection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    roleServiceMock.getSystemGlobalTools.mockResolvedValue(systemResponse())
    roleServiceMock.getDisabledTools.mockResolvedValue({
      code: 200,
      message: '',
      data: { globalDisabled: ['func_playMusic'], roleDisabled: ['func_changeRole'] },
    })
  })

  it('默认勾选排除角色禁用与全局禁用的工具，全局禁用的也不出现在下拉里', async () => {
    const selection = useMcpToolSelection()
    await selection.loadTools(7)

    expect(selection.selectedToolNames.value).toEqual(['func_exitSession'])
    expect(selection.availableTools.value.map(tool => tool.name)).not.toContain('func_playMusic')
  })

  it('buildExcludeTools 只提交被取消勾选的工具，不把全局禁用写进角色维度', async () => {
    const selection = useMcpToolSelection()
    await selection.loadTools(7)
    selection.selectedToolNames.value = ['func_changeRole']

    expect(selection.buildExcludeTools()).toEqual(['func_exitSession'])
  })

  it('新建角色时禁用列表只取全局部分', async () => {
    const selection = useMcpToolSelection()
    await selection.loadTools()

    expect(roleServiceMock.getDisabledTools).toHaveBeenCalledWith(0)
    expect(selection.selectedToolNames.value).toEqual(['func_exitSession', 'func_changeRole'])
  })

  it('系统工具描述为空时用文案 key 兜底', async () => {
    const selection = useMcpToolSelection()
    await selection.loadTools(7)

    const playMusic = selection.allMcpTools.value.find(tool => tool.name === 'func_playMusic')
    expect(playMusic?.description).toBe('role.systemTool.playMusic')
  })

  // 两路工具是一次 Promise.all，任一路挂了整批就没有结果，只弹一条失败提示
  it('某一路挂了时工具列表为空，只弹一条提示', async () => {
    roleServiceMock.getSystemGlobalTools.mockRejectedValue(new Error('boom'))
    const selection = useMcpToolSelection()
    await selection.loadTools(7)

    expect(selection.allMcpTools.value).toEqual([])
    expect(message.error).toHaveBeenCalledTimes(1)
    expect(message.error).toHaveBeenCalledWith('role.mcpLoadToolsFailed')
    expect(selection.mcpToolsLoading.value).toBe(false)
  })


})
