import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { enTranslations } from '../src/locales/en.js';
import { zhTwTranslations } from '../src/locales/zhTW.js';

const srcRoot = new URL('../src/', import.meta.url);
const repoRoot = new URL('../../', import.meta.url);

const walkSourceFiles = (dirUrl, files = []) => {
  for (const entry of readdirSync(dirUrl, { withFileTypes: true })) {
    if (entry.name === 'assets') continue;
    const childUrl = new URL(`${entry.name}${entry.isDirectory() ? '/' : ''}`, dirUrl);
    if (entry.isDirectory()) {
      walkSourceFiles(childUrl, files);
    } else if (/\.(vue|js)$/.test(entry.name)) {
      files.push(childUrl);
    }
  }
  return files;
};

const walkFiles = (dirUrl, pattern, files = []) => {
  for (const entry of readdirSync(dirUrl, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'target' || entry.name === 'dist') continue;
    const childUrl = new URL(`${entry.name}${entry.isDirectory() ? '/' : ''}`, dirUrl);
    if (entry.isDirectory()) {
      walkFiles(childUrl, pattern, files);
    } else if (pattern.test(entry.name)) {
      files.push(childUrl);
    }
  }
  return files;
};

const producedMessageKeys = () => {
  const roots = [
    new URL('fatcatkill-api/src/main/java/', repoRoot),
    new URL('fatcatkill-gateway/', repoRoot)
  ];
  const patterns = [
    /MessagePayload\.of\(\s*["']([^"']+)["']/g,
    /localized\(\s*["']([^"']+)["']/g,
    /gatewayError\(\s*["']([^"']+)["']/g,
    /messagePayload\(\s*["']([^"']+)["']/g
  ];
  const keys = new Set();
  for (const root of roots) {
    for (const fileUrl of walkFiles(root, /\.(java|js)$/)) {
      const source = readFileSync(fileUrl, 'utf8');
      for (const pattern of patterns) {
        for (const match of source.matchAll(pattern)) {
          if (/^(backend|gateway)\./.test(match[1])) keys.add(match[1]);
        }
      }
    }
  }
  return [...keys].sort();
};

const staticTranslationKeys = () => {
  const keys = new Map();
  for (const fileUrl of walkSourceFiles(srcRoot)) {
    const source = readFileSync(fileUrl, 'utf8');
    for (const match of source.matchAll(/\bt\(\s*['"]([^'"`]+)['"]/g)) {
      const key = match[1];
      const list = keys.get(key) || [];
      list.push(fileUrl.pathname);
      keys.set(key, list);
    }
  }
  return keys;
};

test('locale tables contain the same keys', () => {
  const zhKeys = Object.keys(zhTwTranslations).sort();
  const enKeys = Object.keys(enTranslations).sort();

  assert.deepEqual(zhKeys.filter((key) => !enTranslations[key]), []);
  assert.deepEqual(enKeys.filter((key) => !zhTwTranslations[key]), []);
  assert.deepEqual(zhKeys, enKeys);
});

test('static translation calls reference existing locale keys', () => {
  const missing = [...staticTranslationKeys().keys()]
    .filter((key) => !zhTwTranslations[key] || !enTranslations[key])
    .sort();

  assert.deepEqual(missing, []);
});

test('backend and gateway message keys have locale translations', () => {
  const missing = producedMessageKeys()
    .filter((key) => !zhTwTranslations[key] || !enTranslations[key]);

  assert.deepEqual(missing, []);
});