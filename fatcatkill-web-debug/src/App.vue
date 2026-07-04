<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { socket } from './socket'
import { resolveBrowserServiceUrl } from './serviceUrl'
import { roleTranslations, translateRole } from './roleTranslations'

const STORAGE_KEY = 'fatcatkill.session'
const CLIENT_ID_KEY = 'fatcatkill.clientId'
const HOST_DECKS_KEY = 'fatcatkill.hostDecks'
const AUTH_KEY = 'fatcatkill.authUser'
const GATEWAY_URL = resolveBrowserServiceUrl(import.meta.env.VITE_GATEWAY_URL)
const clientId = (() => {
  const saved = window.localStorage.getItem(CLIENT_ID_KEY)
  if (saved) return saved
  const created = window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
  window.localStorage.setItem(CLIENT_ID_KEY, created)
  return created
})()


const loadSavedAuthUser = () => {
  try {
    return JSON.parse(window.localStorage.getItem(AUTH_KEY) || 'null')
  } catch {
    return null
  }
}

const loadSavedSession = () => {
  try {
    return JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

const savedSession = loadSavedSession()
const authUser = ref(loadSavedAuthUser())
const authUsername = ref(authUser.value?.username || '')
const authPassword = ref('')
const authMode = ref('login')

const roomId = ref(savedSession.roomId || '')
const userId = ref(savedSession.userId || '')
const nickname = ref(savedSession.nickname || '')
const isConnected = ref(false)
const joinedSocketId = ref(null)
const serverMessage = ref('')
const gameState = ref(null)
const actionError = ref('')
const showRoomList = ref(false)
const connectedRooms = ref([])
const historyRecords = ref([])
const historyLoading = ref(false)
const revealPlayers = ref([])
const methaneSelection = ref([])
const mubaimuSelection = ref([])
const shushuSelection = ref([])
const selectedMode = ref('OLD_HOME')
const roomSize = ref(savedSession.roomSize || 7)
const hostMode = ref(Boolean(savedSession.hostMode))
const defaultHostDeck = (size) => ['FATCAT', 'LIVER_INDEX', 'HIGH_RABBIT', 'METHANE', 'GUOGUO', 'XIANGXIANG', 'AC_CAT', 'FORVKUSA', 'HATONG', 'CAN_MAN'].slice(0, Number(size) || 7)
const customDeck = ref(Array.isArray(savedSession.customDeck) && savedSession.customDeck.length === Number(roomSize.value) ? [...savedSession.customDeck] : defaultHostDeck(roomSize.value))
const fatcatHintRoles = ref(Array.isArray(savedSession.fatcatHintRoles) ? [...savedSession.fatcatHintRoles] : [])
const highRabbitRole = ref(savedSession.highRabbitRole || '')
const hostMethaneHallucinationTargetId = ref(savedSession.hostMethaneHallucinationTargetId || '')
const hostAdvancedOpen = ref(Boolean(savedSession.hostAdvancedOpen))
const deckName = ref('')
const savedDecks = ref((() => { try { return JSON.parse(window.localStorage.getItem(HOST_DECKS_KEY) || '[]') } catch { return [] } })())
const selectedSavedDeck = ref('')
const phServiceTargetRole = ref('METHANE')
const testRoleAssignments = ref({})

const fatcatHorcruxRoles = new Set([
  'LIVER_INDEX',
  'PINK_RABBIT',
  'EMPEROR',
  'NTHU_MATH',
  'MAGIC_MEOW',
  'PH_SERVICE',
  'RAT_MAN'
])

const roleHints = {
  WEREWOLF: 'Wake at night and choose a player to kill with the other werewolves.',
  VILLAGER: 'You have no night power. Use discussion, nomination, and voting to find the enemy.',
  SEER: 'At night, verify one player to learn whether they are on the werewolf side.',
  WITCH: 'At night, choose to save, poison, or skip. Your potion choice affects the selected player.',
  HUNTER: 'A classic hunter role. If this mode enables hunter resolution, you may take a shot when removed.',
  GUARD: 'A classic guard role. Protect a player at night if this mode enables guard actions.',
  METHANE: 'Each night, choose two players. You learn whether at least one appears to be Fatcat side.',
  GUOGUO: 'On the first night, learn the seat number of one Fatcat-side player.',
  XIANGXIANG: 'Each night, check whether your two alive neighbors are both Fatcat side.',
  AC_CAT: 'Each night, learn the exact role of the player exiled on the previous day.',
  FORVKUSA: 'On the first night, learn how many adjacent seat pairs belong to the Fatcat faction.',
  HATONG: 'On nights after you voted, learn whether Fatcat voted on the previous day.',
  KB: 'The first time you are nominated for exile by a volunteer-army player, that nominator dies immediately.',
  SALTED_FISH: 'Once during voting, stab another player. If they are Fatcat faction, they die; otherwise you die.',
  XIAOXIANG: 'On the first night, learn how many anti-Fatcat alliance players are present.',
  MOCHI_BOSS: 'You cannot be killed by Fatcat at night. Each time Fatcat attacks you, you may check one player for Fatcat.',
  GRASS_BEAN: 'On the first night, learn one present Fatcat horcrux role.',
  NANGONG: 'Once, choose a player to drink with. You both become drunk and cannot vote or use abilities for the next day and night. Choosing Liver Index kills them; High Rabbit is unaffected.',
  CASTER: 'Fatcat sees you as a horcrux, and horcruxes see you as Fatcat. Alignment checks still show you as volunteer army.',
  ANDY: 'On the first night, learn one volunteer-army player and bind them as your cloud. If they leave the game, you leave too.',
  CAN_MAN: 'Each night, invite one player to drink. They cannot be killed by Fatcat that night. If you die that night, they die too. Drinking with a drunk or liver-debuffed player kills you both.',
  SINGLE_DOG: 'Whenever you would leave the group, including exile, your death is delayed by one day.',
  STR: 'Each night, before others act, you may swap seat numbers with one alive player until daytime voting begins. Each seat can be swapped once.',
  HIGH_RABBIT: 'You are permanently drunk. You may think you are another role, but your ability never works.',
  MEATBUN: 'You are treated as Fatcat faction for all checks and ability effects, except win-condition checks.',
  MUBAIMU: 'On the first night, share tarts with up to three players. If any target is Fatcat faction, you die on the next night without using Fatcat kill quota.',
  CHEN: 'Once, during daytime discussion, use one neighbor consent to remove the opposite neighbor at night. If a neighbor is dead, skip to the next alive seat.',
  XIAOEN: 'Once per night, when another player targets a Fatcat-faction player with an ability, the target redirects to you.',
  NUKO: 'When Fatcat attacks your left foot, you do not die immediately. Fatcat loses their night ability instead.',
  SHUSHU: 'Each night, choose two travel companions. They learn each other. If they appear to be the same faction, you die the next morning.',
  BARK_KING: 'You no-show all invitations such as travel or drinks. The invitation has no effect, and you die the following night.',
  FATCAT: 'You are the main Fatcat. From the second night onward, choose one player to remove. If you die and Magic Meow cannot replace you, volunteer army wins.',
  LIVER_INDEX: 'Each night, choose one player to give a liver-debuff. Their ability fails, but they are not told.',
  PINK_RABBIT: 'While present, if Fatcat is attacked on the left foot, Fatcat does not die immediately and instead loses night ability for that night.',
  EMPEROR: 'On the first night, reveal every player role. If liver-debuffed, the revealed roles are scrambled.',
  NTHU_MATH: 'When you join the game, the anti-Fatcat alliance count is increased by two.',
  MAGIC_MEOW: 'If the true Fatcat is removed or loses kill authority, you become Fatcat and the game continues.',
  PH_SERVICE: 'On the first night, choose an absent volunteer-army role. If absent, steal that role ability and appear as volunteer army from day two.',
  RAT_MAN: 'Identity checks show you as volunteer army. You also learn which players checked your identity.'
}

const modeLabels = {
  OLD_HOME: '養老院'
}

const testRoleOptions = computed(() => Object.keys(roleTranslations).sort((a, b) => roleName(a).localeCompare(roleName(b), 'zh-Hant')))

const customRoleOptions = Object.keys(roleTranslations).filter((role) => !['WEREWOLF', 'VILLAGER', 'SEER', 'WITCH', 'HUNTER', 'GUARD'].includes(role))

const volunteerRoleOptions = [
  'METHANE',
  'GUOGUO',
  'XIANGXIANG',
  'AC_CAT',
  'FORVKUSA',
  'HATONG',
  'KB',
  'SALTED_FISH',
  'XIAOXIANG',
  'MOCHI_BOSS',
  'GRASS_BEAN',
  'NANGONG',
  'CASTER',
  'ANDY',
  'CAN_MAN',
  'SINGLE_DOG',
  'STR'
]

const phaseLabels = {
  WAITING: 'Waiting',
  NIGHT_WAITING: 'Night: waiting for another player',
  STR_ACTION: 'Str Action',
  PH_SERVICE_ACTION: 'PH Service Action',
  NIGHT_START: 'Night Start',
  GUOGUO_ACTION: 'Guoguo Action',
  FORVKUSA_ACTION: 'Forvkusa Action',
  HATONG_ACTION: 'Hatong Action',
  XIAOXIANG_ACTION: 'Xiaoxiang Action',
  MUBAIMU_ACTION: 'Mubaimu Action',
  SHUSHU_ACTION: 'Shushu Action',
  GRASS_BEAN_ACTION: 'Grass Bean Action',
  AC_CAT_ACTION: 'AC Cat Action',
  XIANGXIANG_ACTION: 'Xiangxiang Action',
  LIVER_INDEX_ACTION: 'Liver Index Action',
  CAN_MAN_ACTION: 'Can Man Action',
  NANGONG_ACTION: 'Nangong Action',
  ANDY_ACTION: 'Andy Action',
  METHANE_ACTION: 'Methane Action',
  MOCHI_BOSS_ACTION: 'Mochi Boss Check',
  DAY_START: 'Day Start',
  NOMINATION: 'Nomination',
  VOTING: 'Execution Vote',
  GAME_OVER: 'Game Over'
}

const nightPhases = new Set([
  'NIGHT_WAITING',
  'STR_ACTION',
  'PH_SERVICE_ACTION',
  'NIGHT_START',
  'GUOGUO_ACTION',
  'FORVKUSA_ACTION',
  'HATONG_ACTION',
  'XIAOXIANG_ACTION',
  'MUBAIMU_ACTION',
  'SHUSHU_ACTION',
  'GRASS_BEAN_ACTION',
  'AC_CAT_ACTION',
  'XIANGXIANG_ACTION',
  'LIVER_INDEX_ACTION',
  'CAN_MAN_ACTION',
  'NANGONG_ACTION',
  'ANDY_ACTION',
  'METHANE_ACTION',
  'MOCHI_BOSS_ACTION',
  'WITCH_ACTION',
  'SEER_VERIFY'
])

const displayName = computed(() => nickname.value.trim() || authUser.value?.username || (userId.value ? `Player ${userId.value}` : 'Guest'))
const isLoggedIn = computed(() => Boolean(authUser.value?.id))

const saveSession = () => {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
    roomId: roomId.value,
    userId: userId.value,
    nickname: nickname.value,
    selectedMode: selectedMode.value,
    roomSize: Number(roomSize.value) || 7,
    hostMode: hostMode.value,
    customDeck: customDeck.value,
    fatcatHintRoles: fatcatHintRoles.value,
    highRabbitRole: highRabbitRole.value,
    hostMethaneHallucinationTargetId: hostMethaneHallucinationTargetId.value,
    hostAdvancedOpen: hostAdvancedOpen.value
  }))
}

const clearSavedRoom = () => {
  const nextSession = {
    userId: userId.value,
    nickname: nickname.value,
    selectedMode: selectedMode.value,
    roomSize: Number(roomSize.value) || 7,
    hostMode: hostMode.value,
    customDeck: customDeck.value,
    fatcatHintRoles: fatcatHintRoles.value,
    highRabbitRole: highRabbitRole.value,
    hostMethaneHallucinationTargetId: hostMethaneHallucinationTargetId.value,
    hostAdvancedOpen: hostAdvancedOpen.value
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession))
}

watch([roomId, userId, nickname, selectedMode, roomSize, hostMode, customDeck, fatcatHintRoles, highRabbitRole, hostMethaneHallucinationTargetId, hostAdvancedOpen], saveSession, { deep: true })
watch(roomSize, (size) => { if (hostMode.value && customDeck.value.length !== Number(size)) { customDeck.value = defaultHostDeck(size); fatcatHintRoles.value = []; highRabbitRole.value = ''; hostMethaneHallucinationTargetId.value = '' } })

const errorMessage = (error, fallback = 'Action failed.') => {
  const data = error?.response?.data
  if (typeof error === 'string') return error || fallback
  if (typeof data === 'string') return data
  if (data?.message) return data.message
  if (data?.error && data?.path) return `${data.error}: ${data.path}`
  if (data?.error) return data.error
  if (data) return JSON.stringify(data)
  return error?.message || fallback
}

let lastActionErrorPopup = { message: '', time: 0 }
const showActionError = (error, fallback = 'Action failed.') => {
  const message = errorMessage(error, fallback)
  actionError.value = message
  if (message) {
    const now = Date.now()
    const isDuplicate = lastActionErrorPopup.message === message && now - lastActionErrorPopup.time < 800
    if (!isDuplicate) window.alert(message)
    lastActionErrorPopup = { message, time: now }
  }
  return message
}

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
    const timer = window.setTimeout(() => reject(new Error('Join room timeout.')), 10000)
    socket.emit('joinRoom', {
      roomId: roomId.value,
      userId: userId.value ? Number(userId.value) : null,
      clientId,
      nickname: nickname.value.trim(),
      roomSize: Number(roomSize.value),
      spectator: hostMode.value
    }, (response) => {
      window.clearTimeout(timer)
      if (!response?.ok) {
        reject(new Error(response?.error || 'Failed to join room.'))
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
        showActionError(response.error, 'Failed to load room list.')
      }
      resolve(response)
    })
  })
}

const openRoomList = async () => {
  if (!requireLogin()) return
  showRoomList.value = true
  actionError.value = ''
  await requestRoomList()
}

const loadGameHistory = async () => {
  if (!isLoggedIn.value) return
  historyLoading.value = true
  try {
    const response = await fetch(`${GATEWAY_URL}/history`)
    const data = await response.json().catch(() => [])
    if (!response.ok) throw new Error(data.message || 'Failed to load game history.')
    historyRecords.value = Array.isArray(data) ? data : []
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Failed to load game history.')
  } finally {
    historyLoading.value = false
  }
}

const historyWinnerText = (winnerCamp) => {
  if (winnerCamp === 'WOLF') return '????'
  if (winnerCamp === 'VILLAGER') return '?????'
  return winnerCamp || '??'
}

const historyTimeText = (value) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

const downloadHistoryRecord = async (record) => {
  try {
    const response = await fetch(`${GATEWAY_URL}/history/${encodeURIComponent(record.gameId)}`)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.message || 'Failed to download game history.')
    const parsedState = typeof data.finalState === 'string' ? JSON.parse(data.finalState) : data.finalState
    const payload = { ...data.summary, finalState: parsedState }
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `fatcatkill_history_${record.roomId}_${record.gameId}.json`
    link.click()
    URL.revokeObjectURL(url)
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Failed to download game history.')
  }
}


const saveAuthUser = (user) => {
  authUser.value = user
  window.localStorage.setItem(AUTH_KEY, JSON.stringify(user))
  if (!nickname.value.trim()) nickname.value = user.username
  loadGameHistory()
}

const submitAuth = async () => {
  try {
    const endpoint = authMode.value === 'register' ? '/auth/register' : '/auth/login'
    const response = await fetch(`${GATEWAY_URL}${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: authUsername.value.trim(), password: authPassword.value })
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.message || 'Login failed.')
    saveAuthUser(data)
    authPassword.value = ''
    actionError.value = ''
  } catch (error) {
    showActionError(error, authMode.value === 'register' ? 'Registration failed.' : 'Login failed.')
  }
}

const logoutAuth = () => {
  authUser.value = null
  authPassword.value = ''
  window.localStorage.removeItem(AUTH_KEY)
}

const loginAsGuest = () => {
  const suffix = clientId.slice(0, 5).toUpperCase()
  const guest = {
    id: `guest-${clientId}`,
    username: `Guest ${suffix}`,
    gamesPlayed: 0,
    gamesWon: 0,
    guest: true
  }
  saveAuthUser(guest)
  authUsername.value = guest.username
  authPassword.value = ''
  actionError.value = ''
}

const requireLogin = () => {
  if (isLoggedIn.value) return true
  showActionError('Please login before creating or joining a room.')
  return false
}

const createHostedRoom = async () => {
  if (!requireLogin()) return
  hostMode.value = true
  if (!roomId.value) roomId.value = String(Math.floor(1000 + Math.random() * 9000))
  await joinRoom()
}

const createRoom = async () => {
  if (!requireLogin()) return
  hostMode.value = false
  if (!roomId.value) {
    roomId.value = String(Math.floor(1000 + Math.random() * 9000))
  }
  await joinRoom()
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
    showActionError('Please enter room ID.')
    return
  }

  try {
    const joinResponse = await joinSocketRoom({ force: true })
    if (joinResponse?.userId != null) userId.value = String(joinResponse.userId)
    isConnected.value = true
    showRoomList.value = false
    actionError.value = ''
    saveSession()
    await requestRoomList()
    await fetchRoomState()
  } catch (error) {
    isConnected.value = false
    showActionError(error, 'Failed to join room.')
  }
}

onMounted(() => {
  socket.on('disconnect', () => {
    joinedSocketId.value = null
  })

  socket.on('message', (msg) => {
    serverMessage.value = msg
  })

  socket.on('gameStateUpdate', (newState) => {
    gameState.value = newState
    actionError.value = ''
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
    serverMessage.value = payload?.message || 'You were kicked from the room.'
  })

  socket.on('actionError', (errorMsg) => {
    showActionError(typeof errorMsg === 'string' ? errorMsg : 'Action failed.')
  })

  socket.on('roomClosed', () => {
    resetToMain()
    serverMessage.value = 'Room closed.'
  })

  if (roomId.value) {
    joinRoom()
  }
})

const phase = computed(() => gameState.value?.currentPhase || 'WAITING')
const winnerText = computed(() => {
  if (gameState.value?.winnerCamp === 'WOLF') return '肥貓陣營獲勝'
  if (gameState.value?.winnerCamp === 'VILLAGER') return '反肥貓義勇軍獲勝'
  return '遊戲結束'
})
const isNight = computed(() => nightPhases.has(phase.value))

const myPlayer = computed(() => {
  if (!gameState.value) return null
  return gameState.value.players.find((player) => player.userId === Number(userId.value)) || null
})

const myEffectiveRole = computed(() => {
  if (!gameState.value || !myPlayer.value) return null
  if (myPlayer.value.role === 'HIGH_RABBIT') {
    const perceivedRoles = gameState.value.highRabbitPerceivedRoles || {}
    return perceivedRoles[myPlayer.value.userId] || perceivedRoles[String(myPlayer.value.userId)] || null
  }
  if (myPlayer.value.role === 'PH_SERVICE' && gameState.value.phServiceStolenRole) {
    return gameState.value.phServiceStolenRole
  }
  return myPlayer.value.role
})

const myHighRabbitPerceivedRole = computed(() => {
  if (!gameState.value || myPlayer.value?.role !== 'HIGH_RABBIT') return null
  const perceivedRoles = gameState.value.highRabbitPerceivedRoles || {}
  return perceivedRoles[myPlayer.value.userId] || perceivedRoles[String(myPlayer.value.userId)] || null
})

const myDisplayedRole = computed(() => myHighRabbitPerceivedRole.value || myPlayer.value?.role)

const canActAsRole = (role) => myEffectiveRole.value === role

const alivePlayers = computed(() => {
  return gameState.value?.players?.filter((player) => player.alive) || []
})

const eligibleVoters = computed(() => {
  const finalVoteIds = new Set((gameState.value?.finalVoteEligiblePlayerIds || []).map(String))
  return gameState.value?.players?.filter(
    (player) => player.alive || finalVoteIds.has(String(player.userId))
  ) || []
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
      username: player.nickname || `Player ${player.userId || ''}`.trim(),
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

const currentRoomInfo = computed(() => connectedRooms.value.find((room) => room.roomId === roomId.value) || null)
const roomHostId = computed(() => currentRoomInfo.value?.hostId ?? gameState.value?.hostId ?? null)
const isRoomHost = computed(() => roomHostId.value != null && Number(roomHostId.value) === Number(userId.value))
const isHostSpectator = computed(() => hostMode.value && isRoomHost.value)
const isDeadObserver = computed(() => Boolean(gameState.value && gameState.value.status !== 'WAITING' && myPlayer.value && !myPlayer.value.alive))
const isObserverMode = computed(() => isHostSpectator.value || isDeadObserver.value)
const observerTitle = computed(() => isDeadObserver.value ? 'Observer mode' : 'Host observer')
const observerSubtitle = computed(() => isDeadObserver.value ? '你已離場，正在觀戰' : '遊戲監看')
const currentRoomPlayer = computed(() => onlineRoomPlayers.value.find((player) => Number(player.userId) === Number(userId.value)) || null)
const isRoomParticipant = computed(() => Boolean(currentRoomPlayer.value) && !hostMode.value)
const isPlayerReady = computed(() => currentRoomPlayer.value?.ready === true)
const hostDeckHasHighRabbit = computed(() => customDeck.value.includes('HIGH_RABBIT'))
const hostDeckHasMethane = computed(() => customDeck.value.includes('METHANE'))
const availableFatcatHintRoles = computed(() => volunteerRoleOptions.filter((role) => !customDeck.value.includes(role)))
const selectedFatcatHintRoles = computed(() => fatcatHintRoles.value.filter(Boolean))
const fatcatHintSlotIndexes = [0, 1, 2]
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
  if (cleaned.join('|') !== fatcatHintRoles.value.filter(Boolean).join('|')) {
    fatcatHintRoles.value = cleaned
  }
}
const setFatcatHintRole = (slotIndex, role) => {
  const next = [...fatcatHintRoles.value]
  if (role) {
    next[slotIndex] = role
  } else {
    next.splice(slotIndex, 1)
  }
  fatcatHintRoles.value = next
  cleanFatcatHintRoles()
}
const highRabbitRoleOptions = computed(() => [...new Set(customDeck.value.filter((role) => role && role !== 'HIGH_RABBIT'))])
const hostAdvancedSummary = computed(() => {
  const summary = []
  summary.push(fatcatHintRoles.value.length ? '肥貓情報 ' + fatcatHintRoles.value.length + '/3' : '肥貓情報隨機')
  summary.push(hostDeckHasHighRabbit.value && highRabbitRole.value ? '高能兔: ' + roleName(highRabbitRole.value) : '高能兔隨機')
  summary.push(hostDeckHasMethane.value && hostMethaneHallucinationTargetId.value ? '甲烷目標已指定' : '甲烷隨機')
  return summary.join(' · ')
})
const deckValidation = computed(() => {
  if (!hostMode.value) return ''
  if (customDeck.value.length !== Number(roomSize.value) || customDeck.value.some((role) => !role)) return 'Fill every deck slot.'
  if (new Set(customDeck.value).size !== customDeck.value.length) return 'Roles cannot be duplicated.'
  if (customDeck.value.filter((role) => role === 'FATCAT').length !== 1) return 'Deck must contain exactly one Fatcat.'
  if (selectedFatcatHintRoles.value.length > 3) return 'Choose at most three absent-role hints.'
  if (selectedFatcatHintRoles.value.some((role) => !availableFatcatHintRoles.value.includes(role))) return 'Fatcat hints must be absent volunteer roles.'
  if (new Set(selectedFatcatHintRoles.value).size !== selectedFatcatHintRoles.value.length) return 'Fatcat hints cannot be duplicated.'
  if (hostDeckHasHighRabbit.value && highRabbitRole.value && !highRabbitRoleOptions.value.includes(highRabbitRole.value)) return 'High Rabbit illusion must be a role in this deck.'
  if (hostDeckHasMethane.value && hostMethaneHallucinationTargetId.value && !hostMethaneTargetOptions.value.some((player) => String(player.userId) === String(hostMethaneHallucinationTargetId.value))) return 'Methane hallucination target must be a valid non-Methane non-Fatcat player.'
  return ''
})

const isBotRoomPlayer = (player) => {
  const name = player?.username || player?.nickname || ''
  return name.startsWith('Bot ') || name.startsWith('胖貓測試員_')
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
  .filter((player) => player.userId != null && player.role && !['FATCAT', 'WEREWOLF', 'METHANE'].includes(player.role)))

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
const roomFillPercent = computed(() => {
  if (!roomCapacity.value) return 0
  return Math.round((roomPlayerCount.value / roomCapacity.value) * 100)
})
const roomSeatSlots = computed(() => {
  const playersBySeat = new Map(setupRoomPlayers.value
    .filter((player) => player.userId != null)
    .map((player) => [Number(player.userId), player]))
  return Array.from({ length: roomCapacity.value }, (_, index) => {
    const seatId = index + 1
    return {
      index: seatId,
      player: playersBySeat.get(seatId) || null
    }
  })
})

const readyRequiredPlayers = computed(() => setupRoomPlayers.value.filter((player) => Number(player.userId) !== Number(roomHostId.value)))
const roomReadyCount = computed(() => readyRequiredPlayers.value.filter((player) => player.ready).length)
const roomRequiredReadyCount = computed(() => readyRequiredPlayers.value.length)
const allRoomPlayersReady = computed(() => roomPlayerCount.value > 0 && roomReadyCount.value === roomRequiredReadyCount.value)
const canStartGame = computed(() => isRoomHost.value && !deckValidation.value && allRoomPlayersReady.value)
const roomStartStatus = computed(() => {
  if (!roomPlayerCount.value) return ''
  if (allRoomPlayersReady.value) return roomRequiredReadyCount.value ? 'All non-host players ready' : 'Host can start'
  return `${roomReadyCount.value} / ${roomRequiredReadyCount.value} non-host players ready`
})

const roomOccupancyText = computed(() => {
  return `${roomPlayerCount.value} / ${roomCapacity.value} players`
})

const emitRoomControl = (eventName, payload = {}) => new Promise((resolve, reject) => {
  const timer = window.setTimeout(() => reject(new Error('Gateway timeout.')), 10000)
  socket.emit(eventName, payload, (response) => {
    window.clearTimeout(timer)
    if (!response?.ok) {
      reject(new Error(response?.error || 'Room action failed.'))
      return
    }
    resolve(response)
  })
})

const toggleReady = async () => {
  try {
    await emitRoomControl('setReady', { ready: !isPlayerReady.value })
    actionError.value = ''
    await requestRoomList()
  } catch (error) {
    showActionError(error, 'Failed to update ready state.')
  }
}

const moveToSeat = async (slot) => {
  if (slot.player || hostMode.value || gameState.value?.status === 'PLAYING') return
  try {
    const response = await emitRoomControl('moveSeat', { seatId: slot.index })
    if (response?.userId != null) userId.value = String(response.userId)
    actionError.value = ''
    await requestRoomList()
  } catch (error) {
    showActionError(error, 'Failed to move seat.')
  }
}

const kickRoomPlayer = async (player) => {
  if (!isRoomHost.value || !player || Number(player.userId) === Number(userId.value)) return
  if (!window.confirm(`Kick ${player.username || `Player ${player.userId}`} from room ${roomId.value}?`)) return
  try {
    await emitRoomControl('kickPlayer', { userId: Number(player.userId) })
    actionError.value = ''
    await requestRoomList()
  } catch (error) {
    showActionError(error, 'Failed to kick player.')
  }
}

const handleSeatClick = (slot) => {
  if (slot.player) {
    kickRoomPlayer(slot.player)
    return
  }
  moveToSeat(slot)
}

const seatTitle = (slot) => {
  if (slot.player) {
    const readyText = slot.player.ready ? 'Ready' : 'Not ready'
    return `${slot.player.userId} - ${slot.player.username} (${readyText})`
  }
  return isRoomParticipant.value ? `Move to seat ${slot.index}` : `Open slot ${slot.index}`
}

const seatActionText = (slot) => {
  if (slot.player) {
    if (isRoomHost.value && Number(slot.player.userId) !== Number(userId.value)) return 'Kick'
    return slot.player.ready ? 'Ready' : 'Waiting'
  }
  return isRoomParticipant.value ? 'Move here' : 'Open'
}
const saveHostDeck = () => {
  if (deckValidation.value) { showActionError(deckValidation.value); return }
  const name = deckName.value.trim(); if (!name) { showActionError('Enter a deck name.'); return }
  cleanFatcatHintRoles()
  const deck = { name, size: Number(roomSize.value), roles: [...customDeck.value], fatcatHintRoles: [...selectedFatcatHintRoles.value], highRabbitRole: highRabbitRole.value || '', methaneHallucinationTargetId: hostMethaneHallucinationTargetId.value || '' }
  savedDecks.value = [...savedDecks.value.filter((item) => item.name !== name), deck]
  window.localStorage.setItem(HOST_DECKS_KEY, JSON.stringify(savedDecks.value)); selectedSavedDeck.value = name; actionError.value = ''
}
const loadHostDeck = () => { const deck = savedDecks.value.find((item) => item.name === selectedSavedDeck.value); if (!deck) return; roomSize.value = Number(deck.size); customDeck.value = [...deck.roles]; fatcatHintRoles.value = [...(deck.fatcatHintRoles || [])]; highRabbitRole.value = deck.highRabbitRole || ''; hostMethaneHallucinationTargetId.value = deck.methaneHallucinationTargetId || '' }
const hostStartPayload = () => hostMode.value ? { hostMode: true, customDeck: [...customDeck.value], fatcatHintRoles: selectedFatcatHintRoles.value.length ? [...selectedFatcatHintRoles.value] : null, highRabbitRole: highRabbitRole.value || null, methaneHallucinationTargetId: hostDeckHasMethane.value && hostMethaneHallucinationTargetId.value ? Number(hostMethaneHallucinationTargetId.value) : null } : {}

const voteLogTypes = new Set(['SELECT_NOMINATION', 'SELECT_EXECUTION_VOTE', 'CONFIRM_VOTE', 'CANCEL_VOTE', 'SKIP_VOTE', 'TALLY_VOTES', 'START_NOMINATION'])
const hostPlayerLabel = (playerId) => {
  if (playerId == null) return '-'
  const player = gameState.value?.players?.find((item) => String(item.userId) === String(playerId))
  return player ? `Seat ${player.seatNumber} · ${player.username}` : `Player ${playerId}`
}
const hostVoteRows = computed(() => {
  if (!gameState.value || !['NOMINATION', 'VOTING'].includes(phase.value)) return []
  return gameState.value.players
    .filter((player) => player.alive || gameState.value.finalVoteEligiblePlayerIds?.includes(player.userId))
    .map((player) => ({ ...player, choice: player.votedTargetId == null ? null : hostPlayerLabel(player.votedTargetId) }))
    .sort((a, b) => (a.seatNumber || 999) - (b.seatNumber || 999))
})
const hostConfirmedVotes = computed(() => hostVoteRows.value.filter((player) => player.voteConfirmed).length)
const hostVoteTotals = computed(() => {
  const totals = new Map()
  for (const player of hostVoteRows.value) {
    if (player.votedTargetId == null) continue
    const key = String(player.votedTargetId)
    totals.set(key, (totals.get(key) || 0) + 1)
  }
  return [...totals.entries()].map(([playerId, count]) => ({ playerId, label: hostPlayerLabel(playerId), count })).sort((a, b) => b.count - a.count)
})
const hostActivityLogs = computed(() => [...(gameState.value?.logs || [])].filter((entry) => !voteLogTypes.has(entry.actionType)).reverse().slice(0, 40))
const hostActionLabel = (actionType) => ({
  START_GAME: '遊戲開始', FATCAT_KILL: '肥貓踢人', FATCAT_TEAM_HINT: '查看肥貓陣營',
  METHANE_CHECK: '甲烷查驗', LIVER_ACTION: '施加爆肝', CANMAN_ACTION: '罐頭邀約',
  NANGONG_ACTION: '南宮邀約', STR_ACTION: '交換座位', STR_SKIP: '跳過交換',
  MUBAIMU_ACTION: '分享蛋塔', SHUSHU_ACTION: '邀請旅遊', CHEN_ACTION: '臣本布衣發動',
  SALTED_FISH_STAB: '鹹魚發動', BOT_AUTO: 'Bot 行動'
}[actionType] || (actionType || '系統事件').replaceAll('_', ' '))
const hostLogTime = (timestamp) => {
  if (!timestamp) return '--:--:--'
  const date = new Date(timestamp)
  return Number.isNaN(date.getTime()) ? '--:--:--' : date.toLocaleTimeString('zh-TW', { hour12: false })
}
const hostLogDetail = (entry) => {
  if (entry.message) return entry.message
  const targets = [entry.targetId, entry.targetId2].filter((id) => id != null).map(hostPlayerLabel)
  return targets.length ? targets.join(' / ') : '已送出'
}

const nominatedPlayer = computed(() => {
  const nomineeId = gameState.value?.nominatedPlayerId
  if (!gameState.value || nomineeId == null) return null
  return gameState.value.players.find((player) => player.userId === nomineeId) || null
})

const myVotedPlayer = computed(() => {
  const votedTargetId = myPlayer.value?.votedTargetId
  if (!gameState.value || votedTargetId == null) return null
  return gameState.value.players.find((player) => player.userId === votedTargetId) || null
})

const confirmedAliveCount = computed(() => {
  return eligibleVoters.value.filter((player) => player.voteConfirmed).length
})

const ratManCheckerLabels = computed(() => {
  const checkerIds = gameState.value?.ratManCheckerIds || []
  return checkerIds.map((checkerId) => {
    const checker = gameState.value?.players?.find((player) => player.userId === checkerId)
    return checker ? `${checker.userId} - ${checker.username}` : String(checkerId)
  })
})

const canUseDayVote = computed(() => {
  if (!myPlayer.value || !['NOMINATION', 'VOTING'].includes(phase.value)) return false
  const finalVoteIds = new Set((gameState.value?.finalVoteEligiblePlayerIds || []).map(String))
  return myPlayer.value.alive || finalVoteIds.has(String(myPlayer.value.userId))
})

const canUseFatcatTeamHint = computed(() => {
  if (!gameState.value || !myPlayer.value?.alive) return false
  if (gameState.value.currentRound !== 1 || !isNight.value) return false
  return myEffectiveRole.value === 'FATCAT' || fatcatHorcruxRoles.has(myPlayer.value.role)
})

const canUseFatcatKill = computed(() => {
  if (!gameState.value || !myPlayer.value?.alive || phase.value !== 'NIGHT_START') return false
  if (gameState.value.currentRound === 1) return false
  if (myPlayer.value.role === 'HIGH_RABBIT' && myEffectiveRole.value === 'FATCAT') return true
  return gameState.value.fatcatKillerPlayerId != null
    ? String(gameState.value.fatcatKillerPlayerId) === String(myPlayer.value.userId)
    : myPlayer.value.role === 'FATCAT'
})

const skippedThisRound = (rounds, playerId) => {
  if (!rounds || playerId == null || !gameState.value?.currentRound) return false
  return rounds[playerId] === gameState.value.currentRound || rounds[String(playerId)] === gameState.value.currentRound
}

const canUseChenAction = computed(() => {
  if (!gameState.value || !myPlayer.value?.alive) return false
  if (myEffectiveRole.value !== 'CHEN' || phase.value !== 'DAY_START') return false
  if (gameState.value.chenUsedPlayerIds?.includes(myPlayer.value.userId)) return false
  return !skippedThisRound(gameState.value.chenSkippedRounds, myPlayer.value.userId)
})

const canUseSaltedFishAction = computed(() => {
  if (!gameState.value || !myPlayer.value) return false
  const canActAfterDeath = gameState.value.fatcatKilledPlayerIds?.includes(myPlayer.value.userId)
  if (!myPlayer.value.alive && !canActAfterDeath) return false
  if (myEffectiveRole.value !== 'SALTED_FISH' || phase.value !== 'VOTING') return false
  if (gameState.value.saltedFishUsedPlayerIds?.includes(myPlayer.value.userId)) return false
  return !skippedThisRound(gameState.value.saltedFishSkippedRounds, myPlayer.value.userId)
})

const fatcatHintButtonText = computed(() => {
  return myPlayer.value?.role === 'FATCAT' ? 'Scout absent roles' : 'Team hint'
})

const currentPhaseText = computed(() => {
  if (!gameState.value) return 'No game state yet'
  if (phase.value === 'NOMINATION') return 'Nomination: choose a player for the execution vote'
  if (phase.value === 'VOTING') {
    return nominatedPlayer.value
      ? `Execution vote: ${nominatedPlayer.value.username}`
      : 'Execution vote: no nominee'
  }
  if (phase.value === 'METHANE_ACTION' && canActAsRole('METHANE')) {
    return `Methane: select 2 players (${methaneSelection.value.length}/2)`
  }
  if (phase.value === 'MUBAIMU_ACTION' && canActAsRole('MUBAIMU')) {
    return `Mubaimu: share tarts (${mubaimuSelection.value.length}/3)`
  }
  if (phase.value === 'SHUSHU_ACTION' && canActAsRole('SHUSHU')) {
    return `Shushu: choose travel companions (${shushuSelection.value.length}/2)`
  }
  if (phase.value === 'PH_SERVICE_ACTION' && canActAsRole('PH_SERVICE')) {
    return 'PH Service: choose a volunteer role to hack'
  }
  if (phase.value === 'NIGHT_START' && canActAsRole('FATCAT') && gameState.value.currentRound === 1) {
    return 'Fatcat: scout absent volunteer roles. No killing on the first night.'
  }
  return phaseLabels[phase.value] || phase.value
})

const dayFlowText = computed(() => {
  if (phase.value === 'NOMINATION') {
    if (myPlayer.value?.voteConfirmed && myVotedPlayer.value) return `Confirmed nomination: ${myVotedPlayer.value.username}`
    if (myPlayer.value?.voteConfirmed) return 'You skipped nomination.'
    return myVotedPlayer.value
      ? `Selected nomination: ${myVotedPlayer.value.username}`
      : 'Choose a nominee, or skip.'
  }
  if (phase.value === 'VOTING' && nominatedPlayer.value) {
    const majority = Math.floor(alivePlayers.value.length / 2) + 1
    const status = myPlayer.value?.voteConfirmed
      ? (myVotedPlayer.value ? 'Your yes vote is confirmed.' : 'You skipped this vote.')
      : (myVotedPlayer.value ? 'Your yes vote is selected.' : 'Choose yes or skip.')
    return `${nominatedPlayer.value.username} needs ${majority} yes votes out of ${alivePlayers.value.length} alive players. ${status}`
  }
  return 'Waiting for the day flow to advance.'
})

const tallyButtonText = computed(() => {
  if (phase.value === 'NOMINATION') return 'Tally nominations'
  if (phase.value === 'VOTING') return 'Tally execution vote'
  return 'Advance'
})

const botActionButtonText = computed(() => {
  if (phase.value === 'NOMINATION') return 'All bots nominate'
  if (phase.value === 'VOTING') return 'All bots vote'
  return 'Bot auto step'
})

const roleName = translateRole
const roleHint = (role) => roleHints[role] || 'No ability hint is available for this role yet.'

const myRoleHint = computed(() => roleHint(myDisplayedRole.value))
const myEffectiveRoleHint = computed(() => {
  if (myPlayer.value?.role !== 'PH_SERVICE' || !gameState.value?.phServiceStolenRole) return null
  return roleHint(gameState.value.phServiceStolenRole)
})
const myPrivateMessage = computed(() => {
  if (!gameState.value || !myPlayer.value) return ''
  const messages = gameState.value.privateMessages || {}
  return messages[myPlayer.value.userId] || messages[String(myPlayer.value.userId)] || ''
})

const voteCountFor = (playerId) => {
  const counts = gameState.value?.dayVoteCounts || {}
  return counts[playerId] || counts[String(playerId)] || 0
}

const lastVoteRows = computed(() => {
  const counts = gameState.value?.lastVoteCounts || {}
  return Object.entries(counts)
    .map(([playerId, count]) => {
      const numericId = Number(playerId)
      const player = gameState.value?.players?.find((entry) => entry.userId === numericId)
      return {
        playerId: numericId,
        label: player ? `${player.username} (${roleName(player.role)})` : `Player ${playerId}`,
        count
      }
    })
    .sort((a, b) => b.count - a.count || a.playerId - b.playerId)
})

const isMyVotedPlayer = (player) => {
  return phase.value === 'NOMINATION' && myPlayer.value?.votedTargetId === player.userId
}

const canVoteFor = (player) => {
  if (!canUseDayVote.value || myPlayer.value?.voteConfirmed || !player.alive || player.userId === myPlayer.value?.userId) return false
  if (phase.value === 'NOMINATION') return true
  return gameState.value?.nominatedPlayerId === player.userId
}

const dayVoteButtonText = (player) => {
  if (phase.value === 'NOMINATION') return isMyVotedPlayer(player) ? 'Cancel selection' : 'Select nominee'
  if (phase.value === 'VOTING') return isMyVotedPlayer(player) ? 'Cancel yes' : 'Select yes'
  return 'Vote'
}

const emitGameAction = async (endpoint, data, method = 'POST') => {
  await joinSocketRoom()
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new Error('Gateway timeout.')), 10000)
    socket.emit('gameAction', { roomId: roomId.value, endpoint, data, method }, (response) => {
      window.clearTimeout(timer)
      if (!response?.ok) {
        reject(new Error(response?.error || 'Action failed.'))
        return
      }
      resolve(response)
    })
  })
}

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
    actionError.value = ''
    if (response.message) alert(response.message)
  } catch (error) {
    showActionError(error, 'Action failed.')
  }
}

const fetchRoomState = async () => {
  try {
    const response = await emitGameAction(`/room/${roomId.value}`, {}, 'GET')
    gameState.value = response.gameState
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Failed to load room.')
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
    if (response.message) alert(response.message)
    gameState.value = response.gameState
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Action failed.')
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
    if (response.message) alert(response.message)
    gameState.value = response.gameState
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Action failed.')
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
    if (response.message) alert(response.message)
    gameState.value = response.gameState
    mubaimuSelection.value = []
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Action failed.')
  }
}

const sendShushuAction = async () => {
  if (shushuSelection.value.length !== 2) {
    showActionError('Choose exactly 2 companions.')
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
    if (response.message) alert(response.message)
    gameState.value = response.gameState
    shushuSelection.value = []
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Action failed.')
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
    if (response.message) alert(response.message)
    gameState.value = response.gameState
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Action failed.')
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

const createMockRoom = async () => {
  testRoleAssignments.value = {}
  await sendAction(`/room/mock/${roomId.value}?count=${roomSize.value}`, null)
}

const activeRoleAssignments = () => {
  return Object.fromEntries(
    Object.entries(testRoleAssignments.value)
      .filter(([, role]) => role)
      .map(([playerId, role]) => [playerId, role])
  )
}

const startGame = async () => {
  try {
    if (deckValidation.value) throw new Error(deckValidation.value)
    if (!allRoomPlayersReady.value) throw new Error('All non-host players must be ready before starting.')
    const response = await emitGameAction(`/room/start/${roomId.value}?mode=${selectedMode.value}`, {
      roomId: roomId.value,
      playerId: Number(userId.value),
      roleAssignments: activeRoleAssignments(),
      ...hostStartPayload()
    })
    gameState.value = response.gameState
    actionError.value = ''
    if (response.message) alert(response.message)
  } catch (error) {
    showActionError(error, 'Failed to start game.')
  }
}
const currentRoomPlayers = () => {
  const room = connectedRooms.value.find((entry) => entry.roomId === roomId.value)
  const players = (room?.players || [])
    .filter((player) => player.userId !== null && player.userId !== undefined)
    .map((player) => ({
      userId: Number(player.userId),
      nickname: player.nickname || `Player ${player.userId}`
    }))
    .filter((player) => Number.isFinite(player.userId))

  const currentUserId = Number(userId.value)
  if (!hostMode.value && Number.isFinite(currentUserId) && !players.some((player) => player.userId === currentUserId)) {
    players.push({
      userId: currentUserId,
      nickname: nickname.value.trim(),
        roomSize: Number(roomSize.value)
    })
  }

  return players
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
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Failed to fill bots.')
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
const wolfKill = (targetId) => sendAction('/night/wolf', targetId)
const witchAction = (targetId, type) => sendAction('/night/witch', targetId, type)
const seerVerify = (targetId) => sendAction('/night/seer', targetId)
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
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Bot action failed.')
  }
}

const downloadGameLogs = async () => {
  try {
    const res = await emitGameAction(`/logs/${roomId.value}`, {}, 'GET')
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
    link.href = url
    link.download = `fatcatkill_room_${roomId.value}_${timestamp}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    actionError.value = ''
  } catch (error) {
    showActionError(error, 'Failed to download logs.')
  }
}

const resetToMain = ({ clearRoom = true } = {}) => {
  if (socket.connected) {
    socket.disconnect()
  }
  gameState.value = null
  revealPlayers.value = []
  methaneSelection.value = []
  mubaimuSelection.value = []
  shushuSelection.value = []
  serverMessage.value = ''
  actionError.value = ''
  isConnected.value = false
  joinedSocketId.value = null
  if (clearRoom) {
    roomId.value = ''
    clearSavedRoom()
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
      showActionError(error, 'Failed to close room.')
      return
    }
    resetToMain()
    serverMessage.value = 'Room closed.'
  } else {
    resetToMain()
  }
}

const leaveRoom = async () => {
  if (!socket.connected) {
    resetToMain()
    return
  }

  try {
    await new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => {
        reject(new Error('Gateway timeout.'))
      }, 10000)

      socket.emit('leaveRoom', (response) => {
        window.clearTimeout(timer)
        if (!response?.ok) {
          reject(new Error(response?.error || 'Failed to leave room.'))
          return
        }
        resolve(response)
      })
    })
    resetToMain()
    serverMessage.value = 'Left room.'
  } catch (error) {
    showActionError(error, 'Failed to leave room.')
  }
}

const fetchReveal = async () => {
  try {
    const res = await emitGameAction(`/debug/reveal/${roomId.value}`, {}, 'GET')
    revealPlayers.value = res.data.players || []
  } catch (error) {
    showActionError(error)
  }
}
</script>

<template>
  <main :class="['app-shell', { 'night-mode': isNight, 'day-mode': !isNight && gameState }]">
    <section v-if="!isConnected" class="login-panel">
      <h1>FatCatKill</h1>
      <div class="nickname-card">
        <span class="eyebrow">Account</span>
        <strong>{{ authUser ? authUser.username : 'Not logged in' }}</strong>
        <small v-if="authUser?.guest">Guest session</small>
        <small v-else-if="authUser">{{ authUser.gamesWon }} / {{ authUser.gamesPlayed }} wins</small>
      </div>
      <div v-if="!authUser" class="auth-panel">
        <div class="auth-tabs">
          <button type="button" :class="['secondary-button small', { success: authMode === 'login' }]" @click="authMode = 'login'">Login</button>
          <button type="button" :class="['secondary-button small', { success: authMode === 'register' }]" @click="authMode = 'register'">Register</button>
        </div>
        <label class="field">
          <span>Username</span>
          <input v-model="authUsername" type="text" autocomplete="username" placeholder="Username" />
        </label>
        <label class="field">
          <span>Password</span>
          <input v-model="authPassword" type="password" autocomplete="current-password" placeholder="Password" @keyup.enter="submitAuth" />
        </label>
        <div class="auth-actions">
          <button class="primary-button" @click="submitAuth">{{ authMode === 'register' ? 'Register' : 'Login' }}</button>
          <button class="secondary-button" @click="loginAsGuest">Guest login</button>
        </div>
      </div>
      <div v-else class="auth-panel compact">
        <button class="secondary-button" @click="logoutAuth">Logout</button>
      </div>
      <div class="nickname-card">
        <span class="eyebrow">Display name</span>
        <strong>{{ displayName }}</strong>
      </div>
      <label class="field">
        <span>Nickname</span>
        <input v-model="nickname" type="text" placeholder="Your display name" />
      </label>
      <label class="field">
        <span>Player ID</span>
        <input v-model="userId" type="number" min="1" max="10" />
      </label>
      <label class="field">
        <span>Room ID</span>
        <input v-model="roomId" type="text" />
      </label>
      <label class="field">
        <span>Room size</span>
        <select v-model="roomSize">
          <option :value="6">6 players</option>
          <option :value="7">7 players</option>
          <option :value="10">10 players</option>
        </select>
      </label>
      <div class="main-actions">
        <button class="primary-button" @click="createRoom">Create room</button>
        <button class="action-button" @click="createHostedRoom">Host customized game</button>
        <button class="secondary-button" @click="openRoomList">Join room</button>
      </div>

      <div v-if="showRoomList" class="room-browser">
        <div class="room-browser-header">
          <span class="eyebrow">Online rooms</span>
          <button class="secondary-button small" @click="requestRoomList">Refresh</button>
        </div>
        <button
          v-for="room in connectedRooms"
          :key="room.roomId"
          class="room-list-item"
          @click="selectRoom(room.roomId)"
        >
          <strong>Room {{ room.roomId }}</strong>
          <span>{{ room.playerCount }} / {{ room.capacity }} players</span>
          <small>{{ room.players.map((player) => player.nickname).join(', ') }}</small>
        </button>
        <p v-if="!connectedRooms.length" class="empty-state">
          No online rooms yet. Create one to get started.
        </p>
      </div>

      <div v-if="authUser" class="history-panel">
        <div class="room-browser-header">
          <span class="eyebrow">Game history</span>
          <button class="secondary-button small" :disabled="historyLoading" @click="loadGameHistory">{{ historyLoading ? 'Loading' : 'Refresh' }}</button>
        </div>
        <div v-if="historyRecords.length" class="history-list">
          <article v-for="record in historyRecords" :key="record.gameId" class="history-item">
            <div>
              <strong>Room {{ record.roomId }}</strong>
              <span>{{ historyWinnerText(record.winnerCamp) }}?? ? {{ record.roundsPlayed }} rounds ? {{ record.playerCount }} players</span>
              <small>{{ historyTimeText(record.endTime) }}</small>
            </div>
            <button class="secondary-button small" @click="downloadHistoryRecord(record)">Download</button>
          </article>
        </div>
        <p v-else class="empty-state">No finished games yet.</p>
      </div>
    </section>

    <section v-else class="game-board">
      <header class="topbar">
        <div>
          <p class="eyebrow">Room {{ roomId }} | {{ isObserverMode ? observerTitle : `Player ${userId}` }} | {{ displayName }}<span v-if="isRoomHost"> | Host</span></p>
          <h1>{{ currentPhaseText }}</h1>
        </div>
        <div class="topbar-actions">
          <div v-if="gameState" class="round-pill">Round {{ gameState.currentRound }}</div>
          <button class="secondary-button" @click="leaveRoom">Leave room</button>
        </div>
      </header>

      <div v-if="actionError" class="error-banner">{{ actionError }}</div>
      <div v-if="gameState?.publicMessage" class="public-banner">{{ gameState.publicMessage }}</div>

      <section v-if="gameState?.status === 'FINISHED' || phase === 'GAME_OVER'" class="game-over-panel">
        <span class="eyebrow">Game finished</span>
        <h2>{{ winnerText }}</h2>
        <div class="button-row">
          <button class="secondary-button" @click="downloadGameLogs">Download game logs</button>
          <button class="primary-button" @click="returnToMain">Back to main</button>
        </div>
      </section>

      <section v-if="!gameState || gameState.status === 'WAITING'" class="setup-panel">
        <h2>Setup</h2>
        <p>{{ serverMessage || 'Create mock players, then start the game.' }}</p>

        <div class="room-ready-panel">
          <div class="room-ready-header">
            <div>
              <span class="eyebrow">Room occupancy</span>
              <strong>{{ roomOccupancyText }}</strong>
            </div>
            <div class="room-ready-actions">
              <button v-if="isRoomParticipant" class="secondary-button" :class="{ success: isPlayerReady }" @click="toggleReady">{{ isPlayerReady ? 'Cancel ready' : 'Ready' }}</button>
              <button class="secondary-button" @click="fetchRoomState">Refresh</button>
            </div>
          </div>

          <div class="occupancy-meter" aria-label="Room occupancy">
            <div class="occupancy-meter-fill" :style="{ width: `${roomFillPercent}%` }"></div>
          </div>

          <div class="room-stat-grid">
            <div class="room-stat">
              <span>Joined</span>
              <strong>{{ roomPlayerCount }}</strong>
            </div>
            <div class="room-stat">
              <span>Open slots</span>
              <strong>{{ roomOpenSlots }}</strong>
            </div>
            <div class="room-stat">
              <span>Capacity</span>
              <strong>{{ roomCapacity }}</strong>
            </div>
          </div>

          <div class="seat-map" :style="{ '--seat-count': roomCapacity }">
            <button
              v-for="slot in roomSeatSlots"
              :key="slot.index"
              type="button"
              :class="['seat-slot', { filled: slot.player, empty: !slot.player, mine: Number(slot.player?.userId) === Number(userId), clickable: !slot.player && isRoomParticipant }]"
              :title="seatTitle(slot)"
              @click="handleSeatClick(slot)"
            >
              <span class="seat-number">{{ slot.index }}</span>
              <span class="seat-content">
                <strong>{{ slot.player ? slot.player.username : 'Open' }}</strong>
                <small>{{ seatActionText(slot) }}</small>
              </span>
            </button>
          </div>
        </div>

        <div v-if="setupRoomPlayers.length" class="waiting-list">
          <span class="eyebrow">Players in room</span>
          <div class="waiting-list-grid">
            <label v-for="player in setupRoomPlayers" :key="player.userId" class="waiting-player-row">
              <span>{{ player.userId }} - {{ player.username }}<strong v-if="Number(player.userId) === Number(roomHostId)"> (Host)</strong></span>
              <small :class="['ready-badge', { ready: player.ready }]">{{ player.ready ? 'Ready' : 'Not ready' }}</small>
              <select v-model="testRoleAssignments[player.userId]">
                <option value="">Random role</option>
                <option v-for="role in testRoleOptions" :key="role" :value="role">
                  {{ roleName(role) }}
                </option>
              </select>
            </label>
          </div>
        </div>

        <div v-if="isRoomHost" class="settings-grid">
          <label class="field">
            <span>Mode</span>
            <select v-model="selectedMode">
              <option v-for="(label, value) in modeLabels" :key="value" :value="value">
                {{ label }}
              </option>
            </select>
          </label>
        </div>

        <section v-if="isHostSpectator" class="host-config-panel">
          <div class="host-config-header"><div><span class="eyebrow">Customized game</span><h3>Deck and controlled outcomes</h3></div><select v-model="selectedSavedDeck" @change="loadHostDeck"><option value="">Load saved deck</option><option v-for="deck in savedDecks" :key="deck.name" :value="deck.name">{{ deck.name }}</option></select></div>
          <div class="deck-slot-grid"><label v-for="(_, index) in customDeck" :key="index" class="deck-slot-field"><span>Seat {{ index + 1 }}</span><select v-model="customDeck[index]"><option value="">Choose role</option><option v-for="role in customRoleOptions" :key="role" :value="role">{{ roleName(role) }}</option></select></label></div>
          <section class="host-advanced-panel"><button class="host-advanced-toggle" type="button" @click="hostAdvancedOpen = !hostAdvancedOpen"><span>Advanced</span><small>{{ hostAdvancedSummary }}</small><strong>{{ hostAdvancedOpen ? '收合' : '展開' }}</strong></button><div v-if="hostAdvancedOpen" class="host-advanced-body"><div class="host-random-grid"><div class="field fatcat-hint-picker">
                  <span>肥貓首夜不在場角色情報</span>
                  <div class="fatcat-hint-selects">
                    <label v-for="slotIndex in fatcatHintSlotIndexes" :key="slotIndex" class="mini-field">
                      <small>提示 {{ slotIndex + 1 }}</small>
                      <select :value="fatcatHintRoles[slotIndex] || ''" @change="setFatcatHintRole(slotIndex, $event.target.value)">
                        <option value="">隨機</option>
                        <option v-for="role in fatcatHintOptionsFor(slotIndex)" :key="role" :value="role">{{ roleName(role) }}</option>
                      </select>
                    </label>
                  </div>
                </div><label v-if="hostDeckHasHighRabbit" class="field"><span>高能兔幻覺角色</span><select v-model="highRabbitRole"><option value="">隨機場上角色</option><option v-for="role in highRabbitRoleOptions" :key="role" :value="role">{{ roleName(role) }}</option></select></label><div v-else class="host-controlled-note"><span>高能兔幻覺角色</span><strong>牌組未包含高能兔</strong></div><label v-if="hostDeckHasMethane" class="field"><span>甲烷幻覺目標</span><select v-model="hostMethaneHallucinationTargetId"><option value="">隨機合格玩家</option><option v-for="player in hostMethaneTargetOptions" :key="player.userId" :value="String(player.userId)">Seat {{ player.seatNumber || '?' }} · {{ player.username }} · {{ roleName(player.role) }}</option></select></label><div v-else class="host-controlled-note"><span>甲烷幻覺目標</span><strong>牌組未包含甲烷</strong></div></div><div class="host-controlled-note full-row"><span>其他可控要素</span><strong>安弟雲、草盛豆苗稀情報、Guoguo 提示等仍由當下場況自動決定</strong></div></div></section>
          <p v-if="deckValidation" class="validation-text">{{ deckValidation }}</p><div class="save-deck-row"><input v-model="deckName" type="text" placeholder="Deck name" /><button class="secondary-button" @click="saveHostDeck">Save as deck</button></div>
        </section>

        <p v-if="isRoomHost && roomStartStatus" class="empty-state">{{ roomStartStatus }}</p>
        <p v-if="!isRoomHost" class="empty-state">Waiting for the host to start the game.</p>

        <div v-if="isRoomHost" class="button-row">
          <button class="primary-button" :disabled="!canStartGame" :title="roomStartStatus" @click="startGame">Start game</button>
          <button class="secondary-button" @click="fillBotsOnly">Fill bots</button>
          <button class="secondary-button" @click="createMockRoom">Create test room</button>
        </div>
      </section>

      <section v-if="gameState && isObserverMode && gameState.status !== 'WAITING'" class="host-observer-panel">
        <div class="observer-header"><div><span class="eyebrow">{{ observerTitle }}</span><h2>{{ observerSubtitle }}</h2></div><button v-if="isHostSpectator && gameState.status === 'PLAYING'" class="secondary-button" @click="autoPlayBot">{{ botActionButtonText }}</button></div>
        <div class="observer-role-grid">
          <article v-for="player in gameState.players" :key="player.userId" class="observer-player-row"><div><strong>{{ player.seatNumber }} · {{ player.username }}</strong><span>{{ roleName(player.role) }}</span></div><span :class="['observer-status', player.alive ? 'alive' : 'dead']">{{ player.alive ? '存活' : '離場' }}</span></article>
        </div>
        <section v-if="hostVoteRows.length" class="observer-vote-board">
          <div class="observer-section-header"><div><span class="eyebrow">{{ phase === 'NOMINATION' ? 'Nomination' : 'Execution vote' }}</span><h3>投票進度</h3></div><strong>{{ hostConfirmedVotes }} / {{ hostVoteRows.length }} 已確認</strong></div>
          <div v-if="hostVoteTotals.length" class="vote-total-strip"><span v-for="item in hostVoteTotals" :key="item.playerId"><strong>{{ item.count }}</strong> {{ item.label }}</span></div>
          <div class="observer-vote-table"><div v-for="player in hostVoteRows" :key="player.userId" class="observer-vote-row"><span class="observer-seat">{{ player.seatNumber }}</span><strong>{{ player.username }}</strong><span class="observer-choice">{{ player.choice || '跳過 / 尚未選擇' }}</span><span :class="['vote-state', player.voteConfirmed ? 'confirmed' : 'pending']">{{ player.voteConfirmed ? '已確認' : '未確認' }}</span></div></div>
        </section>
        <section class="observer-activity">
          <div class="observer-section-header"><div><span class="eyebrow">Activity</span><h3>技能與系統事件</h3></div><span>最近 {{ hostActivityLogs.length }} 筆</span></div>
          <div v-if="Object.keys(gameState.nightActions || {}).length" class="pending-action-strip"><span v-for="(target, action) in gameState.nightActions" :key="action"><strong>{{ hostActionLabel(action) }}</strong> → {{ hostPlayerLabel(target) }}</span></div>
          <p v-if="!hostActivityLogs.length" class="empty-state">尚無技能或系統事件。</p>
          <div v-else class="observer-log-table"><div v-for="(entry, index) in hostActivityLogs" :key="entry.timestamp || index" class="observer-event-row"><time>{{ hostLogTime(entry.timestamp) }}</time><div class="observer-event-actor"><strong>{{ entry.username || '系統' }}</strong><span v-if="entry.role">{{ roleName(entry.role) }}</span></div><strong class="observer-event-action">{{ hostActionLabel(entry.actionType) }}</strong><span class="observer-event-detail">{{ hostLogDetail(entry) }}</span></div></div>
        </section>
      </section>

      <section v-if="gameState && !isObserverMode" class="status-layout">
        <div class="self-panel">
          <span class="eyebrow">My status</span>
          <h2>{{ roleName(myDisplayedRole) }}</h2>
          <p>{{ myPlayer?.alive ? 'Alive' : 'Dead' }}</p>
          <div class="ability-hint">
            <strong>Ability</strong>
            <p>{{ myRoleHint }}</p>
          </div>
          <p v-if="myPlayer?.role === 'PH_SERVICE' && gameState.phServiceStolenRole">
            Stolen ability: {{ roleName(gameState.phServiceStolenRole) }}
          </p>
          <div v-if="myEffectiveRoleHint" class="ability-hint secondary">
            <strong>Current effective ability</strong>
            <p>{{ myEffectiveRoleHint }}</p>
          </div>
          <p v-if="myPlayer?.role === 'RAT_MAN' && gameState.ratManCheckerIds?.length">
            Checked by: {{ ratManCheckerLabels.join(', ') }}
          </p>
          <div v-if="myPrivateMessage" class="ability-hint secondary">
            <strong>Private information</strong>
            <p>{{ myPrivateMessage }}</p>
          </div>
        </div>

        <div v-if="phase === 'NOMINATION' || phase === 'VOTING' || gameState.lastVoteResult" class="vote-panel">
          <span class="eyebrow">Day flow</span>
          <h2>{{ phase === 'NOMINATION' ? 'Nomination' : 'Execution Vote' }}</h2>
          <p>{{ dayFlowText }}</p>
          <p v-if="phase === 'NOMINATION' || phase === 'VOTING'" class="vote-progress">
            Confirmed {{ confirmedAliveCount }} / {{ eligibleVoters.length }}
          </p>
          <div v-if="canUseDayVote" class="vote-controls">
            <button
              class="action-button vote"
              :disabled="myPlayer?.voteConfirmed || !myPlayer?.votedTargetId"
              @click="confirmVote"
            >
              Confirm
            </button>
            <button
              class="secondary-button"
              :disabled="!myPlayer?.voteConfirmed && !myPlayer?.votedTargetId"
              @click="cancelVote"
            >
              Cancel
            </button>
            <button
              class="secondary-button"
              :disabled="myPlayer?.voteConfirmed"
              @click="skipVote"
            >
              Skip
            </button>
          </div>
          <div v-if="gameState.lastVoteResult || lastVoteRows.length" class="vote-result">
            <strong>{{ gameState.lastVoteResult || 'Last vote count' }}</strong>
            <div v-if="lastVoteRows.length" class="vote-result-list">
              <span v-for="row in lastVoteRows" :key="row.playerId">
                {{ row.label }}: {{ row.count }}
              </span>
            </div>
          </div>
        </div>
      </section>

      <section v-if="gameState && !isObserverMode" class="players-grid">
        <article
          v-for="player in gameState.players"
          :key="player.userId"
          :class="[
            'player-card',
            {
              dead: !player.alive,
              mine: player.userId === myPlayer?.userId,
              nominated: player.userId === gameState.nominatedPlayerId,
              'my-vote-target': isMyVotedPlayer(player)
            }
          ]"
        >
          <div class="player-header">
            <span>Seat {{ player.seatNumber || '-' }}</span>
            <strong>{{ player.username }}</strong>
          </div>

          <div class="player-meta">
            <span>{{ player.alive ? 'Alive' : 'Dead' }}</span>
            <span v-if="player.userId === myPlayer?.userId">You</span>
            <span v-if="player.userId === gameState.nominatedPlayerId">Nominee</span>
            <span v-if="isMyVotedPlayer(player)">You nominated</span>
            <span v-if="voteCountFor(player.userId)">Votes {{ voteCountFor(player.userId) }}</span>
          </div>

          <div v-if="player.alive && player.userId !== myPlayer?.userId" class="action-area">
            <button
              v-if="canActAsRole('STR') && phase === 'STR_ACTION'"
              class="action-button"
              @click="strAction(player.userId)"
            >
              Swap seat
            </button>
            <button
              v-if="canUseFatcatKill"
              class="action-button danger"
              @click="fatcatKill(player.userId)"
            >
              Kill
            </button>
            <button
              v-if="canActAsRole('LIVER_INDEX') && phase === 'LIVER_INDEX_ACTION'"
              class="action-button"
              @click="liverAction(player.userId)"
            >
              Debuff
            </button>
            <button
              v-if="canActAsRole('CAN_MAN') && phase === 'CAN_MAN_ACTION'"
              class="action-button"
              @click="canManAction(player.userId)"
            >
              Drink
            </button>
            <button
              v-if="canActAsRole('NANGONG') && phase === 'NANGONG_ACTION'"
              class="action-button"
              @click="nangongAction(player.userId)"
            >
              Bind
            </button>
            <button
              v-if="canActAsRole('MUBAIMU') && phase === 'MUBAIMU_ACTION'"
              :class="['action-button', mubaimuSelection.includes(player.userId) ? 'selected' : '']"
              @click="handleMubaimuClick(player.userId)"
            >
              {{ mubaimuSelection.includes(player.userId) ? 'Tart selected' : 'Give tart' }}
            </button>
            <button
              v-if="canActAsRole('SHUSHU') && phase === 'SHUSHU_ACTION'"
              :class="['action-button', shushuSelection.includes(player.userId) ? 'selected' : '']"
              @click="handleShushuClick(player.userId)"
            >
              {{ shushuSelection.includes(player.userId) ? 'Companion selected' : 'Invite travel' }}
            </button>
            <button
              v-if="canActAsRole('METHANE') && phase === 'METHANE_ACTION'"
              :class="['action-button', methaneSelection.includes(player.userId) ? 'selected' : '']"
              @click="handleMethaneClick(player.userId)"
            >
              {{ methaneSelection.includes(player.userId) ? 'Selected' : 'Check' }}
            </button>
            <button
              v-if="canActAsRole('MOCHI_BOSS') && phase === 'MOCHI_BOSS_ACTION'"
              class="action-button"
              @click="mochiBossCheck(player.userId)"
            >
              Check Fatcat
            </button>
            <template v-if="myPlayer?.role === 'WITCH' && phase === 'WITCH_ACTION'">
              <button class="action-button success" @click="witchAction(player.userId, 'SAVE')">Save</button>
              <button class="action-button danger" @click="witchAction(player.userId, 'POISON')">Poison</button>
            </template>
            <button
              v-if="myPlayer?.role === 'SEER' && phase === 'SEER_VERIFY'"
              class="action-button"
              @click="seerVerify(player.userId)"
            >
              Verify
            </button>
            <button
              v-if="canUseChenAction"
              class="action-button danger"
              @click="chenAction(player.userId)"
            >
              Declare consent
            </button>
            <button
              v-if="canUseSaltedFishAction"
              class="action-button"
              @click="fishAction(player.userId)"
            >
              Stab
            </button>
            <button
              v-if="canVoteFor(player)"
              class="action-button vote"
              @click="dayVote(player.userId)"
            >
              {{ dayVoteButtonText(player) }}
            </button>
          </div>
        </article>
      </section>

      <section v-if="gameState && !isObserverMode" class="global-actions">
        <button v-if="gameState.status === 'PLAYING' && isRoomHost" class="secondary-button" @click="autoPlayBot">
          {{ botActionButtonText }}
        </button>
        <button v-if="canUseFatcatTeamHint" class="action-button" @click="fatcatTeamHint">
          {{ fatcatHintButtonText }}
        </button>
        <label v-if="canActAsRole('PH_SERVICE') && phase === 'PH_SERVICE_ACTION'" class="inline-field">
          <span>Hack role</span>
          <select v-model="phServiceTargetRole">
            <option v-for="role in volunteerRoleOptions" :key="role" :value="role">
              {{ roleName(role) }}
            </option>
          </select>
        </label>
        <button v-if="canActAsRole('PH_SERVICE') && phase === 'PH_SERVICE_ACTION'" class="action-button" @click="sendPhServiceAction">
          Hack account
        </button>
        <button v-if="canActAsRole('EMPEROR') && gameState.currentRound === 1 && isNight" class="action-button" @click="emperorReveal">
          Reveal all roles
        </button>
        <button v-if="canActAsRole('STR') && phase === 'STR_ACTION'" class="action-button" @click="strSkip">
          Skip swap
        </button>
        <button v-if="canActAsRole('GUOGUO') && phase === 'GUOGUO_ACTION'" class="action-button" @click="guoguoAction">
          Guoguo hint
        </button>
        <button v-if="canActAsRole('FORVKUSA') && phase === 'FORVKUSA_ACTION'" class="action-button" @click="forvkusaAction">
          Check adjacent Fatcat pairs
        </button>
        <button v-if="canActAsRole('HATONG') && phase === 'HATONG_ACTION'" class="action-button" @click="hatongAction">
          Check if Fatcat voted
        </button>
        <button v-if="canActAsRole('XIAOXIANG') && phase === 'XIAOXIANG_ACTION'" class="action-button" @click="xiaoxiangAction">
          Count alliance
        </button>
        <button v-if="canActAsRole('MUBAIMU') && phase === 'MUBAIMU_ACTION'" class="action-button" @click="sendMubaimuAction">
          Share tarts
        </button>
        <button v-if="canActAsRole('SHUSHU') && phase === 'SHUSHU_ACTION'" class="action-button" @click="sendShushuAction">
          Start trip
        </button>
        <button v-if="canActAsRole('GRASS_BEAN') && phase === 'GRASS_BEAN_ACTION'" class="action-button" @click="grassBeanAction">
          Find horcrux
        </button>
        <button v-if="canActAsRole('XIANGXIANG') && phase === 'XIANGXIANG_ACTION'" class="action-button" @click="xiangxiangAction">
          Check neighbors
        </button>
        <button v-if="canActAsRole('AC_CAT') && phase === 'AC_CAT_ACTION'" class="action-button" @click="acCatAction">
          Reveal exile role
        </button>
        <button v-if="canActAsRole('ANDY') && phase === 'ANDY_ACTION'" class="action-button" @click="andyAction">
          Find cloud
        </button>
        <button v-if="myPlayer?.role === 'WITCH' && phase === 'WITCH_ACTION'" class="action-button" @click="witchAction(myPlayer.userId, 'SKIP')">
          Witch skip
        </button>
        <button v-if="canUseChenAction" class="secondary-button" @click="chenSkip">
          Skip Chen
        </button>
        <button v-if="canUseSaltedFishAction" class="secondary-button" @click="fishSkip">
          Skip stab
        </button>
        <button v-if="phase === 'DAY_START'" class="primary-button" @click="startNomination">
          Start nomination
        </button>
        <button v-if="phase === 'NOMINATION' || phase === 'VOTING'" class="primary-button" @click="tallyVotes">
          {{ tallyButtonText }}
        </button>
      </section>

      <section v-if="gameState" class="debug-panel">
        <button class="secondary-button" @click="fetchReveal">Debug: reveal roles</button>
        <ul v-if="revealPlayers.length">
          <li v-for="player in revealPlayers" :key="player.userId">
            {{ player.userId }} - {{ player.username }}: {{ roleName(player.role) }}
          </li>
        </ul>
      </section>
    </section>
  </main>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  padding: 24px;
  color: #1d2733;
  background: #eef3f7;
  font-family: "Segoe UI", Arial, sans-serif;
}

.night-mode {
  color: #edf2f7;
  background: #151923;
}

.day-mode {
  background: #eef3f7;
}

.login-panel,
.game-board {
  width: min(1040px, 100%);
  margin: 0 auto;
}

.login-panel {
  margin-top: 10vh;
  max-width: 520px;
  padding: 28px;
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  box-shadow: 0 14px 35px rgba(21, 31, 45, 0.12);
}

.auth-panel {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #d7e0e8;
  border-radius: 8px;
  background: #ffffff;
}
.auth-panel.compact { align-items: start; }
.auth-tabs { display: flex; gap: 8px; }
.auth-actions { display: flex; flex-wrap: wrap; gap: 8px; }

.nickname-card {
  margin-top: 18px;
  padding: 14px;
  background: #f4f8fb;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.nickname-card strong {
  display: block;
  margin-top: 4px;
  font-size: 22px;
}

.topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(120, 135, 150, 0.35);
}

h1,
h2,
p {
  margin: 0;
}

.topbar h1 {
  margin-top: 4px;
  font-size: 28px;
  line-height: 1.25;
}

.topbar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.topbar-actions .secondary-button {
  margin-top: 0;
}

.eyebrow {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.night-mode .eyebrow {
  color: #9fb0c3;
}

.round-pill,
.player-meta span,
.status-layout p {
  font-size: 14px;
}

.round-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(82, 105, 130, 0.14);
  white-space: nowrap;
}

.field {
  display: grid;
  gap: 6px;
  margin-top: 16px;
  font-weight: 700;
}

.inline-field {
  display: grid;
  gap: 4px;
  min-width: 180px;
  font-size: 13px;
  font-weight: 700;
}

input,
select {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid #c8d2dc;
  border-radius: 8px;
  background: #ffffff;
  color: #1d2733;
  font: inherit;
}

button {
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.primary-button,
.secondary-button,
.action-button {
  min-height: 40px;
  padding: 9px 14px;
}

.secondary-button.small {
  min-height: 32px;
  padding: 6px 10px;
  font-size: 13px;
}

.primary-button {
  margin-top: 16px;
  color: #ffffff;
  background: #2f7d5c;
}

.secondary-button {
  color: #1d2733;
  background: #dce6ee;
}

.action-button {
  color: #ffffff;
  background: #486581;
}

.action-button.danger {
  background: #b23b3b;
}

.action-button.success {
  background: #2f7d5c;
}

.action-button.vote {
  background: #6b5bd6;
}

.action-button.selected {
  background: #2f7d5c;
}

.public-banner {
  margin-bottom: 14px;
  padding: 12px 14px;
  color: #293042;
  background: #fff7d6;
  border: 1px solid #e5c558;
  border-radius: 8px;
  font-weight: 800;
}

.main-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.main-actions .primary-button,
.main-actions .secondary-button {
  margin-top: 0;
}

.history-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #d7e0e8;
  border-radius: 8px;
  background: #ffffff;
}
.history-list { display: grid; gap: 8px; }
.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px;
  border: 1px solid #d7e0e8;
  border-radius: 8px;
  background: #f8fbfd;
}
.history-item div { display: grid; gap: 3px; }
.history-item span, .history-item small { color: #64748b; font-size: 13px; }

.room-browser {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #dbe3ea;
}

.room-browser-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.room-list-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  text-align: left;
  color: #1d2733;
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.room-list-item small {
  grid-column: 1 / -1;
  color: #64748b;
  line-height: 1.4;
}

.empty-state {
  margin-top: 10px;
  color: #64748b;
  line-height: 1.5;
}

.setup-panel,
.self-panel,
.vote-panel,
.game-over-panel,
.debug-panel {
  margin-top: 18px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.night-mode .setup-panel,
.night-mode .self-panel,
.night-mode .vote-panel,
.night-mode .game-over-panel,
.night-mode .debug-panel {
  background: rgba(31, 41, 55, 0.86);
  border-color: rgba(148, 163, 184, 0.25);
}

.ability-hint {
  margin-top: 12px;
  padding: 12px;
  background: #f4f8fb;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.ability-hint strong {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  text-transform: uppercase;
  color: #5b6b7c;
}

.ability-hint p {
  margin: 0;
  line-height: 1.5;
}

.ability-hint.secondary {
  background: #fff7e8;
  border-color: #efd39d;
}

.night-mode .ability-hint {
  background: rgba(15, 23, 42, 0.45);
  border-color: rgba(148, 163, 184, 0.28);
}

.night-mode .ability-hint strong {
  color: #b6c2d1;
}

.night-mode .ability-hint.secondary {
  background: rgba(92, 66, 20, 0.35);
  border-color: rgba(217, 179, 92, 0.38);
}

.vote-result {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #dbe3ea;
}

.vote-progress {
  margin: 8px 0 0;
  font-weight: 800;
  color: #486581;
}

.vote-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.vote-result strong {
  display: block;
  margin-bottom: 8px;
}

.vote-result-list {
  display: grid;
  gap: 6px;
}

.vote-result-list span {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 8px;
  background: #f4f8fb;
  border-radius: 8px;
}

.night-mode .vote-result {
  border-color: rgba(148, 163, 184, 0.25);
}

.night-mode .vote-result-list span {
  background: rgba(15, 23, 42, 0.45);
}

.room-ready-panel {
  margin-top: 16px;
  padding: 14px;
  background: #f4f8fb;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.room-ready-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.room-ready-header strong {
  display: block;
  margin-top: 4px;
  font-size: 22px;
}

.occupancy-meter {
  height: 10px;
  margin-top: 14px;
  overflow: hidden;
  background: #dce6ee;
  border-radius: 999px;
}

.occupancy-meter-fill {
  height: 100%;
  background: #2f7d5c;
  border-radius: inherit;
  transition: width 180ms ease;
}

.room-stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.room-stat {
  padding: 10px;
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.room-stat span {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

.room-stat strong {
  display: block;
  margin-top: 4px;
  font-size: 24px;
}

.seat-map {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(112px, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.seat-slot {
  min-height: 62px;
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 8px;
  align-items: center;
  padding: 10px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.seat-slot.empty {
  color: #64748b;
  background: rgba(255, 255, 255, 0.56);
  border-style: dashed;
}

.seat-slot.filled {
  border-color: rgba(47, 125, 92, 0.45);
}

.seat-number {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  color: #ffffff;
  background: #486581;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 900;
}

.seat-slot.empty .seat-number {
  color: #486581;
  background: #dce6ee;
}

.seat-slot strong {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}


.room-ready-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.secondary-button.success {
  color: #ffffff;
  background: #2f7d5c;
}

.seat-slot {
  text-align: left;
}

.seat-slot.clickable {
  border-color: rgba(72, 101, 129, 0.55);
}

.seat-slot.mine {
  box-shadow: 0 0 0 2px rgba(47, 125, 92, 0.15);
}

.seat-content {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.seat-content small,
.ready-badge {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.ready-badge.ready {
  color: #157347;
}

.night-mode .seat-content small,
.night-mode .ready-badge {
  color: #b6c2d1;
}

.night-mode .ready-badge.ready {
  color: #77d39d;
}
.waiting-list {
  margin-top: 14px;
  padding: 14px;
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.waiting-list-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.waiting-player-row {
  display: grid;
  grid-template-columns: 1fr minmax(130px, 0.75fr);
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  background: #eef3f7;
  border-radius: 8px;
}

.waiting-player-row span {
  color: #1d2733;
  font-weight: 700;
}

.waiting-player-row select {
  min-width: 0;
}

.night-mode .room-ready-panel,
.night-mode .waiting-list {
  background: rgba(15, 23, 42, 0.36);
  border-color: rgba(148, 163, 184, 0.25);
}

.night-mode .occupancy-meter {
  background: rgba(71, 85, 105, 0.72);
}

.night-mode .room-stat,
.night-mode .seat-slot {
  background: rgba(31, 41, 55, 0.7);
  border-color: rgba(148, 163, 184, 0.25);
}

.night-mode .room-stat span,
.night-mode .seat-slot.empty {
  color: #b6c2d1;
}

.night-mode .waiting-player-row {
  background: rgba(71, 85, 105, 0.45);
}

.night-mode .waiting-player-row span {
  color: #e5edf5;
}

.settings-grid,
.status-layout {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.button-row,
.global-actions,
.action-area {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.button-row,
.global-actions {
  margin-top: 18px;
}

.players-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.player-card {
  padding: 14px;
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.night-mode .player-card {
  background: #202b39;
  border-color: rgba(148, 163, 184, 0.28);
}

.player-card.mine {
  border-color: #2f7d5c;
}

.player-card.nominated {
  border-color: #6b5bd6;
  box-shadow: 0 0 0 2px rgba(107, 91, 214, 0.16);
}

.player-card.my-vote-target {
  border-color: #d08a22;
  box-shadow: 0 0 0 2px rgba(208, 138, 34, 0.18);
}

.player-card.dead {
  opacity: 0.56;
  filter: grayscale(1);
}

.player-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.player-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.player-meta span {
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.14);
}

.action-area {
  margin-top: 12px;
}

.error-banner {
  margin-top: 14px;
  padding: 12px;
  border-radius: 8px;
  color: #ffffff;
  background: #b23b3b;
}

.debug-panel ul {
  margin: 12px 0 0;
  padding-left: 20px;
}

@media (max-width: 640px) {
  .app-shell {
    padding: 14px;
  }

  .topbar {
    display: grid;
  }

  .topbar-actions {
    justify-content: flex-start;
  }

  .topbar h1 {
    font-size: 22px;
  }
}

.host-config-panel,.host-observer-panel{margin-top:18px;padding:18px;border:1px solid #c8d2dc;border-radius:8px;background:#f7fafc}.host-config-header,.observer-header,.save-deck-row{display:flex;align-items:center;justify-content:space-between;gap:12px}.deck-slot-grid,.observer-role-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:10px;margin-top:14px}.deck-slot-field{display:grid;gap:5px;font-size:13px;font-weight:700}.host-random-grid,.observer-data-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.host-advanced-panel{margin-top:16px;border:1px solid #d7e0e8;border-radius:8px;background:#fff;overflow:hidden}.host-advanced-toggle{width:100%;min-height:48px;padding:10px 12px;display:grid;grid-template-columns:110px 1fr auto;align-items:center;gap:12px;border:0;background:transparent;color:#243447;text-align:left;cursor:pointer}.host-advanced-toggle span{font-weight:850}.host-advanced-toggle small{color:#66788a;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.host-advanced-toggle strong{font-size:12px;color:#34516c}.host-advanced-body{padding:12px;border-top:1px solid #d7e0e8}.host-controlled-note{display:grid;gap:8px;align-content:start;padding:10px 12px;border:1px solid #d7e0e8;border-radius:8px;background:#f4f8fb}.host-controlled-note span{font-size:13px;font-weight:700;color:#536273}.host-controlled-note strong{font-size:14px;color:#243447}.host-controlled-note.full-row{margin-top:12px}.night-mode .host-advanced-panel{background:#171c25;border-color:#46505e}.night-mode .host-advanced-body{border-color:#46505e}.night-mode .host-controlled-note{background:rgba(15,23,42,.45);border-color:#46505e}.night-mode .host-advanced-toggle{color:#e5edf5}.night-mode .host-advanced-toggle small,.night-mode .host-controlled-note span{color:#aebccd}.night-mode .host-controlled-note strong{color:#e5edf5}.save-deck-row{margin-top:14px}.save-deck-row input{flex:1}.validation-text{margin-top:10px;color:#b42318;font-weight:700}.observer-player-row,.observer-log-row{display:flex;justify-content:space-between;gap:10px;padding:10px;border-bottom:1px solid #dbe3ea}.observer-player-row div{display:grid;gap:3px}.observer-status.alive{color:#157347}.observer-status.dead{color:#b42318}.night-mode .host-observer-panel{background:#202631;border-color:#4a5565}@media(max-width:700px){.host-random-grid,.observer-data-grid{grid-template-columns:1fr}.host-config-header,.save-deck-row{align-items:stretch;flex-direction:column}.host-advanced-toggle{grid-template-columns:1fr auto}.host-advanced-toggle small{grid-column:1/3;white-space:normal}}

.observer-vote-board,.observer-activity{margin-top:18px;border-top:1px solid #d7e0e8;padding-top:16px}
.observer-section-header{display:flex;align-items:end;justify-content:space-between;gap:12px}
.observer-section-header h3{margin:3px 0 0}
.vote-total-strip,.pending-action-strip{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}
.vote-total-strip span,.pending-action-strip span{padding:7px 9px;border:1px solid #d7e0e8;border-radius:6px;background:#fff;font-size:13px}
.observer-vote-table,.observer-log-table{margin-top:10px;border:1px solid #d7e0e8;border-radius:6px;overflow:hidden}
.observer-vote-row{display:grid;grid-template-columns:34px minmax(110px,.8fr) minmax(180px,1.5fr) 72px;align-items:center;gap:10px;min-height:42px;padding:6px 10px;border-bottom:1px solid #e3e9ef}
.observer-vote-row:last-child,.observer-event-row:last-child{border-bottom:0}
.observer-seat{font-weight:800;color:#64748b}
.observer-choice{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.vote-state{font-size:12px;font-weight:800;text-align:right}
.vote-state.confirmed{color:#157347}.vote-state.pending{color:#9a6700}
.observer-event-row{display:grid;grid-template-columns:72px minmax(120px,.8fr) minmax(130px,.8fr) minmax(180px,1.5fr);align-items:center;gap:12px;min-height:46px;padding:7px 10px;border-bottom:1px solid #e3e9ef;font-size:13px}
.observer-event-row time{font-variant-numeric:tabular-nums;color:#64748b}
.observer-event-actor{display:grid;gap:2px}.observer-event-actor span{color:#64748b;font-size:12px}
.observer-event-detail{overflow-wrap:anywhere;color:#536273}
.night-mode .observer-vote-board,.night-mode .observer-activity{border-color:#46505e}
.night-mode .vote-total-strip span,.night-mode .pending-action-strip span{background:#171c25;border-color:#46505e}
.night-mode .observer-vote-table,.night-mode .observer-log-table{border-color:#46505e}
.night-mode .observer-vote-row,.night-mode .observer-event-row{border-color:#353e4b}
.night-mode .observer-event-detail,.night-mode .observer-event-row time,.night-mode .observer-event-actor span{color:#aebccd}
@media(max-width:700px){.observer-vote-row{grid-template-columns:28px 1fr 70px}.observer-choice{grid-column:2/4;white-space:normal}.observer-event-row{grid-template-columns:64px 1fr}.observer-event-action,.observer-event-detail{grid-column:2}.observer-section-header{align-items:flex-start;flex-direction:column}}
</style>



