const fs = require('node:fs');
const assert = require('node:assert/strict');
const { properties } = require('../plugins/withCompactAndroid.cjs');
const gradle = fs.readFileSync('android/app/build.gradle', 'utf8');
const props = fs.readFileSync('android/gradle.properties', 'utf8');
for (const [key, value] of Object.entries(properties)) {
  assert.ok(props.split(/\r?\n/).includes(`${key}=${value}`), `Missing compact build property: ${key}`);
}
for (const text of ["findProperty('android.enableProguardInReleaseBuilds')", 'minifyEnabled enableProguardInReleaseBuilds',
  "findProperty('android.enableShrinkResourcesInReleaseBuilds')", "findProperty('expo.useLegacyPackaging')",
  "findProperty('android.enableBundleCompression')", "include 'armeabi-v7a', 'arm64-v8a', 'x86_64'", 'universalApk false']) {
  assert.ok(gradle.includes(text), `Generated Gradle template no longer consumes expected configuration: ${text}`);
}
assert.ok(fs.readFileSync('android/app/proguard-rules.pro', 'utf8').includes('-keep class com.margelo.nitro.** { *; }'));
console.log('Verified generated compact ABI builds, R8/resources, native/JS compression and JNI keep rules.');
