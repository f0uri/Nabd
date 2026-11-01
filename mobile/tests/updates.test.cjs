const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const code = ts.transpileModule(readFileSync(path.join(__dirname, '../src/updates/createUpdateStore.ts'), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
const context = { exports: {}, require };
vm.runInNewContext(code, context);
const { createUpdateStore } = context.exports;
const available = { isAvailable: true, manifest: { id: 'synthetic-update-1' } };
function fixture(overrides = {}, clock = () => 1_000) {
  const calls = { check: 0, fetch: 0, reload: 0 };
  const adapter = {
    enabled: true,
    check: async () => { calls.check++; return available; },
    fetch: async () => { calls.fetch++; return { isNew: true }; },
    reload: async () => { calls.reload++; },
    ...overrides,
  };
  const store = createUpdateStore(adapter, clock);
  return { store, calls, state: () => store.getState() };
}

test('unconfigured/development builds never call native update APIs', async () => {
  const { state, calls } = fixture({ enabled: false });
  await state().check();
  assert.equal(state().visible, false);
  await state().check(true);
  assert.equal(state().visible, true);
  assert.match(state().message, /غير مفعّل/);
  await state().download(); await state().apply();
  assert.equal(calls.check + calls.fetch + calls.reload, 0);
});
test('available update only opens a notice; it does not download or restart', async () => {
  const { state, calls } = fixture();
  await state().check();
  assert.equal(state().phase, 'available');
  assert.equal(state().visible, true);
  assert.equal(calls.fetch, 0); assert.equal(calls.reload, 0);
});
test('no compatible update displays the current-version message for manual checks', async () => {
  const { state } = fixture({ check: async () => ({ isAvailable: false }) });
  await state().check(true);
  assert.equal(state().phase, 'idle');
  assert.match(state().message, /متوافق/);
});
test('background checks are throttled, while manual checks can bypass the interval', async () => {
  let now = 0;
  const { state, calls } = fixture({}, () => now);
  await state().check();
  await state().check();
  assert.equal(calls.check, 1);
  await state().check(true);
  assert.equal(calls.check, 2);
  now = 16 * 60 * 1000;
  await state().check();
  assert.equal(calls.check, 3);
});
test('later suppresses repeated prompts for the same update during this process', async () => {
  let now = 0;
  const { state } = fixture({}, () => now);
  await state().check(); state().dismiss();
  now = 16 * 60 * 1000;
  await state().check();
  assert.equal(state().visible, false);
  await state().check(true);
  assert.equal(state().visible, true);
});
test('a different update may notify after a previous update was deferred', async () => {
  let id = 'one'; let now = 0;
  const { state } = fixture({ check: async () => ({ isAvailable: true, manifest: { id } }) }, () => now);
  await state().check(); state().dismiss();
  id = 'two'; now = 16 * 60 * 1000;
  await state().check();
  assert.equal(state().visible, true); assert.equal(state().updateId, 'two');
});
test('offline background checks never block use with an error modal', async () => {
  const { state } = fixture({ check: async () => { throw new Error('sensitive network details'); } });
  await state().check();
  assert.equal(state().visible, false); assert.equal(state().phase, 'idle');
  await state().check(true);
  assert.equal(state().phase, 'error'); assert.equal(state().errorStep, 'check');
  assert.ok(!state().message.includes('sensitive'));
});
test('duplicate checks and premature downloads are ignored', async () => {
  let finish;
  const { state, calls } = fixture({ check: () => { calls.check++; return new Promise((r) => { finish = r; }); } });
  const checking = state().check();
  await state().check(true); await state().download(); await state().apply();
  assert.equal(calls.check, 1); assert.equal(calls.fetch, 0); assert.equal(calls.reload, 0);
  finish(available); await checking;
});
test('download completes before explicit restart; no automatic reload occurs', async () => {
  const { state, calls } = fixture();
  await state().check(); await state().download();
  assert.equal(state().phase, 'ready'); assert.equal(calls.reload, 0);
  state().dismiss();
  await state().check(true);
  assert.equal(state().visible, true); assert.equal(state().phase, 'ready');
  assert.equal(calls.check, 1);
  await state().apply();
  assert.equal(calls.reload, 1);
});
test('interrupted download preserves the current app and can be retried', async () => {
  let attempts = 0;
  const { state, calls } = fixture({ fetch: async () => {
    if (++attempts === 1) throw new Error('offline');
    return { isNew: true };
  } });
  await state().check(); await state().download();
  assert.equal(state().phase, 'error'); assert.equal(state().errorStep, 'download');
  await state().apply(); assert.equal(calls.reload, 0);
  await state().download(); assert.equal(state().phase, 'ready');
});
test('in-flight download cannot be duplicated, overwritten by a check or dismissed', async () => {
  let finish;
  const { state, calls } = fixture({ fetch: () => { calls.fetch++; return new Promise((r) => { finish = r; }); } });
  await state().check();
  const downloading = state().download();
  await state().download(); await state().check(true); state().dismiss();
  assert.equal(calls.fetch, 1); assert.equal(calls.check, 1); assert.equal(state().visible, true);
  finish({ isNew: true }); await downloading;
});
test('fetch reporting no new update never reloads the app', async () => {
  const { state, calls } = fixture({ fetch: async () => ({ isNew: false }) });
  await state().check(); await state().download(); await state().apply();
  assert.equal(state().phase, 'idle'); assert.equal(calls.reload, 0);
});
test('rollback-to-embedded is handled as an update with user consent', async () => {
  const { state, calls } = fixture({
    check: async () => ({ isAvailable: false, isRollBackToEmbedded: true }),
    fetch: async () => ({ isNew: false, isRollBackToEmbedded: true }),
  });
  await state().check(); assert.equal(state().phase, 'available');
  await state().download(); assert.equal(state().phase, 'ready');
  assert.equal(calls.reload, 0);
  await state().apply(); assert.equal(calls.reload, 1);
});
test('failed reload leaves the downloaded update ready and allows a retry', async () => {
  let attempts = 0;
  const { state } = fixture({ reload: async () => { if (++attempts === 1) throw new Error('native unavailable'); } });
  await state().check(); await state().download(); await state().apply();
  assert.equal(state().phase, 'ready'); assert.match(state().message, /تعذّرت/);
  await state().apply(); assert.equal(attempts, 2);
});

test('a manual request during a background check receives the same check result', async () => {
  let finish;
  const { state, calls } = fixture({ check: () => { calls.check++; return new Promise((r) => { finish = r; }); } });
  const check = state().check();
  await state().check(true);
  assert.equal(state().visible, true);
  finish({ isAvailable: false }); await check;
  assert.equal(calls.check, 1); assert.equal(state().visible, true);
  assert.equal(state().phase, 'idle');
});
