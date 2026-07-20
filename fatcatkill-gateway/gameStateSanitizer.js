const PUBLIC_PHASES = new Set(['WAITING', 'DAY_START', 'NOMINATION', 'VOTING', 'GAME_OVER']);

const PHASE_ACTOR_ROLES = Object.freeze({
  STR_ACTION: 'STR',
  PH_SERVICE_ACTION: 'PH_SERVICE',
  GUOGUO_ACTION: 'GUOGUO',
  FORVKUSA_ACTION: 'FORVKUSA',
  HATONG_ACTION: 'HATONG',
  XIAOXIANG_ACTION: 'XIAOXIANG',
  MUBAIMU_ACTION: 'MUBAIMU',
  SHUSHU_ACTION: 'SHUSHU',
  GRASS_BEAN_ACTION: 'GRASS_BEAN',
  AC_CAT_ACTION: 'AC_CAT',
  XIANGXIANG_ACTION: 'XIANGXIANG',
  LIVER_INDEX_ACTION: 'LIVER_INDEX',
  CAN_MAN_ACTION: 'CAN_MAN',
  NANGONG_ACTION: 'NANGONG',
  ANDY_ACTION: 'ANDY',
  METHANE_ACTION: 'METHANE',
  MOCHI_BOSS_ACTION: 'MOCHI_BOSS'
});

const hasFullObserverAccess = (gameState, viewerId, spectator = false) => {
  if (spectator) return true;
  if (!gameState || viewerId == null) return false;
  const viewer = gameState.players?.find((player) => String(player.userId) === String(viewerId));
  return Boolean(viewer && viewer.alive === false && gameState.status !== 'WAITING');
};

const stripSensitiveFields = (gameState) => {
  if (!gameState || typeof gameState !== 'object') return gameState;
  if (Array.isArray(gameState.players)) {
    gameState.players = gameState.players.map(({ accountId, sessionToken, ...player }) => player);
  }
  return gameState;
};

const sanitizeGameState = (gameState, viewerId, spectator = false) => {
  if (!gameState || typeof gameState !== 'object') return gameState;

  const sanitized = JSON.parse(JSON.stringify(gameState));
  if (hasFullObserverAccess(sanitized, viewerId, spectator)) return stripSensitiveFields(sanitized);
  const normalizedViewerId = viewerId == null ? null : String(viewerId);
  const viewer = sanitized.players?.find((player) => String(player.userId) === normalizedViewerId);
  const actualPhase = sanitized.currentPhase;
  if (!PUBLIC_PHASES.has(actualPhase)) {
    const perceivedRole = sanitized.highRabbitPerceivedRoles?.[normalizedViewerId];
    const effectiveViewerRole = viewer?.role === 'HIGH_RABBIT' && perceivedRole
      ? perceivedRole
      : viewer?.role === 'PH_SERVICE' && sanitized.phServiceStolenRole
        ? sanitized.phServiceStolenRole
        : viewer?.role;
    const requiredRole = PHASE_ACTOR_ROLES[actualPhase];
    const isFatcatTurn = actualPhase === 'NIGHT_START'
      && (sanitized.fatcatKillerPlayerId != null
        ? String(sanitized.fatcatKillerPlayerId) === normalizedViewerId
        : viewer?.role === 'FATCAT');
    const isViewerTurn = Boolean(viewer?.alive)
      && (isFatcatTurn || (requiredRole && effectiveViewerRole === requiredRole));
    if (!isViewerTurn) sanitized.currentPhase = 'NIGHT_WAITING';
  }

  sanitized.players = (sanitized.players || []).map((player) => {
    const isViewer = String(player.userId) === normalizedViewerId;
    return {
      ...player,
      role: isViewer ? player.role : null,
      votedTargetId: isViewer ? player.votedTargetId : null
    };
  });
  sanitized.nightActions = {};
  sanitized.methaneHallucinationTargetId = null;
  sanitized.guoguoHint = null;
  sanitized.andyCloudPlayerId = null;
  sanitized.mochiBossPendingCheckPlayerId = null;
  sanitized.chenPendingKillPlayerId = null;
  sanitized.fatcatAbsentVolunteerHintRoles = [];
  sanitized.fatcatKillBlockedPlayerIds = [];
  sanitized.delayedDeathRounds = {};
  sanitized.mubaimuDoomRounds = {};
  sanitized.barkKingDoomRounds = {};
  sanitized.drunkUntilRounds = {};
  sanitized.logs = [];
  sanitized.strOriginalSeatNumbers = {};
  sanitized.strTemporarySeatNumbers = {};
  sanitized.strSwappedSeatNumbers = [];
  sanitized.lastDayVoterIds = [];
  sanitized.kbNominationTrapTriggeredIds = [];
  sanitized.chenUsedPlayerIds = [];
  sanitized.chenSkippedRounds = {};
  sanitized.saltedFishUsedPlayerIds = [];
  sanitized.saltedFishSkippedRounds = {};
  sanitized.nangongUsedPlayerIds = [];
  sanitized.xiaoenRedirectRound = null;

  const ownKey = normalizedViewerId;
  sanitized.privateMessages = ownKey && sanitized.privateMessages?.[ownKey]
    ? { [ownKey]: sanitized.privateMessages[ownKey] }
    : {};
  sanitized.highRabbitPerceivedRoles = ownKey && sanitized.highRabbitPerceivedRoles?.[ownKey]
    ? { [ownKey]: sanitized.highRabbitPerceivedRoles[ownKey] }
    : {};
  sanitized.phServiceStolenRole = viewer?.role === 'PH_SERVICE' ? sanitized.phServiceStolenRole : null;
  sanitized.ratManCheckerIds = viewer?.role === 'RAT_MAN' ? sanitized.ratManCheckerIds : [];
  sanitized.hostConfiguredFatcatHintRoles = null;
  sanitized.hostConfiguredHighRabbitRole = null;
  sanitized.hostConfiguredMethaneHallucinationTargetId = null;
  sanitized.fatcatKillerPlayerId = String(sanitized.fatcatKillerPlayerId) === normalizedViewerId
    ? sanitized.fatcatKillerPlayerId
    : null;

  return stripSensitiveFields(sanitized);
};

module.exports = {
  PUBLIC_PHASES,
  PHASE_ACTOR_ROLES,
  hasFullObserverAccess,
  stripSensitiveFields,
  sanitizeGameState
};
