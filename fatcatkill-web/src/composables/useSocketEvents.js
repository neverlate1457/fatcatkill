import { t, translateMessage } from '../i18n'
import { onMounted } from 'vue'

export const useSocketEvents = ({
  socket,
  roomId,
  userId,
  hostMode,
  joinedSocketId,
  serverMessage,
  gameState,
  connectedRooms,
  isLoggedIn,
  loadGameHistory,
  saveSession,
  joinRoom,
  resetToMain,
  showActionError,
  clearActionError
}) => {
  onMounted(() => {
    socket.on('disconnect', () => {
      joinedSocketId.value = null
    })

    socket.on('message', (msg) => {
      serverMessage.value = translateMessage(msg)
    })

    socket.on('gameStateUpdate', (newState) => {
      gameState.value = newState
      clearActionError()
    })

    socket.on('roomListUpdate', (rooms) => {
      connectedRooms.value = rooms || []
    })

    if (isLoggedIn.value) loadGameHistory()

    socket.on('roomIdentityUpdate', (identity) => {
      if (identity?.roomId && String(identity.roomId) === String(roomId.value) && identity.userId != null) {
        userId.value = String(identity.userId)
        hostMode.value = identity.spectator === true
        saveSession()
      }
    })

    socket.on('kickedFromRoom', (payload) => {
      resetToMain()
      serverMessage.value = translateMessage(payload?.message) || t('room.kicked')
    })

    socket.on('actionError', (errorMsg) => {
      showActionError(translateMessage(errorMsg) || t('error.actionFailed'))
    })

    socket.on('roomClosed', () => {
      resetToMain()
      serverMessage.value = t('room.closed')
    })

    if (roomId.value) joinRoom()
  })
}

