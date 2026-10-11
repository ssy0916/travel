import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    // 监听 0.0.0.0，允许同一局域网内的手机/其他电脑通过本机 IP 访问
    host: true,
    port: 5173,
    proxy: {
      // 开发环境将 /api 转发到本地后端服务
      '/api': {
        target: 'http://localhost:3200',
        changeOrigin: true,
        configure: (proxy) => {
          // 透传 SSE 流式响应，避免 Vite 代理缓冲导致前端收不到 chunk
          proxy.on('proxyRes', (proxyRes, req, res) => {
            const ct = proxyRes.headers['content-type'] || ''
            if (ct.includes('text/event-stream')) {
              res.setHeader('Content-Type', 'text/event-stream')
              res.setHeader('Cache-Control', 'no-cache, no-transform')
              res.setHeader('Connection', 'keep-alive')
              res.flushHeaders?.()
            }
          })
        }
      }
    }
  }
})
