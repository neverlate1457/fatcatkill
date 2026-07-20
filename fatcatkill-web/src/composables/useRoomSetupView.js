import { computed, watch } from 'vue'
import { botNamePrefixes, methaneExcludedTargetRoles } from '../config/appConfig'
import { volunteerRoleOptions } from '../data/roles'
import { t } from '../i18n'

export const useRoomSetupView = ({
  connectedRooms,
  roomId,
  gameState,
  userId,
  hostMode,
  onlineRoomPlayers,
  customDeck,
  fatcatHintRoles,
  highRabbitRole,
  hostMethaneHallucinationTargetId,
  roomSize,
  displayName,
  roleName
}) => {
  const currentRoomInfo = computed(() => connectedRooms.value.find((room) => room.roomId === roomId.value) || null)
  const roomHostId = computed(() => currentRoomInfo.value?.hostId ?? gameState.value?.hostId ?? null)
  const isRoomHost = computed(() => roomHostId.value != null && Number(roomHostId.value) === Number(userId.value))
  const isHostSpectator = computed(() => hostMode.value && isRoomHost.value)
  const currentRoomPlayer = computed(() => onlineRoomPlayers.value.find((player) => Number(player.userId) === Number(userId.value)) || null)
  const isRoomParticipant = computed(() => Boolean(currentRoomPlayer.value) && !hostMode.value)
  const isPlayerReady = computed(() => currentRoomPlayer.value?.ready === true)

  const hostDeckHasHighRabbit = computed(() => customDeck.value.includes('HIGH_RABBIT'))
  const hostDeckHasMethane = computed(() => customDeck.value.includes('METHANE'))
  const availableFatcatHintRoles = computed(() => volunteerRoleOptions.filter((role) => !customDeck.value.includes(role)))
  const selectedFatcatHintRoles = computed(() => fatcatHintRoles.value.filter(Boolean))

  const fatcatHintOptionsFor = (slotIndex) => {
    const currentRole = fatcatHintRoles.value[slotIndex] || ''
    const selectedElsewhere = new Set(fatcatHintRoles.value.filter((role, index) => role && index !== slotIndex))
    return availableFatcatHintRoles.value.filter((role) => role === currentRole || !selectedElsewhere.has(role))
  }

  const cleanFatcatHintRoles = () => {
    const validRoles = new Set(availableFatcatHintRoles.value)
    const cleaned = []
    for (const role of fatcatHintRoles.value) {
      if (role && validRoles.has(role) && !cleaned.includes(role)) cleaned.push(role)
      if (cleaned.length === 3) break
    }
    if (cleaned.join('|') !== fatcatHintRoles.value.filter(Boolean).join('|')) fatcatHintRoles.value = cleaned
  }

  const setFatcatHintRole = (slotIndex, role) => {
    const next = [...fatcatHintRoles.value]
    if (role) next[slotIndex] = role
    else next.splice(slotIndex, 1)
    fatcatHintRoles.value = next
    cleanFatcatHintRoles()
  }

  const highRabbitRoleOptions = computed(() => [...new Set(customDeck.value.filter((role) => role && role !== 'HIGH_RABBIT'))])

  const hostAdvancedSummary = computed(() => {
    const summary = []
    summary.push(fatcatHintRoles.value.length ? t('setup.fatcatHintSummary', { count: fatcatHintRoles.value.length }) : t('setup.fatcatHintRandom'))
    summary.push(hostDeckHasHighRabbit.value && highRabbitRole.value ? t('setup.highRabbitSummary', { role: roleName(highRabbitRole.value) }) : t('setup.highRabbitRandom'))
    summary.push(hostDeckHasMethane.value && hostMethaneHallucinationTargetId.value ? t('setup.methaneTargetSet') : t('setup.methaneRandom'))
    return summary.join(' · ')
  })

  const isBotRoomPlayer = (player) => {
    if (player?.bot === true) return true
    const name = player?.username || player?.nickname || ''
    return botNamePrefixes.some((prefix) => name.startsWith(prefix))
  }

  const setupRoomPlayers = computed(() => {
    const backendPlayers = gameState.value?.players || []
    if (!backendPlayers.length) return onlineRoomPlayers.value
    const onlineReadyById = new Map(onlineRoomPlayers.value.map((player) => [Number(player.userId), player.ready === true]))
    return backendPlayers.map((player) => ({
      ...player,
      ready: isBotRoomPlayer(player) || onlineReadyById.get(Number(player.userId)) === true
    }))
  })

  const hostMethaneTargetOptions = computed(() => setupRoomPlayers.value
    .map((player, index) => ({ ...player, role: customDeck.value[index] }))
    .filter((player) => player.userId != null && player.role && !methaneExcludedTargetRoles.has(player.role)))

  const deckValidation = computed(() => {
    if (!hostMode.value) return ''
    if (customDeck.value.length !== Number(roomSize.value) || customDeck.value.some((role) => !role)) return t('validation.fillDeck')
    if (new Set(customDeck.value).size !== customDeck.value.length) return t('validation.duplicateRoles')
    if (customDeck.value.filter((role) => role === 'FATCAT').length !== 1) return t('validation.oneFatcat')
    if (selectedFatcatHintRoles.value.length > 3) return t('validation.maxHints')
    if (selectedFatcatHintRoles.value.some((role) => !availableFatcatHintRoles.value.includes(role))) return t('validation.absentVolunteerHints')
    if (new Set(selectedFatcatHintRoles.value).size !== selectedFatcatHintRoles.value.length) return t('validation.duplicateHints')
    if (hostDeckHasHighRabbit.value && highRabbitRole.value && !highRabbitRoleOptions.value.includes(highRabbitRole.value)) return t('validation.highRabbitRole')
    if (hostDeckHasMethane.value && hostMethaneHallucinationTargetId.value && !hostMethaneTargetOptions.value.some((player) => String(player.userId) === String(hostMethaneHallucinationTargetId.value))) return t('validation.methaneTarget')
    return ''
  })

  watch(customDeck, () => {
    cleanFatcatHintRoles()
    if (highRabbitRole.value && !highRabbitRoleOptions.value.includes(highRabbitRole.value)) highRabbitRole.value = ''
    if (hostMethaneHallucinationTargetId.value && !hostMethaneTargetOptions.value.some((player) => String(player.userId) === String(hostMethaneHallucinationTargetId.value))) {
      hostMethaneHallucinationTargetId.value = ''
    }
  }, { deep: true })

  const roomPlayerCount = computed(() => setupRoomPlayers.value.length)
  const roomCapacity = computed(() => {
    if (gameState.value?.status === 'PLAYING') return roomPlayerCount.value
    return Math.max(Number(currentRoomInfo.value?.capacity || roomSize.value), roomPlayerCount.value)
  })
  const roomOpenSlots = computed(() => Math.max(roomCapacity.value - roomPlayerCount.value, 0))
  const roomFillPercent = computed(() => roomCapacity.value ? Math.round((roomPlayerCount.value / roomCapacity.value) * 100) : 0)

  const roomSeatSlots = computed(() => {
    const playersBySeat = new Map(setupRoomPlayers.value
      .filter((player) => player.userId != null)
      .map((player) => [Number(player.userId), player]))
    return Array.from({ length: roomCapacity.value }, (_, index) => {
      const seatId = index + 1
      return { index: seatId, player: playersBySeat.get(seatId) || null }
    })
  })

  const readyRequiredPlayers = computed(() => setupRoomPlayers.value.filter((player) => Number(player.userId) !== Number(roomHostId.value)))
  const roomReadyCount = computed(() => readyRequiredPlayers.value.filter((player) => player.ready).length)
  const roomRequiredReadyCount = computed(() => readyRequiredPlayers.value.length)
  const allRoomPlayersReady = computed(() => roomPlayerCount.value > 0 && roomReadyCount.value === roomRequiredReadyCount.value)
  const canStartGame = computed(() => isRoomHost.value && !deckValidation.value && allRoomPlayersReady.value)

  const roomStartStatus = computed(() => {
    if (!roomPlayerCount.value) return ''
    if (allRoomPlayersReady.value) return roomRequiredReadyCount.value ? t('setup.allReady') : t('setup.hostCanStart')
    return t('setup.readyStatus', { ready: roomReadyCount.value, required: roomRequiredReadyCount.value })
  })

  const roomOccupancyText = computed(() => t('home.roomPlayers', { joined: roomPlayerCount.value, capacity: roomCapacity.value }))

  const seatTitle = (slot) => {
    if (slot.player) {
      const readyText = slot.player.ready ? t('common.ready') : t('common.notReady')
      return `${slot.player.userId} - ${slot.player.username} (${readyText})`
    }
    return isRoomParticipant.value ? t('setup.moveToSeat', { seat: slot.index }) : t('setup.openSlot', { seat: slot.index })
  }

  const seatActionText = (slot) => {
    if (slot.player) {
      if (isRoomHost.value && Number(slot.player.userId) !== Number(userId.value)) return t('setup.kick')
      return slot.player.ready ? t('common.ready') : t('common.waiting')
    }
    return isRoomParticipant.value ? t('setup.moveHere') : t('setup.openSeat')
  }

  const currentRoomPlayers = () => {
    const room = connectedRooms.value.find((entry) => entry.roomId === roomId.value)
    const players = (room?.players || [])
      .filter((player) => player.userId !== null && player.userId !== undefined)
      .map((player) => ({ userId: Number(player.userId), nickname: player.nickname || t('topbar.player', { id: player.userId }) }))
      .filter((player) => Number.isFinite(player.userId))

    const currentUserId = Number(userId.value)
    if (!hostMode.value && Number.isFinite(currentUserId) && !players.some((player) => player.userId === currentUserId)) {
      players.push({ userId: currentUserId, nickname: displayName.value.trim(), roomSize: Number(roomSize.value) })
    }
    return players
  }

  return {
    currentRoomInfo,
    roomHostId,
    isRoomHost,
    isHostSpectator,
    currentRoomPlayer,
    isRoomParticipant,
    isPlayerReady,
    hostDeckHasHighRabbit,
    hostDeckHasMethane,
    selectedFatcatHintRoles,
    fatcatHintOptionsFor,
    cleanFatcatHintRoles,
    setFatcatHintRole,
    highRabbitRoleOptions,
    hostAdvancedSummary,
    deckValidation,
    setupRoomPlayers,
    hostMethaneTargetOptions,
    roomPlayerCount,
    roomCapacity,
    roomOpenSlots,
    roomFillPercent,
    roomSeatSlots,
    allRoomPlayersReady,
    canStartGame,
    roomStartStatus,
    roomOccupancyText,
    seatTitle,
    seatActionText,
    currentRoomPlayers
  }
}
