import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig(({ mode, command }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      vue(),
      vueDevTools(),
      // ant-design-vue 改按需引入：
      ...(mode === 'test'
        ? []
        : [Components({
            dts: false,
            resolvers: [AntDesignVueResolver({ importStyle: false, resolveIcons: false })],
          })]),
    ],
    // 生产构建剥离 console/debugger，避免内部状态和调试信息随产物暴露到浏览器控制台；
    // dev server 保留，不影响本地调试
    esbuild: command === 'build' ? { drop: ['console', 'debugger'] } : undefined,
    // vue-i18n 的编译期特性开关，不给值时它会在运行时全部按 true 兜底，把用不到的代码打进产物。
    // locales/index.ts 用的是 legacy: false 的 Composition API，模板里也没有 <i18n-t> / v-t，
    // 所以整包安装（内置组件 + v-t 指令）和 legacy 兼容层都能摇掉；
    // 模板里的 $t 由 globalInjection 提供，不受 FULL_INSTALL 影响。
    define: {
      __VUE_I18N_FULL_INSTALL__: false,
      __VUE_I18N_LEGACY_API__: false,
      __INTLIFY_PROD_DEVTOOLS__: false,
    },
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
          // 只在 vite dev server 生效；容器里跑的是 nginx，转发规则见 web/nginx.conf
          target: env.VITE_BACKEND_URL || 'http://localhost:8091',
          changeOrigin: true,
          // 后端地址是 https 且用自签名证书时改成 false，否则代理会因证书校验失败
          secure: true
        },
        // 后端提供的录音与上传文件，dev 下同样代理过去，避免页面里出现跨域的绝对地址
        '^/(audio|uploads)/': {
          target: env.VITE_BACKEND_URL || 'http://localhost:8091',
          changeOrigin: true,
          secure: true
        },
        // WebSocket 挂在 dialogue 进程上，代理后 dev 与容器部署的连接地址形式一致（同源 /ws/...）
        '/ws': {
          target: env.VITE_WS_TARGET || 'http://localhost:8092',
          changeOrigin: true,
          ws: true,
          secure: true
        },
      }
    }
  }
})
