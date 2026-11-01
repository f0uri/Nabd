const { test } = require('node:test');
const assert = require('node:assert/strict');
const { pbkdf2Sync, createHash } = require('node:crypto');
const fs = require('node:fs');
const loadSource = require('./helpers/load-source.cjs');
const load = loadSource();
const { createTelegramApi } = load('telegram/createTelegramApi.ts');
const { passwordDigest, utf8, digest, PASSWORD_ROUNDS } = load(
  'telegram/password.ts',
);
const { createTransport, MAX_VAULT_BYTES } = load('telegram/transport.ts');
const namespace = 'abc123';
const photo = 'data:image/png;base64,aGVsbG8=';
const snapshot = (value) => JSON.parse(JSON.stringify(value));
function fixture() {
  let pin = null,
    counter = 0,
    sequence = 0;
  const messages = new Map();
  const events = [];
  const transport = {
    async chat() {
      events.push('chat');
      return {
        id: -123,
        type: 'channel',
        ...(pin ? { pinned_message: snapshot(messages.get(pin)) } : {}),
      };
    },
    async read(fileId) {
      events.push('read');
      return snapshot(messages.get(Number(fileId)).json);
    },
    async upload(json) {
      events.push('upload');
      const messageId = ++counter;
      messages.set(messageId, {
        message_id: messageId,
        caption: 'nabd-direct-telegram-v1',
        document: { file_id: String(messageId), file_name: 'nabd-vault.json' },
        json: JSON.parse(json),
      });
      return { messageId };
    },
    async pin(messageId) {
      events.push('pin');
      pin = messageId;
    },
  };
  function client({
    ns = namespace,
    time = Date.now,
    avatar = async () => photo,
  } = {}) {
    let token = null;
    return {
      api: createTelegramApi({
        namespace: ns,
        transport,
        getToken: () => token,
        random: () => (++sequence).toString(16).padStart(32, '0'),
        avatar,
        now: time,
      }),
      setToken: (value) => {
        token = value;
      },
    };
  }
  return {
    client,
    transport,
    events,
    messages,
    current: () => messages.get(pin)?.json,
    pin: (id) => {
      pin = id;
    },
  };
}
const register = async (client, email = 'a@example.test') => {
  const p = await client.api.register('اسم متكرر', email, 'test-password', {
    uri: 'file:///a.png',
    type: 'image/png',
    name: 'a.png',
  });
  client.setToken(p.token);
  return p;
};

test('UTF-8, SHA256, PBKDF2 match node crypto including Arabic and unpaired surrogates', async () => {
  for (const text of ['مرحبا 👋', '\ud800', '\udc00', 'ASCII', '']) {
    assert.equal(
      Buffer.from(utf8(text)).toString('hex'),
      Buffer.from(text).toString('hex'),
    );
    assert.equal(digest(text), createHash('sha256').update(text).digest('hex'));
  }
  const salt = '1234567890abcdef1234567890abcdef';
  assert.equal(
    await passwordDigest('كلمة مرور 🌍', salt),
    pbkdf2Sync(
      'كلمة مرور 🌍',
      Buffer.from(salt, 'hex'),
      PASSWORD_ROUNDS,
      32,
      'sha256',
    ).toString('hex'),
  );
  await assert.rejects(
    passwordDigest('a'.repeat(129), salt),
    /INVALID_CREDENTIALS/,
  );
});
test('missing vault never silently initializes; initialization is explicit and refuses occupied pin', async () => {
  const f = fixture();
  const c = f.client();
  await assert.rejects(c.api.status(), /TELEGRAM_VAULT_MISSING/);
  assert.ok(!f.events.includes('upload'));
  await c.api.initialize();
  assert.equal(f.current().app, 'nabd-direct-telegram');
  await assert.rejects(c.api.initialize(), /TELEGRAM_ALREADY_INITIALIZED/);
  f.messages.set(500, {
    message_id: 500,
    caption: 'foreign',
    document: { file_name: 'other.json', file_id: '500' },
  });
  f.pin(500);
  await assert.rejects(c.api.status(), /TELEGRAM_FOREIGN_PIN/);
  await assert.rejects(c.api.initialize(), /TELEGRAM_FOREIGN_PIN/);
});
test('accounts, avatar, username persist across fresh clients; password and raw sessions never appear in JSON', async () => {
  const f = fixture();
  const a = f.client();
  await a.api.initialize();
  const payload = await register(a);
  await a.api.updateProfile({
    name: 'اسم جديد',
    username: '@my_handle',
    bio: 'نبض',
    file: { uri: 'file:///new.png' },
  });
  const b = f.client();
  const login = await b.api.login(' A@EXAMPLE.TEST ', 'test-password');
  b.setToken(login.token);
  assert.equal((await b.api.me()).avatarUrl, photo);
  assert.equal((await b.api.me()).username, 'my_handle');
  assert.equal((await b.api.me()).name, 'اسم جديد');
  const json = JSON.stringify(f.current());
  assert.ok(!json.includes('test-password'));
  assert.ok(!json.includes(payload.token));
  assert.ok(!json.includes(payload.token.split(':')[3]));
  const user = Object.values(f.current().users)[0];
  assert.match(user.passwordHash, /^[a-f0-9]{64}$/);
  assert.match(user.salt, /^[a-f0-9]{32}$/);
  assert.ok(f.messages.size > 3, 'Retain old snapshots, never delete them');
  await assert.rejects(
    b.api.login('a@example.test', 'wrong-password'),
    /INVALID_CREDENTIALS/,
  );
  await b.api.logout();
  await assert.rejects(b.api.me(), /TELEGRAM_SESSION_REQUIRED/);
});
test('unique handles/emails and duplicate display names; public results omit email/admin roles', async () => {
  const f = fixture(),
    a = f.client(),
    b = f.client();
  await a.api.initialize();
  const first = await register(a);
  await register(b, 'b@example.test');
  await a.api.updateProfile({ username: 'same_handle' });
  await assert.rejects(
    b.api.updateProfile({ username: '@SAME_HANDLE' }),
    /USERNAME_TAKEN/,
  );
  await assert.rejects(
    b.api.updateProfile({ username: 'a b' }),
    /INVALID_USERNAME/,
  );
  await assert.rejects(
    b.api.register('اسم ثالث', 'a@example.test', 'test-password'),
    /EMAIL_TAKEN/,
  );
  const [found] = await b.api.users('@same_handle');
  assert.equal(found.id, first.user.id);
  assert.equal(found.email, undefined);
  assert.equal(found.isAdmin, false);
  await b.api.follow(first.user.id);
  assert.equal((await a.api.friends()).length, 1);
  await b.api.unfollow(first.user.id);
  assert.equal((await a.api.friends()).length, 0);
});
test('session/vault namespace isolation, expiry, and malformed token rejection', async () => {
  const f = fixture();
  const a = f.client();
  await a.api.initialize();
  const p = await register(a);
  for (const token of [
    null,
    'graphql.jwt.token',
    p.token.replace(namespace, 'another'),
    'nabd-tg:abc123:constructor:abcd',
  ]) {
    a.setToken(token);
    await assert.rejects(a.api.me(), /TELEGRAM_SESSION_REQUIRED/);
  }
  const other = f.client({ ns: 'other' });
  await assert.rejects(other.api.status(), /TELEGRAM_INVALID_VAULT/);
  const expired = f.client({ time: () => Date.now() + 31 * 86400000 });
  expired.setToken(p.token);
  await assert.rejects(expired.api.me(), /TELEGRAM_SESSION_REQUIRED/);
});
test('public posts, comments and reactions survive reload; private posts are explicitly rejected', async () => {
  const f = fixture(),
    a = f.client(),
    b = f.client();
  await a.api.initialize();
  const pa = await register(a);
  await register(b, 'b@example.test');
  await assert.rejects(
    a.api.createPost({ text: 'private', privacy: 'private' }),
    /TELEGRAM_PUBLIC_ONLY/,
  );
  const post = await a.api.createPost({
    text: 'مرحبا',
    file: { type: 'image/png' },
  });
  await b.api.react(post.id, 'love');
  const comment = await b.api.addComment(post.id, 'تعليق');
  await a.api.likeComment(post.id, comment.id);
  let page = await b.api.fetchFeed();
  assert.equal(page.posts[0].reactions.love, 1);
  assert.equal(page.posts[0].comments[0].likes, 1);
  await assert.rejects(b.api.deletePost(post.id), /FORBIDDEN/);
  await b.api.deleteComment(post.id, comment.id);
  await b.api.react(post.id, null);
  const fresh = f.client();
  fresh.setToken(pa.token);
  page = await fresh.api.fetchFeed();
  assert.equal(page.posts[0].commentsCount, 0);
  assert.equal(page.posts[0].reactions.love, 0);
  await fresh.api.deletePost(post.id);
  assert.equal((await fresh.api.fetchFeed()).posts.length, 0);
});
test('corrupt/version/namespace/malformed schema cannot be overwritten or reinitialized', async () => {
  for (const corrupt of [
    null,
    { v: 1 },
    { version: 99 },
    { namespace: 'other' },
    { users: { constructor: {} } },
    { posts: { bad: {} } },
    { friendships: [[123, 456]] },
    { writeId: 'bad' },
  ]) {
    const f = fixture();
    const c = f.client();
    await c.api.initialize();
    const original = snapshot(f.current());
    Object.assign(f.current(), corrupt ?? { users: null });
    const count = f.messages.size;
    await assert.rejects(c.api.status(), /TELEGRAM_INVALID_VAULT/);
    await assert.rejects(c.api.initialize(), /TELEGRAM_ALREADY_INITIALIZED/);
    assert.equal(f.messages.size, count);
    assert.equal(original.app, f.current().app);
  }
});
test('local queue preserves concurrent updates; pin failure never reports success', async () => {
  const f = fixture();
  const a = f.client();
  await a.api.initialize();
  await register(a);
  await Promise.all([
    a.api.updateProfile({ bio: 'الأول' }),
    a.api.updateProfile({ username: 'second_edit' }),
  ]);
  assert.equal((await a.api.me()).bio, 'الأول');
  assert.equal((await a.api.me()).username, 'second_edit');
  const old = snapshot(f.current());
  f.transport.pin = async () => {
    throw Error('TELEGRAM_REQUEST_FAILED');
  };
  await assert.rejects(
    a.api.updateProfile({ bio: 'unsaved' }),
    /TELEGRAM_REQUEST_FAILED/,
  );
  assert.equal(JSON.stringify(f.current()), JSON.stringify(old));
});
test('cross-device snapshot change before pin is detected and never pinned over', async () => {
  const f = fixture();
  const a = f.client();
  await a.api.initialize();
  await register(a);
  const upload = f.transport.upload;
  f.transport.upload = async (json) => {
    const result = await upload(json);
    const other = await upload(JSON.stringify(f.current()));
    f.pin(other.messageId);
    return result;
  };
  await assert.rejects(
    a.api.updateProfile({ bio: 'racing' }),
    /TELEGRAM_CONFLICT/,
  );
  assert.notEqual(
    f.current().users[Object.keys(f.current().users)[0]].profile.bio,
    'racing',
  );
});
test('vault size checked before any upload and transport errors never expose token or URL', async () => {
  const f = fixture();
  const a = f.client();
  await a.api.initialize();
  f.current().extra = 'a'.repeat(MAX_VAULT_BYTES);
  const count = f.messages.size;
  await assert.rejects(a.api.status(), /TELEGRAM_VAULT_FULL/);
  assert.equal(f.messages.size, count);
  const secret = '123456:synthetic_token_not_real';
  const t = createTransport({
    token: secret,
    chatId: '-123',
    fetch: async () => {
      throw Error(`https://api.telegram.org/bot${secret}/getChat`);
    },
    uploadJson: async () => {
      throw Error(secret);
    },
  });
  for (const request of [() => t.chat(), () => t.upload('{}'), () => t.pin(1)])
    await assert.rejects(
      request(),
      (e) =>
        e.message.startsWith('TELEGRAM_') &&
        !e.message.includes(secret) &&
        !e.message.includes('https:'),
    );
});
function network(overrides = {}) {
  const calls = [];
  const token = '123456:synthetic_not_real';
  const t = createTransport({
    token,
    chatId: '-123',
    fetch: async (url, options) => {
      calls.push({ url, options });
      const method = url.split('/').pop();
      if (overrides[method]) return overrides[method](url, options);
      return new Response(
        JSON.stringify({
          ok: true,
          result:
            method === 'getMe'
              ? { id: 123456, is_bot: true }
              : method === 'getChat'
                ? { id: -123, type: 'channel' }
                : method === 'getFile'
                  ? { file_size: 2, file_path: 'documents/a.json' }
                  : {},
        }),
      );
    },
    uploadJson: async () =>
      new Response(JSON.stringify({ ok: false, description: token })),
  });
  return { t, calls };
}
test('transport verifies bot identity/private destination and uses abort signals without AbortSignal.timeout', async () => {
  const n = network();
  await n.t.chat();
  assert.equal(n.calls.length, 2);
  assert.equal(n.calls[0].options.redirect, 'error');
  assert.ok(n.calls[0].options.signal instanceof AbortSignal);
  for (const result of [
    { id: -123, type: 'channel', username: 'public' },
    { id: -123, type: 'channel', active_usernames: ['public'] },
    { id: -999, type: 'private' },
  ]) {
    const n = network({
      getChat: async () => new Response(JSON.stringify({ ok: true, result })),
    });
    await assert.rejects(n.t.chat(), /TELEGRAM_/);
  }
  const wrong = network({
    getMe: async () =>
      new Response(
        JSON.stringify({ ok: true, result: { id: 999, is_bot: true } }),
      ),
  });
  await assert.rejects(wrong.t.chat(), /TELEGRAM_INVALID_BOT/);
});
test('transport rejects traversals, missing size, oversized metadata/bodies and malformed upload responses', async () => {
  for (const meta of [
    { file_path: '../bad', file_size: 2 },
    { file_path: 'documents/a' },
    { file_path: 'https://elsewhere', file_size: 2 },
    { file_path: 'documents/a', file_size: MAX_VAULT_BYTES + 1 },
  ]) {
    const n = network({
      getFile: async () =>
        new Response(JSON.stringify({ ok: true, result: meta })),
    });
    await assert.rejects(n.t.read('1'), /TELEGRAM_INVALID_VAULT/);
    assert.equal(n.calls.length, 1);
  }
  const n = network({
    'a.json': async () => new Response('ا'.repeat(MAX_VAULT_BYTES / 2 + 1)),
  });
  await assert.rejects(n.t.read('1'), /TELEGRAM_READ_FAILED/);
  await assert.rejects(network().t.upload('{}'), /TELEGRAM_WRITE_FAILED/);
});
test('routing is explicitly Telegram-only, never GraphQL/mock; unsupported writes fail clearly', async () => {
  let calls = 0;
  const layer = loadSource({
    env: { EXPO_PUBLIC_DATA_SOURCE: 'telegram' },
    mocks: {
      '../telegram/config': {
        isTelegramDirect: true,
        telegramConfigured: true,
        telegramNamespace: namespace,
      },
      '../telegram/native': {
        telegramApi: {
          me: async () => {
            calls++;
            throw Error('TELEGRAM_READ_FAILED');
          },
        },
      },
      './client': {
        api: new Proxy(
          {},
          {
            get() {
              throw Error('GraphQL accessed');
            },
          },
        ),
        ApiError: class extends Error {
          constructor(code, message) {
            super(message ?? code);
            this.code = code;
          }
        },
      },
      './mockApi': {
        mockApi: new Proxy(
          {},
          {
            get() {
              throw Error('Mock accessed');
            },
          },
        ),
      },
    },
  })('api/index.ts');
  await assert.rejects(
    layer.dataApi.me(),
    (e) => e.code === 'TELEGRAM_READ_FAILED',
  );
  await assert.rejects(
    layer.dataApi.sendMessage('x', 'secret'),
    (e) => e.code === 'TELEGRAM_FEATURE_UNAVAILABLE',
  );
  assert.equal(calls, 1);
  assert.equal(layer.getSource(), 'telegram');
  assert.equal(layer.authStoragePrefix, `nabd.telegram.${namespace}`);
});
test('GraphQL client refuses direct mode or a direct session before any network; realtime stays off', async () => {
  for (const env of [{ EXPO_PUBLIC_DATA_SOURCE: 'telegram' }, {}]) {
    let network = 0;
    const loader = loadSource({
      env,
      globals: {
        fetch: async () => {
          network++;
          throw Error('unexpected');
        },
        WebSocket: class {
          constructor() {
            network++;
          }
        },
      },
    });
    const c = loader('api/client.ts');
    c.setAuthToken('nabd-tg:synthetic');
    await assert.rejects(
      c.api.me(),
      (e) => e.code === 'TELEGRAM_GRAPHQL_DISABLED',
    );
    await assert.rejects(
      c.api.createPost({ text: 'x', file: { uri: 'file:///a.png' } }),
      (e) => e.code === 'TELEGRAM_GRAPHQL_DISABLED',
    );
    loader('api/realtime.ts').realtime.connect();
    assert.equal(network, 0);
  }
});
test('namespace stable across token rotation but not different chats; guard wraps hydration', () => {
  const config = (token, chatId) =>
    loadSource({
      env: {
        EXPO_PUBLIC_DATA_SOURCE: 'telegram',
        EXPO_PUBLIC_TELEGRAM_BOT_TOKEN: token,
        EXPO_PUBLIC_TELEGRAM_CHAT_ID: chatId,
      },
    })('telegram/config.ts');
  assert.equal(
    config('123456:oldtoken', '-123').telegramNamespace,
    config('123456:newtoken', '-123').telegramNamespace,
  );
  assert.notEqual(
    config('123456:oldtoken', '-123').telegramNamespace,
    config('123456:oldtoken', '-456').telegramNamespace,
  );
  assert.equal(config('invalid', '-123').telegramConfigured, false);
  const app = fs.readFileSync(require.resolve('../App.tsx'), 'utf8');
  assert.match(app, /<TelegramGate><Gate \/><\/TelegramGate>/);
  const gate = fs.readFileSync(
    require.resolve('../src/telegram/TelegramGate.tsx'),
    'utf8',
  );
  assert.match(gate, /if \(!isTelegramDirect \|\| ready\)/);
  assert.match(gate, /setConfirming\(true\)/);
  assert.ok(!gate.includes('فهمت المخاطر'));
});
test('native image adapter rejects video/oversize and multipart JSON is cleaned up after success/failure', async () => {
  let size = 4,
    uploadFails = false,
    writes = 0,
    deleted = 0;
  const requests = [];
  class NativeFormData {
    constructor() {
      this.values = {};
    }
    append(key, value) {
      this.values[key] = value;
    }
  }
  const native = loadSource({
    env: {
      EXPO_PUBLIC_TELEGRAM_BOT_TOKEN: '123456:synthetic_not_real',
      EXPO_PUBLIC_TELEGRAM_CHAT_ID: '-123',
    },
    mocks: {
      'expo-file-system': {
        cacheDirectory: 'file:///cache/',
        EncodingType: { Base64: 'base64', UTF8: 'utf8' },
        getInfoAsync: async () => ({ exists: true, size, isDirectory: false }),
        readAsStringAsync: async () => 'aGVsbG8=',
        writeAsStringAsync: async () => {
          writes++;
        },
        deleteAsync: async () => {
          deleted++;
        },
      },
      'expo-modules-core': {
        uuid: { v4: () => '12345678-1234-1234-1234-123456789abc' },
      },
      '../api/client': { getAuthToken: () => null },
    },
    globals: {
      FormData: NativeFormData,
      fetch: async (url, options) => {
        requests.push({ url, options });
        const method = url.split('/').pop();
        if (method === 'getMe')
          return new Response(
            JSON.stringify({ ok: true, result: { id: 123456, is_bot: true } }),
          );
        if (method === 'getChat')
          return new Response(
            JSON.stringify({ ok: true, result: { id: -123, type: 'channel' } }),
          );
        assert.equal(options.body.values.document.name, 'nabd-vault.json');
        assert.equal(options.body.values.caption, 'nabd-direct-telegram-v1');
        assert.ok(options.signal);
        if (uploadFails) throw Error('synthetic secret must not escape');
        return new Response(JSON.stringify({ ok: false }));
      },
    },
  })('telegram/native.ts');
  assert.equal(
    await native.inlineImage({ uri: 'file:///a.png', type: 'image/png' }),
    photo,
  );
  await assert.rejects(
    native.inlineImage({ uri: 'file:///a.mp4', type: 'video/mp4' }),
    /TELEGRAM_IMAGE_ONLY/,
  );
  size = native.MAX_IMAGE_BYTES + 1;
  await assert.rejects(
    native.inlineImage({ uri: 'file:///a.png', type: 'image/png' }),
    /TELEGRAM_IMAGE_TOO_LARGE/,
  );
  for (const fail of [false, true]) {
    uploadFails = fail;
    await assert.rejects(
      native.telegramApi.initialize(),
      /TELEGRAM_WRITE_FAILED/,
    );
  }
  assert.equal(writes, 2);
  assert.equal(deleted, 2);
});
test('PBKDF2 does not require TextEncoder on the native JS runtime', async () => {
  const p = loadSource({ globals: { TextEncoder: undefined } })(
    'telegram/password.ts',
  );
  const salt = '00000000000000000000000000000000';
  assert.equal(
    await p.passwordDigest('native-password', salt),
    pbkdf2Sync(
      'native-password',
      Buffer.from(salt, 'hex'),
      PASSWORD_ROUNDS,
      32,
      'sha256',
    ).toString('hex'),
  );
});
test('manual request deadlines abort and clear timers, redacting underlying errors', async () => {
  const timers = [];
  let cleared = 0;
  const { createTransport } = loadSource({
    globals: {
      setTimeout: (fn) => {
        timers.push(fn);
        return 1;
      },
      clearTimeout: () => {
        cleared++;
      },
    },
  })('telegram/transport.ts');
  const t = createTransport({
    token: '123456:synthetic_not_real',
    chatId: '-123',
    fetch: (_url, { signal }) =>
      new Promise((_resolve, reject) =>
        signal.addEventListener('abort', () =>
          reject(Error('url with secret')),
        ),
      ),
    uploadJson: async () => {},
  });
  const pending = t.chat();
  assert.equal(timers.length, 1);
  timers[0]();
  await assert.rejects(pending, /TELEGRAM_REQUEST_FAILED/);
  assert.equal(cleared, 1);
});
test('publication validation rejects missing configuration without leaking it', () => {
  const { spawnSync } = require('node:child_process');
  const script = require.resolve('../scripts/validate-data-source.cjs');
  // Missing-configuration cases must stay missing even when the APK builder has
  // real Telegram credentials. Never pass those credentials to fixture children.
  const isolatedEnv = { ...process.env };
  for (const key of [
    'EXPO_PUBLIC_DATA_SOURCE',
    'EXPO_PUBLIC_TELEGRAM_BOT_TOKEN',
    'EXPO_PUBLIC_TELEGRAM_CHAT_ID',
  ])
    delete isolatedEnv[key];
  for (const env of [
    { EXPO_PUBLIC_DATA_SOURCE: 'telegram' },
    {
      EXPO_PUBLIC_DATA_SOURCE: 'telegram',
      EXPO_PUBLIC_TELEGRAM_BOT_TOKEN: '123456:synthetic_key_not_real_123',
    },
    {
      EXPO_PUBLIC_DATA_SOURCE: 'telegram',
      EXPO_PUBLIC_TELEGRAM_CHAT_ID: '-123',
    },
    { EXPO_PUBLIC_DATA_SOURCE: 'bad-mode' },
    {
      EXPO_PUBLIC_DATA_SOURCE: 'auto',
      EXPO_PUBLIC_TELEGRAM_BOT_TOKEN: 'synthetic-key-must-not-leak',
    },
  ]) {
    const result = spawnSync(process.execPath, [script], {
      env: { ...isolatedEnv, ...env },
      encoding: 'utf8',
    });
    assert.notEqual(result.status, 0);
    assert.ok(!result.stderr.includes('synthetic-key-must-not-leak'));
  }
  const result = spawnSync(process.execPath, [script], {
    env: {
      ...isolatedEnv,
      EXPO_PUBLIC_DATA_SOURCE: 'telegram',
      EXPO_PUBLIC_TELEGRAM_BOT_TOKEN: '123456:synthetic_key_not_real_123',
      EXPO_PUBLIC_TELEGRAM_CHAT_ID: '-123',
    },
    encoding: 'utf8',
  });
  assert.equal(result.status, 0);
  assert.ok(result.stdout.includes('PUBLIC'));
  assert.ok(!result.stdout.includes('synthetic_key'));
});

test('name-derived handles are normalized, collision-safe in a snapshot and editable', async () => {
  const { usernameBase, availableUsername } = load('utils/usernames.ts');
  assert.equal(usernameBase('  Émile Martin '), 'emile_martin');
  assert.equal(usernameBase('علي حسن'), 'aly_hsn');
  assert.equal(
    availableUsername('Émile Martin', ['EMILE_MARTIN', 'emile_martin_2']),
    'emile_martin_3',
  );
  for (const name of ['🤍', '99 Ahmed', 'A', 'علي حسن', '9'.repeat(40)])
    assert.match(usernameBase(name), /^[a-z][a-z0-9_]{2,39}$/);
  const f = fixture();
  const a = f.client();
  const b = f.client();
  await a.api.initialize();
  const first = await a.api.register(
    'علي حسن',
    'ali-a@example.test',
    'test-password',
  );
  const second = await b.api.register(
    'علي حسن',
    'ali-b@example.test',
    'test-password',
  );
  assert.equal(first.user.username, 'aly_hsn');
  assert.equal(second.user.username, 'aly_hsn_2');
  b.setToken(second.token);
  await assert.rejects(
    b.api.updateProfile({ username: '@ALY_HSN' }),
    /USERNAME_TAKEN/,
  );
  const edited = await b.api.updateProfile({ username: 'ali_new' });
  assert.equal(edited.username, 'ali_new');
  const fresh = f.client();
  assert.equal(
    (await fresh.api.login('@ALI_NEW', 'test-password')).user.id,
    second.user.id,
  );
});
test('login avoids redownloading an unchanged document but still confirms the committed snapshot', async () => {
  const f = fixture();
  const c = f.client();
  await c.api.initialize();
  await register(c);
  f.events.length = 0;
  await c.api.login('a@example.test', 'test-password');
  assert.equal(f.events.filter((event) => event === 'read').length, 2);
  assert.ok(f.events.includes('pin'));
  assert.ok(f.events.lastIndexOf('read') > f.events.lastIndexOf('pin'));
});
test('fallback password derivation yields to the UI event loop without lowering the work factor', async () => {
  let ticks = 0;
  const timer = setInterval(() => ticks++, 5);
  try {
    await passwordDigest('test-password', '1234567890abcdef1234567890abcdef');
    assert.ok(ticks > 0, 'A microtask-only loop starves rendering and input');
    assert.equal(PASSWORD_ROUNDS, 210000);
  } finally {
    clearInterval(timer);
  }
});
test('existing WebCrypto produces the same password bytes as the fallback', async () => {
  const native = loadSource({
    globals: { crypto: require('node:crypto').webcrypto },
  })('telegram/password.ts');
  const salt = '1234567890abcdef1234567890abcdef';
  assert.equal(
    await native.passwordDigest('كلمة سر 🌍', salt),
    pbkdf2Sync(
      'كلمة سر 🌍',
      Buffer.from(salt, 'hex'),
      PASSWORD_ROUNDS,
      32,
      'sha256',
    ).toString('hex'),
  );
});

test('a password change during login is reloaded and rejects the stale password', async () => {
  const f = fixture();
  const c = f.client();
  await c.api.initialize();
  const payload = await register(c);
  const chat = f.transport.chat;
  let calls = 0;
  f.transport.chat = async () => {
    if (++calls === 2) {
      const changed = snapshot(f.current());
      changed.users[payload.user.id].passwordHash = '0'.repeat(64);
      changed.revision++;
      const message = await f.transport.upload(JSON.stringify(changed));
      f.pin(message.messageId);
    }
    return chat();
  };
  await assert.rejects(
    c.api.login('a@example.test', 'test-password'),
    /INVALID_CREDENTIALS/,
  );
});
test('duplicate email fails before password derivation or avatar processing', async () => {
  const f = fixture();
  const c = f.client();
  await c.api.initialize();
  await register(c);
  let avatarCalls = 0;
  const duplicate = f.client({
    avatar: async () => {
      avatarCalls++;
      throw new Error('must not read image');
    },
  });
  await assert.rejects(
    duplicate.api.register('اسم', 'a@example.test', 'test-password', {
      uri: 'file:///photo.png',
    }),
    /EMAIL_TAKEN/,
  );
  assert.equal(avatarCalls, 0);
});
test('WebCrypto operational errors fail safely instead of returning a fake password result', async () => {
  const implementation = loadSource({
    globals: {
      crypto: {
        subtle: {
          importKey: async () => ({}),
          deriveBits: async () => {
            throw new Error('platform unavailable');
          },
        },
      },
    },
  })('telegram/password.ts');
  await assert.rejects(
    implementation.passwordDigest(
      'test-password',
      '1234567890abcdef1234567890abcdef',
    ),
    /PASSWORD_DERIVATION_FAILED/,
  );
});

test('Telegram messaging persists between two accounts without root schema changes or startup migration', async () => {
  const f = fixture(),
    a = f.client(),
    b = f.client();
  await a.api.initialize();
  const aa = await register(a);
  const bb = await register(b, 'b@example.test');
  const before = snapshot(f.current());
  const uploads = () => f.events.filter((e) => e === 'upload').length;
  const count = uploads();
  assert.equal((await a.api.conversations()).length, 0);
  assert.equal(await b.api.unreadMessagesCount(), 0);
  assert.equal(uploads(), count);
  assert.deepEqual(f.current(), before);
  const c = await a.api.startConversation(bb.user.id);
  assert.equal(c.other.id, bb.user.id);
  assert.equal(c.other.email, undefined);
  assert.equal(c.other.chatV1, undefined);
  const created = uploads();
  assert.equal((await b.api.startConversation(aa.user.id)).id, c.id);
  assert.equal(uploads(), created);
  const first = await a.api.sendMessage(c.id, 'مرحبا من الحساب الأول');
  assert.equal(first.senderId, aa.user.id);
  assert.equal(first.read, false);
  assert.equal((await b.api.messages(c.id)).messages[0].text, first.text);
  assert.equal(await b.api.unreadMessagesCount(), 1);
  assert.equal(await a.api.unreadMessagesCount(), 0);
  const reply = await b.api.sendMessage(c.id, 'جواب', first.id);
  assert.equal(reply.replyTo.id, first.id);
  assert.equal((await a.api.conversations())[0].lastMessage.id, reply.id);
  await a.api.markConversationRead(c.id);
  const rows = (await a.api.messages(c.id)).messages;
  assert.equal(rows[0].read, false);
  assert.equal(rows[1].read, true);
  await b.api.markConversationRead(c.id);
  assert.equal((await a.api.messages(c.id)).messages[0].read, true);
  const readCount = uploads();
  await b.api.markConversationRead(c.id);
  assert.equal(uploads(), readCount);
  const oldRead = require('./fixtures/legacy-vault-reader.cjs').default;
  const legacy = oldRead(
    snapshot(f.current()),
    namespace,
    utf8,
    MAX_VAULT_BYTES,
  );
  assert.deepEqual(Object.keys(legacy).sort(), Object.keys(before).sort());
  assert.equal(legacy.version, 1);
  // Simulate the old profile writer: unknown account fields survive a real round trip.
  legacy.users[aa.user.id].profile.bio = 'كتابة من القارئ القديم';
  legacy.revision++;
  legacy.parent = (await f.transport.chat()).pinned_message.message_id;
  legacy.writeId = 'a'.repeat(32);
  const saved = await f.transport.upload(JSON.stringify(legacy));
  await f.transport.pin(saved.messageId);
  assert.equal((await a.api.messages(c.id)).messages.length, 2);
  assert.equal((await a.api.me()).bio, 'كتابة من القارئ القديم');
  assert.equal((await b.api.me()).id, bb.user.id);
  assert.equal(
    f.current().users[aa.user.id].passwordHash,
    before.users[aa.user.id].passwordHash,
  );
});
test('Telegram chat supports images, edits, replies, reactions and deletion without mutating the original on failed writes', async () => {
  const f = fixture(),
    a = f.client(),
    b = f.client();
  await a.api.initialize();
  const aa = await register(a);
  const bb = await register(b, 'b@example.test');
  const c = await a.api.startConversation(bb.user.id);
  const m = await a.api.sendMessage(c.id, 'صورة', null, {
    uri: 'file:///p.png',
    type: 'image/png',
    name: 'p.png',
  });
  assert.equal(m.media.uri, photo);
  await assert.rejects(b.api.editMessage(m.id, 'no'), /FORBIDDEN/);
  await assert.rejects(b.api.deleteMessage(m.id), /FORBIDDEN/);
  assert.equal((await a.api.editMessage(m.id, 'تم التعديل')).edited, true);
  assert.equal(
    (await b.api.reactToMessage(m.id, 'love')).reactions[0].mine,
    true,
  );
  assert.equal(
    (await a.api.messages(c.id)).messages[0].reactions[0].mine,
    false,
  );
  const r = await b.api.sendMessage(c.id, 'رد', m.id);
  await a.api.deleteMessage(m.id);
  const data = (await b.api.messages(c.id)).messages;
  assert.equal(data[0].text, '');
  assert.equal(data[0].media, null);
  assert.equal(data[0].deleted, true);
  assert.equal(data[1].replyTo.deleted, true);
  assert.equal(data[1].replyTo.text, '');
  const previous = snapshot(f.current());
  await assert.rejects(
    a.api.sendMessage(c.id, 'x', 'f'.repeat(32)),
    /VALIDATION/,
  );
  await assert.rejects(a.api.sendMessage(c.id, 'x'.repeat(5001)), /VALIDATION/);
  await assert.rejects(a.api.reactToMessage(r.id, 'invalid'), /VALIDATION/);
  assert.deepEqual(f.current(), previous);
  const original = f.transport.pin;
  f.transport.pin = async () => {
    throw Error('offline');
  };
  await assert.rejects(a.api.sendMessage(c.id, 'لن تحفظ'), /offline/);
  f.transport.pin = original;
  assert.deepEqual(f.current(), previous);
});
test('Telegram chat checks session and conversation membership for every supported action', async () => {
  const f = fixture(),
    a = f.client(),
    b = f.client(),
    outsider = f.client();
  await a.api.initialize();
  const aa = await register(a);
  const bb = await register(b, 'b@example.test');
  await register(outsider, 'c@example.test');
  const c = await a.api.startConversation(bb.user.id),
    m = await a.api.sendMessage(c.id, 'نص تجريبي');
  const previous = snapshot(f.current());
  for (const action of [
    () => outsider.api.messages(c.id),
    () => outsider.api.sendMessage(c.id, 'no'),
    () => outsider.api.markConversationRead(c.id),
    () => outsider.api.editMessage(m.id, 'no'),
    () => outsider.api.deleteMessage(m.id),
    () => outsider.api.reactToMessage(m.id, 'like'),
  ])
    await assert.rejects(action(), /FORBIDDEN/);
  assert.equal((await outsider.api.conversations()).length, 0);
  assert.equal(await outsider.api.unreadMessagesCount(), 0);
  await assert.rejects(a.api.startConversation(aa.user.id), /USER_NOT_FOUND/);
  assert.deepEqual(f.current(), previous);
  outsider.setToken(null);
  await assert.rejects(outsider.api.conversations(), /SESSION_REQUIRED/);
  await assert.rejects(
    outsider.api.sendMessage(c.id, 'no'),
    /SESSION_REQUIRED/,
  );
});
test('Telegram message pagination is stable across new arrivals and duplicate starts do not duplicate threads', async () => {
  const f = fixture(),
    a = f.client(),
    b = f.client();
  await a.api.initialize();
  const aa = await register(a);
  const bb = await register(b, 'b@example.test');
  const c = await a.api.startConversation(bb.user.id);
  for (let i = 0; i < 33; i++) await a.api.sendMessage(c.id, String(i));
  const last = await b.api.messages(c.id);
  assert.equal(last.messages.length, 30);
  assert.equal(last.messages[0].text, '3');
  await a.api.sendMessage(c.id, 'new');
  const older = await b.api.messages(c.id, last.nextCursor);
  assert.deepEqual(
    Array.from(older.messages, (m) => m.text),
    ['0', '1', '2'],
  );
  assert.equal(older.nextCursor, null);
  await assert.rejects(b.api.messages(c.id, 'invalid'), /VALIDATION/);
  await Promise.all([
    a.api.startConversation(bb.user.id),
    a.api.startConversation(bb.user.id),
  ]);
  assert.equal((await a.api.conversations()).length, 1);
  assert.equal((await b.api.conversations('@' + aa.user.username)).length, 1);
  assert.equal((await b.api.conversations('not-a-user')).length, 0);
});
test('malformed optional chat records are rejected rather than silently erased or repaired', async () => {
  const f = fixture(),
    a = f.client(),
    b = f.client();
  await a.api.initialize();
  await register(a);
  const bb = await register(b, 'b@example.test');
  const c = await a.api.startConversation(bb.user.id);
  await a.api.sendMessage(c.id, 'x');
  const current = f.current(),
    owner = Object.values(current.users).find((u) => u.chatV1);
  const messages = owner.chatV1[c.id].messages;
  messages[0].replyToId = messages[0].id;
  const count = f.events.filter((e) => e === 'upload').length;
  await assert.rejects(a.api.status(), /INVALID_VAULT/);
  assert.equal(f.events.filter((e) => e === 'upload').length, count);
});
test('production data-source proxy routes real conversation operations to Telegram without GraphQL or mock fallback', async () => {
  const f = fixture(),
    a = f.client(),
    b = f.client();
  await a.api.initialize();
  await register(a);
  const bb = await register(b, 'b@example.test');
  const layer = loadSource({
    env: { EXPO_PUBLIC_DATA_SOURCE: 'telegram' },
    mocks: {
      '../telegram/config': {
        isTelegramDirect: true,
        telegramConfigured: true,
        telegramNamespace: namespace,
      },
      '../telegram/native': { telegramApi: a.api },
      './client': {
        api: new Proxy(
          {},
          {
            get() {
              throw Error('GraphQL fallback');
            },
          },
        ),
        ApiError: class extends Error {
          constructor(code) {
            super(code);
            this.code = code;
          }
        },
      },
      './mockApi': {
        mockApi: new Proxy(
          {},
          {
            get() {
              throw Error('Mock fallback');
            },
          },
        ),
      },
    },
  })('api/index.ts');
  const c = await layer.dataApi.startConversation(bb.user.id);
  const sent = await layer.dataApi.sendMessage(c.id, 'رسالة عبر الربط الفعلي');
  assert.equal((await b.api.messages(c.id)).messages[0].id, sent.id);
  assert.equal((await layer.dataApi.conversations())[0].id, c.id);
  await assert.rejects(
    layer.dataApi.translateMessage(sent.id, 'fr'),
    (e) => e.code === 'TELEGRAM_FEATURE_UNAVAILABLE',
  );
});
test('session changes during image preparation cannot commit a message under another identity', async () => {
  let pause = false,
    finish;
  const f = fixture(),
    a = f.client({
      avatar: () =>
        pause
          ? new Promise((resolve) => {
              finish = resolve;
            })
          : Promise.resolve(photo),
    }),
    b = f.client();
  await a.api.initialize();
  await register(a);
  const bb = await register(b, 'b@example.test');
  const c = await a.api.startConversation(bb.user.id);
  const before = snapshot(f.current());
  pause = true;
  const pending = a.api.sendMessage(c.id, 'image', null, {
    uri: 'file:///p.png',
    type: 'image/png',
    name: 'p.png',
  });
  await new Promise(setImmediate);
  assert.ok(finish);
  a.setToken(null);
  finish(photo);
  await assert.rejects(pending, /SESSION_REQUIRED/);
  assert.deepEqual(f.current(), before);
});

test('HTTP document cache saves duplicate downloads, keeps pins fresh and verifies uploaded bytes remotely', async () => {
  let fileId = 'a',
    chats = 0,
    downloads = 0,
    getFiles = 0;
  const t = createTransport({
    token: '123456:synthetic_not_real',
    chatId: '-123',
    fetch: async (url) => {
      const method = url.split('/').pop();
      let result;
      if (method === 'getMe') result = { id: 123456, is_bot: true };
      else if (method === 'getChat') {
        chats++;
        result = {
          id: -123,
          type: 'channel',
          pinned_message: {
            message_id: fileId === 'a' ? 1 : 2,
            document: { file_id: fileId },
          },
        };
      } else if (method === 'getFile') {
        getFiles++;
        result = { file_size: 100, file_path: `documents/${fileId}.json` };
      } else {
        downloads++;
        return new Response(JSON.stringify({ remote: method }));
      }
      return new Response(JSON.stringify({ ok: true, result }));
    },
    uploadJson: async () =>
      new Response(
        JSON.stringify({
          ok: true,
          result: {
            message_id: 2,
            document: { file_id: 'b', file_name: 'nabd-vault.json' },
          },
        }),
      ),
  });
  const readCurrent = async () => {
    const chat = await t.chat();
    return t.read(chat.pinned_message.document.file_id);
  };
  const [a, b] = await Promise.all([readCurrent(), readCurrent()]);
  assert.equal(chats, 2);
  assert.equal(getFiles, 1);
  assert.equal(downloads, 1);
  a.remote = 'corrupted';
  assert.equal(b.remote, 'a.json');
  assert.equal((await readCurrent()).remote, 'a.json');
  assert.equal(chats, 3);
  await t.upload('{"local":"must not seed the read cache"}');
  fileId = 'b';
  assert.equal((await readCurrent()).remote, 'b.json');
  assert.equal(chats, 4);
  assert.equal(getFiles, 2);
  assert.equal(downloads, 2);
});
test('cached document reads preserve remote session revocation and failed-write isolation', async () => {
  const { createImmutableReader } = load('telegram/immutableReader.ts');
  const f = fixture(),
    setup = f.client();
  await setup.api.initialize();
  const payload = await register(setup);
  const rawRead = f.transport.read;
  f.transport.read = createImmutableReader(rawRead, MAX_VAULT_BYTES);
  const cached = f.client();
  cached.setToken(payload.token);
  assert.equal((await cached.api.me()).id, payload.user.id);
  const initialReads = f.events.filter((x) => x === 'read').length;
  assert.equal((await cached.api.me()).id, payload.user.id);
  assert.equal(f.events.filter((x) => x === 'read').length, initialReads);
  const oldPin = f.transport.pin;
  f.transport.pin = async () => {
    throw Error('network');
  };
  await assert.rejects(cached.api.updateProfile({ name: 'must not leak' }));
  f.transport.pin = oldPin;
  assert.notEqual((await cached.api.me()).name, 'must not leak');
  await setup.api.logout();
  await assert.rejects(cached.api.me(), /TELEGRAM_SESSION_REQUIRED/);
  assert.ok(
    f.events.filter((x) => x === 'read').length > initialReads,
    'new remote pin must fetch and validate the revoked session',
  );
});
