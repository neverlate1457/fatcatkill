const DEBUG_ACTIONS_ENABLED = process.env.ENABLE_DEBUG_ACTIONS === 'true';

const gatewayError = (key, params = {}, fallback = key) => {
  const error = new Error(fallback);
  error.payload = { key, params, fallback };
  return error;
};
const escapeRegExp = (text) => String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const normalizeEndpoint = (endpoint) => {
  if (!endpoint || typeof endpoint !== 'string') {
    throw gatewayError('gateway.action.missingEndpoint', {}, 'Missing endpoint.');
  }
  return endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
};

const allowedRoute = (method, pathname, roomId) => {
  const escapedRoomId = escapeRegExp(roomId);
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

module.exports = {
  allowedRoute,
  hostOnlyRoute,
  normalizeEndpoint
};