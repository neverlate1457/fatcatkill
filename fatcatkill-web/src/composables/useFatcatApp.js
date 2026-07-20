import { computed } from 'vue'
import { socket } from '../socket'
import { customRoleOptions, volunteerRoleOptions } from '../data/roles'
import { fatcatHintSlotIndexes, HOST_DECKS_KEY, isDebugMode, modeLabels, nightPhases } from '../config/appConfig'
import { useActionError } from './useActionError'
import { useAppState } from './useAppState'
import { useAuth } from './useAuth'
import { useSocketEvents } from './useSocketEvents'
import { useGatewayClient } from './useGatewayClient'
import { useHostObserverView } from './useHostObserverView'
import { usePlayerGameView } from './usePlayerGameView'
import { useRoomSetupView } from './useRoomSetupView'
import { useRoomControls } from './useRoomControls'
import { historyTimeText, historyWinnerText, useGameHistory } from './useGameHistory'
import { downloadJson } from '../utils/downloadJson'
import { t, translateMessage } from '../i18n'

const createRoomId = () => {
  if (window.crypto?.getRandomValues) {
    const value = new Uint32Array(1)
    window.crypto.getRandomValues(value)
    return String(1000 + (value[0] % 9000))
  }
  return String(Date.now()).slice(-4).padStart(4, '0')
}

export const useFatcatApp = () => {
  const {
    gatewayUrl,
    clientId,
    authUser,
    authUsername,
    authPassword,
    authMode,
    roomId,
    userId,
    nickname,
    isConnected,
    joinedSocketId,
    serverMessage,
    gameState,
    actionError,
    actionNotice,
    pendingKickPlayer,
    showRoomList,
    connectedRooms,
    historyRecords,
    historyLoading,
    revealPlayers,
    testRoleAssignments,
    methaneSelection,
    mubaimuSelection,
    shushuSelection,
    selectedMode,
    roomSize,
    hostMode,
    customDeck,
    fatcatHintRoles,
    highRabbitRole,
    hostMethaneHallucinationTargetId,
    hostAdvancedOpen,
    deckName,
    savedDecks,
    selectedSavedDeck,
    phServiceTargetRole,
    displayName,
    isLoggedIn,
    saveSession,
    clearSavedRoom,
    clearRoomIdentity
  } = useAppState()

  const { showActionError, clearActionError } = useActionError(actionError, actionNotice)
  const showActionNotice = (message) => {
    const text = translateMessage(message)
    actionNotice.value = text
    if (text) clearActionError()
    return text
  }
  const clearActionNotice = () => { actionNotice.value = '' }
  const { joinSocketRoom, requestRoomList, emitGameAction } = useGatewayClient({
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
  })
  const { loadGameHistory, downloadHistoryRecord } = useGameHistory({
    gatewayUrl,
    authUser,
    isLoggedIn,
    historyLoading,
    historyRecords,
    showActionError,
    clearActionError
  })

  const { submitAuth: submitAuthBase, logoutAuth: clearAuthSession, loginAsGuest: loginAsGuestBase, requireLogin } = useAuth({
    gatewayUrl,
    clientId,
    authUser,
    authUsername,
    authPassword,
    authMode,
    nickname,
    isLoggedIn,
    loadGameHistory,
    showActionError,
    clearActionError
  })

  const syncRoomIdentityAfterAuth = async () => {
    if (!roomId.value || !socket.connected) return
    try {
      await joinSocketRoom({ force: true })
    } catch (error) {
      showActionError(error, t('error.joinRoom'))
    }
  }

  const submitAuth = async () => {
    const ok = await submitAuthBase()
    if (ok) await syncRoomIdentityAfterAuth()
  }

  const loginAsGuest = async () => {
    const ok = loginAsGuestBase()
    if (ok) await syncRoomIdentityAfterAuth()
  }
  const createHostedRoom = async () => {
    if (!requireLogin()) return
    hostMode.value = true
    if (!roomId.value) roomId.value = createRoomId()
    await joinRoom()
  }

  const createRoom = async () => {
    if (!requireLogin()) return
    hostMode.value = false
    if (!roomId.value) {
      roomId.value = createRoomId()
    }
    await joinRoom()
  }


  const openRoomList = async () => {
    showRoomList.value = true
    await requestRoomList()
  }
  const selectRoom = async (selectedRoomId) => {
    hostMode.value = false
    const selectedRoom = connectedRooms.value.find((room) => room.roomId === selectedRoomId)
    if (selectedRoom?.capacity) roomSize.value = selectedRoom.capacity
    roomId.value = selectedRoomId
    await joinRoom()
  }

  const joinRoom = async () => {
    if (!requireLogin()) return
    if (!roomId.value) {
      showActionError(t('error.enterRoomId'))
      return
    }

    try {
      const joinResponse = await joinSocketRoom({ force: true })
      if (joinResponse?.userId != null) userId.value = String(joinResponse.userId)
      isConnected.value = true
      showRoomList.value = false
      clearActionError()
      saveSession()
      await requestRoomList()
      await fetchRoomState()
    } catch (error) {
      isConnected.value = false
      showActionError(error, t('error.joinRoom'))
    }
  }

  const phase = computed(() => gameState.value?.currentPhase || 'WAITING')
  const winnerText = computed(() => {
    if (gameState.value?.winnerCamp === 'WOLF') return t('winner.fatcat')
    if (gameState.value?.winnerCamp === 'VILLAGER') return t('winner.volunteer')
    return t('winner.gameOver')
  })
  const isNight = computed(() => nightPhases.has(phase.value))
  const topbarUserLabel = computed(() => userId.value ? t('topbar.player', { id: userId.value }) : t('topbar.guest'))

  const {
    roleName,
    roleHint,
    testRoleOptions,
    myPlayer,
    myEffectiveRole,
    myDisplayedRole,
    canActAsRole,
    alivePlayers,
    eligibleVoters,
    nominatedPlayer,
    myVotedPlayer,
    confirmedAliveCount,
    ratManCheckerLabels,
    canUseDayVote,
    canUseFatcatTeamHint,
    canUseFatcatKill,
    canUseChenAction,
    canUseSaltedFishAction,
    fatcatHintButtonText,
    currentPhaseText,
    dayFlowText,
    tallyButtonText,
    botActionButtonText,
    myRoleHint,
    myEffectiveRoleHint,
    myPrivateMessage,
    voteCountFor,
    lastVoteRows,
    isMyVotedPlayer,
    canVoteFor,
    dayVoteButtonText
  } = usePlayerGameView({
    gameState,
    userId,
    phase,
    isNight,
    methaneSelection,
    mubaimuSelection,
    shushuSelection
  })

  const onlineRoomPlayers = computed(() => {
    if (!roomId.value) return []

    const room = connectedRooms.value.find((entry) => entry.roomId === roomId.value)
    const playersByKey = new Map()

    for (const player of room?.players || []) {
      const key = player.userId ?? player.socketId
      if (key == null) continue
      playersByKey.set(String(key), {
        userId: player.userId,
        username: player.nickname || t('topbar.player', { id: player.userId || '' }).trim(),
        ready: player.ready === true,
        socketId: player.socketId || null
      })
    }

    const currentUserId = Number(userId.value)
    if (!hostMode.value && Number.isFinite(currentUserId) && !playersByKey.has(String(currentUserId))) {
      playersByKey.set(String(currentUserId), {
        userId: currentUserId,
        username: displayName.value,
        ready: false,
        socketId: null
      })
    }

    return Array.from(playersByKey.values())
  })

  const {
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
  } = useRoomSetupView({
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
  })

  const {
    toggleReady,
    handleSeatClick,
    confirmKickRoomPlayer,
    cancelKickRoomPlayer
  } = useRoomControls({
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
  })
  const saveHostDeck = () => {
    if (deckValidation.value) { showActionError(deckValidation.value); return }
    const name = deckName.value.trim()
    if (!name) { showActionError(t('error.enterDeckName')); return }
    cleanFatcatHintRoles()
    const deck = { name, size: Number(roomSize.value), roles: [...customDeck.value], fatcatHintRoles: [...selectedFatcatHintRoles.value], highRabbitRole: highRabbitRole.value || '', methaneHallucinationTargetId: hostMethaneHallucinationTargetId.value || '' }
    savedDecks.value = [...savedDecks.value.filter((item) => item.name !== name), deck]
    window.localStorage.setItem(HOST_DECKS_KEY, JSON.stringify(savedDecks.value))
    selectedSavedDeck.value = name
    clearActionError()
  }

  const loadHostDeck = () => {
    const deck = savedDecks.value.find((item) => item.name === selectedSavedDeck.value)
    if (!deck) return
    roomSize.value = Number(deck.size)
    customDeck.value = [...deck.roles]
    fatcatHintRoles.value = [...(deck.fatcatHintRoles || [])]
    highRabbitRole.value = deck.highRabbitRole || ''
    hostMethaneHallucinationTargetId.value = deck.methaneHallucinationTargetId || ''
  }

  const activeRoleAssignments = () => Object.fromEntries(
    Object.entries(testRoleAssignments.value)
      .filter(([, role]) => role)
      .map(([playerId, role]) => [playerId, role])
  )

  const createMockRoom = async () => {
    if (!isDebugMode) return
    try {
      testRoleAssignments.value = {}
      const response = await emitGameAction(`/room/mock/${roomId.value}?count=${roomSize.value}`, {
        roomId: roomId.value,
        playerId: Number(userId.value)
      })
      gameState.value = response.gameState
      clearActionError()
      await requestRoomList()
    } catch (error) {
      showActionError(error, t('error.createMockRoom'))
    }
  }

  const fetchReveal = async () => {
    if (!isDebugMode) return
    try {
      const response = await emitGameAction(`/debug/reveal/${roomId.value}`, {}, 'GET')
      revealPlayers.value = response.data?.players || []
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.revealRoles'))
    }
  }
  const hostStartPayload = () => hostMode.value ? {
    hostMode: true,
    customDeck: [...customDeck.value],
    fatcatHintRoles: selectedFatcatHintRoles.value.length ? [...selectedFatcatHintRoles.value] : null,
    highRabbitRole: highRabbitRole.value || null,
    methaneHallucinationTargetId: hostDeckHasMethane.value && hostMethaneHallucinationTargetId.value ? Number(hostMethaneHallucinationTargetId.value) : null
  } : {}

  const {
    hostPlayerLabel,
    hostVoteRows,
    hostConfirmedVotes,
    hostVoteTotals,
    hostActivityLogs,
    hostActionLabel,
    hostLogTime,
    hostLogDetail
  } = useHostObserverView({ gameState, phase })

  const isObserverMode = computed(() => {
    if (!gameState.value || gameState.value.status === 'WAITING') return false
    if (isHostSpectator.value) return true
    return Boolean(myPlayer.value && myPlayer.value.alive === false)
  })
  const observerTitle = computed(() => isHostSpectator.value ? t('setup.hostMode') : t('gameOver.spectatorMode'))
  const observerSubtitle = computed(() => {
    if (!gameState.value) return ''
    if (isHostSpectator.value) return t('observer.activity')
    return t('gameOver.spectatorHint')
  })

  const sendAction = async (endpoint, targetId, actionType = null) => {
    const data = {
      roomId: roomId.value,
      playerId: Number(userId.value),
      targetId,
      actionType
    }

    try {
      const response = await emitGameAction(endpoint, data)
      gameState.value = response.gameState
      clearActionError()
      showActionNotice(response.message)
    } catch (error) {
      showActionError(error, t('error.actionFailed'))
    }
  }

  const fetchRoomState = async () => {
    try {
      const response = await emitGameAction(`/room/${roomId.value}`, {}, 'GET')
      gameState.value = response.gameState
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.loadRoom'))
    }
  }

  const sendSkillAction = async (actionType, targetId) => {
    try {
      const payload = {
        roomId: roomId.value,
        playerId: Number(userId.value),
        actionType,
        targetId
      }
      const response = await emitGameAction('/game/action', payload)
      showActionNotice(response.message)
      gameState.value = response.gameState
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.actionFailed'))
    }
  }

  const sendMethaneAction = async (targetId1, targetId2) => {
    try {
      const payload = {
        roomId: roomId.value,
        playerId: Number(userId.value),
        actionType: 'METHANE_CHECK',
        targetId1,
        targetId2
      }
      const response = await emitGameAction('/game/action', payload)
      showActionNotice(response.message)
      gameState.value = response.gameState
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.actionFailed'))
    }
  }

  const sendMubaimuAction = async () => {
    try {
      const payload = {
        roomId: roomId.value,
        playerId: Number(userId.value),
        actionType: 'MUBAIMU_ACTION',
        targetId1: mubaimuSelection.value[0] || null,
        targetId2: mubaimuSelection.value[1] || null,
        targetId3: mubaimuSelection.value[2] || null
      }
      const response = await emitGameAction('/game/action', payload)
      showActionNotice(response.message)
      gameState.value = response.gameState
      mubaimuSelection.value = []
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.actionFailed'))
    }
  }

  const sendShushuAction = async () => {
    if (shushuSelection.value.length !== 2) {
      showActionError(t('error.chooseTwoCompanions'))
      return
    }

    try {
      const payload = {
        roomId: roomId.value,
        playerId: Number(userId.value),
        actionType: 'SHUSHU_ACTION',
        targetId1: shushuSelection.value[0],
        targetId2: shushuSelection.value[1]
      }
      const response = await emitGameAction('/game/action', payload)
      showActionNotice(response.message)
      gameState.value = response.gameState
      shushuSelection.value = []
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.actionFailed'))
    }
  }

  const sendPhServiceAction = async () => {
    try {
      const payload = {
        roomId: roomId.value,
        playerId: Number(userId.value),
        actionType: 'PH_SERVICE_ACTION',
        targetRole: phServiceTargetRole.value
      }
      const response = await emitGameAction('/game/action', payload)
      showActionNotice(response.message)
      gameState.value = response.gameState
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.actionFailed'))
    }
  }

  const handleMethaneClick = (targetId) => {
    if (methaneSelection.value.includes(targetId)) {
      methaneSelection.value = methaneSelection.value.filter((id) => id !== targetId)
      return
    }

    methaneSelection.value.push(targetId)
    if (methaneSelection.value.length === 2) {
      sendMethaneAction(methaneSelection.value[0], methaneSelection.value[1])
      methaneSelection.value = []
    }
  }

  const handleMubaimuClick = (targetId) => {
    if (mubaimuSelection.value.includes(targetId)) {
      mubaimuSelection.value = mubaimuSelection.value.filter((id) => id !== targetId)
      return
    }
    if (mubaimuSelection.value.length >= 3) return
    mubaimuSelection.value.push(targetId)
  }

  const handleShushuClick = (targetId) => {
    if (shushuSelection.value.includes(targetId)) {
      shushuSelection.value = shushuSelection.value.filter((id) => id !== targetId)
      return
    }
    if (shushuSelection.value.length >= 2) return
    shushuSelection.value.push(targetId)
  }

  const startGame = async () => {
    try {
      if (deckValidation.value) throw new Error(deckValidation.value)
      if (!allRoomPlayersReady.value) throw new Error(t('error.playersMustReady'))
      const response = await emitGameAction(`/room/start/${roomId.value}?mode=${selectedMode.value}`, {
        roomId: roomId.value,
        playerId: Number(userId.value),
        ...(isDebugMode ? { roleAssignments: activeRoleAssignments() } : {}),
        ...hostStartPayload()
      })
      gameState.value = response.gameState
      clearActionError()
      showActionNotice(response.message)
    } catch (error) {
      showActionError(error, t('error.startGame'))
    }
  }

  const fillBotsOnly = async () => {
    try {
      await requestRoomList()
      const response = await emitGameAction(`/room/fill-bots/${roomId.value}?count=${roomSize.value}`, {
        roomId: roomId.value,
        playerId: Number(userId.value),
        hostMode: hostMode.value,
        players: currentRoomPlayers()
      })
      gameState.value = response.gameState
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.fillBots'))
    }
  }
  const fatcatKill = (targetId) => sendSkillAction('FATCAT_KILL', targetId)
  const fatcatTeamHint = () => sendSkillAction('FATCAT_TEAM_HINT', null)
  const emperorReveal = () => sendSkillAction('EMPEROR_REVEAL', null)
  const strAction = (targetId) => sendSkillAction('STR_ACTION', targetId)
  const strSkip = () => sendSkillAction('STR_SKIP', null)
  const liverAction = (targetId) => sendSkillAction('LIVER_ACTION', targetId)
  const canManAction = (targetId) => sendSkillAction('CANMAN_ACTION', targetId)
  const nangongAction = (targetId) => sendSkillAction('NANGONG_ACTION', targetId)
  const andyAction = () => sendSkillAction('ANDY_ACTION', null)
  const fishAction = (targetId) => sendSkillAction('SALTED_FISH_STAB', targetId)
  const fishSkip = () => sendSkillAction('SALTED_FISH_SKIP', null)
  const guoguoAction = () => sendSkillAction('GUOGUO_ACTION', null)
  const forvkusaAction = () => sendSkillAction('FORVKUSA_ACTION', null)
  const hatongAction = () => sendSkillAction('HATONG_ACTION', null)
  const xiaoxiangAction = () => sendSkillAction('XIAOXIANG_ACTION', null)
  const grassBeanAction = () => sendSkillAction('GRASS_BEAN_ACTION', null)
  const xiangxiangAction = () => sendSkillAction('XIANGXIANG_ACTION', null)
  const acCatAction = () => sendSkillAction('AC_CAT_ACTION', null)
  const mochiBossCheck = (targetId) => sendSkillAction('MOCHI_BOSS_CHECK', targetId)
  const chenAction = (targetId) => sendSkillAction('CHEN_ACTION', targetId)
  const chenSkip = () => sendSkillAction('CHEN_SKIP', null)
  const dayVote = (targetId) => sendAction('/day/vote', targetId)
  const confirmVote = () => sendAction('/day/vote/confirm', myPlayer.value?.votedTargetId || null)
  const cancelVote = () => sendAction('/day/vote/cancel', null)
  const skipVote = () => sendAction('/day/vote/skip', null)
  const startNomination = () => sendAction(`/day/nomination/${roomId.value}`, null)
  const tallyVotes = () => sendAction(`/day/tally/${roomId.value}`, null)

  const autoPlayBot = async () => {
    try {
      const payload = { roomId: roomId.value, playerId: Number(userId.value) }
      const response = await emitGameAction('/game/bot/auto', payload)
      gameState.value = response.gameState
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.botAction'))
    }
  }

  const downloadGameLogs = async () => {
    try {
      const res = await emitGameAction(`/logs/${roomId.value}`, {}, 'GET')
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
      downloadJson(res.data, `fatcatkill_room_${roomId.value}_${timestamp}.json`)
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.downloadLogs'))
    }
  }

  const resetToMain = ({ clearRoom = true } = {}) => {
    if (socket.connected) {
      socket.disconnect()
    }
    gameState.value = null
    methaneSelection.value = []
    mubaimuSelection.value = []
    shushuSelection.value = []
    serverMessage.value = ''
    clearActionError()
    isConnected.value = false
    joinedSocketId.value = null
    if (clearRoom) {
      clearRoomIdentity()
    }
  }

  const returnToMain = async () => {
    if (roomId.value && (gameState.value?.status === 'FINISHED' || phase.value === 'GAME_OVER')) {
      try {
        await emitGameAction(`/room/${roomId.value}`, {
          roomId: roomId.value,
          playerId: Number(userId.value)
        }, 'DELETE')
      } catch (error) {
        showActionError(error, t('error.closeRoom'))
        return
      }
      resetToMain()
      serverMessage.value = t('room.closed')
    } else {
      resetToMain()
    }
  }

  const leaveRoom = async () => {
    if (!socket.connected) {
      resetToMain()
      return true
    }

    try {
      const response = await emitWithAck(socket, 'leaveRoom', undefined, t('error.gatewayTimeout'))
      if (!response?.ok) throw response?.error || new Error(t('error.leaveRoom'))
      resetToMain()
      serverMessage.value = t('room.left')
      return true
    } catch (error) {
      showActionError(error, t('error.leaveRoom'))
      return false
    }
  }

  const logoutAuth = async () => {
    if (isConnected.value || roomId.value) {
      const leftRoom = await leaveRoom()
      if (!leftRoom) return
    }
    clearAuthSession()
    resetToMain()
  }

  useSocketEvents({
    socket,
    roomId,
    userId,
    hostMode,
    authUser,
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
  })

  return {
    authMode,
    authUsername,
    authPassword,
    nickname,
    userId,
    roomId,
    roomSize,
    authUser,
    displayName,
    showRoomList,
    connectedRooms,
    historyRecords,
    historyLoading,
    historyWinnerText,
    historyTimeText,
    submitAuth,
    loginAsGuest,
    logoutAuth,
    createRoom,
    createHostedRoom,
    openRoomList,
    requestRoomList,
    selectRoom,
    loadGameHistory,
    downloadHistoryRecord,
    isConnected,
    isNight,
    gameState,
    currentPhaseText,
    topbarUserLabel,
    isRoomHost,
    leaveRoom,
    actionError,
    actionNotice,
    pendingKickPlayer,
    winnerText,
    downloadGameLogs,
    returnToMain,
    selectedMode,
    selectedSavedDeck,
    isDebugMode,
    revealPlayers,
    testRoleAssignments,
    testRoleOptions,
    customDeck,
    hostAdvancedOpen,
    highRabbitRole,
    hostMethaneHallucinationTargetId,
    deckName,
    serverMessage,
    roomOccupancyText,
    isRoomParticipant,
    isPlayerReady,
    roomFillPercent,
    roomPlayerCount,
    roomOpenSlots,
    roomCapacity,
    roomSeatSlots,
    seatTitle,
    seatActionText,
    setupRoomPlayers,
    roomHostId,
    modeLabels,
    isHostSpectator,
    savedDecks,
    customRoleOptions,
    roleName,
    hostAdvancedSummary,
    fatcatHintRoles,
    fatcatHintSlotIndexes,
    fatcatHintOptionsFor,
    hostDeckHasHighRabbit,
    highRabbitRoleOptions,
    hostDeckHasMethane,
    hostMethaneTargetOptions,
    deckValidation,
    roomStartStatus,
    canStartGame,
    toggleReady,
    fetchRoomState,
    handleSeatClick,
    confirmKickRoomPlayer,
    cancelKickRoomPlayer,
    loadHostDeck,
    setFatcatHintRole,
    saveHostDeck,
    createMockRoom,
    fetchReveal,
    startGame,
    fillBotsOnly,
    isObserverMode,
    observerTitle,
    observerSubtitle,
    botActionButtonText,
    phase,
    hostVoteRows,
    hostConfirmedVotes,
    hostVoteTotals,
    hostActivityLogs,
    hostActionLabel,
    hostPlayerLabel,
    hostLogTime,
    hostLogDetail,
    autoPlayBot,
    phServiceTargetRole,
    myDisplayedRole,
    myPlayer,
    myRoleHint,
    myEffectiveRoleHint,
    myPrivateMessage,
    ratManCheckerLabels,
    dayFlowText,
    confirmedAliveCount,
    eligibleVoters,
    canUseDayVote,
    lastVoteRows,
    methaneSelection,
    mubaimuSelection,
    shushuSelection,
    canUseFatcatKill,
    canUseChenAction,
    canUseSaltedFishAction,
    canUseFatcatTeamHint,
    fatcatHintButtonText,
    volunteerRoleOptions,
    tallyButtonText,
    canActAsRole,
    voteCountFor,
    isMyVotedPlayer,
    canVoteFor,
    dayVoteButtonText,
    confirmVote,
    cancelVote,
    skipVote,
    strAction,
    fatcatKill,
    liverAction,
    canManAction,
    nangongAction,
    handleMubaimuClick,
    handleShushuClick,
    handleMethaneClick,
    mochiBossCheck,
    chenAction,
    fishAction,
    dayVote,
    fatcatTeamHint,
    sendPhServiceAction,
    emperorReveal,
    strSkip,
    guoguoAction,
    forvkusaAction,
    hatongAction,
    xiaoxiangAction,
    sendMubaimuAction,
    sendShushuAction,
    grassBeanAction,
    xiangxiangAction,
    acCatAction,
    andyAction,
    chenSkip,
    fishSkip,
    startNomination,
    tallyVotes
  }
}
