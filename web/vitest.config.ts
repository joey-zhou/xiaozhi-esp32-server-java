import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig({ mode: 'test', command: 'serve', isSsrBuild: false, isPreview: false }),
  defineConfig({
    test: {
      environment: 'jsdom',
      include: ['src/**/__tests__/**/*.{test,spec}.ts'],
      globals: true,
      root: fileURLToPath(new URL('./', import.meta.url)),
      setupFiles: ['src/__tests__/setup.ts'],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov'],
        include: ['src/**/*.{ts,vue}'],
        exclude: ['src/**/__tests__/**', 'src/**/*.d.ts', 'src/main.ts'],
        // 实测水位（46.11 / 34.05 / 38.84 / 46.91）向下取整留一点余量，作用是防倒退而不是设目标：
        // 覆盖率掉下来会直接失败，补了测试就往上抬这几个数
        thresholds: {
          statements: 45,
          branches: 32,
          functions: 37,
          lines: 45,
        },
      },
    },
  }),
)
