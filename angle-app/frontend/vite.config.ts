import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Any request React makes to /api/* is forwarded to Spring on port 9010.
      // Browser only ever sees localhost:5173 → no CORS in dev.
      '/api': {
        target: 'http://localhost:9010',
        changeOrigin: true
      }
    }
  }
})
