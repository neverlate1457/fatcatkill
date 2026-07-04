import { t, translateMessage } from '../i18n'

export const errorMessage = (error, fallback = t('error.actionFailed')) => {
  const data = error?.response?.data
  if (typeof error === 'string') return translateMessage(error) || fallback
  if (error?.key) return translateMessage(error) || fallback
  if (typeof data === 'string') return translateMessage(data)
  if (data?.message) return translateMessage(data.message)
  if (data?.error && data?.path) return `${data.error}: ${data.path}`
  if (data?.error) return data.error
  if (data) return JSON.stringify(data)
  return translateMessage(error?.message) || fallback
}

export const useActionError = (actionError, actionNotice = null) => {
  const showActionError = (error, fallback = t('error.actionFailed')) => {
    const message = errorMessage(error, fallback)
    actionError.value = message
    if (actionNotice) actionNotice.value = ''
    return message
  }

  const clearActionError = () => {
    actionError.value = ''
  }

  return { showActionError, clearActionError }
}

