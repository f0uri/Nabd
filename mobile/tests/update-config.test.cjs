const { test } = require('node:test');
const assert = require('node:assert/strict');
const configure = require('../app.config.js');
const base = require('../app.json').expo;
const id = '11111111-2222-4333-8444-555555555555'; // Test fixture, never an actual project.
function withProject(value, run) {
  const before = process.env.EXPO_EAS_PROJECT_ID;
  if (value === undefined) delete process.env.EXPO_EAS_PROJECT_ID;
  else process.env.EXPO_EAS_PROJECT_ID = value;
  try { run(); } finally {
    if (before === undefined) delete process.env.EXPO_EAS_PROJECT_ID;
    else process.env.EXPO_EAS_PROJECT_ID = before;
  }
}
test('unlinked builds explicitly disable OTA instead of using an invented project', () => withProject(undefined, () => {
  const result = configure({ config: base });
  assert.equal(result.updates.enabled, false);
  assert.equal(result.updates.url, undefined);
  assert.equal(result.extra.eas, undefined);
  assert.equal(result.android.package, base.android.package);
}));
test('linked builds use HTTPS, production channel, fingerprint runtime and manual consent', () => withProject(id, () => {
  const result = configure({ config: base });
  assert.equal(result.updates.enabled, true);
  assert.equal(result.updates.url, `https://u.expo.dev/${id}`);
  assert.equal(result.extra.eas.projectId, id);
  assert.equal(result.runtimeVersion.policy, 'fingerprint');
  assert.equal(result.updates.requestHeaders['expo-channel-name'], 'production');
  assert.equal(result.updates.checkAutomatically, 'ON_ERROR_RECOVERY');
  assert.equal(result.updates.fallbackToCacheTimeout, 0);
  assert.ok(!JSON.stringify(result).includes('EXPO_TOKEN'));
}));
test('invalid project identifiers fail the build rather than silently misconfiguring updates', () => withProject('not-a-project', () => {
  assert.throws(() => configure({ config: base }), /real EAS project UUID/);
}));
test('an EAS-initialized project ID in static config is supported', () => withProject(undefined, () => {
  const result = configure({ config: { ...base, extra: { ...base.extra, eas: { projectId: id } } } });
  assert.equal(result.updates.enabled, true);
  assert.equal(result.extra.eas.projectId, id);
}));
