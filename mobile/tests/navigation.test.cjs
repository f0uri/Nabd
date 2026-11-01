const { test, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

// Compile the actual store; type-only imports disappear. No native bridge is needed.
const source = readFileSync(path.join(__dirname, '../src/store/useUiStore.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
const context = { exports: {}, require };
vm.runInNewContext(compiled, context, { filename: 'useUiStore.js' });
const store = context.exports.useUiStore;
beforeEach(() => store.setState({ tab: 'feed', adminOpen: false }));

test('profile shortcut opens a real tab instead of an overlay covering the bottom bar', () => {
  store.getState().openProfile();
  assert.equal(store.getState().tab, 'profile');
  assert.equal(store.getState().adminOpen, false);
});
test('every bottom navigation destination is selectable after opening the profile', () => {
  for (const tab of ['feed', 'reels', 'friends', 'menu', 'notifications', 'profile']) {
    store.getState().openProfile();
    store.getState().setTab(tab);
    assert.equal(store.getState().tab, tab);
  }
});
test('closing the profile returns home and admin controls remain available', () => {
  store.getState().openProfile();
  store.getState().closeProfile();
  assert.equal(store.getState().tab, 'feed');
  store.getState().openAdmin();
  assert.equal(store.getState().adminOpen, true);
  store.getState().closeAdmin();
  assert.equal(store.getState().adminOpen, false);
});
