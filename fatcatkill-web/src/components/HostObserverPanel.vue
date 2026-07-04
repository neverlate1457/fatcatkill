<script setup>
import { useI18n } from '../i18n'

const { t } = useI18n()
const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, required: true },
  showBotButton: { type: Boolean, required: true },
  botActionButtonText: { type: String, required: true },
  players: { type: Array, default: () => [] },
  roleName: { type: Function, required: true },
  phase: { type: String, required: true },
  voteRows: { type: Array, default: () => [] },
  confirmedVotes: { type: Number, required: true },
  voteTotals: { type: Array, default: () => [] },
  nightActions: { type: Object, default: () => ({}) },
  actionLogs: { type: Array, default: () => [] },
  actionLabel: { type: Function, required: true },
  playerLabel: { type: Function, required: true },
  logTime: { type: Function, required: true },
  logDetail: { type: Function, required: true }
})

const emit = defineEmits(['auto-play-bot'])
</script>

<template>
  <section class="host-observer-panel">
    <div class="observer-header">
      <div><span class="eyebrow">{{ props.title }}</span><h2>{{ props.subtitle }}</h2></div>
      <button v-if="props.showBotButton" class="secondary-button" @click="emit('auto-play-bot')">{{ props.botActionButtonText }}</button>
    </div>

    <div class="observer-role-grid">
      <article v-for="player in props.players" :key="player.userId" class="observer-player-row">
        <div><strong>{{ player.seatNumber }} - {{ player.username }}</strong><span>{{ props.roleName(player.role) }}</span></div>
        <span :class="['observer-status', player.alive ? 'alive' : 'dead']">{{ player.alive ? t('common.alive') : t('common.out') }}</span>
      </article>
    </div>

    <section v-if="props.voteRows.length" class="observer-vote-board">
      <div class="observer-section-header">
        <div><span class="eyebrow">{{ props.phase === 'NOMINATION' ? t('observer.nomination') : t('observer.executionVote') }}</span><h3>{{ t('observer.voteProgress') }}</h3></div>
        <strong>{{ t('observer.confirmedCount', { confirmed: props.confirmedVotes, total: props.voteRows.length }) }}</strong>
      </div>
      <div v-if="props.voteTotals.length" class="vote-total-strip">
        <span v-for="item in props.voteTotals" :key="item.playerId"><strong>{{ item.count }}</strong> {{ item.label }}</span>
      </div>
      <div class="observer-vote-table">
        <div v-for="player in props.voteRows" :key="player.userId" class="observer-vote-row">
          <span class="observer-seat">{{ player.seatNumber }}</span>
          <strong>{{ player.username }}</strong>
          <span class="observer-choice">{{ player.choice || t('observer.skipped') }}</span>
          <span :class="['vote-state', player.voteConfirmed ? 'confirmed' : 'pending']">{{ player.voteConfirmed ? t('observer.confirmed') : t('observer.pending') }}</span>
        </div>
      </div>
    </section>

    <section class="observer-activity">
      <div class="observer-section-header"><div><span class="eyebrow">{{ t('observer.activity') }}</span><h3>{{ t('observer.actionsEvents') }}</h3></div><span>{{ t('observer.latest', { count: props.actionLogs.length }) }}</span></div>
      <div v-if="Object.keys(props.nightActions || {}).length" class="pending-action-strip">
        <span v-for="(target, action) in props.nightActions" :key="action"><strong>{{ props.actionLabel(action) }}</strong> -> {{ props.playerLabel(target) }}</span>
      </div>
      <p v-if="!props.actionLogs.length" class="empty-state">{{ t('observer.noEvents') }}</p>
      <div v-else class="observer-log-table">
        <div v-for="(entry, index) in props.actionLogs" :key="entry.timestamp || index" class="observer-event-row">
          <time>{{ props.logTime(entry.timestamp) }}</time>
          <div class="observer-event-actor"><strong>{{ entry.username || t('common.system') }}</strong><span v-if="entry.role">{{ props.roleName(entry.role) }}</span></div>
          <strong class="observer-event-action">{{ props.actionLabel(entry.actionType) }}</strong>
          <span class="observer-event-detail">{{ props.logDetail(entry) }}</span>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.host-observer-panel {
  margin-top: 18px;
  padding: 18px;
  border: 1px solid #c8d2dc;
  border-radius: 8px;
  background: #ffffff;
}

h2, h3, p { margin: 0; }

.eyebrow {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

button {
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}
.secondary-button { min-height: 40px; padding: 9px 14px; color: #1d2733; background: #dce6ee; }

.observer-header,
.observer-section-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
}

.observer-role-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.observer-player-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 10px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}
.observer-player-row div { display: grid; gap: 3px; }
.observer-player-row span { color: #64748b; }
.observer-status.alive { color: #157347; }
.observer-status.dead { color: #b42318; }

.observer-vote-board,
.observer-activity { margin-top: 18px; border-top: 1px solid #d7e0e8; padding-top: 16px; }
.observer-section-header h3 { margin: 3px 0 0; }
.vote-total-strip,
.pending-action-strip { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.vote-total-strip span,
.pending-action-strip span { padding: 7px 9px; border: 1px solid #d7e0e8; border-radius: 6px; background: #fff; font-size: 13px; }
.observer-vote-table,
.observer-log-table { margin-top: 10px; border: 1px solid #d7e0e8; border-radius: 6px; overflow: hidden; }
.observer-vote-row { display: grid; grid-template-columns: 34px minmax(110px,.8fr) minmax(180px,1.5fr) 72px; align-items: center; gap: 10px; min-height: 42px; padding: 6px 10px; border-bottom: 1px solid #e3e9ef; }
.observer-vote-row:last-child,
.observer-event-row:last-child { border-bottom: 0; }
.observer-seat { font-weight: 800; color: #64748b; }
.observer-choice { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.vote-state { font-size: 12px; font-weight: 800; text-align: right; }
.vote-state.confirmed { color: #157347; }
.vote-state.pending { color: #a15c07; }
.observer-event-row { display: grid; grid-template-columns: 72px minmax(150px,.9fr) minmax(120px,.8fr) minmax(180px,1.3fr); align-items: center; gap: 10px; min-height: 44px; padding: 7px 10px; border-bottom: 1px solid #e3e9ef; font-size: 13px; }
.observer-event-row time { font-variant-numeric: tabular-nums; color: #64748b; }
.observer-event-actor { display: grid; gap: 2px; }
.observer-event-actor span { color: #64748b; font-size: 12px; }
.observer-event-detail { overflow-wrap: anywhere; color: #536273; }
.empty-state { margin-top: 10px; color: #64748b; line-height: 1.5; }

@media(max-width:700px) {
  .observer-vote-row { grid-template-columns: 28px 1fr 70px; }
  .observer-choice { grid-column: 2/4; white-space: normal; }
  .observer-event-row { grid-template-columns: 64px 1fr; }
  .observer-event-action,
  .observer-event-detail { grid-column: 2; }
  .observer-section-header { align-items: flex-start; flex-direction: column; }
}
</style>
