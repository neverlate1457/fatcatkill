import { computed } from 'vue'
import { hostVoteLogTypes } from '../config/appConfig'
import { t, translateMessage } from '../i18n'

export const useHostObserverView = ({ gameState, phase }) => {
  const hostPlayerLabel = (playerId) => {
    if (playerId == null) return '-'
    const player = gameState.value?.players?.find((item) => String(item.userId) === String(playerId))
    return player ? t('observer.seatPlayer', { seat: player.seatNumber, name: player.username }) : t('topbar.player', { id: playerId })
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
    return [...totals.entries()]
      .map(([playerId, count]) => ({ playerId, label: hostPlayerLabel(playerId), count }))
      .sort((a, b) => b.count - a.count)
  })

  const hostActivityLogs = computed(() => [...(gameState.value?.logs || [])]
    .filter((entry) => !hostVoteLogTypes.has(entry.actionType))
    .reverse()
    .slice(0, 40))

  const hostActionLabel = (actionType) => {
    if (!actionType) return t('log.systemEvent')
    const key = `logAction.${actionType}`
    const fallback = String(actionType).replaceAll('_', ' ')
    return t(key, {}, fallback)
  }

  const hostLogTime = (timestamp) => {
    if (!timestamp) return '--:--:--'
    const date = new Date(timestamp)
    return Number.isNaN(date.getTime()) ? '--:--:--' : date.toLocaleTimeString('zh-TW', { hour12: false })
  }

  const hostLogDetail = (entry) => {
    if (entry.messageKey) return translateMessage({ key: entry.messageKey, params: entry.messageParams, fallback: entry.messageFallback || entry.message })
    if (entry.message) return translateMessage(entry.message)
    const targets = [entry.targetId, entry.targetId2].filter((id) => id != null).map(hostPlayerLabel)
    return targets.length ? targets.join(' / ') : t('observer.sent')
  }

  return {
    hostPlayerLabel,
    hostVoteRows,
    hostConfirmedVotes,
    hostVoteTotals,
    hostActivityLogs,
    hostActionLabel,
    hostLogTime,
    hostLogDetail
  }
}
