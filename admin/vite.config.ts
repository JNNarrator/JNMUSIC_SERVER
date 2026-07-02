import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // 核心：使用相对资源路径，确保后台部署在 /music/admin/ 子路径时 JS/CSS 仍能正确加载。
  base: './',
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/music': {
        target: 'http://127.0.0.1:19001',
        changeOrigin: true,
      }
    }
  }
})
