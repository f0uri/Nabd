import test, { after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { graphql } from 'graphql';
import { parseSessionInput, MAX_SESSION_BYTES } from '../src/instagramSessionInput.js';
import { encryptSession, decryptSession } from '../src/instagramSessionVault.js';
import { setSession, hasSession, sessionInfo, sessionSuspension, webHeaders, fetchSessionJson } from '../src/instagramSession.js';
import { schema } from '../src/schema.js';
import { users } from '../src/store.js';

// Synthetic values only. Never use real account credentials in tests.
const sessionId = 'synthetic-session-value-123456';
const expected = { sessionId, csrfToken: 'csrf-test', dsUserId: '1234' };
const originalFetch = globalThis.fetch;
const originalKey = process.env.IG_SESSION_ENCRYPTION_KEY;
beforeEach(() => {
  process.env.IG_SESSION_ENCRYPTION_KEY = 'ab'.repeat(32);
  globalThis.fetch = originalFetch;
  setSession({}, null);
});
after(() => {
  globalThis.fetch = originalFetch;
  if (originalKey === undefined) delete process.env.IG_SESSION_ENCRYPTION_KEY;
  else process.env.IG_SESSION_ENCRYPTION_KEY = originalKey;
  users.delete('session-test-user');
});

for (const [name, input] of [
  ['cookie header', `sessionid=${sessionId}; csrftoken=csrf-test; ds_user_id=1234`],
  ['direct JSON', JSON.stringify({ sessionid: sessionId, csrftoken: 'csrf-test', ds_user_id: '1234', password: 'discard-me' })],
  ['normalized JSON', JSON.stringify(expected)],
  ['cookie map', JSON.stringify({ cookies: { sessionid: sessionId, csrftoken: 'csrf-test', ds_user_id: '1234' } })],
  ['authorization_data', JSON.stringify({ authorization_data: { sessionid: sessionId, csrftoken: 'csrf-test', ds_user_id: '1234' }, device_settings: {} })],
  ['browser JSON', JSON.stringify({ cookies: Object.entries({ sessionid: sessionId, csrftoken: 'csrf-test', ds_user_id: '1234' }).map(([name, value]) => ({ name, value, domain: '.instagram.com', expires: -1 })) })],
  ['browser array', JSON.stringify(Object.entries({ sessionid: sessionId, csrftoken: 'csrf-test', ds_user_id: '1234' }).map(([name, value]) => ({ name, value, domain: 'www.instagram.com' })))],
  ['Netscape', `# Netscape HTTP Cookie File\n#HttpOnly_.instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\t${sessionId}\n.instagram.com\tTRUE\t/\tTRUE\t0\tcsrftoken\tcsrf-test\n.instagram.com\tTRUE\t/\tTRUE\t0\tds_user_id\t1234`],
]) {
  test(`parses ${name} and keeps only the three required fields`, () => assert.deepEqual(parseSessionInput(input), expected));
}
test('accepts session ID alone and preserves URL encoding', () => {
  const encoded = '12345678901234567890%3Aabcd%3Axyz';
  assert.equal(parseSessionInput(encoded).sessionId, encoded);
});
for (const [name, input] of [
  ['empty', ''], ['malformed JSON', '{sessionid'], ['missing session', '{}'],
  ['short value', 'abc'], ['non-string JSON session', '{"sessionid":{}}'],
  ['oversize', 'x'.repeat(MAX_SESSION_BYTES + 1)],
  ['foreign domain', JSON.stringify([{ name: 'sessionid', value: sessionId, domain: 'instagram.com.attacker.test' }])],
  ['duplicate account', `sessionid=${sessionId}; sessionid=another-synthetic-session-id`],
  ['raw newline', `${sessionId}\r\nCookie: bad`], ['encoded newline', `${sessionId}%0D%0A`],
  ['encoded semicolon', `${sessionId}%3Bother`], ['invalid escape', `${sessionId}%GG`],
  ['invalid UTF8', `${sessionId}%FF`],
  ['invalid user ID', `sessionid=${sessionId}; ds_user_id=abc`],
]) {
  test(`rejects ${name} with a redacted error`, () => assert.throws(() => parseSessionInput(input), { message: 'INVALID_INSTAGRAM_COOKIE' }));
}
test('rejects expired browser cookies', () => assert.throws(() => parseSessionInput(JSON.stringify([
  { name: 'sessionid', value: sessionId, domain: '.instagram.com', expirationDate: 1 },
])), { message: 'INSTAGRAM_SESSION_EXPIRED' }));

test('encrypts with fresh nonces and authenticates the ciphertext', () => {
  const a = encryptSession(expected);
  const b = encryptSession(expected);
  assert.notEqual(a, b);
  assert.ok(!a.includes(sessionId));
  assert.deepEqual(decryptSession(a), expected);
  const damaged = JSON.parse(a);
  damaged.data = Buffer.from('corrupted').toString('base64');
  assert.throws(() => decryptSession(JSON.stringify(damaged)), { message: 'INSTAGRAM_SESSION_UNREADABLE' });
  process.env.IG_SESSION_ENCRYPTION_KEY = 'cd'.repeat(32);
  assert.throws(() => decryptSession(a), { message: 'INSTAGRAM_SESSION_UNREADABLE' });
});
test('refuses plaintext fallback without a dedicated encryption key', () => {
  delete process.env.IG_SESSION_ENCRYPTION_KEY;
  assert.throws(() => encryptSession(expected), { message: 'INSTAGRAM_SESSION_KEY_REQUIRED' });
});
test('session status and request errors do not disclose credentials', async () => {
  setSession(expected);
  assert.ok(!JSON.stringify(sessionInfo()).includes(sessionId));
  globalThis.fetch = async () => { throw new Error(`secret ${sessionId}`); };
  await assert.rejects(fetchSessionJson('/api/v1/clips/home/'), { message: 'INSTAGRAM_UNREACHABLE' });
});
test('credential requests disallow redirects and use a timeout', async () => {
  setSession(expected);
  globalThis.fetch = async (url, options) => {
    assert.equal(url, 'https://www.instagram.com/api/v1/clips/home/');
    assert.equal(options.redirect, 'manual');
    assert.ok(options.signal);
    assert.ok(options.headers.Cookie.includes(sessionId));
    return new Response(JSON.stringify({ items: [] }), { status: 200 });
  };
  assert.deepEqual(await fetchSessionJson('/api/v1/clips/home/'), { items: [] });
  await assert.rejects(fetchSessionJson('//attacker.test'), { message: 'INVALID_INSTAGRAM_PATH' });
});
for (const status of [302, 401, 403, 429]) {
  test(`HTTP ${status} suspends the session without more authenticated calls`, async () => {
    setSession(expected);
    let calls = 0;
    globalThis.fetch = async () => { calls++; return new Response('', { status }); };
    await assert.rejects(fetchSessionJson('/api/v1/clips/home/'));
    assert.ok(sessionSuspension());
    assert.equal(hasSession(), false);
    assert.equal(sessionInfo().configured, true); // Administrator can still delete it.
    assert.equal(webHeaders().Cookie, undefined);
    setSession(expected); // Background reload must not silently resume a blocked account.
    await assert.rejects(fetchSessionJson('/api/v1/clips/home/'));
    assert.equal(calls, 1);
    setSession(expected, 'admin', true);
    assert.equal(hasSession(), true);
  });
}
test('challenge payload stops the session and does not follow challenge URLs', async () => {
  setSession(expected);
  globalThis.fetch = async () => new Response(JSON.stringify({ challenge: { url: 'https://example.com' } }));
  await assert.rejects(fetchSessionJson('/api/v1/clips/home/'), { message: 'INSTAGRAM_SESSION_REAUTH_REQUIRED' });
  assert.equal(hasSession(), false);
});

for (const [name, source] of [
  ['status', '{ instagramSessionStatus { configured } }'],
  ['save', `mutation { saveInstagramSession(cookies: "${sessionId}") { valid } }`],
  ['clear', 'mutation { clearInstagramSession }'],
  ['probe', 'mutation { testInstagramSession { valid } }'],
  ['ingest', 'mutation { runReelsIngest }'],
]) {
  test(`${name} is administrator-only before touching the database or Instagram`, async () => {
    users.set('session-test-user', { id: 'session-test-user', isAdmin: false });
    for (const [contextValue, error] of [[{}, 'UNAUTHENTICATED'], [{ userId: 'session-test-user' }, 'FORBIDDEN']]) {
      const result = await graphql({ schema, source, contextValue });
      assert.equal(result.errors?.[0]?.message, error);
    }
  });
}
