import { t } from '../i18n'
import { emitWithAck } from '../utils/socketAck'

export const useRoomControls = ({
  socket,
  gameState,
  userId,
  hostMode,
  pendingKickPlayer,
  isPlayerReady,
  isRoomHost,
  requestRoomList,
  showActionError,
  clearActionError,
  clearActionNotice
}) => {
  const emitRoomControl = async (eventName, payload = {}) => {
    const response = await emitWithAck(socket, eventName, payload, t('error.gatewayTimeout'))
    if (!response?.ok) throw response?.error || new Error(t('error.roomAction'))
    return response
  }

  const toggleReady = async () => {
    try {
      await emitRoomControl('setReady', { ready: !isPlayerReady.value })
      clearActionError()
      await requestRoomList()
    } catch (error) {
      showActionError(error, t('error.updateReady'))
    }
  }

  const moveToSeat = async (slot) => {
    if (slot.player || hostMode.value || gameState.value?.status === 'PLAYING') return
    try {
      const response = await emitRoomControl('moveSeat', { seatId: slot.index })
      if (response?.userId != null) userId.value = String(response.userId)
      clearActionError()
      await requestRoomList()
    } catch (error) {
      showActionError(error, t('error.moveSeat'))
    }
  }

  const kickRoomPlayer = async (player) => {
    if (!isRoomHost.value || !player || Number(player.userId) === Number(userId.value)) return
    pendingKickPlayer.value = player
  }

  const confirmKickRoomPlayer = async () => {
    const player = pendingKickPlayer.value
    if (!isRoomHost.value || !player || Number(player.userId) === Number(userId.value)) return
    try {
      await emitRoomControl('kickPlayer', { userId: Number(player.userId) })
      clearActionError()
      pendingKickPlayer.value = null
      clearActionNotice()
      await requestRoomList()
    } catch (error) {
      showActionError(error, t('error.kickPlayer'))
    }
  }

  const cancelKickRoomPlayer = () => {
    pendingKickPlayer.value = null
  }

  const handleSeatClick = (slot) => {
    if (slot.player) {
      kickRoomPlayer(slot.player)
      return
    }
    moveToSeat(slot)
  }

  return {
    toggleReady,
    handleSeatClick,
    confirmKickRoomPlayer,
    cancelKickRoomPlayer
  }
}
