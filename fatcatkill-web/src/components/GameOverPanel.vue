<script setup>
import { useI18n } from '../i18n'

const { t } = useI18n()
defineProps({ winnerText: { type: String, required: true } })
const emit = defineEmits(['download-logs', 'back-main'])
</script>

<template>
  <section class="game-over-panel">
    <span class="eyebrow">{{ t('gameOver.finished') }}</span>
    <h2>{{ winnerText }}</h2>
    <div class="button-row">
      <button class="secondary-button" @click="emit('download-logs')">{{ t('gameOver.downloadLogs') }}</button>
      <button class="primary-button" @click="emit('back-main')">{{ t('gameOver.backMain') }}</button>
    </div>
  </section>
</template>

<style scoped>
.game-over-panel {
  margin-top: 18px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #dbe3ea;
  border-radius: 8px;
}

.eyebrow {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

h2 { margin: 0; }

.button-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

button {
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}

.primary-button,
.secondary-button {
  min-height: 40px;
  padding: 9px 14px;
}

.primary-button { color: #ffffff; background: #2f7d5c; }
.secondary-button { color: #1d2733; background: #dce6ee; }
</style>
