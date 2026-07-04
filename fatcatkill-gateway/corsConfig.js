const allowedOrigins = (process.env.ALLOWED_ORIGINS || '*')
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);

const corsOrigin = allowedOrigins.includes('*') ? '*' : allowedOrigins;

module.exports = {
  allowedOrigins,
  corsOrigin
};