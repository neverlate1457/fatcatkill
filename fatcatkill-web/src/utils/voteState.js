export const isVotedPlayerForPhase = (phase, votedTargetId, playerId) => {
  if (!['NOMINATION', 'VOTING'].includes(phase)) return false
  if (votedTargetId == null || playerId == null) return false
  return String(votedTargetId) === String(playerId)
}
