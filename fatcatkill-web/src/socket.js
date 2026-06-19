import { io } from 'socket.io-client'
import { resolveBrowserServiceUrl } from './serviceUrl'

const URL = resolveBrowserServiceUrl(import.meta.env.VITE_GATEWAY_URL)

export const socket = io(URL, {
  autoConnect: false
})

