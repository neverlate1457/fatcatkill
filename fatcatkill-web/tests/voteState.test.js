import test from 'node:test'
import assert from 'node:assert/strict'
import { isVotedPlayerForPhase } from '../src/utils/voteState.js'

test('isVotedPlayerForPhase marks selected players during nomination and execution voting', () => {
  assert.equal(isVotedPlayerForPhase('NOMINATION', 2, 2), true)
  assert.equal(isVotedPlayerForPhase('VOTING', 2, 2), true)
  assert.equal(isVotedPlayerForPhase('VOTING', '2', 2), true)
})

test('isVotedPlayerForPhase ignores other phases and missing values', () => {
  assert.equal(isVotedPlayerForPhase('DAY_START', 2, 2), false)
  assert.equal(isVotedPlayerForPhase('NIGHT_START', 2, 2), false)
  assert.equal(isVotedPlayerForPhase('VOTING', null, 2), false)
  assert.equal(isVotedPlayerForPhase('VOTING', 2, null), false)
  assert.equal(isVotedPlayerForPhase('VOTING', 2, 3), false)
})
