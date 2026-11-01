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
  const layer = load('api/index.ts', { './client': { api, ApiError: c.ApiError }, './mockApi': { mockApi }, '../telegram/config': { isTelegramDirect: false }, '../telegram/native': {} }, { process: { env: { EXPO_PUBLIC_DATA_SOURCE: mode } } });
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

function authStore(dataApi, initial = author, authStoragePrefix) {
  const c = client();
  const storage = new Map([['nabd.token', 'synthetic-token'], ['nabd.user', JSON.stringify(initial)]]);
  const store = load('store/useAuthStore.ts', {
    '../api': { dataApi, authStoragePrefix }, '../api/client': c,
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

test('Telegram hydration/logout never reads or removes existing GraphQL storage', async () => {
  let calls = 0;
  const { store, storage } = authStore({ me: async () => { calls++; return author; } }, author, 'nabd.telegram.example');
  await store.getState().hydrate();
  assert.equal(store.getState().user, null); assert.equal(calls, 0);
  await store.getState().logout();
  assert.equal(storage.get('nabd.token'), 'synthetic-token');
});
test('expired Telegram sessions clear only direct cache, without deleting old provider accounts', async () => {
  const { store, storage } = authStore({ me: async () => { throw new Error('TELEGRAM_SESSION_REQUIRED'); } }, author, 'nabd.telegram.example');
  storage.set('nabd.telegram.example.token', 'nabd-tg:synthetic'); storage.set('nabd.telegram.example.user', JSON.stringify(author));
  await store.getState().hydrate();
  assert.equal(store.getState().user, null); assert.equal(storage.has('nabd.telegram.example.token'), false);
  assert.equal(storage.has('nabd.token'), true);
});

// Authentication must not wait for a remote refresh when a saved session exists.
function authFixture({ cached = null, handlers = {} } = {}) {
  let authToken = null;
  const values = new Map(cached ? [['auth-test.token', 'saved-session'], ['auth-test.user', JSON.stringify(cached)]] : []);
  const storage = {
    multiGet: async (keys) => keys.map((key) => [key, values.get(key) ?? null]),
    multiSet: async (pairs) => { for (const [key, value] of pairs) values.set(key, value); },
    multiRemove: async (keys) => { for (const key of keys) values.delete(key); },
    setItem: async (key, value) => { values.set(key, value); },
  };
  const module = require('./helpers/load-source.cjs')({ mocks: {
    '@react-native-async-storage/async-storage': storage,
    '../api': { authStoragePrefix: 'auth-test', dataApi: { me: async () => cached, logout: async () => {}, ...handlers } },
    '../api/client': { normalizeAuthor: (value) => value, setAuthToken: (value) => { authToken = value; } },
  } })('store/useAuthStore.ts');
  return { store: module.useAuthStore, values, storage, token: () => authToken };
}
function deferredAuth() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
test('a cached session renders before its remote refresh finishes', async () => {
  const remote = deferredAuth();
  const f = authFixture({ cached: author, handlers: { me: () => remote.promise } });
  const pending = f.store.getState().hydrate();
  await new Promise(setImmediate);
  assert.equal(f.store.getState().hydrated, true);
  assert.equal(f.store.getState().user.id, author.id);
  remote.resolve({ ...author, name: 'اسم محدث' });
  await pending;
  assert.equal(f.store.getState().user.name, 'اسم محدث');
});
test('a late session refresh cannot restore a user after logout', async () => {
  const remote = deferredAuth();
  const f = authFixture({ cached: author, handlers: { me: () => remote.promise } });
  const pending = f.store.getState().hydrate();
  await new Promise(setImmediate);
  await f.store.getState().logout();
  remote.resolve(author);
  await pending;
  assert.equal(f.store.getState().user, null);
  assert.equal(f.token(), null);
  assert.equal(f.values.size, 0);
});
test('duplicate submissions start only one authentication operation', async () => {
  const remote = deferredAuth(); let calls = 0;
  const f = authFixture({ handlers: { login: () => { calls++; return remote.promise; } } });
  const first = f.store.getState().login('name@example.test', 'test-password');
  assert.equal(await f.store.getState().login('name@example.test', 'test-password'), false);
  assert.equal(await f.store.getState().register('اسم', 'name@example.test', 'test-password'), false);
  assert.equal(calls, 1);
  remote.resolve({ token: 'new-session', user: author });
  assert.equal(await first, true);
  assert.equal(f.store.getState().hydrated, true);
});
test('late login completion cannot undo logout', async () => {
  const remote = deferredAuth();
  const f = authFixture({ handlers: { login: () => remote.promise } });
  const pending = f.store.getState().login('name@example.test', 'test-password');
  await f.store.getState().logout();
  remote.resolve({ token: 'new-session', user: author });
  assert.equal(await pending, false);
  assert.equal(f.store.getState().user, null);
  assert.equal(f.values.size, 0);
});
test('offline refresh preserves the existing session without holding the splash screen', async () => {
  const f = authFixture({ cached: author, handlers: { me: async () => { throw new Error('NETWORK_ERROR'); } } });
  await f.store.getState().hydrate();
  assert.equal(f.store.getState().hydrated, true);
  assert.equal(f.store.getState().user.id, author.id);
});

test('failed session persistence does not leave a hidden authenticated transport', async () => {
  const f = authFixture({ handlers: { login: async () => ({token: 'new-session', user: author}) } });
  f.storage.multiSet = async () => { throw new Error('disk unavailable'); };
  assert.equal(await f.store.getState().login('name@example.test', 'test-password'), false);
  assert.equal(f.token(), null);
  assert.equal(f.store.getState().user, null);
  assert.match(f.store.getState().error, /حفظ الجلسة/);
});
test('a confirmed expired session clears the cached identity', async () => {
  const f = authFixture({ cached: author, handlers: { me: async () => null } });
  await f.store.getState().hydrate();
  assert.equal(f.store.getState().user, null);
  assert.equal(f.token(), null);
  assert.equal(f.values.size, 0);
});
test('auth forms submit valid values, block duplicate UI actions and remove developer copy', async () => {
  for (const registration of [false, true]) for (const busy of [false, true]) {
    const calls = []; let hook = 0;
    const states = [registration ? 'register' : 'login', 'علي حسن', registration ? 'ali@example.test' : '@ali_hsn', 'test-password', false, null, false, false];
    const load = require('./helpers/load-source.cjs')({ mocks: {
      react: {useState: (initial) => [hook < states.length ? states[hook++] : initial, () => {}], useEffect: () => {}},
      'react-native': {StyleSheet:{create:(value)=>value}, Platform:{OS:'android',select:(value)=>value.android??value.default}, View:'View',Text:'Text',TextInput:'TextInput',Pressable:'Pressable',ScrollView:'ScrollView',KeyboardAvoidingView:'KeyboardAvoidingView',ActivityIndicator:'Spinner',Image:'Image'},
      '@expo/vector-icons': {Ionicons:'Icon'},
      '../theme': require('./helpers/load-source.cjs')({mocks:{'react-native':{Platform:{OS:'android',select:(value)=>value.android??value.default}}}})('theme/index.ts'),
      'expo-image-picker': {},
      'react-native-safe-area-context': {useSafeAreaInsets:()=>({top:0,bottom:0})},
      '../store/useAuthStore': {useAuthStore:()=>({loading:busy,error:null,clearError:()=>{},login:async(...args)=>calls.push(['login',...args]),register:async(...args)=>calls.push(['register',...args])})},
    } });
    const root = load('screens/AuthScreen.tsx').default();
    const elements = [];
    const visit = (node) => { if(Array.isArray(node)) return node.forEach(visit); if(!node || typeof node !== 'object') return; elements.push(node); visit(node.props?.children); };
    visit(root);
    const submit = elements.find((node)=>node.props?.accessibilityState?.busy===busy);
    assert.ok(submit);
    assert.equal(submit.props.disabled,busy);
    await submit.props.onPress();
    if (busy) assert.equal(calls.length, 0);
    else assert.equal(calls[0][0],registration?'register':'login');
    const texts = elements.flatMap((node)=>typeof node.props?.children==='string'?[node.props.children]:[]).join(' ');
    assert.ok(!/حسابات تجريبية|الحسابات الحقيقية|React Native|GraphQL|فيسبوك/.test(texts));
  }
});
test('login and hydration cannot race a pending sign-out', async () => {
  const remote = deferredAuth(); let signins = 0, refreshes = 0;
  const f = authFixture({ handlers: {logout:()=>remote.promise,login:async()=>{signins++;},me:async()=>{refreshes++;}} });
  const pending = f.store.getState().logout();
  assert.equal(await f.store.getState().login('name@example.test','test-password'), false);
  await f.store.getState().hydrate();
  assert.equal(signins, 0); assert.equal(refreshes, 0);
  remote.resolve(); await pending;
  assert.equal(f.store.getState().loading, false);
});

test('connected Telegram content has no persistent banner while setup still requires consent', () => {
  const renderUi = require('./helpers/render-ui.cjs');
  const mocks = {
    './config': {isTelegramDirect:true,telegramConfigured:true},
    './native': {telegramApi:{}},
    '../store/useAuthStore': {authErrorMessage:()=>''},
  };
  const ready = renderUi('telegram/TelegramGate.tsx',{mocks,initialState:{1:true}});
  assert.equal(renderUi.text(ready({children:'main content'})), 'main content');
  const setup = renderUi('telegram/TelegramGate.tsx',{mocks});
  const screen = setup({children:'main content'});
  assert.ok(!renderUi.text(screen).includes('main content'));
  assert.match(renderUi.text(screen), /مفتاح البوت قابل للاستخراج/);
  const checkbox = renderUi.nodes(screen).find(node=>node.props?.accessibilityRole==='checkbox');
  assert.equal(checkbox.props.accessibilityState.checked,false);
  assert.ok(renderUi.nodes(screen).some(node=>node.type==='Pressable' && node.props.disabled===true));
});
test('header keeps its title and real shortcuts without an in-app brand image', () => {
  const renderUi = require('./helpers/render-ui.cjs');
  const screen = renderUi('components/AppBar.tsx',{mocks:{
    '../store/useChatStore':{useChatStore:(select)=>select({unread:0,openChat:()=>{}})},
    '../store/useUiStore':{useUiStore:(select)=>select({setTab:()=>{}})},
  }})({onCompose:()=>{}});
  const nodes = renderUi.nodes(screen);
  assert.match(renderUi.text(screen), /نبض/);
  assert.ok(!nodes.some(node=>node.type?.name==='BrandMark' || node.type==='Image'));
  for(const label of ['القائمة','بحث','الرسائل','إنشاء منشور']) assert.ok(nodes.some(node=>node.props?.label===label));
});
test('update diagnostics are opt-in, toggleable and collapsed again after closing', () => {
  const renderUi = require('./helpers/render-ui.cjs');
  let downloads=0;
  const update={phase:'available',visible:true,enabled:false,download:()=>{downloads++;},dismiss:()=>{},diagnostics:{runtimeVersion:'a'.repeat(40),channel:'production',dataSource:'telegram',embedded:true}};
  const render = renderUi('components/UpdateNotice.tsx',{mocks:{'../store/useUpdateStore':{useUpdateStore:()=>update}}});
  let screen=render();
  assert.ok(!renderUi.text(screen).includes('Runtime:'));
  const details=()=>renderUi.nodes(screen).find(node=>node.props?.accessibilityLabel==='تفاصيل تقنية');
  assert.equal(details().props.accessibilityState.expanded,false);
  details().props.onPress(); screen=render();
  assert.match(renderUi.text(screen),/Runtime:/);
  assert.equal(details().props.accessibilityState.expanded,true);
  details().props.onPress(); screen=render();
  assert.ok(!renderUi.text(screen).includes('Runtime:'));
  details().props.onPress(); screen=render();
  update.visible=false; render(); update.visible=true; screen=render();
  assert.equal(details().props.accessibilityState.expanded,false);
  const download=renderUi.nodes(screen).find(node=>node.type==='Pressable' && renderUi.text(node)==='تنزيل التحديث');
  download.props.onPress(); assert.equal(downloads,1);
  update.phase='error'; update.errorStep='download'; update.message='تعذّر تنزيل التحديث'; screen=render();
  assert.match(renderUi.text(screen), /تعذّر تنزيل التحديث/);
  const retry=renderUi.nodes(screen).find(node=>node.type==='Pressable' && renderUi.text(node)==='إعادة المحاولة');
  retry.props.onPress(); assert.equal(downloads,2);
});
