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
  assert.match(gate, /if \(ready\)/);
  assert.match(gate, /disabled=\{!accepted \|\| busy\}/);
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
