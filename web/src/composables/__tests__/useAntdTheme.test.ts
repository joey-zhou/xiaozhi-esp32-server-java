import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ConfigProvider } from 'ant-design-vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import { useAntdTheme } from '../useAntdTheme'

/**
 * Modal.confirm / message 等静态方法挂在 <a-config-provider> 组件树之外，
 * 只能靠 ConfigProvider.config() 写入的全局配置拿到主题，
 * 这里验证主题切换会同步调用它，且首次调用也会生效。
 */
// 模块作用域的 watch(immediate: true) 在上面这行 import 求值时就跑过了，
// 先把它留下的痕迹记下来，后面 beforeEach 的 mockClear 会把调用记录抹掉
const syncCallsOnModuleLoad = vi.mocked(ConfigProvider.config).mock.calls.length
const themeOnModuleLoad = (
  vi.mocked(ConfigProvider.config).mock.calls[0] as unknown as [{ theme?: ThemeConfig }] | undefined
)?.[0]?.theme

describe('useAntdTheme 同步静态方法主题', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(ConfigProvider.config).mockClear()
  })

  function lastSyncedTheme(): ThemeConfig | undefined {
    const calls = vi.mocked(ConfigProvider.config).mock.calls as unknown as Array<
      [{ theme?: ThemeConfig }]
    >
    const last = calls[calls.length - 1]
    return last?.[0]?.theme
  }

  it('模块加载时就同步一次，且调用多少次 useAntdTheme 都不会重复注册监听', () => {
    expect(syncCallsOnModuleLoad).toBe(1)
    // 未显式设置过主题时默认 auto，测试环境不支持 matchMedia 时按亮色兜底
    expect(themeOnModuleLoad?.token?.colorBgContainer).toBe('#ffffff')

    // 状态与 watch 都在模块作用域，AudioPlayer 这类按行渲染的组件再怎么调也不会多出监听
    useAntdTheme()
    useAntdTheme()
    expect(ConfigProvider.config).not.toHaveBeenCalled()
  })

  it('切到暗色主题时，重新把暗色 token 同步给 ConfigProvider.config', async () => {
    const { setTheme } = useAntdTheme()
    vi.mocked(ConfigProvider.config).mockClear()

    setTheme('dark')
    await nextTick()

    expect(ConfigProvider.config).toHaveBeenCalledTimes(1)
    expect(lastSyncedTheme()?.token?.colorBgContainer).toBe('#1f1f1f')
  })

  it('从暗色切回亮色时，同步的 token 也跟着变回亮色', async () => {
    const { setTheme } = useAntdTheme()
    setTheme('dark')
    await nextTick()
    vi.mocked(ConfigProvider.config).mockClear()

    setTheme('light')
    await nextTick()

    expect(ConfigProvider.config).toHaveBeenCalledTimes(1)
    expect(lastSyncedTheme()?.token?.colorBgContainer).toBe('#ffffff')
  })
})
