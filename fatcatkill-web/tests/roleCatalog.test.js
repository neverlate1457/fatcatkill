import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { enTranslations } from '../src/locales/en.js';
import { zhTwTranslations } from '../src/locales/zhTW.js';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

const parseStringArray = (source, exportName) => {
  const match = source.match(new RegExp(`export\\s+const\\s+${exportName}\\s*=\\s*\\[([\\s\\S]*?)\\]`));
  assert.ok(match, `Missing exported array: ${exportName}`);
  return [...match[1].matchAll(/'([^']+)'/g)].map((item) => item[1]);
};

const roleSource = read('../src/data/roles.js');
const enumSource = read('../../fatcatkill-api/src/main/java/com/fatcatkill/enums/Role.java');
const roleCodes = parseStringArray(roleSource, 'roleCodes');
const volunteerRoleOptions = parseStringArray(roleSource, 'volunteerRoleOptions');
const enumRoles = [...enumSource.matchAll(/^\s*([A-Z][A-Z0-9_]+)(?:,|;)/gm)].map((item) => item[1]);

test('frontend role catalog only references backend roles', () => {
  assert.deepEqual(roleCodes.filter((role) => !enumRoles.includes(role)), []);
  assert.deepEqual(volunteerRoleOptions.filter((role) => !roleCodes.includes(role)), []);
});

test('custom deck role options mirror the supported frontend role catalog', () => {
  assert.match(roleSource, /export\s+const\s+customRoleOptions\s*=\s*\[\.\.\.roleCodes\]/);
});

test('supported frontend roles have names and hints in every locale', () => {
  for (const role of roleCodes) {
    assert.ok(zhTwTranslations[`role.${role}`], `Missing zh-TW role name: ${role}`);
    assert.ok(enTranslations[`role.${role}`], `Missing en role name: ${role}`);
    assert.ok(zhTwTranslations[`roleHint.${role}`], `Missing zh-TW role hint: ${role}`);
    assert.ok(enTranslations[`roleHint.${role}`], `Missing en role hint: ${role}`);
  }
});