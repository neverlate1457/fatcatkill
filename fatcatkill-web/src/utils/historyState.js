export const parseHistoryFinalState = (finalState) => {
  if (!finalState) return null
  if (typeof finalState !== 'string') return finalState
  try {
    return JSON.parse(finalState)
  } catch {
    return null
  }
}
