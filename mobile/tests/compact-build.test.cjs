const { test } = require('node:test');
const assert = require('node:assert/strict');
const { splitGradle, properties } = require('../plugins/withCompactAndroid.cjs');
const config = require('../app.json');
test('compact builds survive clean Expo prebuild and retain three single-ABI outputs', () => {
  assert.ok(config.expo.plugins.includes('./plugins/withCompactAndroid.cjs'));
  const generated = splitGradle('android {}');
  assert.equal(splitGradle(generated), generated);
  assert.match(generated, /include 'armeabi-v7a', 'arm64-v8a', 'x86_64'/);
  assert.match(generated, /universalApk false/);
});
test('download optimizations enable R8, resource/native/bundle compression without disabling features', () => {
  assert.equal(properties['android.enableProguardInReleaseBuilds'], 'true');
  assert.equal(properties['android.enableShrinkResourcesInReleaseBuilds'], 'true');
  assert.equal(properties['expo.useLegacyPackaging'], 'true');
  assert.equal(properties['android.enableBundleCompression'], 'true');
  assert.equal(properties.reactNativeArchitectures, 'armeabi-v7a,arm64-v8a,x86_64');
});
