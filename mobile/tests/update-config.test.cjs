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
  const unlinked = { ...base, extra: { ...base.extra } };
  unlinked.extra.eas = undefined; // Explicitly clear the linked default for this fixture.
  const result = configure({ config: unlinked });
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

test('the supplied Nabd project is linked without a repository variable', () => withProject(undefined, () => {
  const result = configure();
  assert.equal(result.extra.eas.projectId, 'eec41206-1069-4604-aa2a-3fcc812f1ecd');
  assert.equal(result.updates.url, `https://u.expo.dev/${result.extra.eas.projectId}`);
  assert.equal(result.android.package, 'app.nabd.social');
  assert.equal(result.name, 'نبض');
  assert.equal(result.slug, 'fysbwk');
}));

test('launcher artwork is packaged locally with opaque and adaptive PNG configurations', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const result = configure();
  assert.equal(result.icon, './assets/icon.png');
  assert.equal(result.android.adaptiveIcon.foregroundImage, './assets/adaptive-icon.png');
  assert.equal(result.android.adaptiveIcon.backgroundColor, '#FFFFFF');
  for (const [asset, colorType] of [[result.icon, 2], [result.android.adaptiveIcon.foregroundImage, 6]]) {
    const png = fs.readFileSync(path.resolve(__dirname, '..', asset));
    assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    assert.equal(png.readUInt32BE(16), 1024);
    assert.equal(png.readUInt32BE(20), 1024);
    assert.equal(png[25], colorType, 'Main icon is opaque RGB; adaptive layer supports transparency');
  }
  assert.equal(result.runtimeVersion.policy, 'fingerprint', 'Never force native icon changes into an old runtime');
});

test('the in-app brand mark uses the bundled image, containment and an accessible label', () => {
  const load = require('./helpers/load-source.cjs')({ mocks: {
    'react-native': { Image: 'NativeImage' },
    '../../assets/icon.png': 'bundled-nabd-artwork',
  } });
  const BrandMark = load('components/BrandMark.tsx').default;
  const image = BrandMark({ size: 64 });
  assert.equal(image.type, 'NativeImage');
  assert.equal(image.props.source, 'bundled-nabd-artwork');
  assert.deepEqual({ ...image.props.style }, { width: 64, height: 64 });
  assert.equal(image.props.resizeMode, 'contain');
  assert.equal(image.props.accessibilityLabel, 'شعار نبض');
  assert.equal(BrandMark({}).props.style.width, 40);
});
