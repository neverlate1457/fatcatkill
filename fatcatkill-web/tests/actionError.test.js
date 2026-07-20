import assert from 'node:assert/strict'
import { test } from 'node:test'
import { errorMessage } from '../src/composables/useActionError.js'
import { setLanguage } from '../src/i18n.js'

test('errorMessage translates message payload objects', () => {
  setLanguage('zh-TW')

  assert.equal(
    errorMessage({ key: 'gateway.action.hostOnly', fallback: 'Only the room host can perform this action.' }),
    '只有房主可以執行此行動。'
  )
})

test('errorMessage formats Spring error bodies from response data and raw objects', () => {
  const springError = {
    timestamp: '2026-06-18T16:12:40.683Z',
    status: 500,
    error: 'Internal Server Error',
    path: '/api/room/fill-bots/3234'
  }

  assert.equal(
    errorMessage({ response: { data: springError } }),
    'Internal Server Error: /api/room/fill-bots/3234'
  )
  assert.equal(
    errorMessage(springError),
    'Internal Server Error: /api/room/fill-bots/3234'
  )
})

test('errorMessage stringifies unknown object errors without object placeholders', () => {
  const message = errorMessage({ detail: { reason: 'bad shape' } })

  assert.match(message, /bad shape/)
  assert.notEqual(message, '[object Object]')
})