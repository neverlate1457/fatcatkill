const test = require('node:test');
const assert = require('node:assert/strict');
const { allowedRoute, bindActorIdentity, connectedPlayersForRoom, hostOnlyRoute, sanitizeGameState, validateIdentityClaim } = require('./server');

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

test('fill-bot player payload excludes host spectators', () => {
  const participants = new Map([
    ['host', { userId: 99, nickname: 'Host', isSpectator: true }],
    ['player', { userId: 7, nickname: 'Human', isSpectator: false }]
  ]);
  assert.deepEqual(connectedPlayersForRoom(participants), [{ userId: 7, nickname: 'Human' }]);
});

test('bot automation and room setup are host-only', () => {
  assert.equal(hostOnlyRoute('POST', '/game/bot/auto'), true);
  assert.equal(hostOnlyRoute('POST', '/room/start/1234'), true);
  assert.equal(hostOnlyRoute('POST', '/day/vote'), false);
});
test('room identity claims prevent switching IDs and duplicate IDs', () => {
  const claims = {
    byClient: new Map([['client-a-1234567', 1]]),
    byUser: new Map([[1, 'client-a-1234567']])
  };

  assert.equal(validateIdentityClaim(claims, 'client-a-1234567', 1, 'room-1'), null);
  assert.match(validateIdentityClaim(claims, 'client-a-1234567', 2, 'room-1'), /already joined/);
  assert.match(validateIdentityClaim(claims, 'client-b-1234567', 1, 'room-1'), /already in use/);
});