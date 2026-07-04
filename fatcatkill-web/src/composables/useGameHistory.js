import { t } from '../i18n'
import { downloadJson } from '../utils/downloadJson'

export const historyWinnerText = (winnerCamp) => {
  if (winnerCamp === 'WOLF') return t('history.fatcatWin')
  if (winnerCamp === 'VILLAGER') return t('history.volunteerWin')
  return winnerCamp || t('history.unknownWinner')
}

export const historyTimeText = (value) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

export const useGameHistory = ({ gatewayUrl, authUser, isLoggedIn, historyLoading, historyRecords, showActionError, clearActionError }) => {
  const historyHeaders = () => {
    if (!authUser.value?.id || !authUser.value?.sessionToken) return null
    return {
      'X-User-Id': String(authUser.value.id),
      'X-Auth-Token': authUser.value.sessionToken
    }
  }

  const loadGameHistory = async () => {
    if (!isLoggedIn.value) return
    const headers = historyHeaders()
    if (!headers) {
      historyRecords.value = []
      return
    }
    historyLoading.value = true
    try {
      const response = await fetch(`${gatewayUrl}/history`, { headers })
      const data = await response.json().catch(() => [])
      if (!response.ok) throw data.message || new Error(t('error.loadHistory'))
      historyRecords.value = Array.isArray(data) ? data : []
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.loadHistory'))
    } finally {
      historyLoading.value = false
    }
  }

  const downloadHistoryRecord = async (record) => {
    const headers = historyHeaders()
    if (!headers) {
      showActionError('backend.auth.unauthorized', t('error.downloadHistory'))
      return
    }
    try {
      const response = await fetch(`${gatewayUrl}/history/${encodeURIComponent(record.gameId)}`, { headers })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw data.message || new Error(t('error.downloadHistory'))
      const parsedState = typeof data.finalState === 'string' ? JSON.parse(data.finalState) : data.finalState
      downloadJson({ ...data.summary, finalState: parsedState }, `fatcatkill_history_${record.roomId}_${record.gameId}.json`)
      clearActionError()
    } catch (error) {
      showActionError(error, t('error.downloadHistory'))
    }
  }

  return { loadGameHistory, downloadHistoryRecord }
}