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
        include: ['src/utils/**', 'src/store/**', 'src/composables/**'],
        exclude: ['src/**/__tests__/**', 'src/**/*.d.ts'],
      },
    },
  }),
)
