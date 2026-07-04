<script setup>
import { translateMessage, useI18n } from '../i18n'

const { t } = useI18n()
const phServiceTargetRole = defineModel('phServiceTargetRole')

const props = defineProps({
  gameState: { type: Object, required: true },
  phase: { type: String, required: true },
  myDisplayedRole: { type: String, default: '' },
  myPlayer: { type: Object, default: null },
  myRoleHint: { type: String, default: '' },
  myEffectiveRoleHint: { type: String, default: null },
  myPrivateMessage: { type: String, default: '' },
  ratManCheckerLabels: { type: Array, default: () => [] },
  dayFlowText: { type: String, default: '' },
  confirmedAliveCount: { type: Number, default: 0 },
  eligibleVoters: { type: Array, default: () => [] },
  canUseDayVote: { type: Boolean, default: false },
  lastVoteRows: { type: Array, default: () => [] },
  methaneSelection: { type: Array, default: () => [] },
  mubaimuSelection: { type: Array, default: () => [] },
  shushuSelection: { type: Array, default: () => [] },
  canUseFatcatKill: { type: Boolean, default: false },
  canUseChenAction: { type: Boolean, default: false },
  canUseSaltedFishAction: { type: Boolean, default: false },
  isRoomHost: { type: Boolean, default: false },
  debugMode: { type: Boolean, default: false },
  botActionButtonText: { type: String, default: '' },
  canUseFatcatTeamHint: { type: Boolean, default: false },
  fatcatHintButtonText: { type: String, default: '' },
  volunteerRoleOptions: { type: Array, default: () => [] },
  isNight: { type: Boolean, default: false },
  tallyButtonText: { type: String, default: '' },
  roleName: { type: Function, required: true },
  canActAsRole: { type: Function, required: true },
  voteCountFor: { type: Function, required: true },
  isMyVotedPlayer: { type: Function, required: true },
  canVoteFor: { type: Function, required: true },
  dayVoteButtonText: { type: Function, required: true }
})

const emit = defineEmits([
  'confirm-vote',
  'cancel-vote',
  'skip-vote',
  'str-action',
  'fatcat-kill',
  'liver-action',
  'can-man-action',
  'nangong-action',
  'mubaimu-click',
  'shushu-click',
  'methane-click',
  'mochi-boss-check',
  'witch-action',
  'seer-verify',
  'chen-action',
  'fish-action',
  'day-vote',
  'auto-play-bot',
  'fatcat-team-hint',
  'send-ph-service-action',
  'emperor-reveal',
  'str-skip',
  'guoguo-action',
  'forvkusa-action',
  'hatong-action',
  'xiaoxiang-action',
  'send-mubaimu-action',
  'send-shushu-action',
  'grass-bean-action',
  'xiangxiang-action',
  'ac-cat-action',
  'andy-action',
  'chen-skip',
  'fish-skip',
  'start-nomination',
  'tally-votes'
])
</script>

<template>
  <section class="status-layout">
  <div class="self-panel">
    <span class="eyebrow">{{ t('player.myStatus') }}</span>
    <h2>{{ roleName(myDisplayedRole) }}</h2>
    <p>{{ myPlayer?.alive ? t('common.alive') : t('common.dead') }}</p>
    <div class="ability-hint">
      <strong>{{ t('player.ability') }}</strong>
      <p>{{ myRoleHint }}</p>
    </div>
    <p v-if="myPlayer?.role === 'PH_SERVICE' && gameState.phServiceStolenRole">
      {{ t('player.stolenAbility', { role: roleName(gameState.phServiceStolenRole) }) }}
    </p>
    <div v-if="myEffectiveRoleHint" class="ability-hint secondary">
      <strong>{{ t('player.effectiveAbility') }}</strong>
      <p>{{ myEffectiveRoleHint }}</p>
    </div>
    <p v-if="myPlayer?.role === 'RAT_MAN' && gameState.ratManCheckerIds?.length">
      {{ t('player.checkedBy', { players: ratManCheckerLabels.join(', ') }) }}
    </p>
    <div v-if="myPrivateMessage" class="ability-hint secondary">
      <strong>{{ t('player.privateInfo') }}</strong>
      <p>{{ myPrivateMessage }}</p>
    </div>
  </div>

  <div v-if="phase === 'NOMINATION' || phase === 'VOTING' || gameState.lastVoteResult" class="vote-panel">
    <span class="eyebrow">{{ t('player.dayFlow') }}</span>
    <h2>{{ phase === 'NOMINATION' ? t('observer.nomination') : t('player.executionVote') }}</h2>
    <p>{{ dayFlowText }}</p>
    <p v-if="phase === 'NOMINATION' || phase === 'VOTING'" class="vote-progress">
      {{ t('player.confirmedProgress', { confirmed: confirmedAliveCount, total: eligibleVoters.length }) }}
    </p>
    <div v-if="canUseDayVote" class="vote-controls">
      <button
        class="action-button vote"
        :disabled="myPlayer?.voteConfirmed || !myPlayer?.votedTargetId"
        @click="emit('confirm-vote')"
      >
        {{ t('common.confirm') }}
      </button>
      <button
        class="secondary-button"
        :disabled="!myPlayer?.voteConfirmed && !myPlayer?.votedTargetId"
        @click="emit('cancel-vote')"
      >
        {{ t('common.cancel') }}
      </button>
      <button
        class="secondary-button"
        :disabled="myPlayer?.voteConfirmed"
        @click="emit('skip-vote')"
      >
        {{ t('common.skip') }}
      </button>
    </div>
    <div v-if="gameState.lastVoteResult || lastVoteRows.length" class="vote-result">
      <strong>{{ translateMessage(gameState.lastVoteResult) || t('player.lastVoteCount') }}</strong>
      <div v-if="lastVoteRows.length" class="vote-result-list">
        <span v-for="row in lastVoteRows" :key="row.playerId">
          {{ row.label }}: {{ row.count }}
        </span>
      </div>
    </div>
  </div>
</section>

  <section class="players-grid">
  <article
    v-for="player in gameState.players"
    :key="player.userId"
    :class="[
      'player-card',
      {
        dead: !player.alive,
        mine: player.userId === myPlayer?.userId,
        nominated: player.userId === gameState.nominatedPlayerId,
        'my-vote-target': isMyVotedPlayer(player)
      }
    ]"
  >
    <div class="player-header">
      <span>{{ t('player.seat', { seat: player.seatNumber || '-' }) }}</span>
      <strong>{{ player.username }}</strong>
    </div>

    <div class="player-meta">
      <span>{{ player.alive ? t('common.alive') : t('common.dead') }}</span>
      <span v-if="player.userId === myPlayer?.userId">{{ t('player.you') }}</span>
      <span v-if="player.userId === gameState.nominatedPlayerId">{{ t('player.nominee') }}</span>
      <span v-if="isMyVotedPlayer(player)">{{ t('player.youNominated') }}</span>
      <span v-if="voteCountFor(player.userId)">{{ t('player.votes', { count: voteCountFor(player.userId) }) }}</span>
    </div>

    <div v-if="player.alive && player.userId !== myPlayer?.userId" class="action-area">
      <button
        v-if="canActAsRole('STR') && phase === 'STR_ACTION'"
        class="action-button"
        @click="emit('str-action', player.userId)"
      >
        {{ t('action.swapSeat') }}
      </button>
      <button
        v-if="canUseFatcatKill"
        class="action-button danger"
        @click="emit('fatcat-kill', player.userId)"
      >
        Kill
      </button>
      <button
        v-if="canActAsRole('LIVER_INDEX') && phase === 'LIVER_INDEX_ACTION'"
        class="action-button"
        @click="emit('liver-action', player.userId)"
      >
        Debuff
      </button>
      <button
        v-if="canActAsRole('CAN_MAN') && phase === 'CAN_MAN_ACTION'"
        class="action-button"
        @click="emit('can-man-action', player.userId)"
      >
        Drink
      </button>
      <button
        v-if="canActAsRole('NANGONG') && phase === 'NANGONG_ACTION'"
        class="action-button"
        @click="emit('nangong-action', player.userId)"
      >
        Bind
      </button>
      <button
        v-if="canActAsRole('MUBAIMU') && phase === 'MUBAIMU_ACTION'"
        :class="['action-button', mubaimuSelection.includes(player.userId) ? 'selected' : '']"
        @click="emit('mubaimu-click', player.userId)"
      >
        {{ mubaimuSelection.includes(player.userId) ? t('action.tartSelected') : t('action.giveTart') }}
      </button>
      <button
        v-if="canActAsRole('SHUSHU') && phase === 'SHUSHU_ACTION'"
        :class="['action-button', shushuSelection.includes(player.userId) ? 'selected' : '']"
        @click="emit('shushu-click', player.userId)"
      >
        {{ shushuSelection.includes(player.userId) ? t('action.companionSelected') : t('action.inviteTravel') }}
      </button>
      <button
        v-if="canActAsRole('METHANE') && phase === 'METHANE_ACTION'"
        :class="['action-button', methaneSelection.includes(player.userId) ? 'selected' : '']"
        @click="emit('methane-click', player.userId)"
      >
        {{ methaneSelection.includes(player.userId) ? t('action.selected') : t('action.check') }}
      </button>
      <button
        v-if="canActAsRole('MOCHI_BOSS') && phase === 'MOCHI_BOSS_ACTION'"
        class="action-button"
        @click="emit('mochi-boss-check', player.userId)"
      >
        {{ t('action.checkFatcat') }}
      </button>
      <template v-if="myPlayer?.role === 'WITCH' && phase === 'WITCH_ACTION'">
        <button class="action-button success" @click="emit('witch-action', player.userId, 'SAVE')">{{ t('action.save') }}</button>
        <button class="action-button danger" @click="emit('witch-action', player.userId, 'POISON')">{{ t('action.poison') }}</button>
      </template>
      <button
        v-if="myPlayer?.role === 'SEER' && phase === 'SEER_VERIFY'"
        class="action-button"
        @click="emit('seer-verify', player.userId)"
      >
        {{ t('action.verify') }}
      </button>
      <button
        v-if="canUseChenAction"
        class="action-button danger"
        @click="emit('chen-action', player.userId)"
      >
        {{ t('action.declareConsent') }}
      </button>
      <button
        v-if="canUseSaltedFishAction"
        class="action-button"
        @click="emit('fish-action', player.userId)"
      >
        {{ t('action.stab') }}
      </button>
      <button
        v-if="canVoteFor(player)"
        class="action-button vote"
        @click="emit('day-vote', player.userId)"
      >
        {{ dayVoteButtonText(player) }}
      </button>
    </div>
  </article>
</section>

  <section class="global-actions">
  <button v-if="debugMode && gameState.status === 'PLAYING' && isRoomHost" class="secondary-button" @click="emit('auto-play-bot')">
    {{ botActionButtonText }}
  </button>
  <button v-if="canUseFatcatTeamHint" class="action-button" @click="emit('fatcat-team-hint')">
    {{ fatcatHintButtonText }}
  </button>
  <label v-if="canActAsRole('PH_SERVICE') && phase === 'PH_SERVICE_ACTION'" class="inline-field">
    <span>{{ t('action.hackRole') }}</span>
    <select v-model="phServiceTargetRole">
      <option v-for="role in volunteerRoleOptions" :key="role" :value="role">
        {{ roleName(role) }}
      </option>
    </select>
  </label>
  <button v-if="canActAsRole('PH_SERVICE') && phase === 'PH_SERVICE_ACTION'" class="action-button" @click="emit('send-ph-service-action')">
    {{ t('action.hackAccount') }}
  </button>
  <button v-if="canActAsRole('EMPEROR') && gameState.currentRound === 1 && isNight" class="action-button" @click="emit('emperor-reveal')">
    {{ t('action.revealAllRoles') }}
  </button>
  <button v-if="canActAsRole('STR') && phase === 'STR_ACTION'" class="action-button" @click="emit('str-skip')">
    {{ t('action.skipSwap') }}
  </button>
  <button v-if="canActAsRole('GUOGUO') && phase === 'GUOGUO_ACTION'" class="action-button" @click="emit('guoguo-action')">
    {{ t('action.guoguoHint') }}
  </button>
  <button v-if="canActAsRole('FORVKUSA') && phase === 'FORVKUSA_ACTION'" class="action-button" @click="emit('forvkusa-action')">
    {{ t('action.checkAdjacentPairs') }}
  </button>
  <button v-if="canActAsRole('HATONG') && phase === 'HATONG_ACTION'" class="action-button" @click="emit('hatong-action')">
    {{ t('action.checkFatcatVoted') }}
  </button>
  <button v-if="canActAsRole('XIAOXIANG') && phase === 'XIAOXIANG_ACTION'" class="action-button" @click="emit('xiaoxiang-action')">
    {{ t('action.countAlliance') }}
  </button>
  <button v-if="canActAsRole('MUBAIMU') && phase === 'MUBAIMU_ACTION'" class="action-button" @click="emit('send-mubaimu-action')">
    {{ t('action.shareTarts') }}
  </button>
  <button v-if="canActAsRole('SHUSHU') && phase === 'SHUSHU_ACTION'" class="action-button" @click="emit('send-shushu-action')">
    {{ t('action.startTrip') }}
  </button>
  <button v-if="canActAsRole('GRASS_BEAN') && phase === 'GRASS_BEAN_ACTION'" class="action-button" @click="emit('grass-bean-action')">
    {{ t('action.findHorcrux') }}
  </button>
  <button v-if="canActAsRole('XIANGXIANG') && phase === 'XIANGXIANG_ACTION'" class="action-button" @click="emit('xiangxiang-action')">
    {{ t('action.checkNeighbors') }}
  </button>
  <button v-if="canActAsRole('AC_CAT') && phase === 'AC_CAT_ACTION'" class="action-button" @click="emit('ac-cat-action')">
    {{ t('action.revealExileRole') }}
  </button>
  <button v-if="canActAsRole('ANDY') && phase === 'ANDY_ACTION'" class="action-button" @click="emit('andy-action')">
    {{ t('action.findCloud') }}
  </button>
  <button v-if="myPlayer?.role === 'WITCH' && phase === 'WITCH_ACTION'" class="action-button" @click="emit('witch-action', myPlayer.userId, 'SKIP')">
    {{ t('action.witchSkip') }}
  </button>
  <button v-if="canUseChenAction" class="secondary-button" @click="emit('chen-skip')">
    {{ t('action.skipChen') }}
  </button>
  <button v-if="canUseSaltedFishAction" class="secondary-button" @click="emit('fish-skip')">
    {{ t('action.skipStab') }}
  </button>
  <button v-if="phase === 'DAY_START'" class="primary-button" @click="emit('start-nomination')">
    {{ t('action.startNomination') }}
  </button>
  <button v-if="phase === 'NOMINATION' || phase === 'VOTING'" class="primary-button" @click="emit('tally-votes')">
    {{ tallyButtonText }}
  </button>
</section>
</template>


