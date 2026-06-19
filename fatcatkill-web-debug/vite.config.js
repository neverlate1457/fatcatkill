import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '')
  const port = Number(process.env.VITE_DEV_SERVER_PORT || env.FRONTEND_DEBUG_PORT)

  return {
    envDir: '..',
    plugins: [vue()],
    server: {
      port
    }
  }
})
