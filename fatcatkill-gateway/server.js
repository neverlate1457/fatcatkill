const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const axios = require('axios');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  }
});

const SPRING_BOOT_URL = process.env.SPRING_BOOT_URL || process.env.GATEWAY_BACKEND_URL;
const PORT = Number(process.env.PORT || process.env.GATEWAY_PORT);
const roomParticipants = new Map();
const roomHosts = new Map();
const roomActionTails = new Map();
const roomIdentityClaims = new Map();
const DEBUG_ACTIONS_ENABLED = process.env.ENABLE_DEBUG_ACTIONS === 'true';
const PUBLIC_PHASES = new Set(['WAITING', 'DAY_START', 'NOMINATION', 'VOTING', 'GAME_OVER']);
const PHASE_ACTOR_ROLES = Object.freeze({
  STR_ACTION: 'STR', PH_SERVICE_ACTION: 'PH_SERVICE', GUOGUO_ACTION: 'GUOGUO',
  FORVKUSA_ACTION: 'FORVKUSA', HATONG_ACTION: 'HATONG', XIAOXIANG_ACTION: 'XIAOXIANG',
  MUBAIMU_ACTION: 'MUBAIMU', SHUSHU_ACTION: 'SHUSHU', GRASS_BEAN_ACTION: 'GRASS_BEAN',
  AC_CAT_ACTION: 'AC_CAT', XIANGXIANG_ACTION: 'XIANGXIANG', LIVER_INDEX_ACTION: 'LIVER_INDEX',
  CAN_MAN_ACTION: 'CAN_MAN', NANGONG_ACTION: 'NANGONG', ANDY_ACTION: 'ANDY',
  METHANE_ACTION: 'METHANE', MOCHI_BOSS_ACTION: 'MOCHI_BOSS'
});

const normalizeEndpoint = (endpoint) => {
  if (!endpoint || typeof endpoint !== 'string') {
    throw new Error('Missing endpoint.');
  }
  return endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
};

const allowedRoute = (method, pathname, roomId) => {
  const escapedRoomId = roomId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const routes = [
    ['GET', new RegExp(`^/room/${escapedRoomId}$`)],
    ['DELETE', new RegExp(`^/room/${escapedRoomId}$`)],
    ['POST', /^\/game\/action$/],
    ['POST', /^\/game\/bot\/auto$/],
    ['POST', /^\/day\/vote(?:\/confirm|\/cancel|\/skip)?$/],
    ['POST', new RegExp(`^/day/(?:nomination|tally)/${escapedRoomId}$`)],
    ['POST', new RegExp(`^/room/(?:start|fill-bots)/${escapedRoomId}$`)],
    ['GET', new RegExp(`^/logs/${escapedRoomId}$`)]
  ];
  if (DEBUG_ACTIONS_ENABLED) {
    routes.push(
      ['POST', new RegExp(`^/room/mock/${escapedRoomId}$`)],
      ['GET', new RegExp(`^/debug/reveal/${escapedRoomId}$`)],
      ['POST', /^\/debug\//]
    );
  }
  return routes.some(([allowedMethod, pattern]) => allowedMethod === method && pattern.test(pathname));
};

const hostOnlyRoute = (method, pathname) => method === 'DELETE'
  || pathname === '/game/bot/auto'
  || /^\/logs\//.test(pathname)
  || /^\/room\/(?:start|fill-bots|mock)\//.test(pathname)
  || /^\/day\/(?:nomination|tally)\//.test(pathname);

const validateIdentityClaim = (claims, clientId, userId, roomId) => {
  const claimedUserId = claims.byClient.get(clientId);
  if (claimedUserId != null && claimedUserId !== userId) {
    return `This browser already joined room ${roomId} as player ${claimedUserId}.`;
  }
  const claimedClientId = claims.byUser.get(userId);
  if (claimedClientId != null && claimedClientId !== clientId) {
    return `Player ID ${userId} is already in use in this room.`;
  }
  return null;
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
  return error.message || 'Action failed.';
};

const fetchRoomState = async (roomId) => {
  const response = await axios.get(`${SPRING_BOOT_URL}/room/${roomId}`);
  return response.data;
};

const sanitizeGameState = (gameState, viewerId) => {
  if (!gameState || typeof gameState !== 'object') return gameState;

  const sanitized = JSON.parse(JSON.stringify(gameState));
  const normalizedViewerId = viewerId == null ? null : String(viewerId);
const viewer = sanitized.players?.find((player) => String(player.userId) === normalizedViewerId);
  const actualPhase = sanitized.currentPhase;
  if (!PUBLIC_PHASES.has(actualPhase)) {
    const effectiveViewerRole = viewer?.role === 'PH_SERVICE' && sanitized.phServiceStolenRole
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

  const ownKey = normalizedViewerId;
  sanitized.privateMessages = ownKey && sanitized.privateMessages?.[ownKey]
    ? { [ownKey]: sanitized.privateMessages[ownKey] }
    : {};
  sanitized.highRabbitPerceivedRoles = ownKey && sanitized.highRabbitPerceivedRoles?.[ownKey]
    ? { [ownKey]: sanitized.highRabbitPerceivedRoles[ownKey] }
    : {};
  sanitized.phServiceStolenRole = viewer?.role === 'PH_SERVICE' ? sanitized.phServiceStolenRole : null;
  sanitized.ratManCheckerIds = viewer?.role === 'RAT_MAN' ? sanitized.ratManCheckerIds : [];
  sanitized.fatcatKillerPlayerId = String(sanitized.fatcatKillerPlayerId) === normalizedViewerId
    ? sanitized.fatcatKillerPlayerId
    : null;

  return sanitized;
};

const broadcastRoomState = async (roomId, fallbackData = null) => {
  let gameState = fallbackData?.gameState || fallbackData;

  try {
    gameState = await fetchRoomState(roomId);
  } catch (error) {
    if (!gameState) {
      throw error;
    }
  }

  const roomSockets = await io.in(roomId).fetchSockets();
  for (const roomSocket of roomSockets) {
    roomSocket.emit('gameStateUpdate', sanitizeGameState(gameState, roomSocket.data.userId));
  }
  return gameState;
};

const getRoomList = () => Array.from(roomParticipants.entries())
  .map(([roomId, participants]) => ({
    roomId,
    hostId: roomHosts.get(roomId) ?? null,
    capacity: Array.from(participants.values()).find((player) => player.userId === roomHosts.get(roomId))?.roomSize || 7,
    playerCount: participants.size,
    players: Array.from(participants.values())
  }))
  .filter((room) => room.playerCount > 0)
  .sort((a, b) => a.roomId.localeCompare(b.roomId));

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
  if (participants) {
    participants.delete(socket.id);
    if (participants.size === 0) {
      roomParticipants.delete(roomId);
    }
  }

  socket.leave(roomId);
  socket.data.roomId = null;
  broadcastRoomList();
};

io.on('connection', (socket) => {
  console.log(`Player connected: ${socket.id}`);

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
    const userId = Number(typeof payload === 'string' ? null : payload.userId);
    const clientId = String(typeof payload === 'string' ? '' : payload.clientId || '');
    const requestedRoomSize = typeof payload === 'string' ? 7 : Number(payload.roomSize);
    const roomSize = [6, 7, 10].includes(requestedRoomSize) ? requestedRoomSize : 7;
    const nickname = typeof payload === 'string'
      ? null
      : (payload.nickname || `Player ${userId || socket.id.slice(0, 5)}`);

    if (!roomId) return rejectJoin('Missing roomId.');
    if (!Number.isSafeInteger(userId) || userId <= 0) {
      return rejectJoin('A valid positive player ID is required.');
    }
    if (!/^[A-Za-z0-9-]{16,100}$/.test(clientId)) {
      return rejectJoin('A valid client identity is required.');
    }

    if (!roomIdentityClaims.has(roomId)) {
      roomIdentityClaims.set(roomId, { byClient: new Map(), byUser: new Map() });
    }
    const claims = roomIdentityClaims.get(roomId);
    const claimError = validateIdentityClaim(claims, clientId, userId, roomId);
    if (claimError) return rejectJoin(claimError);

    const existingParticipants = roomParticipants.get(roomId);
    if (existingParticipants) {
      for (const participant of existingParticipants.values()) {
        if (participant.clientId !== clientId || participant.socketId === socket.id) continue;
        const oldSocket = io.sockets.sockets.get(participant.socketId);
        if (oldSocket) oldSocket.disconnect(true);
      }
    }

    if (socket.data.roomId === roomId
        && socket.data.userId === userId
        && socket.data.clientId === clientId) {
      if (typeof ack === 'function') ack({ ok: true, roomId, userId, hostId: roomHosts.get(roomId) ?? userId });
      return;
    }

    leaveTrackedRoom(socket);
    claims.byClient.set(clientId, userId);
    claims.byUser.set(userId, clientId);

    socket.join(roomId);
    socket.data.roomId = roomId;
    socket.data.userId = userId;
    socket.data.clientId = clientId;
    socket.data.nickname = nickname;

    if (!roomParticipants.has(roomId)) roomParticipants.set(roomId, new Map());
    if (!roomHosts.has(roomId)) roomHosts.set(roomId, userId);
    roomParticipants.get(roomId).set(socket.id, {
      socketId: socket.id,
      clientId,
      userId,
      nickname,
      roomSize
    });
    broadcastRoomList();

    console.log(`Player ${socket.id} joined room ${roomId} as ${userId}`);
    socket.emit('message', `${nickname} connected to room ${roomId}`);
    if (typeof ack === 'function') ack({ ok: true, roomId, userId, hostId: roomHosts.get(roomId) });

    try {
      const gameState = await fetchRoomState(roomId);
      socket.emit('gameStateUpdate', sanitizeGameState(gameState, userId));
    } catch (error) {
      if (error.response?.status !== 404) {
        socket.emit('actionError', error.response?.data || 'Failed to load room.');
      }
    }
  });

  socket.on('gameAction', async (payload = {}, ack) => {
    try {
      if (!socket.data.roomId || socket.data.userId == null) {
        throw new Error('Join a room before sending actions.');
      }

      const requestedRoomId = String(payload.roomId || '');
      if (requestedRoomId !== socket.data.roomId) {
        throw new Error('Room mismatch.');
      }

      const endpointUrl = new URL(normalizeEndpoint(payload.endpoint), 'http://gateway.local');
      const httpMethod = String(payload.method || 'POST').toUpperCase();
      if (!allowedRoute(httpMethod, endpointUrl.pathname, socket.data.roomId)) {
        throw new Error('Action route is not allowed.');
      }
      if (hostOnlyRoute(httpMethod, endpointUrl.pathname)
          && roomHosts.get(socket.data.roomId) !== socket.data.userId) {
        throw new Error('Only the room host can perform this action.');
      }

      const path = `${endpointUrl.pathname}${endpointUrl.search}`;
      const url = `${SPRING_BOOT_URL}${path}`;
      const data = bindActorIdentity(payload.data || {}, socket);
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
        gameState: sanitizeGameState(updatedGameState, socket.data.userId)
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
      console.error('Action failed:', message);
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
    console.log(`Player disconnected: ${socket.id}`);
  });
});

if (require.main === module) {
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`Gateway running on port ${PORT}`);
  });
}

module.exports = {
  allowedRoute,
  bindActorIdentity,
  hostOnlyRoute,
  sanitizeGameState,
  validateIdentityClaim
};





