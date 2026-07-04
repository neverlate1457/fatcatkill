import { t } from '../i18n'
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
    return new Promise((resolve) => {
      socket.connect()
      socket.once('connect', resolve)
    })
  }

  const joinSocketRoom = async ({ force = false } = {}) => {
    await ensureSocketConnected()
    if (!force && joinedSocketId.value === socket.id) return

    return new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => reject(new Error(t('error.joinTimeout'))), 10000)
      socket.emit('joinRoom', {
        roomId: roomId.value,
        userId: userId.value ? Number(userId.value) : null,
        clientId,
        nickname: nickname.value.trim(),
        roomSize: Number(roomSize.value),
        spectator: hostMode.value,
        accountId: authUser.value?.guest ? null : authUser.value?.id,
        sessionToken: authUser.value?.guest ? null : authUser.value?.sessionToken
      }, (response) => {
        window.clearTimeout(timer)
        if (!response?.ok) {
          reject(response?.error || new Error(t('error.joinRoom')))
          return
        }
        joinedSocketId.value = socket.id
        resolve(response)
      })
    })
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
    return new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => reject(new Error(t('error.gatewayTimeout'))), 10000)
      socket.emit('gameAction', { roomId: roomId.value, endpoint, data, method }, (response) => {
        window.clearTimeout(timer)
        if (!response?.ok) {
          reject(response?.error || new Error(t('error.actionFailed')))
          return
        }
        resolve(response)
      })
    })
  }

  return {
    ensureSocketConnected,
    joinSocketRoom,
    requestRoomList,
    emitGameAction
  }
}
