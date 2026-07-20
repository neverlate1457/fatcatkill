<script setup>
import { computed, ref } from 'vue'
import { roleCodes, translateRole, translateRoleHint } from '../data/roles.js'
import { useI18n } from '../i18n.js'

const compactUi = defineModel('compactUi', { type: Boolean, default: false })

const props = defineProps({
  roomId: { type: String, default: '' },
  hasGame: { type: Boolean, default: false }
})

const emit = defineEmits(['download-logs'])

const { language, languages, setLanguage, t, languageLabel } = useI18n()
const activePanel = ref('settings')
const roleSearch = ref('')
const copyStatus = ref('')

const menuTabs = computed(() => [
  { id: 'settings', label: t('feature.settings') },
  { id: 'roles', label: t('feature.roleGuide') },
  { id: 'rules', label: t('feature.rules') },
  { id: 'tools', label: t('feature.tools') }
])

const roleItems = computed(() => {
  const keyword = roleSearch.value.trim().toLowerCase()
  return roleCodes
    .map((role) => ({ role, name: translateRole(role), hint: translateRoleHint(role) }))
    .filter((item) => !keyword
      || item.role.toLowerCase().includes(keyword)
      || item.name.toLowerCase().includes(keyword)
      || item.hint.toLowerCase().includes(keyword))
})

const ruleSections = computed(() => [
  { title: t('feature.ruleFlowTitle'), body: t('feature.ruleFlowBody') },
  { title: t('feature.ruleDayTitle'), body: t('feature.ruleDayBody') },
  { title: t('feature.ruleNightTitle'), body: t('feature.ruleNightBody') },
  { title: t('feature.ruleWinTitle'), body: t('feature.ruleWinBody') },
  { title: t('feature.ruleTableTitle'), body: t('feature.ruleTableBody') }
])

const copyRoomId = async () => {
  if (!props.roomId) return
  try {
    if (!globalThis.navigator?.clipboard?.writeText) throw new Error('Clipboard unavailable')
    await globalThis.navigator.clipboard.writeText(props.roomId)
    copyStatus.value = t('feature.copied')
  } catch {
    copyStatus.value = t('feature.copyFailed')
  }
  globalThis.setTimeout?.(() => { copyStatus.value = '' }, 1600)
}
</script>

<template>
  <details class="feature-menu">
    <summary>{{ t('feature.menu') }}</summary>
    <div class="feature-panel">
      <nav class="feature-tabs" :aria-label="t('feature.menu')">
        <button
          v-for="tab in menuTabs"
          :key="tab.id"
          type="button"
          :class="{ active: activePanel === tab.id }"
          @click="activePanel = tab.id"
        >
          {{ tab.label }}
        </button>
      </nav>

      <section v-if="activePanel === 'settings'" class="feature-section">
        <div class="feature-section-title">
          <span class="eyebrow">{{ t('feature.personalization') }}</span>
          <strong>{{ t('feature.settingsTitle') }}</strong>
        </div>
        <label class="feature-field">
          <span>{{ t('language.label') }}</span>
          <select :value="language" @change="setLanguage($event.target.value)">
            <option v-for="item in languages" :key="item.code" :value="item.code">
              {{ t(item.labelKey) }}
            </option>
          </select>
          <small>{{ t('feature.currentLanguage', { language: languageLabel }) }}</small>
        </label>
        <label class="feature-toggle">
          <input v-model="compactUi" type="checkbox" />
          <span>{{ t('feature.compactUi') }}</span>
        </label>
      </section>

      <section v-else-if="activePanel === 'roles'" class="feature-section">
        <div class="feature-section-title">
          <span class="eyebrow">{{ t('feature.roleGuide') }}</span>
          <strong>{{ t('feature.roleGuideTitle') }}</strong>
        </div>
        <input v-model="roleSearch" type="search" :placeholder="t('feature.searchRoles')" />
        <div class="role-guide-list">
          <article v-for="item in roleItems" :key="item.role" class="role-guide-item">
            <strong>{{ item.name }}</strong>
            <small>{{ item.role }}</small>
            <p>{{ item.hint }}</p>
          </article>
        </div>
      </section>

      <section v-else-if="activePanel === 'rules'" class="feature-section">
        <div class="feature-section-title">
          <span class="eyebrow">{{ t('feature.rules') }}</span>
          <strong>{{ t('feature.rulesTitle') }}</strong>
        </div>
        <article v-for="section in ruleSections" :key="section.title" class="rule-summary-item">
          <strong>{{ section.title }}</strong>
          <p>{{ section.body }}</p>
        </article>
      </section>

      <section v-else class="feature-section">
        <div class="feature-section-title">
          <span class="eyebrow">{{ t('feature.tools') }}</span>
          <strong>{{ t('feature.toolsTitle') }}</strong>
        </div>
        <button class="secondary-button" :disabled="!props.roomId" @click="copyRoomId">
          {{ t('feature.copyRoomId') }}
        </button>
        <button class="secondary-button" :disabled="!props.hasGame" @click="emit('download-logs')">
          {{ t('gameOver.downloadLogs') }}
        </button>
        <small v-if="copyStatus">{{ copyStatus }}</small>
      </section>
    </div>
  </details>
</template>