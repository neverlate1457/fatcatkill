import { t } from '../i18n'
import { AUTH_KEY } from '../config/appConfig'

export const useAuth = ({
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
}) => {
  const saveAuthUser = (user) => {
    authUser.value = user
    window.localStorage.setItem(AUTH_KEY, JSON.stringify(user))
    if (!nickname.value.trim()) nickname.value = user.username
    loadGameHistory()
  }

  const submitAuth = async () => {
    const fallback = authMode.value === 'register' ? t('error.registration') : t('error.login')
    const username = authUsername.value.trim()
    const password = authPassword.value

    if (!username || !password) {
      showActionError('backend.auth.usernamePasswordRequired')
      return
    }

    try {
      const endpoint = authMode.value === 'register' ? '/auth/register' : '/auth/login'
      const response = await fetch(`${gatewayUrl}${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) {
        showActionError(data?.message || data || fallback, fallback)
        return
      }
      saveAuthUser(data)
      authPassword.value = ''
      clearActionError()
    } catch (error) {
      showActionError(error, fallback)
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
      username: `${t('topbar.guest')} ${suffix}`,
      gamesPlayed: 0,
      gamesWon: 0,
      guest: true
    }
    saveAuthUser(guest)
    authUsername.value = guest.username
    authPassword.value = ''
    clearActionError()
  }

  const requireLogin = () => {
    if (isLoggedIn.value) return true
    showActionError(t('error.loginRequired'))
    return false
  }

  return {
    submitAuth,
    logoutAuth,
    loginAsGuest,
    requireLogin
  }
}
