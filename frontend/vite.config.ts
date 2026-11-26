import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

const backendProxyTarget = process.env.VITE_BACKEND_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: backendProxyTarget,
        changeOrigin: true,
      },
      '/actuator': {
        target: backendProxyTarget,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    testTimeout: 30000,
    setupFiles: './src/test/setup.ts',
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    css: true,
  },
})
