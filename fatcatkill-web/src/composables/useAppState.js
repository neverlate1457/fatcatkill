import { computed, ref, watch } from 'vue'
import { resolveBrowserServiceUrl } from '../serviceUrl'
import { t } from '../i18n'
import { AUTH_KEY, CLIENT_ID_KEY, defaultHostDeckRoles, HOST_DECKS_KEY, STORAGE_KEY } from '../config/appConfig'

const loadJson = (key, fallback) => {
  try {
    return JSON.parse(window.localStorage.getItem(key) || JSON.stringify(fallback))
  } catch {
    return fallback
  }
}

const cryptoRandomSuffix = () => {
  if (window.crypto?.getRandomValues) {
    const value = new Uint32Array(2)
    window.crypto.getRandomValues(value)
    return Array.from(value, (part) => part.toString(36)).join('')
  }
  return String(Date.now())
}

const createClientId = () => {
  const saved = window.localStorage.getItem(CLIENT_ID_KEY)
  if (saved) return saved
  const created = window.crypto?.randomUUID?.() || `${Date.now()}-${cryptoRandomSuffix()}`
  window.localStorage.setItem(CLIENT_ID_KEY, created)
  return created
}

const defaultHostDeck = (size) => defaultHostDeckRoles.slice(0, Number(size) || 7)

export const useAppState = () => {
  const gatewayUrl = resolveBrowserServiceUrl(import.meta.env.VITE_GATEWAY_URL)
  const clientId = createClientId()
  const savedSession = loadJson(STORAGE_KEY, {})

  const authUser = ref(loadJson(AUTH_KEY, null))
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
  const actionNotice = ref('')
  const pendingKickPlayer = ref(null)
  const showRoomList = ref(false)
  const connectedRooms = ref([])
  const historyRecords = ref([])
  const historyLoading = ref(false)
  const revealPlayers = ref([])
  const testRoleAssignments = ref({})
  const methaneSelection = ref([])
  const mubaimuSelection = ref([])
  const shushuSelection = ref([])
  const selectedMode = ref(savedSession.selectedMode || 'OLD_HOME')
  const roomSize = ref(savedSession.roomSize || 7)
  const hostMode = ref(Boolean(savedSession.hostMode))
  const customDeck = ref(Array.isArray(savedSession.customDeck) && savedSession.customDeck.length === Number(roomSize.value)
    ? [...savedSession.customDeck]
    : defaultHostDeck(roomSize.value))
  const fatcatHintRoles = ref(Array.isArray(savedSession.fatcatHintRoles) ? [...savedSession.fatcatHintRoles] : [])
  const highRabbitRole = ref(savedSession.highRabbitRole || '')
  const hostMethaneHallucinationTargetId = ref(savedSession.hostMethaneHallucinationTargetId || '')
  const hostAdvancedOpen = ref(Boolean(savedSession.hostAdvancedOpen))
  const deckName = ref('')
  const savedDecks = ref(loadJson(HOST_DECKS_KEY, []))
  const selectedSavedDeck = ref('')
  const phServiceTargetRole = ref('METHANE')

  const displayName = computed(() => nickname.value.trim() || authUser.value?.username || (userId.value ? t('topbar.player', { id: userId.value }) : t('topbar.guest')))
  const isLoggedIn = computed(() => Boolean(authUser.value?.id))

  const sessionPayload = () => ({
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
  })

  const saveSession = () => {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(sessionPayload()))
  }

  const clearSavedRoom = () => {
    const { roomId: ignoredRoomId, userId: ignoredUserId, hostMode: ignoredHostMode, ...nextSession } = sessionPayload()
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession))
  }

  const clearRoomIdentity = () => {
    roomId.value = ''
    userId.value = ''
    hostMode.value = false
    clearSavedRoom()
  }

  watch([roomId, userId, nickname, selectedMode, roomSize, hostMode, customDeck, fatcatHintRoles, highRabbitRole, hostMethaneHallucinationTargetId, hostAdvancedOpen], saveSession, { deep: true })
  watch(roomSize, (size) => {
    if (hostMode.value && customDeck.value.length !== Number(size)) {
      customDeck.value = defaultHostDeck(size)
      fatcatHintRoles.value = []
      highRabbitRole.value = ''
      hostMethaneHallucinationTargetId.value = ''
    }
  })

  return {
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
  }
}
