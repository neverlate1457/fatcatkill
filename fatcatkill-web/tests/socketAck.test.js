import assert from 'node:assert/strict';
import { test } from 'node:test';
import { emitWithAck } from '../src/utils/socketAck.js';

const createSocket = (handler) => ({
  calls: [],
  emit(eventName, ...args) {
    this.calls.push({ eventName, args });
    handler?.(eventName, args);
  }
});

test('emitWithAck resolves ack responses with payload', async () => {
  const socket = createSocket((eventName, args) => {
    assert.equal(eventName, 'gameAction');
    assert.deepEqual(args[0], { roomId: 1 });
    args[1]({ ok: true });
  });

  const response = await emitWithAck(socket, 'gameAction', { roomId: 1 }, 'timeout', 50);

  assert.deepEqual(response, { ok: true });
  assert.equal(socket.calls.length, 1);
});

test('emitWithAck supports ack-only socket events', async () => {
  const socket = createSocket((eventName, args) => {
    assert.equal(eventName, 'leaveRoom');
    assert.equal(args.length, 1);
    args[0]({ ok: true });
  });

  const response = await emitWithAck(socket, 'leaveRoom', undefined, 'timeout', 50);

  assert.deepEqual(response, { ok: true });
});

test('emitWithAck rejects when ack does not arrive before timeout', async () => {
  const socket = createSocket();

  await assert.rejects(
    emitWithAck(socket, 'gameAction', {}, 'gateway timeout', 1),
    /gateway timeout/
  );
});