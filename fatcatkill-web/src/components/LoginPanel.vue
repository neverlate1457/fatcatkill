<script setup>
import { useI18n } from '../i18n'

const { t } = useI18n()
const authMode = defineModel('authMode')
const authUsername = defineModel('authUsername')
const authPassword = defineModel('authPassword')
const nickname = defineModel('nickname')
const userId = defineModel('userId')
const roomId = defineModel('roomId')
const roomSize = defineModel('roomSize')

const props = defineProps({
  authUser: { type: Object, default: null },
  displayName: { type: String, required: true },
  showRoomList: { type: Boolean, required: true },
  connectedRooms: { type: Array, default: () => [] },
  historyRecords: { type: Array, default: () => [] },
  historyLoading: { type: Boolean, required: true },
  historyWinnerText: { type: Function, required: true },
  historyTimeText: { type: Function, required: true }
})

const emit = defineEmits([
  'submit-auth',
  'guest-login',
  'logout-auth',
  'create-room',
  'create-hosted-room',
  'open-room-list',
  'request-room-list',
  'select-room',
  'load-history',
  'download-history'
])
</script>

<template>
  <section class="login-panel">
    <div class="brand-block"><span class="eyebrow">FatCatKill</span><h1>FatCatKill</h1></div>
    <div class="nickname-card">
      <span class="eyebrow">{{ t('auth.account') }}</span>
      <strong>{{ props.authUser ? props.authUser.username : t('auth.notLoggedIn') }}</strong>
      <small v-if="props.authUser?.guest">{{ t('auth.guestSession') }}</small>
      <small v-else-if="props.authUser">{{ t('auth.winRecord', { wins: props.authUser.gamesWon, games: props.authUser.gamesPlayed }) }}</small>
    </div>

    <div v-if="!props.authUser" class="auth-panel auth-dock">
      <div class="auth-tabs">
        <button type="button" :class="['secondary-button small', { success: authMode === 'login' }]" @click="authMode = 'login'">{{ t('auth.login') }}</button>
        <button type="button" :class="['secondary-button small', { success: authMode === 'register' }]" @click="authMode = 'register'">{{ t('auth.register') }}</button>
      </div>
      <label class="field">
        <span>{{ t('auth.username') }}</span>
        <input v-model="authUsername" type="text" autocomplete="username" :placeholder="t('auth.username')" />
      </label>
      <label class="field">
        <span>{{ t('auth.password') }}</span>
        <input v-model="authPassword" type="password" autocomplete="current-password" :placeholder="t('auth.password')" @keyup.enter="emit('submit-auth')" />
      </label>
      <div class="auth-actions">
        <button class="primary-button" @click="emit('submit-auth')">{{ authMode === 'register' ? t('auth.register') : t('auth.login') }}</button>
        <button class="secondary-button" @click="emit('guest-login')">{{ t('auth.guestLogin') }}</button>
      </div>
    </div>

    <div v-else class="auth-panel auth-dock compact">
      <button class="secondary-button" @click="emit('logout-auth')">{{ t('auth.logout') }}</button>
    </div>

    <div class="nickname-card">
      <span class="eyebrow">{{ t('home.displayName') }}</span>
      <strong>{{ props.displayName }}</strong>
    </div>
    <label class="field">
      <span>{{ t('home.nickname') }}</span>
      <input v-model="nickname" type="text" :placeholder="t('home.nicknamePlaceholder')" />
    </label>
    <label class="field">
      <span>{{ t('home.playerId') }}</span>
      <input v-model="userId" type="number" min="1" max="10" />
    </label>
    <label class="field">
      <span>{{ t('home.roomId') }}</span>
      <input v-model="roomId" type="text" />
    </label>
    <label class="field">
      <span>{{ t('home.roomSize') }}</span>
      <select v-model="roomSize">
        <option :value="6">{{ t('home.players', { count: 6 }) }}</option>
        <option :value="7">{{ t('home.players', { count: 7 }) }}</option>
        <option :value="10">{{ t('home.players', { count: 10 }) }}</option>
      </select>
    </label>

    <div class="main-actions">
      <button class="primary-button" @click="emit('create-room')">{{ t('home.createRoom') }}</button>
      <button class="action-button" @click="emit('create-hosted-room')">{{ t('home.hostCustomizedGame') }}</button>
      <button class="secondary-button" @click="emit('open-room-list')">{{ t('home.joinRoom') }}</button>
    </div>

    <div v-if="props.showRoomList" class="room-browser">
      <div class="room-browser-header">
        <span class="eyebrow">{{ t('home.onlineRooms') }}</span>
        <button class="secondary-button small" @click="emit('request-room-list')">{{ t('common.refresh') }}</button>
      </div>
      <button
        v-for="room in props.connectedRooms"
        :key="room.roomId"
        class="room-list-item"
        @click="emit('select-room', room.roomId)"
      >
        <strong>{{ t('home.room', { id: room.roomId }) }}</strong>
        <span>{{ t('home.roomPlayers', { joined: room.playerCount, capacity: room.capacity }) }}</span>
        <small>{{ room.players.map((player) => player.nickname).join(', ') }}</small>
      </button>
      <p v-if="!props.connectedRooms.length" class="empty-state">
        {{ t('home.noRooms') }}
      </p>
    </div>

    <div v-if="props.authUser" class="history-panel">
      <div class="room-browser-header">
        <span class="eyebrow">{{ t('history.title') }}</span>
        <button class="secondary-button small" :disabled="props.historyLoading" @click="emit('load-history')">
          {{ props.historyLoading ? t('common.loading') : t('common.refresh') }}
        </button>
      </div>
      <div v-if="props.historyRecords.length" class="history-list">
        <article v-for="record in props.historyRecords" :key="record.gameId" class="history-item">
          <div>
            <strong>{{ t('home.room', { id: record.roomId }) }}</strong>
            <span>{{ t('history.summary', { winner: props.historyWinnerText(record.winnerCamp), rounds: record.roundsPlayed, players: record.playerCount }) }}</span>
            <small>{{ props.historyTimeText(record.endTime) }}</small>
          </div>
          <button class="secondary-button small" @click="emit('download-history', record)">{{ t('common.download') }}</button>
        </article>
      </div>
      <p v-else class="empty-state">{{ t('history.noGames') }}</p>
    </div>
  </section>
</template>

<style scoped>
.login-panel {
  width: min(720px, 100%);
  margin: 7vh auto 0;
  padding: 28px;
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  box-shadow: 0 14px 35px rgba(21, 31, 45, 0.12);
}

.brand-block {
  display: grid;
  gap: 4px;
  padding-bottom: 18px;
  border-bottom: 1px solid #dbe3ea;
}

.brand-block h1 {
  font-size: clamp(32px, 6vw, 52px);
  line-height: 1;
}

h1,
p { margin: 0; }

.eyebrow {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.field {
  display: grid;
  gap: 6px;
  margin-top: 16px;
  font-weight: 700;
}

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
  transition: transform 120ms ease, box-shadow 120ms ease, background-color 120ms ease;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(21, 31, 45, 0.12);
}

button:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 3px solid rgba(72, 101, 129, 0.25);
  outline-offset: 2px;
}

button:disabled { cursor: not-allowed; opacity: 0.5; }

.primary-button,
.secondary-button,
.action-button {
  min-height: 40px;
  padding: 9px 14px;
}

.secondary-button.small {
  min-height: 32px;
  padding: 6px 10px;
  font-size: 13px;
}

.primary-button { color: #ffffff; background: #2f7d5c; }
.secondary-button { color: #1d2733; background: #dce6ee; }
.secondary-button.success { color: #ffffff; background: #2f7d5c; }
.action-button { color: #ffffff; background: #486581; }

.auth-panel,
.history-panel {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #d7e0e8;
  border-radius: 8px;
  background: #ffffff;
}

.auth-dock {
  position: fixed;
  top: 14px;
  left: 14px;
  z-index: 30;
  width: min(340px, calc(100vw - 28px));
  box-shadow: 0 12px 28px rgba(21, 31, 45, 0.12);
}

.auth-dock.compact {
  width: auto;
  min-width: 120px;
}
.auth-panel.compact { align-items: start; }
.auth-tabs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.auth-tabs button { width: 100%; }
.auth-actions { display: flex; flex-wrap: wrap; gap: 8px; }

.nickname-card {
  margin-top: 18px;
  padding: 14px;
  background: #f4f8fb;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.display-card {
  margin-top: 18px;
  background: #eef6f1;
  border-color: #cfe4d7;
}

.nickname-card strong {
  display: block;
  margin-top: 4px;
  font-size: 22px;
}

.main-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.main-actions button { width: 100%; }

.main-actions .primary-button,
.main-actions .secondary-button { margin-top: 0; }

.room-browser {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #dbe3ea;
}

.room-browser-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.room-list-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  text-align: left;
  color: #1d2733;
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.room-list-item small {
  grid-column: 1 / -1;
  color: #64748b;
  line-height: 1.4;
}

.history-list { display: grid; gap: 8px; }
.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px;
  border: 1px solid #d7e0e8;
  border-radius: 8px;
  background: #f8fbfd;
}
.history-item div { display: grid; gap: 3px; }
.history-item span,
.history-item small { color: #64748b; font-size: 13px; }

.empty-state {
  margin-top: 10px;
  color: #64748b;
  line-height: 1.5;
}
@media (max-width: 760px) {
  .login-panel {
    margin-top: 18px;
    padding: 18px;
  }

  .auth-dock {
    position: static;
    width: auto;
    box-shadow: none;
  }

  .main-actions {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 680px) {
  .login-panel > .field {
    display: inline-grid;
    width: calc(50% - 7px);
    vertical-align: top;
  }

  .login-panel > .field:nth-of-type(odd) {
    margin-right: 10px;
  }
}
</style>
