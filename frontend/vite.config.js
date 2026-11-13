import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 開發模式：/api 轉發到本機 Spring Boot（正式環境由 Nginx 反向代理）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
