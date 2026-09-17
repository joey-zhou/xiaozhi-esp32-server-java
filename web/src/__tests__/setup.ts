/**
 * Vitest 全局设置
 * 在所有测试执行前运行
 */

// Mock ant-design-vue message 组件（避免在测试中调用真实 DOM 通知）
// theme 也要给：useAntdTheme 在模块顶层就读 theme.darkAlgorithm，缺了会让引用它的组件整份加载失败
vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    loading: vi.fn(),
  },
  theme: {
    defaultAlgorithm: vi.fn(),
    darkAlgorithm: vi.fn(),
    compactAlgorithm: vi.fn(),
  },
  Modal: {
    confirm: vi.fn(),
    info: vi.fn(),
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  },
}))

// Mock vue-i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      // 返回 key 本身，方便断言
      if (params) {
        return `${key}:${JSON.stringify(params)}`
      }
      return key
    },
    locale: { value: 'zh-CN' },
  }),
  // locales/index.ts 在模块顶层就 `export const { t } = i18n.global`，
  // 返回 undefined 会让任何 import 到 '@/locales' 的模块在求值阶段直接炸
  createI18n: vi.fn(() => ({
    global: {
      t: (key: string, params?: Record<string, unknown>) =>
        params ? `${key}:${JSON.stringify(params)}` : key,
      locale: { value: 'zh-CN' },
    },
  })),
}))
