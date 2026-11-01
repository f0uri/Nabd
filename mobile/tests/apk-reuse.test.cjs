const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'),
  os = require('node:os'),
  path = require('node:path');
const { spawnSync } = require('node:child_process');
const script = path.resolve(__dirname, '../scripts/check-apk-reuse.py');
for (const scenario of [
  'same',
  'source-changed',
  'runtime-changed',
  'config-changed',
  'wrong-mode',
])
  test(`APK reuse requires exact application/runtime/config inputs: ${scenario}`, () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nabd-reuse-'));
    try {
      const apk = path.join(
        root,
        'mobile/android/app/build/outputs/apk/release/app-release.apk',
      );
      fs.mkdirSync(path.dirname(apk), { recursive: true });
      fs.writeFileSync(
        path.join(root, 'nabd-source-diff.txt'),
        scenario === 'source-changed'
          ? 'mobile/src/telegram/password.ts'
          : 'mobile/scripts/smoke-android-apk.sh',
      );
      fs.writeFileSync(
        path.join(root, 'nabd-runtime.json'),
        JSON.stringify({ runtime: 'a'.repeat(40) }),
      );
      const create = spawnSync('python3', [
        '-c',
        `import zipfile,sys
with zipfile.ZipFile(sys.argv[1],'w') as z:
 z.writestr('assets/fingerprint',sys.argv[2]);z.writestr('assets/index.android.bundle',sys.argv[3])`,
        apk,
        (scenario === 'runtime-changed' ? 'b' : 'a').repeat(40),
        `123456:synthetic_token -123 https://fixture.test/graphql`,
      ]);
      assert.equal(create.status, 0);
      const output = path.join(root, 'output');
      const result = spawnSync('python3', [script], {
        cwd: root,
        env: {
          ...process.env,
          GITHUB_SHA: 'c'.repeat(40),
          RUNNER_TEMP: root,
          GITHUB_OUTPUT: output,
          EXPO_PUBLIC_DATA_SOURCE:
            scenario === 'wrong-mode' ? 'graphql' : 'telegram',
          EXPO_PUBLIC_TELEGRAM_BOT_TOKEN:
            scenario === 'config-changed'
              ? 'other-token'
              : '123456:synthetic_token',
          EXPO_PUBLIC_TELEGRAM_CHAT_ID: '-123',
          EXPO_PUBLIC_API_URL: 'https://fixture.test/graphql',
        },
        encoding: 'utf8',
      });
      assert.equal(result.status, 0, result.stderr);
      const reused = scenario === 'same';
      assert.match(
        fs.readFileSync(output, 'utf8'),
        new RegExp(`reuse=${reused}`),
      );
      assert.equal(fs.existsSync(apk), reused);
      assert.ok(!result.stdout.includes('synthetic_token'));
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });
