<script setup>
import { useI18n } from '../i18n'

const { t } = useI18n()
defineProps({
  roomId: { type: [String, Number], required: true },
  title: { type: String, required: true },
  userLabel: { type: String, required: true },
  displayName: { type: String, required: true },
  isHost: { type: Boolean, required: true },
  round: { type: [String, Number], default: null },
  hasGame: { type: Boolean, required: true }
})

const emit = defineEmits(['leave-room'])
</script>

<template>
  <header class="topbar">
    <div>
      <p class="eyebrow">{{ t('header.roomLine', { roomId, userLabel, displayName }) }}<span v-if="isHost">{{ t('header.hostSuffix') }}</span></p>
      <h1>{{ title }}</h1>
    </div>
    <div class="topbar-actions">
      <div v-if="hasGame" class="round-pill">{{ t('header.round', { round }) }}</div>
      <button class="secondary-button" @click="emit('leave-room')">{{ t('header.leaveRoom') }}</button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(120, 135, 150, 0.35);
}

.topbar h1 {
  margin: 4px 0 0;
  font-size: 28px;
  line-height: 1.25;
}

.topbar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.eyebrow {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.round-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(82, 105, 130, 0.14);
  white-space: nowrap;
  font-size: 14px;
}

button {
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}

.secondary-button {
  min-height: 40px;
  padding: 9px 14px;
  color: #1d2733;
  background: #dce6ee;
}

@media (max-width: 640px) {
  .topbar { display: grid; }
  .topbar-actions { justify-content: flex-start; }
  .topbar h1 { font-size: 22px; }
}
</style>
