import { t } from '../i18n'

export const roleCodes = [
  'WEREWOLF',
  'VILLAGER',
  'SEER',
  'WITCH',
  'HUNTER',
  'GUARD',
  'METHANE',
  'GUOGUO',
  'XIANGXIANG',
  'AC_CAT',
  'FORVKUSA',
  'HATONG',
  'KB',
  'SALTED_FISH',
  'XIAOXIANG',
  'MOCHI_BOSS',
  'GRASS_BEAN',
  'NANGONG',
  'CASTER',
  'ANDY',
  'CAN_MAN',
  'SINGLE_DOG',
  'STR',
  'HIGH_RABBIT',
  'MEATBUN',
  'MUBAIMU',
  'CHEN',
  'XIAOEN',
  'NUKO',
  'SHUSHU',
  'BARK_KING',
  'FATCAT',
  'LIVER_INDEX',
  'PINK_RABBIT',
  'EMPEROR',
  'NTHU_MATH',
  'MAGIC_MEOW',
  'PH_SERVICE',
  'RAT_MAN'
]

export const roleTranslations = Object.fromEntries(roleCodes.map((role) => [role, role]))

export const fatcatHorcruxRoles = new Set([
  'LIVER_INDEX',
  'PINK_RABBIT',
  'EMPEROR',
  'NTHU_MATH',
  'MAGIC_MEOW',
  'PH_SERVICE',
  'RAT_MAN'
])

export const customRoleOptions = roleCodes
  .filter((role) => !['WEREWOLF', 'VILLAGER', 'SEER', 'WITCH', 'HUNTER', 'GUARD'].includes(role))

export const volunteerRoleOptions = [
  'METHANE',
  'GUOGUO',
  'XIANGXIANG',
  'AC_CAT',
  'FORVKUSA',
  'HATONG',
  'KB',
  'SALTED_FISH',
  'XIAOXIANG',
  'MOCHI_BOSS',
  'GRASS_BEAN',
  'NANGONG',
  'CASTER',
  'ANDY',
  'CAN_MAN',
  'SINGLE_DOG',
  'STR'
]

export const translateRole = (role) => t(`role.${role}`, {}, role || t('common.unknownRole'))
export const translateRoleHint = (role) => t(`roleHint.${role}`, {}, t('common.unknownRole'))