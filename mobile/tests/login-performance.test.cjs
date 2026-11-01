const { test } = require('node:test');
const assert = require('node:assert/strict');
const { pbkdf2Sync } = require('node:crypto');
const load = require('./helpers/load-source.cjs');

test('PBKDF scheduler excludes slow native timer waits from the next CPU budget', async () => {
  for (const delay of [16, 100]) {
    let clock = 0,
      yields = 0;
    const seen = [];
    const utils = load({
      globals: {
        Date: { now: () => clock++ },
        setTimeout: (callback) => {
          yields++;
          clock += delay;
          queueMicrotask(callback);
          return 0;
        },
      },
    })('telegram/vendor/utils.js');
    await utils.asyncLoop(200, 10, (i) => seen.push(i));
    assert.equal(seen.length, 200);
    assert.equal(new Set(seen).size, 200);
    // Before the fix, a 16ms+ native timer caused ~191 yields instead of 20.
    assert.ok(
      yields >= 18 && yields <= 22,
      `${delay}ms timers must not cause per-iteration yields: ${yields}`,
    );
  }
});
test('scheduler still yields, tolerates backward clocks, and never skips work', async () => {
  let clock = 0,
    yields = 0,
    work = 0;
  const utils = load({
    globals: {
      Date: { now: () => clock++ },
      setTimeout: (callback) => {
        yields++;
        clock += 16;
        queueMicrotask(callback);
        return 0;
      },
    },
  })('telegram/vendor/utils.js');
  await utils.asyncLoop(200, 10, (i) => {
    work++;
    if (i === 50) clock -= 500;
  });
  assert.equal(work, 200);
  assert.ok(yields > 0 && yields < 30);
});
test('full-strength PBKDF2 stays byte-compatible with a real 16ms timer scheduler', async () => {
  let yields = 0;
  const p = load({
    globals: {
      setTimeout: (callback, ms) => {
        yields++;
        return setTimeout(callback, Math.max(16, ms));
      },
    },
  })('telegram/password.ts');
  const password = 'كلمة مرور 🌍',
    salt = '1234567890abcdef1234567890abcdef';
  const result = await p.passwordDigest(password, salt);
  assert.equal(p.PASSWORD_ROUNDS, 210000);
  assert.equal(
    result,
    pbkdf2Sync(
      password,
      Buffer.from(salt, 'hex'),
      210000,
      32,
      'sha256',
    ).toString('hex'),
  );
  assert.ok(
    yields > 0 && yields < 10000,
    'must yield without a near-per-round timer storm',
  );
});
test('immutable document reads coalesce but mutations never contaminate cached snapshots', async () => {
  const { createImmutableReader } = load()('telegram/immutableReader.ts');
  let calls = 0;
  const read = createImmutableReader(async (id) => {
    calls++;
    return { id, users: { a: { sessions: { token: 1 } } } };
  }, 10000);
  const [a, b] = await Promise.all([read('file-a'), read('file-a')]);
  assert.equal(calls, 1);
  a.users.a.sessions.token = 9;
  assert.equal(b.users.a.sessions.token, 1);
  assert.equal((await read('file-a')).users.a.sessions.token, 1);
  assert.equal(calls, 1);
  await read('file-b');
  assert.equal(calls, 2);
  await read('file-a');
  assert.equal(calls, 3, 'only the last document is retained');
});
test('immutable reader never caches failed or oversized reads and isolates provider instances', async () => {
  const { createImmutableReader } = load()('telegram/immutableReader.ts');
  let calls = 0;
  const read = createImmutableReader(async () => {
    calls++;
    if (calls === 1) throw Error('network');
    return { ok: true };
  }, 100);
  await assert.rejects(read('id'), /network/);
  assert.equal((await read('id')).ok, true);
  assert.equal(calls, 2);
  const other = createImmutableReader(async () => ({ other: true }), 100);
  assert.equal((await other('id')).other, true);
  const large = createImmutableReader(
    async () => ({ text: 'ا'.repeat(100) }),
    150,
  );
  await assert.rejects(large('id'), /VAULT_FULL/);
});

test('supplied character follows real password focus/visibility without fake authentication or timers', () => {
  const renderUi = require('./helpers/render-ui.cjs');
  const { nodes } = renderUi;
  const render = renderUi('screens/AuthScreen.tsx', {
    initialState: { 3: 'test-password' },
    mocks: {
      'expo-image-picker': {},
      'react-native-safe-area-context': {
        useSafeAreaInsets: () => ({ top: 0, bottom: 0 }),
      },
      '../store/useAuthStore': {
        useAuthStore: () => ({
          loading: false,
          error: null,
          login: async () => false,
          register: async () => false,
          clearError: () => {},
        }),
      },
    },
  });
  let tree = render();
  const mascot = () => nodes(tree).find((x) => x.type?.name === 'AuthMascot');
  const password = () =>
    nodes(tree).find((x) => x.props?.label === 'كلمة المرور');
  assert.equal(mascot().props.covered, false);
  password().props.onFocus();
  tree = render();
  assert.equal(mascot().props.covered, true);
  const toggle = nodes(password().props.trailing).find(
    (x) => x.props?.accessibilityLabel === 'إظهار كلمة المرور',
  );
  assert.ok(toggle);
  toggle.props.onPress();
  tree = render();
  assert.equal(password().props.secureTextEntry, false);
  assert.equal(mascot().props.covered, false);
  password().props.onBlur();
  tree = render();
  assert.equal(mascot().props.covered, false);
  render.unmount();
  const character = load({
    mocks: {
      react: {
        useEffect: () => {
          throw Error('no idle timers');
        },
      },
      'react-native': { StyleSheet: { create: (x) => x }, View: 'View' },
    },
  })('components/AuthMascot.tsx').default({ covered: true });
  assert.equal(
    character.props.importantForAccessibility,
    'no-hide-descendants',
  );
});

test('existing WebCrypto fast path stays compatible and never falls back to JS timers', async () => {
  const { webcrypto } = require('node:crypto');
  const p = load({
    globals: {
      crypto: webcrypto,
      setTimeout: () => {
        throw Error('WebCrypto should not invoke fallback timers');
      },
    },
  })('telegram/password.ts');
  const salt = '1234567890abcdef1234567890abcdef',
    password = 'عربية 🌍';
  assert.equal(
    await p.passwordDigest(password, salt),
    pbkdf2Sync(
      password,
      Buffer.from(salt, 'hex'),
      210000,
      32,
      'sha256',
    ).toString('hex'),
  );
});
