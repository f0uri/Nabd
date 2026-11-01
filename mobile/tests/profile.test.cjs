const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function load(file, imports = {}, globals = {}) {
  const compiled = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src', file), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, esModuleInterop: true },
  }).outputText;
  const context = { exports: {}, require: (name) => name in imports ? imports[name] : require(name), process: { env: {} }, console, setTimeout, clearTimeout, AbortController, ...globals };
  vm.runInNewContext(compiled, context, { filename: file });
  return context.exports;
}
class NativeFormData {
  constructor() { this.values = {}; }
  append(key, value) { this.values[key] = value; }
}
const author = { id: 'account-test', name: 'اسم مكرر', initial: 'ا', color: '#123456', avatarUrl: '/media/avatar-test', username: 'test_user' };
function client({ supported = true, handler = () => ({}), failProbe = false } = {}) {
  const calls = [];
  let probes = 0;
  const result = load('api/client.ts', {}, {
    process: { env: { EXPO_PUBLIC_API_URL: 'https://nabd.example.test/graphql' } },
    FormData: NativeFormData,
    fetch: async (_url, options) => {
      const payload = options.body instanceof NativeFormData ? JSON.parse(options.body.values.operations) : JSON.parse(options.body);
      calls.push(payload);
      if (payload.query.includes('query UsernameCapability')) {
        probes++;
        if (failProbe && probes === 1) throw new Error('temporary outage');
        return { ok: true, json: async () => ({ data: { author: { fields: supported ? [{ name: 'username' }] : [{ name: 'id' }] }, mutation: { fields: [{ name: 'updateProfile', args: supported ? [{ name: 'username' }] : [] }] } } }) };
      }
      assert.ok(!payload.query.includes('__NABD_USERNAME__'), 'Internal field markers must not reach the server');
      if (!supported) assert.ok(!/\busername\b/.test(payload.query), 'Do not send new fields to legacy servers');
      return { ok: true, json: async () => ({ data: await handler(payload, options) }) };
    },
  });
  return { ...result, calls, probes: () => probes };
}

test('login and hydration queries normalize relative avatar URLs consistently', async () => {
  const c = client({ handler: ({ query }) => query.includes('mutation Login') ? { login: { token: 'synthetic-token', user: author } } : { me: author } });
  assert.equal((await c.api.me()).avatarUrl, 'https://nabd.example.test/media/avatar-test');
  assert.equal((await c.api.login('test@example.test', 'test-password')).user.avatarUrl, 'https://nabd.example.test/media/avatar-test');
});
test('null profiles and already absolute avatar URLs are preserved', async () => {
  const c = client({ handler: () => ({ me: null }) });
  assert.equal(await c.api.me(), null);
  assert.equal(c.normalizeAuthor({ ...author, avatarUrl: 'https://cdn.example.test/avatar.png' }).avatarUrl, 'https://cdn.example.test/avatar.png');
});
test('legacy servers still support avatar refresh and multipart profile saves', async () => {
  const { username: _unused, ...legacy } = author;
  const c = client({ supported: false, handler: ({ query }) => query.includes('mutation UpdateProfile') ? { updateProfile: legacy } : { me: legacy } });
  assert.equal((await c.api.me()).avatarUrl, 'https://nabd.example.test/media/avatar-test');
  const saved = await c.api.updateProfile({ file: { uri: 'file:///test-avatar.jpg', name: 'avatar.jpg', type: 'image/jpeg' } });
  assert.equal(saved.avatarUrl, 'https://nabd.example.test/media/avatar-test');
  assert.equal(saved.username, undefined, 'Never invent a globally unique handle on a legacy server');
  assert.equal(c.probes(), 1);
});
test('legacy servers reject username changes clearly without sending a mutation', async () => {
  const c = client({ supported: false });
  await assert.rejects(c.api.updateProfile({ username: 'new_user' }), (e) => e.code === 'USERNAME_UNAVAILABLE');
  assert.equal(c.calls.length, 1);
});
test('upgraded servers receive username changes and real multipart upload metadata', async () => {
  const c = client({ handler: (payload, options) => {
    assert.equal(payload.variables.username, 'new_user');
    assert.equal(payload.variables.file, null);
    assert.equal(options.body.values['0'].uri, 'file:///test.jpg');
    assert.equal(options.headers['Content-Type'], undefined, 'Native fetch must create the multipart boundary');
    return { updateProfile: { ...author, username: 'new_user' } };
  } });
  const saved = await c.api.updateProfile({ username: 'new_user', file: { uri: 'file:///test.jpg', name: 'test.jpg', type: 'image/jpeg' } });
  assert.equal(saved.username, 'new_user');
  assert.equal(saved.avatarUrl, 'https://nabd.example.test/media/avatar-test');
});
test('registration and user lists normalize avatars and retain handles', async () => {
  const c = client({ handler: ({ query }) => query.includes('mutation Register') ? { register: { token: 'synthetic-token', user: author } } : { users: [author] } });
  assert.equal((await c.api.register('Name', 'test@example.test', 'test-password')).user.avatarUrl, 'https://nabd.example.test/media/avatar-test');
  const [u] = await c.api.users('@test_user');
  assert.equal(u.username, 'test_user');
  assert.equal(u.avatarUrl, 'https://nabd.example.test/media/avatar-test');
});
test('simultaneous requests share one capability check', async () => {
  const c = client({ handler: () => ({ me: author }) });
  await Promise.all([c.api.me(), c.api.me(), c.api.me()]);
  assert.equal(c.probes(), 1);
});
test('transient capability-probe outages do not permanently hide usernames', async () => {
  const c = client({ failProbe: true, handler: () => ({ me: author }) });
  await c.api.me(); await c.api.me();
  assert.equal(c.probes(), 2);
  assert.match(c.calls.at(-1).query, /\busername\b/);
});

function dataLayer(mode, apiOverrides = {}) {
  const c = client();
  const mockCalls = [];
  const realCalls = [];
  const api = {
    ping: async () => { throw new Error('offline'); },
    me: async () => { realCalls.push('me'); return author; },
    updateProfile: async () => { realCalls.push('updateProfile'); return author; },
    login: async () => { realCalls.push('login'); throw new c.ApiError('NETWORK_ERROR'); },
    register: async () => { realCalls.push('register'); throw new c.ApiError('NETWORK_ERROR'); },
    ...apiOverrides,
  };
  const mockApi = new Proxy({}, { get: (_obj, name) => async () => { mockCalls.push(name); return { posts: [] }; } });
  const layer = load('api/index.ts', { './client': { api, ApiError: c.ApiError }, './mockApi': { mockApi } }, { process: { env: { EXPO_PUBLIC_DATA_SOURCE: mode } } });
  return { ...layer, mockCalls, realCalls, ApiError: c.ApiError };
}
test('feed fallback cannot overwrite real profiles or turn saves into mock successes', async () => {
  const d = dataLayer('auto');
  await d.dataApi.fetchFeed();
  assert.equal(d.getSource(), 'mock');
  assert.equal((await d.dataApi.me()).id, author.id);
  await d.dataApi.updateProfile({ file: { uri: 'file:///test.jpg' } });
  assert.deepEqual(d.realCalls, ['me', 'updateProfile']);
  assert.deepEqual(d.mockCalls, ['fetchFeed']);
});
test('unreachable auto-mode authentication never fabricates an account', async () => {
  const d = dataLayer('auto');
  await assert.rejects(d.dataApi.login('test@example.test', 'test-password'), (e) => e.code === 'NETWORK_ERROR');
  await assert.rejects(d.dataApi.register('Test', 'test@example.test', 'test-password'), (e) => e.code === 'NETWORK_ERROR');
  assert.equal(d.mockCalls.length, 0);
});
test('mock mode refuses profile writes, fake avatar registration and profile refresh', async () => {
  const d = dataLayer('mock');
  for (const run of [() => d.dataApi.me(), () => d.dataApi.updateProfile({ name: 'New' }), () => d.dataApi.register('Test', 'test@example.test', 'test-password', { uri: 'file:///test.jpg' })]) {
    await assert.rejects(run(), (e) => e.code === 'PROFILE_REQUIRES_SERVER');
  }
  assert.equal(d.mockCalls.length, 0);
});

function authStore(dataApi, initial = author) {
  const c = client();
  const storage = new Map([['nabd.token', 'synthetic-token'], ['nabd.user', JSON.stringify(initial)]]);
  const store = load('store/useAuthStore.ts', {
    '../api': { dataApi }, '../api/client': c,
    '@react-native-async-storage/async-storage': {
      multiGet: async (keys) => keys.map((key) => [key, storage.get(key) ?? null]),
      multiSet: async (entries) => entries.forEach(([key, value]) => storage.set(key, value)),
      setItem: async (key, value) => storage.set(key, value),
      multiRemove: async (keys) => keys.forEach((key) => storage.delete(key)),
    },
  });
  return { store: store.useAuthStore, storage };
}
test('offline hydration preserves and normalizes the cached profile instead of replacing it', async () => {
  const { store } = authStore({ me: async () => { throw new Error('offline'); } });
  await store.getState().hydrate();
  assert.equal(store.getState().user.avatarUrl, 'https://nabd.example.test/media/avatar-test');
  assert.equal(store.getState().user.username, 'test_user');
});
test('successful avatar saves persist through another hydration', async () => {
  const fresh = { ...author, avatarUrl: 'https://nabd.example.test/media/new-avatar', username: 'saved_user' };
  const { store, storage } = authStore({ me: async () => fresh, updateProfile: async () => fresh });
  await store.getState().hydrate();
  assert.equal(await store.getState().updateProfile({ username: 'saved_user' }), true);
  assert.equal(JSON.parse(storage.get('nabd.user')).avatarUrl, fresh.avatarUrl);
  await store.getState().hydrate();
  assert.equal(store.getState().user.avatarUrl, fresh.avatarUrl);
});
test('failed profile saves keep the previous cached avatar and expose a useful Arabic error', async () => {
  const { store, storage } = authStore({ me: async () => author, updateProfile: async () => { throw new Error('USERNAME_TAKEN'); } });
  await store.getState().hydrate();
  assert.equal(await store.getState().updateProfile({ username: 'taken' }), false);
  assert.match(store.getState().error, /مستعمل/);
  assert.equal(JSON.parse(storage.get('nabd.user')).avatarUrl, author.avatarUrl);
});

for (const [name, code] of [['AbortError', 'TIMEOUT'], ['Error', 'NETWORK_ERROR']]) {
  test(`failed avatar uploads report ${code} instead of success`, async () => {
    const c = client({ handler: () => { const error = new Error('synthetic failure'); error.name = name; throw error; } });
    await assert.rejects(c.api.updateProfile({ file: { uri: 'file:///test.jpg', name: 'test.jpg', type: 'image/jpeg' } }), (error) => error.code === code);
  });
}
