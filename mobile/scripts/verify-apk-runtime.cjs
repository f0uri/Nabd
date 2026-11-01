const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
function assertRuntime(expected, actual) {
  if (!/^[a-f0-9]{40}$/.test(expected ?? '') || expected !== actual)
    throw Error(
      'APK runtime does not match canonical OTA native inputs. Refusing to upload an incompatible APK.',
    );
}
async function verifyApks(directory, metadataPath) {
  const expected = JSON.parse(fs.readFileSync(metadataPath, 'utf8'));
  const files = fs
    .readdirSync(directory)
    .filter((name) => name.endsWith('.apk'));
  if (!files.length) throw Error('No APK found.');
  for (const name of files) {
    const result = spawnSync(
      'python3',
      [
        '-c',
        "import sys,zipfile; z=zipfile.ZipFile(sys.argv[1]); print(z.read('assets/fingerprint').decode().strip())",
        path.join(directory, name),
      ],
      { encoding: 'utf8' },
    );
    if (result.status !== 0)
      throw Error('Cannot read APK native runtime fingerprint.');
    assertRuntime(expected.runtime, result.stdout.trim());
  }
  const {
    resolveRuntimeVersionAsync,
  } = require('expo-updates/utils/build/resolveRuntimeVersionAsync');
  const after = await resolveRuntimeVersionAsync(
    process.cwd(),
    'android',
    {},
    { workflowOverride: 'managed' },
  );
  assertRuntime(expected.runtime, after.runtimeVersion);
  console.log(
    'Verified: packaged APK runtime and post-build native inputs match canonical OTA runtime.',
  );
}
if (require.main === module)
  verifyApks(process.argv[2], process.argv[3]).catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
module.exports = { assertRuntime, verifyApks };
