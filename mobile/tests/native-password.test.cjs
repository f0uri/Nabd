const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const loader = require('./helpers/load-source.cjs');
function fixture(pbkdf2 = crypto.pbkdf2) {
  const calls = [];
  const load = loader({
    mocks: {
      'react-native-quick-crypto': {
        pbkdf2: (...args) => {
          calls.push(args.slice(0, 5));
          return pbkdf2(...args);
        },
      },
    },
  });
  const p = load('telegram/password.ts'),
    native = load('telegram/nativePassword.ts');
  p.setNativePasswordDeriver(native.deriveNativePassword);
  return { p, native, calls };
}
test('native adapter preserves every legacy password byte, NULs, Arabic, emoji and surrogate replacement', async () => {
  const f = fixture();
  const salt = '000102030405060708090a0b0c0d0e0f';
  assert.equal(await f.native.prepareNativePassword(), true);
  for (const password of [
    'test-password',
    'عربية 🌍',
    'abc\0def\0',
    '\ud800',
    '\udc00',
    '',
  ]) {
    assert.equal(
      await f.p.passwordDigest(password, salt),
      crypto
        .pbkdf2Sync(
          Buffer.from(password),
          Buffer.from(salt, 'hex'),
          210000,
          32,
          'sha256',
        )
        .toString('hex'),
    );
  }
  assert.equal(
    f.calls.length,
    7,
    'one fixed self-test + all six real password derivations',
  );
  for (const [password, salt, rounds, length, digest] of f.calls) {
    assert.ok(password instanceof Uint8Array);
    assert.equal(salt.length, 16);
    assert.equal(rounds, 210000);
    assert.equal(length, 32);
    assert.equal(digest, 'sha256');
  }
});
test('native derivation is asynchronous and does not memoize user passwords/results', async () => {
  const f = fixture();
  await f.native.prepareNativePassword();
  let tick = false;
  const pending = f.p.passwordDigest('same-password', '0'.repeat(32));
  await new Promise((resolve) =>
    setImmediate(() => {
      tick = true;
      resolve();
    }),
  );
  assert.equal(tick, true);
  await pending;
  await f.p.passwordDigest('same-password', '0'.repeat(32));
  assert.equal(f.calls.length, 3);
});
test('broken native compatibility vector fails closed before hashing user data', async () => {
  const f = fixture((...args) => args.at(-1)(null, Buffer.alloc(32)));
  await assert.rejects(
    f.p.passwordDigest('user-secret', '0'.repeat(32)),
    /PASSWORD_DERIVATION_FAILED/,
  );
  assert.equal(f.calls.length, 1);
});
test('native errors/invalid output never produce successful authentication or a different hash', async () => {
  for (const bad of ['reject', 'short']) {
    let count = 0;
    const f = fixture((...args) => {
      if (count++ === 0) return crypto.pbkdf2(...args);
      args.at(-1)(
        bad === 'reject' ? Error('native failed') : null,
        Buffer.alloc(3),
      );
    });
    await assert.rejects(
      f.p.passwordDigest('password', '0'.repeat(32)),
      /PASSWORD_DERIVATION_FAILED/,
    );
  }
});
test('older shells without native binding keep the exact strengthened JS fallback', async () => {
  const load = loader({
    mocks: {
      'react-native-quick-crypto': new Proxy(
        {},
        {
          get() {
            throw Error('missing native module');
          },
        },
      ),
    },
  });
  const p = load('telegram/password.ts'),
    native = load('telegram/nativePassword.ts');
  p.setNativePasswordDeriver(native.deriveNativePassword);
  assert.equal(await native.prepareNativePassword(), false);
  const salt = '0'.repeat(32);
  assert.equal(
    await p.passwordDigest('password', salt),
    crypto
      .pbkdf2Sync('password', Buffer.from(salt, 'hex'), 210000, 32, 'sha256')
      .toString('hex'),
  );
});
test('native versions are pinned above the NUL-truncation advisory and real Android smoke is required', () => {
  const fs = require('node:fs'),
    path = require('node:path');
  const root = path.resolve(__dirname, '../..');
  const pkg = require('../package.json');
  assert.equal(pkg.dependencies['react-native-quick-crypto'], '1.1.9');
  assert.equal(pkg.dependencies['react-native-nitro-modules'], '0.33.2');
  const workflow = fs.readFileSync(
    path.join(root, '.github/workflows/android-apk.yml'),
    'utf8',
  );
  assert.match(workflow, /smoke-android-apk\.sh/);
  assert.match(workflow, /Verify actual APK runtime/);
  assert.match(workflow, /gh release create/);
});

test('release probe is written only after actual fixed native self-test, with no account or password persistence', async () => {
  for (const available of [true, false]) {
    const writes = [];
    let complete;
    const finished = new Promise((resolve) => {
      complete = resolve;
    });
    const nativeModule = available
      ? { pbkdf2: crypto.pbkdf2 }
      : new Proxy(
          {},
          {
            get() {
              throw Error('Native module unavailable');
            },
          },
        );
    const load = loader({
      mocks: {
        'react-native-quick-crypto': nativeModule,
        'expo-file-system': {
          cacheDirectory: 'file:///cache/',
          writeAsStringAsync: async (uri, text) => {
            const value = JSON.parse(text);
            writes.push({ uri, value });
            if (value.state !== 'pending') complete();
          },
        },
        'expo-modules-core': {
          uuid: { v4: () => '12345678-1234-1234-1234-123456789abc' },
        },
        '../api/client': { getAuthToken: () => null },
      },
    });
    load('telegram/native.ts');
    await finished;
    assert.equal(writes[0].value.state, 'pending');
    assert.equal(
      writes.at(-1).value.state,
      available ? 'passed' : 'unavailable',
    );
    for (const entry of writes) {
      assert.equal(entry.uri, 'file:///cache/nabd-native-crypto-proof.json');
      assert.deepEqual(Object.keys(entry.value).sort(), ['reason', 'state']);
    }
  }
});
