const test = require('node:test');
const assert = require('node:assert/strict');
const { allowedRoute, applyParticipantIdentity, arePlayersReady, bindActorIdentity, connectedPlayersForRoom, formatErrorMessage, hostOnlyRoute, canCloseFinishedRoom, isFinishedGameState, publicRoomParticipant, resolveBroadcastGameState, isRoomSetupOpen, ensureRoomSetupOpen, sanitizeGameState, validateIdentityClaim, releaseIdentityClaim } = require('./server');
const { authHeaders, registerHttpProxyRoutes } = require('./httpProxyRoutes');

test('route allowlist rejects arbitrary and cross-room endpoints', () => {
  assert.equal(allowedRoute('POST', '/game/action', '1234'), true);
  assert.equal(allowedRoute('GET', '/room/1234', '1234'), true);
  assert.equal(allowedRoute('GET', '/room/9999', '1234'), false);
  assert.equal(allowedRoute('POST', '/debug/setRole', '1234'), false);
  assert.equal(allowedRoute('POST', '/anything', '1234'), false);
});

test('socket identity overrides payload identity', () => {
  const bound = bindActorIdentity(
    { roomId: 'victim-room', playerId: 999, targetId: 2 },
    { data: { roomId: 'real-room', userId: 7 } }
  );
  assert.equal(bound.roomId, 'real-room');
  assert.equal(bound.playerId, 7);
  assert.equal(bound.targetId, 2);
});

test('sanitized state hides other players and secret state', () => {
  const state = sanitizeGameState({
    players: [
      { userId: 1, role: 'FATCAT', votedTargetId: 2 },
      { userId: 2, role: 'METHANE', votedTargetId: 1 }
    ],
    logs: [{ actionType: 'FATCAT_KILL' }],
    strOriginalSeatNumbers: { 1: 2 },
    strTemporarySeatNumbers: { 1: 2 },
    strSwappedSeatNumbers: [1, 2],
    lastDayVoterIds: [1],
    privateMessages: {},
    highRabbitPerceivedRoles: {}
  }, 2);

  assert.equal(state.players[0].role, null);
  assert.equal(state.players[0].votedTargetId, null);
  assert.equal(state.players[1].role, 'METHANE');
  assert.equal(state.players[1].votedTargetId, 1);
  assert.deepEqual(state.logs, []);
  assert.deepEqual(state.strOriginalSeatNumbers, {});
  assert.deepEqual(state.lastDayVoterIds, []);
});
test('sanitized state strips hidden night and host configuration fields', () => {
  const state = sanitizeGameState({
    status: 'PLAYING',
    currentPhase: 'DAY_START',
    players: [
      { userId: 1, role: 'METHANE', alive: true },
      { userId: 2, role: 'FATCAT', alive: true }
    ],
    hostConfiguredFatcatHintRoles: ['GUOGUO'],
    hostConfiguredHighRabbitRole: 'METHANE',
    hostConfiguredMethaneHallucinationTargetId: 2,
    drunkUntilRounds: { 1: 2 },
    nangongUsedPlayerIds: [1],
    xiaoenRedirectRound: 1,
    privateMessages: {},
    highRabbitPerceivedRoles: {}
  }, 1);

  assert.equal(state.hostConfiguredFatcatHintRoles, null);
  assert.equal(state.hostConfiguredHighRabbitRole, null);
  assert.equal(state.hostConfiguredMethaneHallucinationTargetId, null);
  assert.deepEqual(state.drunkUntilRounds, {});
  assert.deepEqual(state.nangongUsedPlayerIds, []);
  assert.equal(state.xiaoenRedirectRound, null);
});
test('sanitized state exposes only the viewer private state', () => {
  const state = sanitizeGameState({
    currentPhase: 'DAY_START',
    players: [
      { userId: 1, role: 'HIGH_RABBIT', alive: true },
      { userId: 2, role: 'METHANE', alive: true }
    ],
    privateMessages: { 1: 'viewer-secret', 2: 'other-secret' },
    highRabbitPerceivedRoles: { 1: 'METHANE', 2: 'FATCAT' }
  }, 1);

  assert.deepEqual(state.privateMessages, { 1: 'viewer-secret' });
  assert.deepEqual(state.highRabbitPerceivedRoles, { 1: 'METHANE' });
  assert.equal(JSON.stringify(state).includes('other-secret'), false);
});
test('host spectator receives full game state', () => {
  const game = {
    currentPhase: 'METHANE_ACTION',
    players: [
      { userId: 1, role: 'FATCAT', alive: true },
      { userId: 2, role: 'METHANE', alive: true }
    ],
    nightActions: { FATCAT_KILL: 2 },
    logs: [{ actionType: 'FATCAT_KILL' }]
  };

  const state = sanitizeGameState(game, 99, true);
  assert.equal(state.currentPhase, 'METHANE_ACTION');
  assert.equal(state.players[0].role, 'FATCAT');
  assert.equal(state.players[1].role, 'METHANE');
  assert.equal(state.nightActions.FATCAT_KILL, 2);
  assert.equal(state.logs.length, 1);
});

test('sanitized state strips account credentials even for full observers', () => {
  const state = sanitizeGameState({
    status: 'PLAYING',
    currentPhase: 'METHANE_ACTION',
    players: [
      { userId: 1, role: 'FATCAT', alive: true, accountId: 42, sessionToken: 'secret-token' },
      { userId: 2, role: 'METHANE', alive: false, accountId: 43, sessionToken: 'other-token' }
    ],
    nightActions: { FATCAT_KILL: 2 },
    logs: [{ actionType: 'FATCAT_KILL' }]
  }, 2);

  assert.equal(state.players[0].role, 'FATCAT');
  assert.equal(Object.hasOwn(state.players[0], 'accountId'), false);
  assert.equal(Object.hasOwn(state.players[0], 'sessionToken'), false);
  assert.equal(Object.hasOwn(state.players[1], 'accountId'), false);
});
test('night phase is visible only to its acting role', () => {
  const game = {
    currentPhase: 'METHANE_ACTION',
    players: [
      { userId: 1, role: 'FATCAT', alive: true },
      { userId: 2, role: 'METHANE', alive: true }
    ],
    privateMessages: {},
    highRabbitPerceivedRoles: {}
  };

  assert.equal(sanitizeGameState(game, 1).currentPhase, 'NIGHT_WAITING');
  assert.equal(sanitizeGameState(game, 2).currentPhase, 'METHANE_ACTION');
});
test('High Energy Rabbit illusion reveals the perceived role ability phase', () => {
  const game = {
    currentPhase: 'METHANE_ACTION',
    players: [{ userId: 1, role: 'HIGH_RABBIT', alive: true }],
    privateMessages: {},
    highRabbitPerceivedRoles: { 1: 'METHANE' }
  };

  const state = sanitizeGameState(game, 1);
  assert.equal(state.currentPhase, 'METHANE_ACTION');
  assert.equal(state.highRabbitPerceivedRoles[1], 'METHANE');
});

test('room list participants hide account credentials', () => {
  const publicParticipant = publicRoomParticipant({
    socketId: 'socket-1',
    clientId: 'client-1',
    userId: 7,
    nickname: 'Human',
    roomSize: 7,
    isSpectator: false,
    ready: true,
    accountId: 42,
    sessionToken: 'token-42'
  });

  assert.equal(publicParticipant.userId, 7);
  assert.equal(Object.hasOwn(publicParticipant, 'socketId'), false);
  assert.equal(Object.hasOwn(publicParticipant, 'clientId'), false);
  assert.equal(Object.hasOwn(publicParticipant, 'accountId'), false);
  assert.equal(Object.hasOwn(publicParticipant, 'sessionToken'), false);
});
test('fill-bot player payload excludes host spectators', () => {
  const participants = new Map([
    ['host', { userId: 99, nickname: 'Host', isSpectator: true }],
    ['player', { userId: 7, nickname: 'Human', isSpectator: false, accountId: 42, sessionToken: 'token-42' }]
  ]);
  assert.deepEqual(connectedPlayersForRoom(participants), [{ userId: 7, nickname: 'Human', accountId: 42, sessionToken: 'token-42' }]);
});

test('bot automation, debug actions, and room setup are host-only', () => {
  assert.equal(hostOnlyRoute('POST', '/game/bot/auto'), true);
  assert.equal(hostOnlyRoute('POST', '/room/start/1234'), true);
  assert.equal(hostOnlyRoute('GET', '/debug/reveal/1234'), true);
  assert.equal(hostOnlyRoute('POST', '/debug/setRole'), true);
  assert.equal(hostOnlyRoute('POST', '/day/vote'), false);
});

test('finished game logs are allowed for room participants without host privileges', () => {
  assert.equal(allowedRoute('GET', '/logs/1234', '1234'), true);
  assert.equal(allowedRoute('GET', '/logs/9999', '1234'), false);
  assert.equal(hostOnlyRoute('GET', '/logs/1234'), false);
});

test('finished room close helper allows only completed rooms', async () => {
  assert.equal(isFinishedGameState({ status: 'FINISHED' }), true);
  assert.equal(isFinishedGameState({ currentPhase: 'GAME_OVER' }), true);
  assert.equal(isFinishedGameState({ status: 'PLAYING', currentPhase: 'DAY_START' }), false);
  assert.equal(await canCloseFinishedRoom('DELETE', '/room/1234', '1234', async () => ({ status: 'FINISHED' })), true);
  assert.equal(await canCloseFinishedRoom('DELETE', '/room/1234', '1234', async () => ({ status: 'PLAYING' })), false);
  assert.equal(await canCloseFinishedRoom('POST', '/room/1234', '1234', async () => ({ status: 'FINISHED' })), false);
  assert.equal(await canCloseFinishedRoom('DELETE', '/room/9999', '1234', async () => ({ status: 'FINISHED' })), false);
});
test('room setup controls are blocked after the game starts', async () => {
  assert.equal(isRoomSetupOpen(null), true);
  assert.equal(isRoomSetupOpen({ status: 'WAITING' }), true);
  assert.equal(isRoomSetupOpen({ status: 'PLAYING' }), false);
  assert.equal(isRoomSetupOpen({ status: 'FINISHED' }), false);

  await ensureRoomSetupOpen('new-room', async () => {
    const error = new Error('not found');
    error.response = { status: 404 };
    throw error;
  });
  await ensureRoomSetupOpen('waiting-room', async () => ({ status: 'WAITING' }));

  await assert.rejects(
    () => ensureRoomSetupOpen('playing-room', async () => ({ status: 'PLAYING' })),
    (error) => error.payload?.key === 'gateway.roomSetup.gameAlreadyStarted'
  );
});

test('room identity claims prevent switching IDs and duplicate IDs', () => {
  const claims = {
    byClient: new Map([['client-a-1234567', 1]]),
    byUser: new Map([[1, 'client-a-1234567']])
  };

  assert.equal(validateIdentityClaim(claims, 'client-a-1234567', 1, 'room-1'), null);
  const browserClaimError = validateIdentityClaim(claims, 'client-a-1234567', 2, 'room-1');
  assert.equal(browserClaimError.key, 'gateway.room.browserAlreadyJoined');
  assert.match(browserClaimError.fallback, /already joined/);
  const duplicateIdError = validateIdentityClaim(claims, 'client-b-1234567', 1, 'room-1');
  assert.equal(duplicateIdError.key, 'gateway.room.playerIdInUse');
  assert.match(duplicateIdError.fallback, /already in use/);
});

test('released identity claims can be reused by another client', () => {
  const claims = {
    byClient: new Map([['client-a-1234567', 1]]),
    byUser: new Map([[1, 'client-a-1234567']])
  };
  const roomId = 'room-1';
  const claimsByRoom = new Map([[roomId, claims]]);

  releaseIdentityClaim(roomId, 'client-a-1234567', 1, claimsByRoom);

  assert.equal(validateIdentityClaim(claims, 'client-b-1234567', 1, roomId), null);
  assert.equal(claims.byClient.has('client-a-1234567'), false);
  assert.equal(claims.byUser.has(1), false);
});
test('gateway error formatter preserves message payloads', () => {
  const payload = { key: 'gateway.action.roomMismatch', params: {}, fallback: 'Room mismatch.' };
  assert.deepEqual(formatErrorMessage({ payload }), payload);
  assert.deepEqual(formatErrorMessage(new Error('Boom')), {
    key: 'gateway.actionFailed',
    params: {},
    fallback: 'Boom'
  });
});
test('authHeaders forwards history credentials', () => {
  const req = {
    get(name) {
      return {
        'x-user-id': '42',
        'x-auth-token': 'token-abc'
      }[name];
    }
  };

  assert.deepEqual(authHeaders(req), {
    'X-User-Id': '42',
    'X-Auth-Token': 'token-abc'
  });
});

test('proxy fallback errors use message payloads', async () => {
  const routes = {};
  const app = {
    post(path, handler) { routes[`POST ${path}`] = handler; },
    get(path, handler) { routes[`GET ${path}`] = handler; }
  };
  const axios = {
    async post() { throw new Error('backend unavailable'); },
    async get() { throw new Error('backend unavailable'); }
  };
  registerHttpProxyRoutes(app, axios, 'http://backend/api');

  let statusCode = null;
  let responseBody = null;
  const res = {
    status(code) { statusCode = code; return this; },
    json(body) { responseBody = body; return this; }
  };

  await routes['POST /auth/login']({ body: {} }, res);

  assert.equal(statusCode, 500);
  assert.deepEqual(responseBody, {
    message: {
      key: 'gateway.auth.loginFailed',
      params: {},
      fallback: 'Login failed.'
    }
  });
});
test('docker compose defines FatCatKill services', () => {
  const compose = require('node:fs').readFileSync(require('node:path').join(__dirname, '..', 'docker-compose.yml'), 'utf8');

  assert.match(compose, /backend:\s*[\r\n]+\s+build:\s*[\r\n]+\s+context: \.\/fatcatkill-api/);
  assert.match(compose, /gateway:\s*[\r\n]+\s+build:\s*[\r\n]+\s+context: \.\/fatcatkill-gateway/);
  assert.match(compose, /frontend:\s*[\r\n]+\s+build:\s*[\r\n]+\s+context: \.\/fatcatkill-web/);
  assert.match(compose, /gateway:[\s\S]*ENABLE_DEBUG_ACTIONS: \$\{ENABLE_DEBUG_ACTIONS:-false\}/);
  assert.doesNotMatch(compose, /palworld/i);
});
test('default CORS origin is not wildcard', () => {
  assert.deepEqual(require('./corsConfig').allowedOrigins, ['http://localhost:5173']);
});
test('rejoining same socket refreshes participant account credentials', () => {
  const participant = {
    socketId: 'socket-1',
    clientId: 'client-1',
    userId: 3,
    nickname: 'Old name',
    roomSize: 7,
    isSpectator: false,
    ready: true,
    accountId: null,
    sessionToken: null
  };

  applyParticipantIdentity(participant, { nickname: 'New name' }, 3, 10, 42, 'token-42');

  assert.equal(participant.nickname, 'New name');
  assert.equal(participant.roomSize, 10);
  assert.equal(participant.accountId, 42);
  assert.equal(participant.sessionToken, 'token-42');
  assert.equal(participant.ready, true);
});
test('arePlayersReady ignores the host ready state but requires every other player', () => {
  assert.equal(arePlayersReady([], 1), true);
  assert.equal(arePlayersReady([
    { userId: 1, ready: false },
    { userId: 2, ready: true }
  ], 1), true);
  assert.equal(arePlayersReady([
    { userId: 1, ready: false },
    { userId: 2, ready: false }
  ], 1), false);
});
test('broadcast state resolution uses final action response when active room is gone', async () => {
  const finalState = { status: 'FINISHED', currentPhase: 'GAME_OVER', winnerCamp: 'VILLAGER' };
  const resolved = await resolveBroadcastGameState('room-1', { gameState: finalState, message: { key: 'done' } }, async () => {
    const error = new Error('not found');
    error.response = { status: 404 };
    throw error;
  });

  assert.equal(resolved, finalState);
});
