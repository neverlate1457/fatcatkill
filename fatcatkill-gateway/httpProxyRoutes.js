const messagePayload = (key, params = {}, fallback = key) => ({ key, params, fallback });

const authHeaders = (req) => {
  const headers = {};
  const userId = req.get('x-user-id');
  const authToken = req.get('x-auth-token');
  if (userId) headers['X-User-Id'] = userId;
  if (authToken) headers['X-Auth-Token'] = authToken;
  return headers;
};

const registerHttpProxyRoutes = (app, axios, backendUrl) => {
  const forwardError = (res, error, key, fallback) => {
    const status = error.response?.status || 500;
    res.status(status).json(error.response?.data || { message: messagePayload(key, {}, fallback) });
  };

  app.post('/auth/register', async (req, res) => {
    try {
      const response = await axios.post(`${backendUrl}/auth/register`, req.body || {});
      res.status(response.status).json(response.data);
    } catch (error) {
      forwardError(res, error, 'gateway.auth.registrationFailed', 'Registration failed.');
    }
  });

  app.post('/auth/login', async (req, res) => {
    try {
      const response = await axios.post(`${backendUrl}/auth/login`, req.body || {});
      res.status(response.status).json(response.data);
    } catch (error) {
      forwardError(res, error, 'gateway.auth.loginFailed', 'Login failed.');
    }
  });

  app.get('/history', async (req, res) => {
    try {
      const response = await axios.get(`${backendUrl}/history`, { headers: authHeaders(req) });
      res.status(response.status).json(response.data);
    } catch (error) {
      forwardError(res, error, 'gateway.history.loadFailed', 'Failed to load game history.');
    }
  });

  app.get('/history/:gameId', async (req, res) => {
    try {
      const response = await axios.get(`${backendUrl}/history/${encodeURIComponent(req.params.gameId)}`, { headers: authHeaders(req) });
      res.status(response.status).json(response.data);
    } catch (error) {
      forwardError(res, error, 'gateway.history.loadFailed', 'Failed to load game history.');
    }
  });
};

module.exports = {
  registerHttpProxyRoutes,
  authHeaders
};