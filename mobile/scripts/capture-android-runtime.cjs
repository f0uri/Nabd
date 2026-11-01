const fs = require('node:fs');
async function captureRuntime(destination, githubEnv) {
  if (!destination) throw Error('A runtime metadata destination is required.');
  const {
    resolveRuntimeVersionAsync,
  } = require('expo-updates/utils/build/resolveRuntimeVersionAsync');
  const config = require('../app.config.js')();
  if (config.runtimeVersion?.policy !== 'fingerprint')
    throw Error(
      'Expected fingerprint runtime policy; refusing an unsafe override.',
    );
  const result = await resolveRuntimeVersionAsync(
    process.cwd(),
    'android',
    {},
    { workflowOverride: 'managed' },
  );
  if (!/^[a-f0-9]{40}$/.test(result.runtimeVersion ?? ''))
    throw Error('Invalid canonical Android runtime.');
  fs.writeFileSync(
    destination,
    JSON.stringify({
      runtime: result.runtimeVersion,
      projectId: config.extra?.eas?.projectId,
      channel: config.updates?.requestHeaders?.['expo-channel-name'],
    }),
  );
  // Official expo-updates build hook: lock the native input fingerprint BEFORE
  // Gradle creates transient files in its plugins. Never use a hardcoded runtime.
  if (githubEnv)
    fs.appendFileSync(
      githubEnv,
      `EXPO_UPDATES_FINGERPRINT_OVERRIDE=${result.runtimeVersion}\n`,
    );
  console.log(`Canonical Android runtime: ${result.runtimeVersion}`);
  return result.runtimeVersion;
}
if (require.main === module)
  captureRuntime(process.argv[2], process.env.GITHUB_ENV).catch(() => {
    console.error('Unable to capture canonical Android runtime.');
    process.exitCode = 1;
  });
module.exports = { captureRuntime };
