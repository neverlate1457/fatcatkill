const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1'])

export const resolveBrowserServiceUrl = (configuredUrl) => {
  if (!configuredUrl) return window.location.origin

  const url = new URL(configuredUrl, window.location.origin)
  if (LOOPBACK_HOSTS.has(url.hostname) && !LOOPBACK_HOSTS.has(window.location.hostname)) {
    url.hostname = window.location.hostname
  }

  return url.toString().replace(/\/$/, '')
}
