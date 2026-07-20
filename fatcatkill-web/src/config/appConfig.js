export const isDebugMode = import.meta.env.VITE_ENABLE_DEBUG_MODE === 'true'
export const STORAGE_KEY = 'fatcatkill.session'
export const CLIENT_ID_KEY = 'fatcatkill.clientId'
export const HOST_DECKS_KEY = 'fatcatkill.hostDecks'
export const AUTH_KEY = 'fatcatkill.authUser'

export const modeLabels = {
  OLD_HOME: '\u990a\u8001\u9662'
}


export const nightPhases = new Set([
  'NIGHT_WAITING',
  'STR_ACTION',
  'PH_SERVICE_ACTION',
  'NIGHT_START',
  'GUOGUO_ACTION',
  'FORVKUSA_ACTION',
  'HATONG_ACTION',
  'XIAOXIANG_ACTION',
  'MUBAIMU_ACTION',
  'SHUSHU_ACTION',
  'GRASS_BEAN_ACTION',
  'AC_CAT_ACTION',
  'XIANGXIANG_ACTION',
  'LIVER_INDEX_ACTION',
  'CAN_MAN_ACTION',
  'NANGONG_ACTION',
  'ANDY_ACTION',
  'METHANE_ACTION',
  'MOCHI_BOSS_ACTION'
])


export const defaultHostDeckRoles = [
  'FATCAT',
  'LIVER_INDEX',
  'HIGH_RABBIT',
  'METHANE',
  'GUOGUO',
  'XIANGXIANG',
  'AC_CAT',
  'FORVKUSA',
  'HATONG',
  'CAN_MAN'
]

export const fatcatHintSlotIndexes = [0, 1, 2]

export const botNamePrefixes = ['Bot ', '\u80d6\u8c93\u6e2c\u8a66\u54e1_']

export const hostVoteLogTypes = new Set([
  'SELECT_NOMINATION',
  'SELECT_EXECUTION_VOTE',
  'CONFIRM_VOTE',
  'CANCEL_VOTE',
  'SKIP_VOTE',
  'TALLY_VOTES',
  'START_NOMINATION'
])

export const methaneExcludedTargetRoles = new Set(['FATCAT', 'METHANE'])
