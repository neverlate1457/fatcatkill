import { computed } from 'vue'
import { fatcatHorcruxRoles, roleTranslations, translateRole, translateRoleHint } from '../data/roles'
import { t, translateMessage } from '../i18n'
import { isVotedPlayerForPhase } from '../utils/voteState'

export const usePlayerGameView = ({
  gameState,
  userId,
  phase,
  isNight,
  methaneSelection,
  mubaimuSelection,
  shushuSelection
}) => {
  const roleName = translateRole
  const roleHint = translateRoleHint
  const testRoleOptions = computed(() => Object.keys(roleTranslations).sort((a, b) => roleName(a).localeCompare(roleName(b), 'zh-Hant')))

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

  const alivePlayers = computed(() => gameState.value?.players?.filter((player) => player.alive) || [])

  const eligibleVoters = computed(() => {
    const finalVoteIds = new Set((gameState.value?.finalVoteEligiblePlayerIds || []).map(String))
    return gameState.value?.players?.filter(
      (player) => player.alive || finalVoteIds.has(String(player.userId))
    ) || []
  })

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

  const confirmedAliveCount = computed(() => eligibleVoters.value.filter((player) => player.voteConfirmed).length)

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

  const fatcatHintButtonText = computed(() => myPlayer.value?.role === 'FATCAT' ? t('button.scoutAbsentRoles') : t('button.teamHint'))

  const currentPhaseText = computed(() => {
    if (!gameState.value) return t('phase.noGame')
    if (phase.value === 'NOMINATION') return t('phase.nominationPrompt')
    if (phase.value === 'VOTING') {
      return nominatedPlayer.value
        ? t('phase.executionVoteTarget', { name: nominatedPlayer.value.username })
        : t('phase.executionVoteNoNominee')
    }
    if (phase.value === 'METHANE_ACTION' && canActAsRole('METHANE')) return t('phase.methaneSelect', { count: methaneSelection.value.length })
    if (phase.value === 'MUBAIMU_ACTION' && canActAsRole('MUBAIMU')) return t('phase.mubaimuSelect', { count: mubaimuSelection.value.length })
    if (phase.value === 'SHUSHU_ACTION' && canActAsRole('SHUSHU')) return t('phase.shushuSelect', { count: shushuSelection.value.length })
    if (phase.value === 'PH_SERVICE_ACTION' && canActAsRole('PH_SERVICE')) return t('phase.phServicePrompt')
    if (phase.value === 'NIGHT_START' && canActAsRole('FATCAT') && gameState.value.currentRound === 1) return t('phase.fatcatFirstNight')
    return t(`phaseLabel.${phase.value}`, {}, phase.value)
  })

  const dayFlowText = computed(() => {
    if (phase.value === 'NOMINATION') {
      if (myPlayer.value?.voteConfirmed && myVotedPlayer.value) return t('day.confirmedNomination', { name: myVotedPlayer.value.username })
      if (myPlayer.value?.voteConfirmed) return t('day.skippedNomination')
      return myVotedPlayer.value ? t('day.selectedNomination', { name: myVotedPlayer.value.username }) : t('day.chooseNominee')
    }
    if (phase.value === 'VOTING' && nominatedPlayer.value) {
      const majority = Math.floor(alivePlayers.value.length / 2) + 1
      const status = myPlayer.value?.voteConfirmed
        ? (myVotedPlayer.value ? t('day.voteStatusConfirmed') : t('day.voteStatusSkipped'))
        : (myVotedPlayer.value ? t('day.voteStatusSelected') : t('day.voteStatusChoose'))
      return t('day.executionNeedVotes', { name: nominatedPlayer.value.username, majority, alive: alivePlayers.value.length, status })
    }
    return t('day.waitingAdvance')
  })

  const tallyButtonText = computed(() => {
    if (phase.value === 'NOMINATION') return t('button.tallyNominations')
    if (phase.value === 'VOTING') return t('button.tallyExecution')
    return t('button.advance')
  })

  const botActionButtonText = computed(() => {
    if (phase.value === 'NOMINATION') return t('button.allBotsNominate')
    if (phase.value === 'VOTING') return t('button.allBotsVote')
    return t('button.botAutoStep')
  })

  const myRoleHint = computed(() => roleHint(myDisplayedRole.value))
  const myEffectiveRoleHint = computed(() => {
    if (myPlayer.value?.role !== 'PH_SERVICE' || !gameState.value?.phServiceStolenRole) return null
    return roleHint(gameState.value.phServiceStolenRole)
  })

  const myPrivateMessage = computed(() => {
    if (!gameState.value || !myPlayer.value) return ''
    const messages = gameState.value.privateMessages || {}
    return translateMessage(messages[myPlayer.value.userId] || messages[String(myPlayer.value.userId)] || '')
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
          label: player ? `${player.username} (${roleName(player.role)})` : t('topbar.player', { id: playerId }),
          count
        }
      })
      .sort((a, b) => b.count - a.count || a.playerId - b.playerId)
  })

  const isMyVotedPlayer = (player) => isVotedPlayerForPhase(phase.value, myPlayer.value?.votedTargetId, player?.userId)

  const canVoteFor = (player) => {
    if (!canUseDayVote.value || myPlayer.value?.voteConfirmed || !player.alive || player.userId === myPlayer.value?.userId) return false
    if (phase.value === 'NOMINATION') return true
    return gameState.value?.nominatedPlayerId === player.userId
  }

  const dayVoteButtonText = (player) => {
    if (phase.value === 'NOMINATION') return isMyVotedPlayer(player) ? t('button.cancelSelection') : t('button.selectNominee')
    if (phase.value === 'VOTING') return isMyVotedPlayer(player) ? t('button.cancelYes') : t('button.selectYes')
    return t('button.vote')
  }

  return {
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
  }
}
