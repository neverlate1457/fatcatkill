import { onMounted, onUnmounted } from 'vue'
import { t, translateMessage } from '../i18n'

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
  const handlers = {
    disconnect: () => {
      joinedSocketId.value = null
    },
    message: (msg) => {
      serverMessage.value = translateMessage(msg)
    },
    gameStateUpdate: (newState) => {
      gameState.value = newState
      clearActionError()
    },
    roomListUpdate: (rooms) => {
      connectedRooms.value = rooms || []
    },
    roomIdentityUpdate: (identity) => {
      if (identity?.roomId && String(identity.roomId) === String(roomId.value) && identity.userId != null) {
        userId.value = String(identity.userId)
        hostMode.value = identity.spectator === true
        saveSession()
      }
    },
    kickedFromRoom: (payload) => {
      resetToMain()
      serverMessage.value = translateMessage(payload?.message) || t('room.kicked')
    },
    actionError: (errorMsg) => {
      showActionError(translateMessage(errorMsg) || t('error.actionFailed'))
    },
    roomClosed: () => {
      resetToMain()
      serverMessage.value = t('room.closed')
    }
  }

  onMounted(() => {
    Object.entries(handlers).forEach(([eventName, handler]) => socket.on(eventName, handler))

    if (isLoggedIn.value) loadGameHistory()
    if (roomId.value) joinRoom()
  })

  onUnmounted(() => {
    Object.entries(handlers).forEach(([eventName, handler]) => socket.off(eventName, handler))
  })
}
