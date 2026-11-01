import test, { afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { TelegramStorage, MAX_TELEGRAM_UPLOAD_BYTES } from '../src/storage/telegram.js';
import { resolveAuthSecret, resolveStorageConfig } from '../src/runtimeConfig.js';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
// Synthetic sentinels only, never actual credentials or a real Telegram destination.
const token = 'synthetic-private-token';
const chatId = '-1000000000000';
const create = () => new TelegramStorage({ token, chatId });
const response = (result) => new Response(JSON.stringify({ ok: true, result }), { headers: { 'content-type': 'application/json' } });

test('explicit or partial Telegram configuration never silently falls back to local storage', () => {
  for (const env of [{ MEDIA_PROVIDER: 'telegram' }, { TELEGRAM_BOT_TOKEN: token }, { TELEGRAM_CHAT_ID: chatId }]) {
    assert.throws(() => resolveStorageConfig(env), /TELEGRAM_STORAGE_REQUIRES_CREDENTIALS/);
  }
  assert.equal(resolveStorageConfig({}).provider, 'local');
  assert.equal(resolveStorageConfig({ MEDIA_PROVIDER: 'local', TELEGRAM_BOT_TOKEN: token }).provider, 'local');
  assert.equal(resolveStorageConfig({ TELEGRAM_BOT_TOKEN: token, TELEGRAM_CHAT_ID: chatId }).provider, 'telegram');
  assert.throws(() => resolveStorageConfig({ MEDIA_PROVIDER: 'typo' }), /INVALID_MEDIA_PROVIDER/);
});
test('production cannot use the development signing secret', () => {
  for (const secret of ['', 'short', ' '.repeat(64), 'nabd-dev-secret-change-me']) {
    assert.throws(() => resolveAuthSecret({ NODE_ENV: 'production', AUTH_SECRET: secret }), /AUTH_SECRET_REQUIRED_IN_PRODUCTION/);
  }
  assert.equal(resolveAuthSecret({ NODE_ENV: 'production', AUTH_SECRET: 'x'.repeat(64) }).length, 64);
});
test('startup checks bot authentication and private chat without uploading a file', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response(url.endsWith('/getMe') ? { is_bot: true } : { id: chatId, type: 'channel' });
  };
  assert.equal(await create().checkConnection(), true);
  assert.equal(calls.length, 2);
  assert.equal(JSON.parse(calls[1].options.body).chat_id, chatId);
  assert.ok(calls.every((call) => call.options.redirect === 'error' && call.options.signal));
});
test('public channels/groups are rejected as a real-user storage destination', async () => {
  globalThis.fetch = async (url) => response(url.endsWith('/getMe') ? { is_bot: true } : { id: chatId, type: 'channel', username: 'public_example' });
  await assert.rejects(create().checkConnection(), /TELEGRAM_CHAT_MUST_BE_PRIVATE/);
});
test('a personal private bot conversation is allowed even if the user has a public username', async () => {
  globalThis.fetch = async (url) => response(url.endsWith('/getMe') ? { is_bot: true } : { id: 123, type: 'private', username: 'example_user' });
  assert.equal(await create().checkConnection(), true);
});
test('uploads return stable file IDs, never bot-token download URLs', async () => {
  globalThis.fetch = async (url, options) => {
    assert.ok(url.endsWith('/sendDocument'));
    assert.equal(options.body.get('chat_id'), chatId);
    assert.equal(options.body.get('document').name, 'avatar.png');
    return response({ message_id: 42, document: { file_id: 'test-file-id', file_unique_id: 'test-unique', file_size: 3 } });
  };
  const metadata = await create().upload({ buffer: Buffer.from('abc'), filename: 'avatar.png', mime: 'image/png' });
  assert.equal(metadata.fileId, 'test-file-id');
  assert.equal(metadata.provider, 'telegram');
  assert.ok(!JSON.stringify(metadata).includes(token));
});
test('empty and oversized uploads fail before any network request', async () => {
  globalThis.fetch = async () => { throw new Error('should not be called'); };
  await assert.rejects(create().upload({ buffer: Buffer.alloc(0) }), /EMPTY_UPLOAD/);
  await assert.rejects(create().upload({ buffer: { byteLength: MAX_TELEGRAM_UPLOAD_BYTES + 1 } }), /UPLOAD_TOO_LARGE/);
});
for (const kind of ['network', 'http', 'api', 'json']) {
  test(`${kind} upload errors do not disclose upstream URLs, tokens or descriptions`, async () => {
    globalThis.fetch = async () => {
      if (kind === 'network') throw new Error(`https://api.telegram.org/bot${token}/sendDocument`);
      if (kind === 'http') return new Response(token, { status: 401 });
      if (kind === 'json') return new Response(token);
      return new Response(JSON.stringify({ ok: false, description: token }));
    };
    await assert.rejects(create().upload({ buffer: Buffer.from('abc'), filename: 'test.png' }), (error) => error.message === 'TELEGRAM_SENDDOCUMENT_FAILED' && !error.stack.includes(token));
  });
}
test('an incomplete successful upload is rejected before metadata can be stored', async () => {
  globalThis.fetch = async () => response({ message_id: 42 });
  await assert.rejects(create().upload({ buffer: Buffer.from('abc') }), /TELEGRAM_INVALID_UPLOAD_RESPONSE/);
});
test('file-path cache avoids repeat lookups but refreshes expired Telegram paths', async () => {
  let calls = 0;
  globalThis.fetch = async () => { calls++; return response({ file_path: `documents/file-${calls}.png` }); };
  const storage = create();
  assert.equal(await storage.getFilePath('id'), 'documents/file-1.png');
  assert.equal(await storage.getFilePath('id'), 'documents/file-1.png');
  assert.equal(calls, 1);
  storage._pathCache.get('id').expiresAt = 0;
  assert.equal(await storage.getFilePath('id'), 'documents/file-2.png');
});
test('file paths cannot escape the Telegram file endpoint', async () => {
  for (const path of ['../escape', '/absolute', 'https://example.test/file', 'file?x=1', 'a\\b', 'x\ny']) {
    globalThis.fetch = async () => response({ file_path: path });
    await assert.rejects(create().getFilePath('id'), /TELEGRAM_INVALID_FILE_PATH/);
  }
});
test('successful downloads stream through the server adapter', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(options.redirect, 'error');
    return url.includes('/getFile?') ? response({ file_path: 'documents/file.png' }) : new Response('synthetic-image');
  };
  const result = await create().download('test-file');
  assert.equal(await result.text(), 'synthetic-image');
});
test('download failures never expose the token-bearing URL', async () => {
  globalThis.fetch = async (url) => {
    if (url.includes('/getFile?')) return response({ file_path: 'documents/file.png' });
    throw new Error(`Failed to fetch ${url}`);
  };
  await assert.rejects(create().download('id'), (error) => error.message === 'TELEGRAM_DOWNLOAD_FAILED' && !error.stack.includes(token));
});
