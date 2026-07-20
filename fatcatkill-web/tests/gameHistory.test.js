import test from 'node:test'
import assert from 'node:assert/strict'
import { parseHistoryFinalState } from '../src/utils/historyState.js'

test('parseHistoryFinalState accepts object and valid JSON states', () => {
  const state = { roomId: 'room-1', players: [] }

  assert.equal(parseHistoryFinalState(state), state)
  assert.deepEqual(parseHistoryFinalState('{"roomId":"room-2","players":[]}'), {
    roomId: 'room-2',
    players: []
  })
})

test('parseHistoryFinalState tolerates empty or malformed archived states', () => {
  assert.equal(parseHistoryFinalState(''), null)
  assert.equal(parseHistoryFinalState(null), null)
  assert.equal(parseHistoryFinalState('{"players":['), null)
})
