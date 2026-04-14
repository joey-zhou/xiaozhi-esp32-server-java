/**
 * Vitest 全局设置
 * 在所有测试执行前运行
 */

// Mock ant-design-vue message 组件（避免在测试中调用真实 DOM 通知）
vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    loading: vi.fn(),
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
  createI18n: vi.fn(),
}))
