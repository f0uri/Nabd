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

test('Telegram connects read-only automatically without a warning or consent wall', async () => {
  const renderUi = require('./helpers/render-ui.cjs');
  const remote = deferredAuth(); let reads=0, writes=0;
  const render = renderUi('telegram/TelegramGate.tsx',{mocks:{
    './config':{isTelegramDirect:true,telegramConfigured:true},
    './native':{telegramApi:{status:()=>{reads++;return remote.promise;},initialize:async()=>{writes++;}}},
  }});
  const pending=render({children:'main content'});
  assert.equal(reads,1); assert.equal(writes,0);
  assert.ok(!renderUi.text(pending).includes('main content'));
  assert.ok(!/تلغرام|المخاطر|Telegram/.test(renderUi.text(pending)));
  remote.resolve(); await new Promise(setImmediate);
  assert.equal(renderUi.text(render({children:'main content'})),'main content');
  assert.equal(reads,1); assert.equal(writes,0);
});
test('a missing Telegram vault requires separate confirmation before creation', async () => {
  const renderUi = require('./helpers/render-ui.cjs');
  let initialized=false, writes=0;
  const render=renderUi('telegram/TelegramGate.tsx',{mocks:{
    './config':{isTelegramDirect:true,telegramConfigured:true},
    './native':{telegramApi:{status:async()=>{if(!initialized)throw new Error('TELEGRAM_VAULT_MISSING');},initialize:async()=>{writes++;initialized=true;}}},
  }});
  render(); await new Promise(setImmediate); let screen=render();
  const press=(text)=>renderUi.nodes(screen).find(node=>node.type==='Pressable'&&renderUi.text(node)===text).props.onPress();
  assert.equal(writes,0); press('إنشاء مساحة جديدة'); screen=render(); assert.equal(writes,0);
  assert.match(renderUi.text(screen),/لا يستعيد أي حسابات سابقة/);
  press('إلغاء'); screen=render(); assert.equal(writes,0);
  press('إنشاء مساحة جديدة'); screen=render(); press('تأكيد الإنشاء'); press('تأكيد الإنشاء');
  await new Promise(setImmediate); assert.equal(writes,1);
  assert.equal(renderUi.text(render({children:'main content'})),'main content');
});
test('connection errors remain actionable and never create or expose account data', async () => {
  const renderUi=require('./helpers/render-ui.cjs'); let writes=0;
  const render=renderUi('telegram/TelegramGate.tsx',{mocks:{
    './config':{isTelegramDirect:true,telegramConfigured:true},
    './native':{telegramApi:{status:async()=>{throw new Error('TELEGRAM_INVALID_VAULT');},initialize:async()=>{writes++;}}},
  }});
  render(); await new Promise(setImmediate); const screen=render({children:'main content'});
  assert.equal(writes,0); assert.ok(!renderUi.text(screen).includes('main content'));
  assert.match(renderUi.text(screen),/تعذّر الاتصال/);
  assert.ok(!renderUi.text(screen).includes('إنشاء مساحة جديدة'));
  assert.ok(renderUi.nodes(screen).some(node=>node.type==='Pressable'&&renderUi.text(node)==='إعادة المحاولة'));
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
test('compact green update pill delegates download and automatic restart to one install action', () => {
  const renderUi=require('./helpers/render-ui.cjs'); let installs=0,retries=0;
  const update={phase:'available',visible:true,enabled:false,install:()=>{installs++;},check:()=>{retries++;},dismiss:()=>{},diagnostics:{runtimeVersion:'a'.repeat(40),channel:'production',dataSource:'telegram'}};
  const render=renderUi('components/UpdateNotice.tsx',{mocks:{'../store/useUpdateStore':{useUpdateStore:()=>update}}});
  let screen=render();
  const action=()=>renderUi.nodes(screen).find(node=>node.type==='Pressable'&&node.props.accessibilityState);
  assert.equal(renderUi.text(action()).trim(),'تحديث التطبيق');
  const pill=renderUi.nodes(screen).find(node=>node.props?.style?.maxWidth===320);
  assert.equal(pill.props.style.backgroundColor,'rgba(229,249,237,0.97)');
  assert.ok(pill.props.style.borderRadius>=160);
  assert.equal(action().props.accessibilityLabel,'تحديث التطبيق وإعادة تشغيله تلقائياً');
  assert.ok(!/Runtime|Channel|تفاصيل|متوافق مع نسختك|إعادة تشغيل/.test(renderUi.text(screen)));
  assert.ok(!renderUi.nodes(screen).some(node=>node.type==='Modal'));
  assert.equal(installs,0); action().props.onPress(); assert.equal(installs,1);
  update.phase='downloading';screen=render();assert.equal(action().props.disabled,true);
  update.phase='applying';screen=render();assert.equal(action().props.disabled,true);
  update.phase='ready';screen=render();action().props.onPress();assert.equal(installs,2);
  update.phase='error';update.errorStep='download';screen=render();action().props.onPress();assert.equal(installs,3);
  update.errorStep='check';screen=render();action().props.onPress();assert.equal(retries,1);
  update.visible=false;assert.equal(render(),null);
});
test('glass theme matches design tokens without introducing a native blur dependency',()=>{
  const theme=require('./helpers/load-source.cjs')({mocks:{'react-native':{Platform:{select:v=>v.android,OS:'android'}}}})('theme/index.ts');
  const tokens=JSON.parse(fs.readFileSync(path.join(__dirname,'../../design/tokens.json'),'utf8'));
  for(const [name,value] of Object.entries(theme.glass)) assert.equal(tokens.color.glass[name].value,value);
  for(const key of ['sm','md','lg']) assert.equal(tokens.radius[key].value,theme.radius[key]);
  for(const key of ['appBar','tabBar']) assert.equal(tokens.size[key].value,theme.size[key]);
  assert.equal(tokens.color.bg.canvas.value,theme.colors.bg.canvas);
  const deps=JSON.parse(fs.readFileSync(path.join(__dirname,'../package.json'),'utf8')).dependencies;
  assert.ok(!Object.keys(deps).some(name=>/blur|liquid-glass/.test(name)));
});
test('floating glass dock keeps six accessible destinations, unread badge and full-size touch targets',()=>{
  const renderUi=require('./helpers/render-ui.cjs');let chosen;
  const screen=renderUi('components/TabBar.tsx',{mocks:{
    '@expo/vector-icons':{Ionicons:'Icon',MaterialCommunityIcons:'Icon'},
    '../store/useFeedStore':{useFeedStore:select=>select({unreadNotifications:7})},
    '../store/useAuthStore':{useAuthStore:select=>select({user:null})},
  }})({active:'reels',onChange:key=>{chosen=key;}});
  const tabs=renderUi.nodes(screen).filter(node=>node.props?.accessibilityRole==='tab');
  assert.equal(tabs.length,6);assert.equal(tabs.filter(tab=>tab.props.accessibilityState.selected).length,1);
  for(const tab of tabs){assert.ok(tab.props.accessibilityLabel);assert.ok(tab.props.style({pressed:false})[0].minHeight>=44);}
  tabs[1].props.onPress();assert.equal(chosen,'reels');tabs[5].props.onPress();assert.equal(chosen,'profile');
  assert.ok(renderUi.nodes(screen).some(node=>node.type==='Text'&&node.props.children===7));
});

test('interactive outlines, text and icon colors retain measurable contrast on light surfaces',()=>{
  const theme=require('./helpers/load-source.cjs')({mocks:{'react-native':{Platform:{select:v=>v.android,OS:'android'}}}})('theme/index.ts');
  const tokens=JSON.parse(fs.readFileSync(path.join(__dirname,'../../design/tokens.json'),'utf8'));
  const luminance=hex=>{const c=hex.slice(1).match(/../g).map(v=>parseInt(v,16)/255).map(v=>v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4);return c[0]*0.2126+c[1]*0.7152+c[2]*0.0722;};
  const ratio=(a,b)=>{const x=luminance(a),y=luminance(b);return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);};
  for(const [key,value] of Object.entries(theme.control))assert.equal(tokens.color.control[key].value,value);
  for(const bg of [theme.colors.bg.surface,theme.colors.bg.canvas,theme.control.surface,theme.control.selected]){
    assert.ok(ratio(theme.control.icon,bg)>=7,'recognizable icon foreground');
    assert.ok(ratio(theme.control.border,bg)>=3,'visible interactive boundary');
    for(const role of ['primary','secondary','tertiary'])assert.ok(ratio(theme.colors.text[role],bg)>=4.5,`${role} text on ${bg}`);
  }
  assert.ok(ratio(theme.colors.text.inverse,theme.colors.brand.primary)>=4.5,'primary action text');
  assert.ok(ratio(theme.control.selectedIcon,theme.control.selected)>=4.5,'selected destination label');
  assert.equal(tokens.color.brand.primary.value,theme.colors.brand.primary);
  assert.equal(tokens.color.text.tertiary.value,theme.colors.text.tertiary);
  const renderUi=require('./helpers/render-ui.cjs');
  for(const [kind,ink] of Object.entries(theme.reactionInk)){
    assert.equal(tokens.color.reactionInk[kind].value,ink);
    assert.ok(ratio(ink,theme.colors.bg.surface)>=3,`${kind} standalone glyph`);
    const onBadge=kind==='like'||kind==='love'?theme.colors.text.inverse:ink;
    assert.ok(ratio(onBadge,theme.colors.reaction[kind])>=3,`${kind} badge glyph`);
    const render=renderUi('components/atoms/ReactionIcon.tsx');
    assert.equal(render({kind,size:24}).props.color,ink);
    assert.ok(renderUi.nodes(render({kind,badge:true,size:24})).some(n=>n.type==='Icon'&&n.props.color===onBadge));
  }
});
test('icon font gate waits for both bundled families and does not re-load on re-render',async()=>{
  const renderUi=require('./helpers/render-ui.cjs');let finish,calls=0;
  const render=renderUi('components/IconFontGate.tsx',{mocks:{
    '@expo/vector-icons':{Ionicons:{font:{ionicons:41}},MaterialCommunityIcons:{font:{'material-community':42}}},
    'expo-font':{loadAsync:fonts=>{calls++;assert.equal(fonts.ionicons,41);assert.equal(fonts['material-community'],42);return new Promise(resolve=>{finish=resolve;});}},
  }});
  assert.ok(!renderUi.text(render({children:'account content'})).includes('account content'));
  await new Promise(setImmediate);assert.equal(calls,1);render();assert.equal(calls,1);
  finish();await new Promise(setImmediate);const ready=render({children:'account content'});
  assert.equal(ready.props.nativeID,'nabd-icons-ready-v2');assert.equal(renderUi.text(ready),'account content');
});
test('font failures have a text-only retry and late resolution is ignored after unmount',async()=>{
  const renderUi=require('./helpers/render-ui.cjs');let calls=0;
  const render=renderUi('components/IconFontGate.tsx',{mocks:{
    'expo-font':{loadAsync:()=>{calls++;if(calls===1)throw Error('synthetic font failure');return Promise.resolve();}},
  }});
  render();await new Promise(setImmediate);let screen=render({children:'content'});
  assert.match(renderUi.text(screen),/تعذّر تحميل الأيقونات/);
  const retry=renderUi.nodes(screen).find(n=>n.type==='Pressable');assert.equal(retry.props.accessibilityLabel,'إعادة تحميل الأيقونات');
  retry.props.onPress();render();await new Promise(setImmediate);assert.equal(renderUi.text(render({children:'content'})),'content');assert.equal(calls,2);
  let finish;
  const pending=renderUi('components/IconFontGate.tsx',{mocks:{'expo-font':{loadAsync:()=>new Promise(r=>{finish=r;})}}});
  pending();await new Promise(setImmediate);pending.unmount();finish();await new Promise(setImmediate);
  assert.ok(!renderUi.text(pending({children:'late content'})).includes('late content'));
});
test('icon names used directly and in dock destinations exist in the shipped glyph maps',()=>{
  const dir=path.join(__dirname,'../node_modules/@expo/vector-icons/build/vendor/react-native-vector-icons');
  const maps={Ionicons:JSON.parse(fs.readFileSync(path.join(dir,'glyphmaps/Ionicons.json'))),MaterialCommunityIcons:JSON.parse(fs.readFileSync(path.join(dir,'glyphmaps/MaterialCommunityIcons.json')))};
  for(const family of Object.keys(maps))assert.ok(fs.statSync(path.join(dir,`Fonts/${family}.ttf`)).size>1000);
  let checked=0;
  const values=n=>!n?[]:ts.isJsxExpression(n)?values(n.expression):ts.isStringLiteral(n)?[n.text]:ts.isConditionalExpression(n)?[...values(n.whenTrue),...values(n.whenFalse)]:[];
  const sources=fs.readdirSync(path.join(__dirname,'../src'),{recursive:true}).filter(f=>f.endsWith('.tsx')).map(f=>path.join(__dirname,'../src',f));sources.push(path.join(__dirname,'../App.tsx'));
  for(const file of sources){
    const text=fs.readFileSync(file,'utf8'),tree=ts.createSourceFile(file,text,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
    const check=(name,family)=>{checked++;assert.ok(Object.hasOwn(maps[family],name),`${family}/${name} in ${path.basename(file)}`);};
    const walk=node=>{
      if((ts.isJsxOpeningElement(node)||ts.isJsxSelfClosingElement(node))&&maps[node.tagName.getText(tree)]){
        const name=node.attributes.properties.find(a=>a.name?.getText(tree)==='name');for(const value of values(name?.initializer))check(value,node.tagName.getText(tree));
      }
      if(file.endsWith('TabBar.tsx')&&ts.isPropertyAssignment(node)&&['icon','activeIcon'].includes(node.name.getText(tree)))for(const value of values(node.initializer))check(value,'Ionicons');
      ts.forEachChild(node,walk);
    };walk(tree);
  }
  assert.ok(checked>90);
  assert.match(fs.readFileSync(path.join(__dirname,'../App.tsx'),'utf8'),/<IconFontGate>[\s\S]*<TelegramGate>[\s\S]*<UpdateNotice\s*\/>[\s\S]*<\/IconFontGate>/);
});
test('header actions cannot shrink away and primary compose has a distinct visible treatment',()=>{
  const renderUi=require('./helpers/render-ui.cjs');
  const render=renderUi('components/AppBar.tsx',{mocks:{'../store/useChatStore':{useChatStore:s=>s({unread:0,openChat:()=>{}})},'../store/useUiStore':{useUiStore:s=>s({setTab:()=>{}})}}});
  const nodes=renderUi.nodes(render({onCompose:()=>{}}));
  assert.ok(nodes.some(n=>n.type==='View'&&n.props.style?.flexShrink===0));
  const title=nodes.find(n=>n.type==='Text'&&n.props.accessibilityRole==='header');assert.equal(title.props.numberOfLines,1);assert.equal(title.props.style.flexShrink,1);
  const compose=nodes.find(n=>n.props?.label==='إنشاء منشور');assert.equal(compose.props.variant,'primary');
  const button=renderUi('components/atoms/IconButton.tsx')({name:'add',label:'إنشاء منشور',variant:'primary',onPress:()=>{}});
  const styles=button.props.style({pressed:false});assert.ok(styles[0].width>=44&&styles[0].height>=44);
});

test('profile cover shortcuts use readable shared icon controls rather than white glyphs on a pale cover',()=>{
  const text=fs.readFileSync(path.join(__dirname,'../src/screens/ProfileScreen.tsx'),'utf8');
  const tree=ts.createSourceFile('ProfileScreen.tsx',text,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
  const found=[];
  const walk=node=>{
    if(ts.isJsxSelfClosingElement(node)&&node.tagName.getText(tree)==='IconButton'){
      const props=Object.fromEntries(node.attributes.properties.filter(a=>ts.isJsxAttribute(a)).map(a=>[a.name.getText(tree),a.initializer]));
      if(['القائمة','تعديل الملف','البحث','خيارات الملف'].includes(props.label?.text)){
        assert.equal(props.variant.text,'plain');assert.ok(props.onPress);found.push(props.label.text);
      }
    }
    ts.forEachChild(node,walk);
  };walk(tree);assert.equal(found.length,4);
});
test('large system text leaves more clearance between the update pill and the dock',()=>{
  const renderUi=require('./helpers/render-ui.cjs');const state={phase:'available',enabled:false,visible:true,install:()=>{},check:()=>{},dismiss:()=>{}};
  const screen=scale=>renderUi('components/UpdateNotice.tsx',{windowDimensions:{fontScale:scale},mocks:{'../store/useUpdateStore':{useUpdateStore:()=>state}}})();
  assert.equal(screen(3).props.style[1].bottom-screen(1).props.style[1].bottom,32);
});
test('reaction picker follows measured action height and retains comment/share callbacks',()=>{
  const renderUi=require('./helpers/render-ui.cjs');let comments=0,shares=0;
  const render=renderUi('components/molecules/PostActions.tsx',{initialState:{0:true}});
  const props={myReaction:null,onReact:()=>{},onComment:()=>{comments++;},onShare:()=>{shares++;}};
  let screen=render(props);const row=renderUi.nodes(screen).find(n=>n.props?.onLayout);
  row.props.onLayout({nativeEvent:{layout:{height:88}}});screen=render(props);
  assert.ok(renderUi.nodes(screen).some(n=>Array.isArray(n.props?.style)&&n.props.style[1]?.bottom===94));
  for(const label of ['تعليق','مشاركة'])renderUi.nodes(screen).find(n=>n.props?.accessibilityLabel===label).props.onPress();
  assert.equal(comments,1);assert.equal(shares,1);
});
