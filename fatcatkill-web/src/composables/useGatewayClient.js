import { t } from '../i18n'
import { emitWithAck, SOCKET_ACK_TIMEOUT_MS } from '../utils/socketAck'

export const useGatewayClient = ({
  socket,
  roomId,
  userId,
  clientId,
  nickname,
  roomSize,
  hostMode,
  authUser,
  joinedSocketId,
  connectedRooms,
  showActionError
}) => {
  const ensureSocketConnected = () => {
    if (socket.connected) return Promise.resolve()

    return new Promise((resolve, reject) => {
      const cleanup = () => {
        globalThis.clearTimeout(timer)
        socket.off('connect', onConnect)
        socket.off('connect_error', onError)
      }
      const onConnect = () => {
        cleanup()
        resolve()
      }
      const onError = (error) => {
        cleanup()
        reject(error || new Error(t('error.gatewayTimeout')))
      }
      const timer = globalThis.setTimeout(() => {
        cleanup()
        reject(new Error(t('error.gatewayTimeout')))
      }, SOCKET_ACK_TIMEOUT_MS)

      socket.once('connect', onConnect)
      socket.once('connect_error', onError)
      socket.connect()
    })
  }

  const joinSocketRoom = async ({ force = false } = {}) => {
    await ensureSocketConnected()
    if (!force && joinedSocketId.value === socket.id) return

    const response = await emitWithAck(socket, 'joinRoom', {
      roomId: roomId.value,
      userId: userId.value ? Number(userId.value) : null,
      clientId,
      nickname: nickname.value.trim(),
      roomSize: Number(roomSize.value),
      spectator: hostMode.value,
      accountId: authUser.value?.guest ? null : authUser.value?.id,
      sessionToken: authUser.value?.guest ? null : authUser.value?.sessionToken
    }, t('error.joinTimeout'))
    if (!response?.ok) throw response?.error || new Error(t('error.joinRoom'))
    joinedSocketId.value = socket.id
    return response
  }

  const requestRoomList = async () => {
    await ensureSocketConnected()
    return new Promise((resolve) => {
      socket.emit('listRooms', (response) => {
        if (response?.ok) {
          connectedRooms.value = response.rooms || []
        } else if (response?.error) {
          showActionError(response.error, t('error.loadRoomList'))
        }
        resolve(response)
      })
    })
  }

  const emitGameAction = async (endpoint, data, method = 'POST') => {
    await joinSocketRoom()
    const response = await emitWithAck(socket, 'gameAction', { roomId: roomId.value, endpoint, data, method }, t('error.gatewayTimeout'))
    if (!response?.ok) throw response?.error || new Error(t('error.actionFailed'))
    return response
  }

  return {
    ensureSocketConnected,
    joinSocketRoom,
    requestRoomList,
    emitGameAction
  }
}
