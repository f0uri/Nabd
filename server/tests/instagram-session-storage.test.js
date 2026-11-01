import test from 'node:test';
import assert from 'node:assert/strict';
import { cpSync, mkdtempSync, rmSync, symlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// An isolated source copy gives PGlite its own temp data directory, never the host's database.
test('admin session save/reload/migration/delete round trip with isolated PostgreSQL', async () => {
  const root = fileURLToPath(new URL('..', import.meta.url));
  const dir = mkdtempSync(join(tmpdir(), 'nabd-ig-test-'));
  const savedFetch = globalThis.fetch;
  const savedKey = process.env.IG_SESSION_ENCRYPTION_KEY;
  const savedImport = process.env.IG_AUTO_IMPORT;
  let db;
  try {
    cpSync(join(root, 'src'), join(dir, 'src'), { recursive: true });
    symlinkSync(join(root, 'node_modules'), join(dir, 'node_modules'));
    process.env.IG_SESSION_ENCRYPTION_KEY = 'ab'.repeat(32);
    process.env.IG_AUTO_IMPORT = '0';
    const load = (name) => import(pathToFileURL(join(dir, 'src', name)).href);
    db = await load('db.js');
    const store = await load('store.js');
    const ingest = await load('ingest.js');
    const sessions = await load('instagramSession.js');
    const vault = await load('instagramSessionVault.js');
    const id = 'synthetic-session-storage-test';
    const input = JSON.stringify({ authorization_data: { sessionid: id, ds_user_id: '123' }, password: 'must-not-be-stored' });
    let calls = 0;
    globalThis.fetch = async (url) => {
      calls++;
      return new Response(JSON.stringify(url.includes('/current_user/') ? { user: { pk: '123' } } : { items: [] }));
    };
    const result = await ingest.saveInstagramSessionFromInput(input);
    assert.equal(result.valid, true); // Empty feeds do not mean invalid authentication.
    assert.equal(result.totalCodes, 0);
    assert.equal(calls, 3);
    const raw = await store.getSetting('ig_session');
    assert.ok(!raw.includes(id));
    assert.ok(!raw.includes('password'));
    assert.equal(vault.decryptSession(raw).sessionId, id);
    sessions.setSession({}, null);
    await ingest.loadInstagramSession();
    assert.equal(sessions.hasSession(), true);
    assert.ok(!JSON.stringify(await ingest.instagramAdminStatus()).includes(id));
    await assert.rejects(ingest.saveInstagramSessionFromInput('{bad-json'), { message: 'INVALID_INSTAGRAM_COOKIE' });
    assert.equal(await store.getSetting('ig_session'), raw);
    delete process.env.IG_SESSION_ENCRYPTION_KEY;
    await assert.rejects(ingest.saveInstagramSessionFromInput(input), { message: 'INSTAGRAM_SESSION_KEY_REQUIRED' });
    assert.equal(await store.getSetting('ig_session'), raw);
    process.env.IG_SESSION_ENCRYPTION_KEY = 'ab'.repeat(32);
    await store.setSetting('ig_session', JSON.stringify({ sessionId: id, csrfToken: '', dsUserId: '123' }));
    await ingest.loadInstagramSession();
    assert.equal(JSON.parse(await store.getSetting('ig_session')).v, 1); // Legacy plaintext is migrated.
    globalThis.fetch = async () => new Response('', { status: 401 });
    assert.equal((await ingest.probeInstagramSession()).valid, false);
    await ingest.loadInstagramSession();
    assert.equal(sessions.hasSession(), false); // Reload does not undo suspension.
    assert.equal((await ingest.instagramAdminStatus()).lastError, 'INSTAGRAM_SESSION_REAUTH_REQUIRED');
    sessions.setSession({ sessionId: id, dsUserId: '123' }, 'admin', true);
    calls = 0;
    globalThis.fetch = async () => { calls++; return new Response('', { status: 429 }); };
    await ingest.runIngestCycle('test-session-block');
    assert.equal(calls, 1); // Discovery reached the endpoint, then stopped instead of trying alternatives.
    assert.equal((await ingest.ingestStatus()).lastError, 'INSTAGRAM_RATE_LIMITED');
    const blockedStatus = await ingest.instagramAdminStatus();
    assert.ok(Array.isArray(blockedStatus.channels));
    assert.equal(blockedStatus.channels[0].error, 'INSTAGRAM_RATE_LIMITED');
    await ingest.clearInstagramSession();
    assert.equal(await store.getSetting('ig_session'), null);
    assert.equal(sessions.sessionInfo().configured, false);
  } finally {
    globalThis.fetch = savedFetch;
    if (savedKey === undefined) delete process.env.IG_SESSION_ENCRYPTION_KEY;
    else process.env.IG_SESSION_ENCRYPTION_KEY = savedKey;
    if (savedImport === undefined) delete process.env.IG_AUTO_IMPORT;
    else process.env.IG_AUTO_IMPORT = savedImport;
    await db?.closeDb();
    rmSync(dir, { recursive: true, force: true });
  }
});
