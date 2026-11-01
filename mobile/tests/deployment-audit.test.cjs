const { test } = require('node:test');
const assert = require('node:assert/strict');
const { inspectBot, telegramCall } = require('../scripts/audit-deployment.cjs');
const keys = [
  'AUDIT_DATA_SOURCE',
  'AUDIT_NABD_TOKEN',
  'AUDIT_LEGACY_TOKEN',
  'AUDIT_PUBLIC_TOKEN',
  'AUDIT_NABD_CHAT_VAR',
  'AUDIT_NABD_CHAT_SECRET',
  'AUDIT_LEGACY_CHAT_VAR',
  'AUDIT_LEGACY_CHAT_SECRET',
  'AUDIT_PUBLIC_CHAT_SECRET',
];
async function fixture(env, fetch, run) {
  const old = Object.fromEntries(keys.map((key) => [key, process.env[key]]));
  const oldFetch = global.fetch,
    log = console.log;
  const lines = [];
  for (const key of keys) delete process.env[key];
  Object.assign(process.env, env);
  global.fetch = fetch;
  console.log = (value) => lines.push(value);
  try {
    await run(lines);
  } finally {
    global.fetch = oldFetch;
    console.log = log;
    for (const key of keys) {
      if (old[key] === undefined) delete process.env[key];
      else process.env[key] = old[key];
    }
  }
}
test('audit distinguishes unprovided configuration without bot calls', async () => {
  await fixture(
    {},
    () => {
      throw Error('No request expected');
    },
    async (lines) => {
      await inspectBot();
      assert.ok(lines.some((s) => s.includes('NO_READABLE_CONFIGURATION')));
      assert.ok(lines.some((s) => s.includes('"mode":"auto"')));
    },
  );
});
test('audit checks legacy bot names read-only and emits booleans, never credentials or chat IDs', async () => {
  const token = '123456:fixture_not_real_do_not_log';
  const chat = '-100123456789';
  const calls = [];
  await fixture(
    { AUDIT_LEGACY_TOKEN: token, AUDIT_LEGACY_CHAT_SECRET: chat },
    async (url) => {
      calls.push(url.split('/').pop());
      return new Response(
        JSON.stringify({
          ok: true,
          result: url.endsWith('getMe')
            ? { id: 123456, is_bot: true }
            : { id: Number(chat), type: 'channel' },
        }),
      );
    },
    async (lines) => {
      await inspectBot();
      assert.deepEqual(calls, ['getMe', 'getChat']);
      assert.ok(lines.some((s) => s.includes('"identityValid":true')));
      assert.ok(!lines.join('').includes(token));
      assert.ok(!lines.join('').includes(chat));
    },
  );
});
test('diagnostic API failures never expose raw exceptions or token URLs', async () => {
  const old = global.fetch;
  global.fetch = async (url) => {
    throw Error(url);
  };
  try {
    await assert.rejects(
      telegramCall('123456:secret_fixture', 'getMe'),
      (e) => e.message === 'TELEGRAM_CHECK_FAILED',
    );
  } finally {
    global.fetch = old;
  }
});
test('APK verifier refuses mismatches, missing and malformed runtime values', () => {
  const { assertRuntime } = require('../scripts/verify-apk-runtime.cjs');
  const current = '218a8b15ac41835207e2125ecc9ee2f046f3fec2';
  assertRuntime(current, current);
  assert.throws(
    () => assertRuntime(current, '5af5c2ccf709c41ea5c3bb99c0308f72ea7827be'),
    /Refusing/,
  );
  assert.throws(() => assertRuntime(null, null), /Refusing/);
  assert.throws(() => assertRuntime('1.0.0', '1.0.0'), /Refusing/);
});
test('canonical fingerprint reaches the native SDK build hook and packaged APK verification', async () => {
  const fs = require('node:fs'),
    path = require('node:path'),
    os = require('node:os');
  const { spawnSync } = require('node:child_process');
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'nabd-runtime-test-'));
  const cwd = path.resolve(__dirname, '..');
  const env = { ...process.env, GITHUB_ENV: path.join(temp, 'github-env') };
  function run(args, overrides = {}) {
    return spawnSync(process.execPath, args, {
      cwd,
      env: { ...env, ...overrides },
      encoding: 'utf8',
    });
  }
  try {
    const metadata = path.join(temp, 'metadata.json');
    const captured = run(['scripts/capture-android-runtime.cjs', metadata]);
    assert.equal(captured.status, 0, captured.stderr);
    const { runtime } = JSON.parse(fs.readFileSync(metadata));
    assert.match(runtime, /^[a-f0-9]{40}$/);
    assert.equal(
      fs.readFileSync(env.GITHUB_ENV, 'utf8').trim(),
      `EXPO_UPDATES_FINGERPRINT_OVERRIDE=${runtime}`,
    );
    const hooked = run(
      [
        '-e',
        "require('expo-updates/utils/build/createFingerprintForBuildAsync').createFingerprintForBuildAsync('android',process.cwd(),process.argv[1])",
        temp,
      ],
      { EXPO_UPDATES_FINGERPRINT_OVERRIDE: runtime },
    );
    assert.equal(hooked.status, 0, hooked.stderr);
    assert.equal(
      fs.readFileSync(path.join(temp, 'fingerprint'), 'utf8'),
      runtime,
    );
    function zip(value) {
      const r = spawnSync('python3', [
        '-c',
        "import sys,zipfile; z=zipfile.ZipFile(sys.argv[1],'w'); z.writestr('assets/fingerprint',sys.argv[2]); z.close()",
        path.join(temp, 'fixture.apk'),
        value,
      ]);
      assert.equal(r.status, 0);
    }
    zip(runtime);
    const verified = run(['scripts/verify-apk-runtime.cjs', temp, metadata]);
    assert.equal(verified.status, 0, verified.stderr);
    zip('0'.repeat(40));
    const rejected = run(['scripts/verify-apk-runtime.cjs', temp, metadata]);
    assert.notEqual(rejected.status, 0);
    assert.match(rejected.stderr, /incompatible APK/);
  } finally {
    fs.rmSync(temp, { recursive: true, force: true });
  }
});
test('workflows isolate bot credentials from PR validation and gate aliases on explicit Telegram mode', () => {
  const fs = require('node:fs'),
    path = require('node:path');
  const yaml = require('js-yaml');
  const root = path.resolve(__dirname, '../../.github/workflows');
  const ota = yaml.load(
    fs.readFileSync(path.join(root, 'ota-updates.yml'), 'utf8'),
  );
  assert.ok(
    !Object.keys(ota.jobs.validate.env).some((key) => key.includes('TELEGRAM')),
  );
  const token = ota.jobs.publish.env.EXPO_PUBLIC_TELEGRAM_BOT_TOKEN;
  assert.ok(token.includes("== 'telegram'"));
  assert.ok(token.includes('secrets.TELEGRAM_BOT_TOKEN'));
  const android = yaml.load(
    fs.readFileSync(path.join(root, 'android-apk.yml'), 'utf8'),
  );
  const tests = android.jobs.apk.steps.find(
    (step) => step.name === 'Update and navigation tests',
  );
  assert.equal(tests.env.EXPO_PUBLIC_DATA_SOURCE, 'telegram');
  assert.match(tests.env.EXPO_PUBLIC_TELEGRAM_BOT_TOKEN, /synthetic_fixture/);
  assert.ok(!JSON.stringify(tests.env).includes('secrets.'));
  assert.ok(
    android.jobs.apk.env.EXPO_PUBLIC_TELEGRAM_BOT_TOKEN.includes(
      'secrets.NABD_TELEGRAM_BOT_TOKEN',
    ),
  );
  for (const name of ['Generate Android project', 'Build compact ABI APKs (R8 + resource shrinking + compressed libraries)']) {
    const build = android.jobs.apk.steps.find((step) => step.name === name);
    assert.equal(
      build.env?.EXPO_PUBLIC_TELEGRAM_BOT_TOKEN,
      undefined,
      'Real builds must inherit the job configuration, not fixture overrides',
    );
  }
  const regression = ota.jobs.validate.steps.find(
    (step) =>
      step.name ===
      'Regress config tests with an inherited Telegram fixture environment',
  );
  assert.match(
    regression.env.EXPO_PUBLIC_TELEGRAM_BOT_TOKEN,
    /synthetic_fixture/,
  );
  const audit = yaml.load(
    fs.readFileSync(path.join(root, 'deployment-audit.yml'), 'utf8'),
  );
  assert.deepEqual(audit.on.push.branches, ['main']);
  assert.equal(audit.on.pull_request, undefined);
  assert.equal(audit.permissions.contents, 'read');
  assert.equal(audit.permissions.actions, 'read');
});

test('pre-icon publication computes compatibility, never forces a runtime and always restores configuration', async () => {
  const fs = require('node:fs'); const path = require('node:path'); const os = require('node:os');
  const { publishLegacyUpdate, LEGACY_ANDROID_RUNTIME } = require('../scripts/publish-legacy-update.cjs');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nabd-legacy-ota-'));
  const file = path.join(root, 'app.json');
  const original = fs.readFileSync(path.join(__dirname, '../app.json'), 'utf8');
  fs.writeFileSync(file, original);
  const good = { runtimeVersion: LEGACY_ANDROID_RUNTIME, fingerprintSources: [{}], workflow: 'managed' };
  let published = 0;
  try {
    const compute = async () => {
      const config = JSON.parse(fs.readFileSync(file)).expo;
      assert.equal(config.icon, undefined);
      assert.equal(config.android.adaptiveIcon.foregroundImage, undefined);
      assert.equal(config.android.adaptiveIcon.backgroundColor, '#1877F2');
      assert.equal(config.runtimeVersion, undefined, 'Never inject a runtime string into static config');
      return good;
    };
    assert.equal((await publishLegacyUpdate({ root, compute, publish: () => { published++; } })).published, true);
    assert.equal(fs.readFileSync(file, 'utf8'), original);
    assert.equal((await publishLegacyUpdate({ root, compute, dryRun: true, publish: () => { published++; } })).published, false);
    const changed = await publishLegacyUpdate({ root, compute: async () => ({...good, runtimeVersion: '0'.repeat(40)}), publish: () => { published++; } });
    assert.equal(changed.compatible, false);
    const forced = await publishLegacyUpdate({ root, compute: async () => ({...good, fingerprintSources: null}), publish: () => { published++; } });
    assert.equal(forced.compatible, false);
    await assert.rejects(publishLegacyUpdate({ root, compute, publish: () => { throw new Error('fixture publish failure'); } }), /fixture publish failure/);
    assert.equal(fs.readFileSync(file, 'utf8'), original);
    assert.equal(published, 1);
  } finally { fs.rmSync(root, {recursive: true, force: true}); }
});
