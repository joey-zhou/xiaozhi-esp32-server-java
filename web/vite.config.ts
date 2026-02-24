import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      vue(),
      vueDevTools(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
    },
    server: {
      port: 8084,
      host: '0.0.0.0',
      proxy: {
        '/api': {
          target: process.env.API_URL || env.VITE_BACKEND_URL || 'http://localhost:8091',
          changeOrigin: true,
          secure: true, // 支持 https
          rewrite: (path) => path.replace(/^\/api/, '/api')
        },
      }
    }
  }
})
