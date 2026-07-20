const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const axios = require('axios');
const cors = require('cors');
const { corsOrigin } = require('./corsConfig');
const { hasFullObserverAccess, sanitizeGameState } = require('./gameStateSanitizer');
const { allowedRoute, hostOnlyRoute, normalizeEndpoint } = require('./routeGuard');
const { registerHttpProxyRoutes } = require('./httpProxyRoutes');
const { logger } = require('./logger');

const SPRING_BOOT_URL = process.env.SPRING_BOOT_URL || process.env.GATEWAY_BACKEND_URL;
const PORT = Number(process.env.PORT || process.env.GATEWAY_PORT || 3000);


const app = express();
app.use(cors({ origin: corsOrigin }));
app.use(express.json());
registerHttpProxyRoutes(app, axios, SPRING_BOOT_URL);


const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: corsOrigin,
    methods: ['GET', 'POST']
  }
});

const roomParticipants = new Map();
const roomHosts = new Map();
const roomActionTails = new Map();
const roomIdentityClaims = new Map();
const messagePayload = (key, params = {}, fallback = key) => ({ key, params, fallback });

const gatewayError = (key, params = {}, fallback = key) => {
  const error = new Error(fallback);
  error.payload = messagePayload(key, params, fallback);
  return error;
};

const validateIdentityClaim = (claims, clientId, userId, roomId) => {
  const claimedUserId = claims.byClient.get(clientId);
  if (claimedUserId != null && claimedUserId !== userId) {
    return messagePayload("gateway.room.browserAlreadyJoined", { roomId, playerId: claimedUserId }, `This browser already joined room ${roomId} as player ${claimedUserId}.`);
  }
  const claimedClientId = claims.byUser.get(userId);
  if (claimedClientId != null && claimedClientId !== clientId) {
    return messagePayload("gateway.room.playerIdInUse", { playerId: userId }, `Player ID ${userId} is already in use in this room.`);
  }
  return null;
};
const roomCapacity = (roomId, fallbackSize = 7) => {
  const participants = Array.from(roomParticipants.get(roomId)?.values?.() || []);
  const hostId = roomHosts.get(roomId);
  return participants.find((participant) => participant.userId === hostId)?.roomSize
    || participants.find((participant) => participant.roomSize)?.roomSize
    || fallbackSize;
};

const playerParticipants = (roomId) => Array.from(roomParticipants.get(roomId)?.values?.() || [])
  .filter((participant) => !participant.isSpectator)
  .sort((a, b) => Number(a.userId) - Number(b.userId));

const releaseIdentityClaim = (roomId, clientId, userId, claimsByRoom = roomIdentityClaims) => {
  const claims = claimsByRoom.get(roomId);
  if (!claims) return;
  if (clientId && claims.byClient.get(clientId) === userId) claims.byClient.delete(clientId);
  if (userId != null && claims.byUser.get(userId) === clientId) claims.byUser.delete(userId);
};

const allocatePlayerId = (roomId, capacity) => {
  const used = new Set(playerParticipants(roomId).map((participant) => Number(participant.userId)));
  for (let seat = 1; seat <= capacity; seat += 1) {
    if (!used.has(seat)) return seat;
  }
  return null;
};

const allocateSpectatorId = (roomId) => {
  const claims = roomIdentityClaims.get(roomId);
  for (let id = 9001; id < 10000; id += 1) {
    if (!claims?.byUser?.has(id)) return id;
  }
  return Date.now();
};

const arePlayersReady = (players, hostId) => players.length === 0
  || players.every((participant) => participant.userId === hostId || participant.ready === true);

const allPlayersReady = (roomId) => arePlayersReady(playerParticipants(roomId), roomHosts.get(roomId));

const emitRoomIdentity = (socket) => {
  socket.emit('roomIdentityUpdate', {
    roomId: socket.data.roomId,
    userId: socket.data.userId,
    hostId: roomHosts.get(socket.data.roomId) ?? null,
    spectator: Boolean(socket.data.isSpectator)
  });
};


const acquireRoomLock = async (roomId) => {
  const previous = roomActionTails.get(roomId) || Promise.resolve();
  let release;
  const current = new Promise((resolve) => { release = resolve; });
  const tail = previous.catch(() => {}).then(() => current);
  roomActionTails.set(roomId, tail);
  await previous.catch(() => {});
  return () => {
    release();
    if (roomActionTails.get(roomId) === tail) roomActionTails.delete(roomId);
  };
};

const bindActorIdentity = (data, socket) => ({
  ...data,
  roomId: socket.data.roomId,
  playerId: socket.data.userId
});

const formatErrorMessage = (error) => {
  const data = error.response?.data;
  if (typeof data === 'string') return data;
  if (data?.message) return data.message;
  if (data?.error && data?.path) return `${data.error}: ${data.path}`;
  if (data?.error) return data.error;
  if (data) return JSON.stringify(data);
  if (error.payload) return error.payload;
  return messagePayload("gateway.actionFailed", {}, error.message || "Action failed.");
};

const fetchRoomState = async (roomId) => {
  const response = await axios.get(`${SPRING_BOOT_URL}/room/${roomId}`);
  return response.data;
};

const isFinishedGameState = (gameState) => gameState?.status === 'FINISHED' || gameState?.currentPhase === 'GAME_OVER';
const isRoomSetupOpen = (gameState) => !gameState || gameState.status === 'WAITING';

const ensureRoomSetupOpen = async (roomId, fetchState = fetchRoomState) => {
  try {
    if (!isRoomSetupOpen(await fetchState(roomId))) {
      throw gatewayError('gateway.roomSetup.gameAlreadyStarted', {}, 'Room setup is closed after the game starts.');
    }
  } catch (error) {
    if (error.response?.status === 404) return;
    throw error;
  }
};

const canCloseFinishedRoom = async (httpMethod, pathname, roomId, fetchState = fetchRoomState) => {
  if (httpMethod !== 'DELETE' || pathname !== `/room/${roomId}`) return false;
  try {
    return isFinishedGameState(await fetchState(roomId));
  } catch (error) {
    return false;
  }
};

const fallbackGameState = (fallbackData = null) => {
  if (fallbackData?.gameState) return fallbackData.gameState;
  return fallbackData;
};

const resolveBroadcastGameState = async (roomId, fallbackData = null, fetchState = fetchRoomState) => {
  let gameState = fallbackGameState(fallbackData);
  try {
    return await fetchState(roomId);
  } catch (error) {
    if (!gameState) throw error;
    return gameState;
  }
};

const broadcastRoomState = async (roomId, fallbackData = null) => {
  const gameState = await resolveBroadcastGameState(roomId, fallbackData);

  const roomSockets = await io.in(roomId).fetchSockets();
  for (const roomSocket of roomSockets) {
    roomSocket.emit('gameStateUpdate', sanitizeGameState(gameState, roomSocket.data.userId, roomSocket.data.isSpectator));
  }
  return gameState;
};

const participantNickname = (payload, userId, fallbackNickname = null) => typeof payload === 'string'
  ? fallbackNickname
  : (payload.nickname || fallbackNickname || `Player ${userId}`);

const applyParticipantIdentity = (participant, payload, userId, roomSize, accountId, sessionToken) => {
  if (!participant) return participant;
  participant.nickname = participantNickname(payload, userId, participant.nickname);
  participant.roomSize = roomSize;
  participant.accountId = Number.isSafeInteger(accountId) && accountId > 0 ? accountId : null;
  participant.sessionToken = sessionToken || null;
  return participant;
};
const publicRoomParticipant = (participant) => ({
  userId: participant.userId,
  nickname: participant.nickname,
  roomSize: participant.roomSize,
  isSpectator: Boolean(participant.isSpectator),
  ready: participant.ready === true
});
const getRoomList = () => Array.from(roomParticipants.entries())
  .map(([roomId, participants]) => {
    const connected = Array.from(participants.values());
    const players = connected.filter((participant) => !participant.isSpectator).sort((a, b) => Number(a.userId) - Number(b.userId));
    return {
      roomId,
      hostId: roomHosts.get(roomId) ?? null,
      capacity: connected.find((participant) => participant.userId === roomHosts.get(roomId))?.roomSize || 7,
      playerCount: players.length,
      players: players.map(publicRoomParticipant),
      spectators: connected.filter((participant) => participant.isSpectator).map(publicRoomParticipant)
    };
  })
  .filter((room) => room.playerCount > 0 || room.spectators.length > 0)
  .sort((a, b) => a.roomId.localeCompare(b.roomId));

const connectedPlayersForRoom = (participants) => Array.from(participants?.values?.() || [])
  .filter((participant) => !participant.isSpectator && participant.userId != null)
  .sort((a, b) => Number(a.userId) - Number(b.userId))
  .map((participant) => ({
    userId: Number(participant.userId),
    nickname: participant.nickname || `Player ${participant.userId}`,
    accountId: participant.accountId || null,
    sessionToken: participant.sessionToken || null
  }));

const broadcastRoomList = () => {
  io.emit('roomListUpdate', getRoomList());
};

const closeTrackedRoom = (roomId) => {
  const sockets = io.sockets.adapter.rooms.get(roomId);
  if (sockets) {
    for (const socketId of sockets) {
      const roomSocket = io.sockets.sockets.get(socketId);
      if (!roomSocket) continue;
      roomSocket.leave(roomId);
      if (roomSocket.data.roomId === roomId) {
        roomSocket.data.roomId = null;
      }
    }
  }
  roomParticipants.delete(roomId);
  roomHosts.delete(roomId);
  roomActionTails.delete(roomId);
  roomIdentityClaims.delete(roomId);
  broadcastRoomList();
};

const leaveTrackedRoom = (socket) => {
  const roomId = socket.data.roomId;
  if (!roomId) return;

  const participants = roomParticipants.get(roomId);
  const leaving = participants?.get(socket.id);
  if (leaving) {
    releaseIdentityClaim(roomId, leaving.clientId, leaving.userId);
  }

  if (participants) {
    participants.delete(socket.id);
    if (participants.size === 0) {
      roomParticipants.delete(roomId);
      roomHosts.delete(roomId);
      roomIdentityClaims.delete(roomId);
    } else if (leaving && roomHosts.get(roomId) === leaving.userId) {
      const nextHost = Array.from(participants.values()).find((participant) => participant.isSpectator)
        || playerParticipants(roomId)[0]
        || Array.from(participants.values())[0];
      if (nextHost) roomHosts.set(roomId, nextHost.userId);
    }
  }

  socket.leave(roomId);
  socket.data.roomId = null;
  socket.data.userId = null;
  socket.data.isSpectator = false;
  broadcastRoomList();
};

io.on('connection', (socket) => {
  logger.info(`Player connected: ${socket.id}`);

  socket.on('listRooms', (ack) => {
    const rooms = getRoomList();
    socket.emit('roomListUpdate', rooms);
    if (typeof ack === 'function') {
      ack({ ok: true, rooms });
    }
  });

  socket.on('joinRoom', async (payload = {}, ack) => {
    const rejectJoin = (message) => {
      socket.emit('actionError', message);
      if (typeof ack === 'function') ack({ ok: false, error: message });
    };

    const roomId = String(typeof payload === 'string' ? payload : payload.roomId || '');
    let userId = Number(typeof payload === 'string' ? null : payload.userId);
    const hasExplicitUserId = Number.isSafeInteger(userId) && userId > 0;
    const clientId = String(typeof payload === 'string' ? '' : payload.clientId || '');
    const requestedRoomSize = typeof payload === 'string' ? 7 : Number(payload.roomSize);
    const roomSize = [6, 7, 10].includes(requestedRoomSize) ? requestedRoomSize : 7;
    const wantsSpectator = typeof payload !== 'string' && payload.spectator === true;
    const accountId = Number(typeof payload === 'string' ? null : payload.accountId);
    const sessionToken = typeof payload === 'string' ? null : payload.sessionToken;

    if (!roomId) return rejectJoin(messagePayload('gateway.join.missingRoomId', {}, 'Missing roomId.'));
    if (!/^[A-Za-z0-9-]{16,100}$/.test(clientId)) {
      return rejectJoin(messagePayload('gateway.join.invalidClientId', {}, 'A valid client identity is required.'));
    }

    if (!roomIdentityClaims.has(roomId)) {
      roomIdentityClaims.set(roomId, { byClient: new Map(), byUser: new Map() });
    }

    const existingClaim = roomIdentityClaims.get(roomId).byClient.get(clientId);
    if (!hasExplicitUserId && existingClaim != null) {
      userId = existingClaim;
    } else if (!hasExplicitUserId && wantsSpectator) {
      userId = roomHosts.get(roomId) || allocateSpectatorId(roomId);
    } else if (!hasExplicitUserId) {
      const openSeat = allocatePlayerId(roomId, roomSize);
      if (openSeat == null) return rejectJoin(messagePayload('gateway.join.roomFull', {}, 'This room is full.'));
      userId = openSeat;
    }

    if (!Number.isSafeInteger(userId) || userId <= 0) {
      return rejectJoin(messagePayload('gateway.join.validPositiveId', {}, 'A valid positive player ID is required.'));
    }
    if (!wantsSpectator && (userId < 1 || userId > roomSize)) {
      return rejectJoin(messagePayload('gateway.join.idRange', { roomSize }, `Player ID must be between 1 and ${roomSize}.`));
    }
    if (wantsSpectator && roomHosts.has(roomId) && roomHosts.get(roomId) !== userId) {
      return rejectJoin(messagePayload('gateway.join.hostSpectatorOnly', {}, 'Only the room host can join as spectator.'));
    }

    let claims = roomIdentityClaims.get(roomId);
    const claimError = validateIdentityClaim(claims, clientId, userId, roomId);
    if (claimError) return rejectJoin(claimError);

    const existingParticipants = roomParticipants.get(roomId);
    if (existingParticipants) {
      for (const participant of existingParticipants.values()) {
        if (participant.clientId !== clientId || participant.socketId === socket.id) continue;
        const oldSocket = io.sockets.sockets.get(participant.socketId);
        if (oldSocket) {
          leaveTrackedRoom(oldSocket);
          oldSocket.disconnect(true);
        }
      }
    }

    if (!roomIdentityClaims.has(roomId)) {
      roomIdentityClaims.set(roomId, { byClient: new Map(), byUser: new Map() });
    }
    claims = roomIdentityClaims.get(roomId);
    const refreshedClaimError = validateIdentityClaim(claims, clientId, userId, roomId);
    if (refreshedClaimError) return rejectJoin(refreshedClaimError);

    if (socket.data.roomId === roomId
        && socket.data.userId === userId
        && socket.data.clientId === clientId
        && socket.data.isSpectator === wantsSpectator) {
      const participant = applyParticipantIdentity(roomParticipants.get(roomId)?.get(socket.id), payload, userId, roomSize, accountId, sessionToken);
      if (participant) socket.data.nickname = participant.nickname;
      emitRoomIdentity(socket);
      broadcastRoomList();
      if (typeof ack === 'function') ack({ ok: true, roomId, userId, hostId: roomHosts.get(roomId) ?? userId });
      return;
    }

    leaveTrackedRoom(socket);
    claims.byClient.set(clientId, userId);
    claims.byUser.set(userId, clientId);

    const nickname = participantNickname(payload, userId, null) || `Player ${userId || socket.id.slice(0, 5)}`;

    socket.join(roomId);
    socket.data.roomId = roomId;
    socket.data.userId = userId;
    socket.data.clientId = clientId;
    socket.data.nickname = nickname;
    socket.data.isSpectator = wantsSpectator;

    if (!roomParticipants.has(roomId)) roomParticipants.set(roomId, new Map());
    if (!roomHosts.has(roomId)) roomHosts.set(roomId, userId);
    const participant = applyParticipantIdentity({
      socketId: socket.id,
      clientId,
      userId,
      nickname,
      roomSize,
      isSpectator: wantsSpectator,
      ready: wantsSpectator
    }, payload, userId, roomSize, accountId, sessionToken);
    roomParticipants.get(roomId).set(socket.id, participant);
    broadcastRoomList();

    logger.info(`Player ${socket.id} joined room ${roomId} as ${userId}`);
    socket.emit('message', `${nickname} connected to room ${roomId}`);
    emitRoomIdentity(socket);
    if (typeof ack === 'function') ack({ ok: true, roomId, userId, hostId: roomHosts.get(roomId) });

    try {
      const gameState = await fetchRoomState(roomId);
      socket.emit('gameStateUpdate', sanitizeGameState(gameState, userId, wantsSpectator));
    } catch (error) {
      if (error.response?.status !== 404) {
        socket.emit('actionError', error.response?.data || 'Failed to load room.');
      }
    }
  });
  socket.on('setReady', async (payload = {}, ack) => {
    try {
      const roomId = socket.data.roomId;
      const participants = roomParticipants.get(roomId);
      const participant = participants?.get(socket.id);
      if (!roomId || !participant) throw gatewayError('gateway.ready.joinRoomFirst', {}, 'Join a room before changing ready state.');
      await ensureRoomSetupOpen(roomId);
      if (participant.isSpectator) throw gatewayError('gateway.ready.spectatorNoReady', {}, 'Spectators do not need to ready.');
      participant.ready = payload.ready === true;
      broadcastRoomList();
      if (typeof ack === 'function') ack({ ok: true, ready: participant.ready });
    } catch (error) {
      const message = formatErrorMessage(error);
      socket.emit('actionError', message);
      if (typeof ack === 'function') ack({ ok: false, error: message });
    }
  });

  socket.on('moveSeat', async (payload = {}, ack) => {
    try {
      const roomId = socket.data.roomId;
      const participants = roomParticipants.get(roomId);
      const participant = participants?.get(socket.id);
      if (!roomId || !participant) throw gatewayError('gateway.seat.joinRoomFirst', {}, 'Join a room before moving seats.');
      await ensureRoomSetupOpen(roomId);
      if (participant.isSpectator) throw gatewayError('gateway.seat.spectatorCannotSeat', {}, 'Spectators cannot occupy player seats.');
      const targetSeat = Number(payload.seatId);
      const capacity = roomCapacity(roomId, participant.roomSize);
      if (!Number.isSafeInteger(targetSeat) || targetSeat < 1 || targetSeat > capacity) {
        throw gatewayError("gateway.seat.invalidSeat", { capacity }, `Choose an open seat from 1 to ${capacity}.`);
      }
      const occupied = Array.from(participants.values()).find((entry) => !entry.isSpectator && Number(entry.userId) === targetSeat && entry.socketId !== socket.id);
      if (occupied) throw gatewayError('gateway.seat.occupied', {}, 'That seat is already occupied.');
      const oldUserId = participant.userId;
      releaseIdentityClaim(roomId, participant.clientId, oldUserId);
      if (!roomIdentityClaims.has(roomId)) {
        roomIdentityClaims.set(roomId, { byClient: new Map(), byUser: new Map() });
      }
      let claims = roomIdentityClaims.get(roomId);
      const claimError = validateIdentityClaim(claims, participant.clientId, targetSeat, roomId);
      if (claimError) {
        claims.byClient.set(participant.clientId, oldUserId);
        claims.byUser.set(oldUserId, participant.clientId);
        throw gatewayError(claimError.key, claimError.params, claimError.fallback);
      }
      claims.byClient.set(participant.clientId, targetSeat);
      claims.byUser.set(targetSeat, participant.clientId);
      participant.userId = targetSeat;
      participant.ready = false;
      socket.data.userId = targetSeat;
      if (roomHosts.get(roomId) === oldUserId) roomHosts.set(roomId, targetSeat);
      emitRoomIdentity(socket);
      broadcastRoomList();
      if (typeof ack === 'function') ack({ ok: true, userId: targetSeat, hostId: roomHosts.get(roomId) });
    } catch (error) {
      const message = formatErrorMessage(error);
      socket.emit('actionError', message);
      if (typeof ack === 'function') ack({ ok: false, error: message });
    }
  });

  socket.on('kickPlayer', async (payload = {}, ack) => {
    try {
      const roomId = socket.data.roomId;
      const participants = roomParticipants.get(roomId);
      if (!roomId || !participants) throw gatewayError('gateway.kick.joinRoomFirst', {}, 'Join a room before kicking players.');
      await ensureRoomSetupOpen(roomId);
      if (roomHosts.get(roomId) !== socket.data.userId) throw gatewayError('gateway.kick.hostOnly', {}, 'Only the room host can kick players.');
      const targetUserId = Number(payload.userId);
      const targetEntry = Array.from(participants.values()).find((participant) => !participant.isSpectator && Number(participant.userId) === targetUserId);
      if (!targetEntry) throw gatewayError('gateway.kick.playerNotFound', {}, 'Player not found in this room.');
      if (targetEntry.socketId === socket.id) throw gatewayError('gateway.kick.cannotKickSelf', {}, 'Host cannot kick themself.');
      const targetSocket = io.sockets.sockets.get(targetEntry.socketId);
      releaseIdentityClaim(roomId, targetEntry.clientId, targetEntry.userId);
      participants.delete(targetEntry.socketId);
      if (targetSocket) {
        targetSocket.emit('kickedFromRoom', { roomId, message: messagePayload('room.kicked', {}, 'You were kicked from the room.') });
        targetSocket.leave(roomId);
        targetSocket.data.roomId = null;
        targetSocket.data.userId = null;
        targetSocket.data.isSpectator = false;
      }
      broadcastRoomList();
      if (typeof ack === 'function') ack({ ok: true });
    } catch (error) {
      const message = formatErrorMessage(error);
      socket.emit('actionError', message);
      if (typeof ack === 'function') ack({ ok: false, error: message });
    }
  });
  socket.on('gameAction', async (payload = {}, ack) => {
    try {
      if (!socket.data.roomId || socket.data.userId == null) {
        throw gatewayError('gateway.action.joinRoomFirst', {}, 'Join a room before sending actions.');
      }

      const requestedRoomId = String(payload.roomId || '');
      if (requestedRoomId !== socket.data.roomId) {
        throw gatewayError('gateway.action.roomMismatch', {}, 'Room mismatch.');
      }

      const endpointUrl = new URL(normalizeEndpoint(payload.endpoint), 'http://gateway.local');
      const httpMethod = String(payload.method || 'POST').toUpperCase();
      if (!allowedRoute(httpMethod, endpointUrl.pathname, socket.data.roomId)) {
        throw gatewayError('gateway.action.routeNotAllowed', {}, 'Action route is not allowed.');
      }
      const isHostRoute = hostOnlyRoute(httpMethod, endpointUrl.pathname);
      if (isHostRoute && roomHosts.get(socket.data.roomId) !== socket.data.userId) {
        const canCloseAsParticipant = await canCloseFinishedRoom(httpMethod, endpointUrl.pathname, socket.data.roomId);
        if (!canCloseAsParticipant) {
          throw gatewayError('gateway.action.hostOnly', {}, 'Only the room host can perform this action.');
        }
      }
      if (socket.data.isSpectator && !isHostRoute
          && !(httpMethod === 'GET' && endpointUrl.pathname === `/room/${socket.data.roomId}`)) {
        throw gatewayError('gateway.action.spectatorCannotAct', {}, 'Spectators cannot perform player actions.');
      }
      if (httpMethod === 'POST' && /^\/room\/start\//.test(endpointUrl.pathname) && !allPlayersReady(socket.data.roomId)) {
        throw gatewayError('gateway.action.playersMustReady', {}, 'All non-host players must be ready before the host can start.');
      }

      const path = `${endpointUrl.pathname}${endpointUrl.search}`;
      const url = `${SPRING_BOOT_URL}${path}`;
      let data = bindActorIdentity(payload.data || {}, socket);
      if (/^\/room\/(?:start|fill-bots)\//.test(endpointUrl.pathname)) {
        data = {
          ...data,
          hostMode: Boolean(socket.data.isSpectator),
          players: connectedPlayersForRoom(roomParticipants.get(socket.data.roomId))
        };
      }
      const response = httpMethod === 'GET'
        ? await axios.get(url, { params: data })
        : httpMethod === 'DELETE'
          ? await axios.delete(url, { data })
          : await axios.post(url, data);

      if (httpMethod === 'GET' && (endpointUrl.pathname.startsWith('/debug/') || endpointUrl.pathname.startsWith('/logs/'))) {
        if (typeof ack === 'function') ack({ ok: true, data: response.data });
        return;
      }

      if (httpMethod === 'DELETE') {
        if (typeof ack === 'function') ack({ ok: true, gameState: null });
        io.to(socket.data.roomId).emit('roomClosed', { roomId: socket.data.roomId });
        closeTrackedRoom(socket.data.roomId);
        return;
      }

      const updatedGameState = await broadcastRoomState(socket.data.roomId, response.data);
      const result = {
        ok: true,
        message: response.data?.message || null,
        gameState: sanitizeGameState(updatedGameState, socket.data.userId, socket.data.isSpectator)
      };
      if (response.data?.message) socket.emit('actionResult', result);
      if (typeof ack === 'function') ack(result);
    } catch (error) {
      const isMissingWaitingRoom = error.response?.status === 404
        && String(payload.method || 'POST').toUpperCase() === 'GET'
        && String(payload.endpoint || '').startsWith(`/room/${socket.data.roomId}`);
      if (isMissingWaitingRoom) {
        if (typeof ack === 'function') ack({ ok: true, gameState: null });
        return;
      }

      const message = formatErrorMessage(error);
      logger.error('Action failed:', message);
      socket.emit('actionError', message);
      if (typeof ack === 'function') ack({ ok: false, error: message });
    }
  });

  socket.on('leaveRoom', (ack) => {
    leaveTrackedRoom(socket);
    if (typeof ack === 'function') {
      ack({ ok: true });
    }
  });

  socket.on('disconnect', () => {
    leaveTrackedRoom(socket);
    logger.info(`Player disconnected: ${socket.id}`);
  });
});

if (require.main === module) {
  server.listen(PORT, '0.0.0.0', () => {
    logger.info(`Gateway running on port ${PORT}`);
  });
}

module.exports = {
  allowedRoute,
  arePlayersReady,
  bindActorIdentity,
  hostOnlyRoute,
  connectedPlayersForRoom,
  publicRoomParticipant,
  applyParticipantIdentity,
  sanitizeGameState,
  hasFullObserverAccess,
  validateIdentityClaim,
  releaseIdentityClaim,
  canCloseFinishedRoom,
  isFinishedGameState,
  isRoomSetupOpen,
  ensureRoomSetupOpen,
  resolveBroadcastGameState,
  formatErrorMessage
};
