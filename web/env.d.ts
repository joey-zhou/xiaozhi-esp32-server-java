/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 接口基础路径，开发环境走 Vite proxy 所以是相对路径 */
  readonly VITE_API_BASE_URL: string
  /** WebSocket 接入地址，对应后端 dialogue 进程的 /ws/xiaozhi/v1；留空按当前页面地址推导 */
  readonly VITE_WS_URL: string
  /** 浏览器标题与页脚展示用 */
  readonly VITE_APP_TITLE: string
  /** 静态资源（头像、音频、固件）的后端地址，供 getResourceUrl 拼接；留空走同源相对路径 */
  readonly VITE_BACKEND_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
