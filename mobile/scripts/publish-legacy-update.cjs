const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

// Verified native runtime of the repaired pre-icon APK (run 41295217228).
// An allowlist check, NOT a runtime override: Expo must independently compute it.
const LEGACY_ANDROID_RUNTIME = '218a8b15ac41835207e2125ecc9ee2f046f3fec2';
function legacyConfig(value) {
  const config = JSON.parse(JSON.stringify(value));
  delete config.expo.icon;
  delete config.expo.android.adaptiveIcon.foregroundImage;
  config.expo.android.adaptiveIcon.backgroundColor = '#1877F2';
  return config;
}
async function publishLegacyUpdate({
  root = path.resolve(__dirname, '..'),
  dryRun = false,
  compute = async (project) =>
    require('expo-updates/utils/build/resolveRuntimeVersionAsync').resolveRuntimeVersionAsync(
      project,
      'android',
      {},
      { workflowOverride: 'managed' },
    ),
  publish = (project) =>
    execFileSync(
      'npx',
      [
        '--yes',
        'eas-cli@27.2.0',
        'update',
        '--channel',
        'production',
        '--platform',
        'android',
        '--message',
        `GitHub ${process.env.GITHUB_SHA || 'manual'} · pre-icon native cohort`,
        '--non-interactive',
      ],
      { cwd: project, stdio: 'inherit' },
    ),
} = {}) {
  if (process.env.EXPO_UPDATES_FINGERPRINT_OVERRIDE)
    throw Error('Legacy publication refuses a fingerprint override.');
  const file = path.join(root, 'app.json');
  const original = fs.readFileSync(file, 'utf8');
  try {
    // Change only publication-time native metadata. JS/assets remain the newest
    // code; the actual APK job never runs this script or changes its app config.
    fs.writeFileSync(
      file,
      `${JSON.stringify(legacyConfig(JSON.parse(original)), null, 2)}\n`,
    );
    const result = await compute(root);
    const compatible =
      Array.isArray(result.fingerprintSources) &&
      result.fingerprintSources.length > 0 &&
      result.workflow === 'managed' &&
      result.runtimeVersion === LEGACY_ANDROID_RUNTIME;
    if (!compatible) {
      console.log(
        '::notice::Pre-icon OTA skipped: native inputs no longer match the verified older APK. No runtime was forced.',
      );
      return { compatible: false, published: false };
    }
    console.log(
      `Verified pre-icon native compatibility: ${result.runtimeVersion}`,
    );
    if (!dryRun) await publish(root);
    return { compatible: true, published: !dryRun };
  } finally {
    fs.writeFileSync(file, original);
  }
}
if (require.main === module)
  publishLegacyUpdate({ dryRun: process.argv.includes('--check') }).catch(
    () => {
      console.error(
        'Pre-icon OTA verification/publication failed. Current app configuration was restored.',
      );
      process.exitCode = 1;
    },
  );
module.exports = { publishLegacyUpdate, legacyConfig, LEGACY_ANDROID_RUNTIME };
