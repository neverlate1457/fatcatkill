const LEVELS = {
  silent: 0,
  error: 1,
  info: 2
};

const configuredLevel = String(process.env.LOG_LEVEL || 'info').toLowerCase();
const currentLevel = LEVELS[configuredLevel] ?? LEVELS.info;

const logger = {
  info: (...args) => {
    if (currentLevel >= LEVELS.info) console.log(...args);
  },
  error: (...args) => {
    if (currentLevel >= LEVELS.error) console.error(...args);
  }
};

module.exports = { logger };