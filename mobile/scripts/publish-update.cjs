const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

function verifyPublication(raw, expectedRuntime, expectedCommit) {
  const updates = JSON.parse(raw);
  if (!Array.isArray(updates)) throw Error('Invalid publication metadata.');
  const update = updates.find((entry) => entry.platform === 'android');
  if (
    !update ||
    !/^[a-f0-9-]{36}$/i.test(update.id || '') ||
    update.runtimeVersion !== expectedRuntime ||
    update.gitCommitHash !== expectedCommit ||
    update.isRollBackToEmbedded === true
  ) {
    throw Error(
      'Published OTA does not match the computed native runtime and source commit.',
    );
  }
  return {
    updateId: update.id,
    runtime: update.runtimeVersion,
    commit: update.gitCommitHash,
  };
}
async function publishUpdate({
  root = path.resolve(__dirname, '..'),
  cohort = 'current',
  expectedRuntime,
  commit = process.env.GITHUB_SHA,
  exportBundle = (project) =>
    execFileSync(
      'npx',
      [
        'expo',
        'export',
        '--platform',
        'android',
        '--clear',
        '--output-dir',
        'dist',
      ],
      { cwd: project, stdio: 'inherit' },
    ),
  verifyBundle = (project) =>
    require('./verify-release-bundle.cjs').verifyReleaseBundle(
      path.join(project, 'dist'),
    ),
  report = reportProof,
  run = (args, project) =>
    execFileSync('npx', ['--yes', 'eas-cli@27.2.0', ...args], {
      cwd: project,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'inherit'],
      maxBuffer: 16 * 1024 * 1024,
    }),
} = {}) {
  if (!['current', 'pre-icon'].includes(cohort))
    throw Error('Unknown native cohort.');
  if (!/^[a-f0-9]{40}$/.test(commit || ''))
    throw Error('A verified source commit is required.');
  if (process.env.EXPO_UPDATES_FINGERPRINT_OVERRIDE)
    throw Error('OTA publisher refuses a runtime override.');
  if (!expectedRuntime) {
    const result =
      await require('expo-updates/utils/build/resolveRuntimeVersionAsync').resolveRuntimeVersionAsync(
        root,
        'android',
        {},
        { workflowOverride: 'managed' },
      );
    if (!result.fingerprintSources?.length)
      throw Error('Fingerprint policy is required.');
    expectedRuntime = result.runtimeVersion;
  }
  if (!/^[a-f0-9]{40}$/.test(expectedRuntime || ''))
    throw Error('Invalid computed fingerprint.');
  exportBundle(root);
  const bundleProof = verifyBundle(root);
  const raw = run(
    [
      'update',
      '--channel',
      'production',
      '--platform',
      'android',
      '--message',
      `GitHub ${commit} · clarity-icons · ${cohort}`,
      '--skip-bundler',
      '--input-dir',
      'dist',
      '--json',
      '--non-interactive',
    ],
    root,
  );
  const proof = {
    cohort,
    ...verifyPublication(raw, expectedRuntime, commit),
    ...bundleProof,
  };
  report(proof);
  return proof;
}
function reportProof(proof) {
  // Only allowlisted metadata, never raw CLI output/manifest/credentials.
  console.log(
    `::notice title=Verified OTA publication::${JSON.stringify(proof)}`,
  );
  if (process.env.RUNNER_TEMP)
    fs.writeFileSync(
      path.join(
        process.env.RUNNER_TEMP,
        proof.cohort === 'pre-icon'
          ? 'nabd-ota-pre-icon.json'
          : 'nabd-ota-current.json',
      ),
      JSON.stringify(proof),
    );
  if (process.env.GITHUB_STEP_SUMMARY)
    fs.appendFileSync(
      process.env.GITHUB_STEP_SUMMARY,
      `\nVerified Android OTA (${proof.cohort === 'pre-icon' ? 'pre-icon' : 'current'}): ${proof.updateId}; runtime ${proof.runtime}; source ${proof.commit}.\n`,
    );
}
if (require.main === module)
  publishUpdate().catch(() => {
    console.error(
      'OTA publication or metadata verification failed; phone delivery is not confirmed.',
    );
    process.exitCode = 1;
  });
module.exports = { publishUpdate, verifyPublication };
