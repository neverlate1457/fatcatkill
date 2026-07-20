import assert from 'node:assert/strict';
import { test } from 'node:test';
import viteConfig from '../vite.config.js';

test('dev server proxies socket and HTTP gateway routes', () => {
  const config = viteConfig({ mode: 'test' });
  const proxy = config.server.proxy;

  assert.equal(proxy['/socket.io'].ws, true);
  assert.ok(proxy['/socket.io'].target);
  assert.equal(proxy['/auth'].target, proxy['/socket.io'].target);
  assert.equal(proxy['/history'].target, proxy['/socket.io'].target);
});