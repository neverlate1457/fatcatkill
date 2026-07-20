import { computed, ref } from 'vue'
import { translations } from './locales/index.js'

const LANGUAGE_KEY = 'fatcatkill.language'

export const languages = [
  { code: 'zh-TW', labelKey: 'language.zhTw' },
  { code: 'en', labelKey: 'language.en' }
]

const localStorageRef = globalThis.window?.localStorage || globalThis.localStorage || null
const savedLanguage = localStorageRef?.getItem(LANGUAGE_KEY)
export const currentLanguage = ref(languages.some((item) => item.code === savedLanguage) ? savedLanguage : 'zh-TW')

const applyParams = (text, params = {}) => String(text).replace(/\{(\w+)\}/g, (_, key) => params[key] ?? `{${key}}`)

export const setLanguage = (language) => {
  if (!languages.some((item) => item.code === language)) return
  currentLanguage.value = language
  localStorageRef?.setItem(LANGUAGE_KEY, language)
}

export const t = (key, params = {}, fallback = key) => {
  const value = translations[currentLanguage.value]?.[key] ?? translations.en[key] ?? fallback
  return applyParams(value, params)
}

export const translateMessage = (message) => {
  if (!message) return ''
  if (typeof message === 'string') return t(message, {}, message)
  if (typeof message === 'object') {
    const key = message.key || message.messageKey
    const params = message.params || message.messageParams || {}
    const fallback = message.fallback || message.messageFallback || message.message || key || ''
    return key ? t(key, params, fallback) : String(fallback)
  }
  return String(message)
}
export const useI18n = () => ({
  language: currentLanguage,
  languages,
  setLanguage,
  t,
  languageLabel: computed(() => {
    const item = languages.find((option) => option.code === currentLanguage.value)
    return item ? t(item.labelKey) : currentLanguage.value
  })
})
