<script setup>
import { useI18n } from '../i18n'

const { t } = useI18n()
const selectedMode = defineModel('selectedMode')
const selectedSavedDeck = defineModel('selectedSavedDeck')
const customDeck = defineModel('customDeck')
const hostAdvancedOpen = defineModel('hostAdvancedOpen')
const highRabbitRole = defineModel('highRabbitRole')
const hostMethaneHallucinationTargetId = defineModel('hostMethaneHallucinationTargetId')
const deckName = defineModel('deckName')
const testRoleAssignments = defineModel('testRoleAssignments')

const props = defineProps({
  serverMessage: { type: String, default: '' },
  roomOccupancyText: { type: String, required: true },
  isRoomParticipant: { type: Boolean, required: true },
  isPlayerReady: { type: Boolean, required: true },
  roomFillPercent: { type: Number, required: true },
  roomPlayerCount: { type: Number, required: true },
  roomOpenSlots: { type: Number, required: true },
  roomCapacity: { type: Number, required: true },
  roomSeatSlots: { type: Array, default: () => [] },
  userId: { type: [String, Number], default: '' },
  seatTitle: { type: Function, required: true },
  seatActionText: { type: Function, required: true },
  setupRoomPlayers: { type: Array, default: () => [] },
  roomHostId: { type: [String, Number], default: null },
  isRoomHost: { type: Boolean, required: true },
  modeLabels: { type: Object, required: true },
  isHostSpectator: { type: Boolean, required: true },
  savedDecks: { type: Array, default: () => [] },
  customRoleOptions: { type: Array, default: () => [] },
  roleName: { type: Function, required: true },
  hostAdvancedSummary: { type: String, default: '' },
  fatcatHintRoles: { type: Array, default: () => [] },
  fatcatHintSlotIndexes: { type: Array, default: () => [] },
  fatcatHintOptionsFor: { type: Function, required: true },
  hostDeckHasHighRabbit: { type: Boolean, required: true },
  highRabbitRoleOptions: { type: Array, default: () => [] },
  hostDeckHasMethane: { type: Boolean, required: true },
  hostMethaneTargetOptions: { type: Array, default: () => [] },
  deckValidation: { type: String, default: '' },
  roomStartStatus: { type: String, default: '' },
  canStartGame: { type: Boolean, required: true },
  debugMode: { type: Boolean, default: false },
  testRoleOptions: { type: Array, default: () => [] }
})

const emit = defineEmits([
  'toggle-ready',
  'refresh-room',
  'seat-click',
  'load-host-deck',
  'set-fatcat-hint-role',
  'save-host-deck',
  'create-mock-room',
  'start-game',
  'fill-bots'
])
</script>

<template>
  <section class="setup-panel">
    <h2>{{ t('setup.title') }}</h2>
    <p>{{ props.serverMessage || t('setup.defaultMessage') }}</p>

    <div class="room-ready-panel">
      <div class="room-ready-header">
        <div>
          <span class="eyebrow">{{ t('setup.roomOccupancy') }}</span>
          <strong>{{ props.roomOccupancyText }}</strong>
        </div>
        <div class="room-ready-actions">
          <button v-if="props.isRoomParticipant" class="secondary-button" :class="{ success: props.isPlayerReady }" @click="emit('toggle-ready')">
            {{ props.isPlayerReady ? t('setup.cancelReady') : t('common.ready') }}
          </button>
          <button class="secondary-button" @click="emit('refresh-room')">{{ t('common.refresh') }}</button>
        </div>
      </div>

      <div class="occupancy-meter" :aria-label="t('setup.roomOccupancy')">
        <div class="occupancy-meter-fill" :style="{ width: `${props.roomFillPercent}%` }"></div>
      </div>

      <div class="room-stat-grid">
        <div class="room-stat"><span>{{ t('setup.joined') }}</span><strong>{{ props.roomPlayerCount }}</strong></div>
        <div class="room-stat"><span>{{ t('setup.openSlots') }}</span><strong>{{ props.roomOpenSlots }}</strong></div>
        <div class="room-stat"><span>{{ t('setup.capacity') }}</span><strong>{{ props.roomCapacity }}</strong></div>
      </div>

      <div class="seat-map" :style="{ '--seat-count': props.roomCapacity }">
        <button
          v-for="slot in props.roomSeatSlots"
          :key="slot.index"
          type="button"
          :class="['seat-slot', { filled: slot.player, empty: !slot.player, mine: Number(slot.player?.userId) === Number(props.userId), clickable: !slot.player && props.isRoomParticipant }]"
          :title="props.seatTitle(slot)"
          @click="emit('seat-click', slot)"
        >
          <span class="seat-number">{{ slot.index }}</span>
          <span class="seat-content">
            <strong>{{ slot.player ? slot.player.username : t('setup.openSeat') }}</strong>
            <small>{{ props.seatActionText(slot) }}</small>
          </span>
        </button>
      </div>
    </div>

    <div v-if="props.setupRoomPlayers.length" class="waiting-list">
      <span class="eyebrow">{{ t('setup.playersInRoom') }}</span>
      <div class="waiting-list-grid">
        <div v-for="player in props.setupRoomPlayers" :key="player.userId" class="waiting-player-row">
          <span>{{ player.userId }} - {{ player.username }}<strong v-if="Number(player.userId) === Number(props.roomHostId)"> ({{ t('common.host') }})</strong></span>
          <small :class="['ready-badge', { ready: player.ready }]">{{ player.ready ? t('common.ready') : t('common.notReady') }}</small>
          <select v-if="props.debugMode" v-model="testRoleAssignments[player.userId]" class="debug-role-select">
            <option value="">{{ t('debug.randomRole') }}</option>
            <option v-for="role in props.testRoleOptions" :key="role" :value="role">{{ props.roleName(role) }}</option>
          </select>
        </div>
      </div>
    </div>

    <div v-if="props.isRoomHost" class="settings-grid">
      <label class="field">
        <span>{{ t('setup.mode') }}</span>
        <select v-model="selectedMode">
          <option v-for="(label, value) in props.modeLabels" :key="value" :value="value">{{ label }}</option>
        </select>
      </label>
    </div>

    <section v-if="props.isHostSpectator" class="host-config-panel">
      <div class="host-config-header">
        <div><span class="eyebrow">{{ t('setup.customizedGame') }}</span><h3>{{ t('setup.deckControlledOutcomes') }}</h3></div>
        <div class="saved-deck-controls">
          <select v-model="selectedSavedDeck" @change="emit('load-host-deck')">
            <option value="">{{ t('setup.loadSavedDeck') }}</option>
            <option v-for="deck in props.savedDecks" :key="deck.name" :value="deck.name">{{ deck.name }}</option>
          </select>
        </div>
      </div>

      <div class="deck-slot-grid">
        <label v-for="(_, index) in customDeck" :key="index" class="deck-slot-field">
          <span>{{ t('setup.seat', { seat: index + 1 }) }}</span>
          <select v-model="customDeck[index]">
            <option value="">{{ t('setup.chooseRole') }}</option>
            <option v-for="role in props.customRoleOptions" :key="role" :value="role">{{ props.roleName(role) }}</option>
          </select>
        </label>
      </div>

      <section class="host-advanced-panel">
        <button class="host-advanced-toggle" type="button" @click="hostAdvancedOpen = !hostAdvancedOpen">
          <span>{{ t('setup.advanced') }}</span>
          <small>{{ props.hostAdvancedSummary }}</small>
          <strong>{{ hostAdvancedOpen ? t('common.close') : t('common.open') }}</strong>
        </button>
        <div v-if="hostAdvancedOpen" class="host-advanced-body">
          <div class="host-random-grid">
            <div class="field fatcat-hint-picker">
              <span>{{ t('setup.fatcatHints') }}</span>
              <div class="fatcat-hint-selects">
                <label v-for="slotIndex in props.fatcatHintSlotIndexes" :key="slotIndex" class="mini-field">
                  <small>{{ t('setup.hint', { index: slotIndex + 1 }) }}</small>
                  <select :value="props.fatcatHintRoles[slotIndex] || ''" @change="emit('set-fatcat-hint-role', slotIndex, $event.target.value)">
                    <option value="">{{ t('common.random') }}</option>
                    <option v-for="role in props.fatcatHintOptionsFor(slotIndex)" :key="role" :value="role">{{ props.roleName(role) }}</option>
                  </select>
                </label>
              </div>
            </div>
            <label v-if="props.hostDeckHasHighRabbit" class="field">
              <span>{{ t('setup.highRabbitRole') }}</span>
              <select v-model="highRabbitRole">
                <option value="">{{ t('setup.randomPresentRole') }}</option>
                <option v-for="role in props.highRabbitRoleOptions" :key="role" :value="role">{{ props.roleName(role) }}</option>
              </select>
            </label>
            <div v-else class="host-controlled-note"><span>{{ t('setup.highRabbitRole') }}</span><strong>{{ t('setup.noHighRabbit') }}</strong></div>
            <label v-if="props.hostDeckHasMethane" class="field">
              <span>{{ t('setup.methaneTarget') }}</span>
              <select v-model="hostMethaneHallucinationTargetId">
                <option value="">{{ t('setup.randomEligiblePlayer') }}</option>
                <option v-for="player in props.hostMethaneTargetOptions" :key="player.userId" :value="String(player.userId)">
                  {{ t('setup.seat', { seat: player.seatNumber || '?' }) }} - {{ player.username }} - {{ props.roleName(player.role) }}
                </option>
              </select>
            </label>
            <div v-else class="host-controlled-note"><span>{{ t('setup.methaneTarget') }}</span><strong>{{ t('setup.noMethane') }}</strong></div>
          </div>
          <div class="host-controlled-note full-row">
            <span>{{ t('setup.otherControlled') }}</span>
            <strong>{{ t('setup.otherControlledNote') }}</strong>
          </div>
        </div>
      </section>

      <p v-if="props.deckValidation" class="validation-text">{{ props.deckValidation }}</p>
      <div class="save-deck-row">
        <input v-model="deckName" type="text" :placeholder="t('setup.deckName')" />
        <button class="secondary-button" @click="emit('save-host-deck')">{{ t('setup.saveAsDeck') }}</button>
      </div>
    </section>

    <p v-if="props.isRoomHost && props.roomStartStatus" class="empty-state">{{ props.roomStartStatus }}</p>
    <p v-if="!props.isRoomHost" class="empty-state">{{ t('setup.waitHost') }}</p>

    <div v-if="props.isRoomHost" class="button-row">
      <button class="primary-button" :disabled="!props.canStartGame" :title="props.roomStartStatus" @click="emit('start-game')">{{ t('setup.startGame') }}</button>
      <button v-if="props.debugMode" class="secondary-button" @click="emit('fill-bots')">{{ t('setup.fillBots') }}</button>
      <button v-if="props.debugMode" class="secondary-button" @click="emit('create-mock-room')">{{ t('debug.createMockRoom') }}</button>
    </div>
  </section>
</template>

<style scoped>
.setup-panel,
.host-config-panel {
  margin-top: 18px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

h2, h3, p { margin: 0; }

.eyebrow {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.field,
.deck-slot-field {
  display: grid;
  gap: 6px;
  margin-top: 16px;
  font-weight: 700;
}

.deck-slot-field { margin-top: 0; font-size: 13px; }

input,
select {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid #c8d2dc;
  border-radius: 8px;
  background: #ffffff;
  color: #1d2733;
  font: inherit;
}

button {
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}
button:disabled { cursor: not-allowed; opacity: 0.5; }

.primary-button,
.secondary-button,
.action-button { min-height: 40px; padding: 9px 14px; }
.primary-button { margin-top: 16px; color: #ffffff; background: #2f7d5c; }
.secondary-button { color: #1d2733; background: #dce6ee; }
.secondary-button.success { color: #ffffff; background: #2f7d5c; }

.button-row,
.save-deck-row,
.host-config-header,
.room-ready-header,
.room-ready-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.button-row,
.save-deck-row { margin-top: 14px; }
.host-config-header,
.room-ready-header { align-items: center; justify-content: space-between; }
.room-ready-actions { justify-content: flex-end; }
.save-deck-row input { flex: 1; }

.room-ready-panel,
.waiting-list {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid #d7e0e8;
  border-radius: 8px;
  background: #f8fbfd;
}

.occupancy-meter {
  height: 10px;
  margin-top: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: #dce6ee;
}
.occupancy-meter-fill { height: 100%; background: #2f7d5c; }

.room-stat-grid,
.deck-slot-grid,
.host-random-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}
.host-random-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }

.room-stat {
  padding: 10px;
  border: 1px solid #d7e0e8;
  border-radius: 8px;
  background: #ffffff;
}
.room-stat span { color: #64748b; font-size: 12px; font-weight: 800; }
.room-stat strong { display: block; margin-top: 4px; font-size: 20px; }

.seat-map {
  display: grid;
  grid-template-columns: repeat(var(--seat-count), minmax(72px, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.seat-slot {
  display: grid;
  grid-template-columns: 28px 1fr;
  align-items: center;
  gap: 8px;
  min-height: 58px;
  padding: 8px;
  text-align: left;
  color: #1d2733;
  background: #ffffff;
  border: 1px solid #d7e0e8;
}
.seat-slot.empty { background: #f3f7fa; }
.seat-slot.clickable { border-color: rgba(72, 101, 129, 0.55); }
.seat-slot.mine { box-shadow: 0 0 0 2px rgba(47, 125, 92, 0.15); }
.seat-number { color: #64748b; font-weight: 900; }
.seat-content { display: grid; min-width: 0; gap: 3px; }
.seat-content strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.seat-content small,
.ready-badge { color: #64748b; font-size: 12px; font-weight: 800; }
.ready-badge.ready { color: #157347; }

.waiting-list-grid { display: grid; gap: 8px; margin-top: 10px; }
.waiting-player-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #d7e0e8;
  border-radius: 8px;
  background: #ffffff;
}

.debug-role-select { grid-column: 1 / -1; }

.settings-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }

.host-config-panel { background: #ffffff; }
.deck-slot-grid { grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); }
.host-advanced-panel { margin-top: 16px; border: 1px solid #d7e0e8; border-radius: 8px; background: #fff; overflow: hidden; }
.host-advanced-toggle { width: 100%; min-height: 48px; padding: 10px 12px; display: grid; grid-template-columns: 110px 1fr auto; align-items: center; gap: 12px; border: 0; background: transparent; color: #243447; text-align: left; cursor: pointer; }
.host-advanced-toggle span { font-weight: 850; }
.host-advanced-toggle small { color: #66788a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.host-advanced-toggle strong { font-size: 12px; color: #34516c; }
.host-advanced-body { padding: 12px; border-top: 1px solid #d7e0e8; }
.host-controlled-note { display: grid; gap: 8px; align-content: start; padding: 10px 12px; border: 1px solid #d7e0e8; border-radius: 8px; background: #f4f8fb; }
.host-controlled-note span { font-size: 13px; font-weight: 700; color: #536273; }
.host-controlled-note strong { font-size: 14px; color: #243447; }
.host-controlled-note.full-row { margin-top: 12px; }
.fatcat-hint-picker { display: grid; gap: 8px; }
.fatcat-hint-selects { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.mini-field { display: grid; gap: 4px; min-width: 0; }
.mini-field small { color: #66788a; font-weight: 800; }
.validation-text { margin-top: 10px; color: #b42318; font-weight: 700; }
.empty-state { margin-top: 10px; color: #64748b; line-height: 1.5; }

@media (max-width: 800px) {
  .seat-map { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .room-stat-grid,
  .host-random-grid,
  .fatcat-hint-selects { grid-template-columns: 1fr; }
}
</style>
