import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '')
  const port = Number(process.env.VITE_DEV_SERVER_PORT || env.FRONTEND_DEBUG_PORT)

  return {
    envDir: '..',
    plugins: [vue()],
    server: {
      port,
      proxy: {
        '/socket.io': {
          target: env.VITE_DEV_GATEWAY_URL || ('http://localhost:' + env.GATEWAY_PORT),
          ws: true
        }
      }
    }
  }
})
