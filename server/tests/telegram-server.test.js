import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, cp, symlink, writeFile, rm } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const token = 'synthetic-server-only-token';
let dir, child, url, logs = '', account, avatar;
const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=', 'base64');
before(async () => {
  dir = await mkdtemp(join(tmpdir(), 'nabd-telegram-http-'));
  await cp(join(root, 'src'), join(dir, 'src'), { recursive: true });
  await symlink(join(root, 'node_modules'), join(dir, 'node_modules'));
  await writeFile(join(dir, 'package.json'), '{"type":"module"}');
  // Replace Telegram only inside the isolated server process. No real bot or
  // external network request is used, including during startup verification.
  await writeFile(join(dir, 'fake-telegram.mjs'), `
    const files = new Map(); let sequence = 0;
    const result = (value) => Response.json({ok:true,result:value});
    globalThis.fetch = async (input, options = {}) => {
      const url = new URL(String(input));
      if (url.hostname !== 'api.telegram.org') throw new Error('External test request blocked');
      if (url.pathname.endsWith('/getMe')) return result({is_bot:true});
      if (url.pathname.endsWith('/getChat')) return result({id:-1000000000000,type:'channel'});
      if (url.pathname.endsWith('/sendDocument')) {
        const file = options.body.get('document'); const id = file.name.startsWith('broken') ? 'broken' : String(++sequence);
        const bytes = await file.arrayBuffer(); files.set(id, bytes);
        return result({message_id:sequence+1,document:{file_id:id,file_size:bytes.byteLength}});
      }
      if (url.pathname.endsWith('/getFile')) return result({file_path:'files/'+url.searchParams.get('file_id')+'.png'});
      if (url.pathname.includes('/file/bot')) {
        const id = url.pathname.split('/').at(-1).replace('.png','');
        if (id === 'broken') throw new Error('Failure at '+url.href);
        return new Response(files.get(id));
      }
      throw new Error('Unexpected Telegram method');
    };
  `);
  child = spawn(process.execPath, ['--import', join(dir, 'fake-telegram.mjs'), join(dir, 'src/index.js')], {
    cwd: dir,
    env: { ...process.env, NODE_ENV: 'production', PORT: '0', DATABASE_URL: '', AUTH_SECRET: 'synthetic-authentication-key-'.repeat(3), MEDIA_PROVIDER: 'telegram', TELEGRAM_BOT_TOKEN: token, TELEGRAM_CHAT_ID: '-1000000000000', IG_AUTO_IMPORT: '0', ADMIN_EMAILS: '', UPLOAD_DIR: join(dir, 'uploads') },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  child.stderr.on('data', (chunk) => { logs += chunk; });
  await new Promise((resolveReady, reject) => {
    const timer = setTimeout(() => reject(new Error('Server startup timed out')), 30000);
    child.once('exit', () => { clearTimeout(timer); reject(new Error(`Server exited before listening: ${logs}`)); });
    child.stdout.on('data', (chunk) => {
      logs += chunk;
      const match = logs.match(/0\.0\.0\.0:(\d+)\/graphql/);
      if (match) { clearTimeout(timer); url = `http://127.0.0.1:${match[1]}`; resolveReady(); }
    });
  });
});
after(async () => {
  if (child?.exitCode === null) {
    await new Promise((resolveExit) => { child.once('exit', resolveExit); child.kill('SIGTERM'); });
  }
  if (dir) await rm(dir, { recursive: true, force: true });
});
async function gql(query, auth) {
  const response = await fetch(`${url}/graphql`, { method: 'POST', headers: { 'content-type': 'application/json', ...(auth ? { authorization: `Bearer ${auth}` } : {}) }, body: JSON.stringify({ query }) });
  return response.json();
}
async function upload(name) {
  const form = new FormData();
  form.set('operations', JSON.stringify({ query: 'mutation($file:File){updateProfile(file:$file){avatarUrl}}', variables: { file: null } }));
  form.set('map', JSON.stringify({ 0: ['variables.file'] }));
  form.set('0', new Blob([image], { type: 'image/png' }), name);
  return (await fetch(`${url}/graphql`, { method: 'POST', headers: { authorization: `Bearer ${account.token}` }, body: form })).json();
}
test('production health confirms Telegram without exposing server destination/credentials', async () => {
  const response = await fetch(`${url}/health`);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.storage.provider, 'telegram');
  assert.equal(body.storage.chatId, null);
  assert.equal(body.database.persistent, true);
  assert.ok(!JSON.stringify(body).includes(token));
});
test('the first public registrant is not automatically a production administrator', async () => {
  const body = await gql('mutation{register(name:"حساب اختبار",email:"http@example.test",password:"synthetic-password"){token user{id username isAdmin}}}');
  assert.equal(body.errors, undefined);
  account = body.data.register;
  assert.equal(account.user.isAdmin, false);
  assert.ok(account.user.username);
});
test('actual HTTP multipart avatar upload uses the authenticated server-to-Telegram adapter', async () => {
  const body = await upload('avatar.png');
  assert.equal(body.errors, undefined);
  avatar = body.data.updateProfile.avatarUrl;
  assert.match(avatar, /^\/media\/m_/);
  const refreshed = await gql('{me{avatarUrl}}', account.token);
  assert.equal(refreshed.data.me.avatarUrl, avatar);
  const download = await fetch(`${url}${avatar}`);
  assert.equal(download.status, 200);
  assert.deepEqual(Buffer.from(await download.arrayBuffer()), image);
  assert.ok(!JSON.stringify(body).includes(token));
});
test('unauthenticated profile writes are rejected', async () => {
  const body = await gql('mutation{updateProfile(name:"غير مسموح"){id}}');
  assert.match(body.errors[0].message, /UNAUTHENTICATED/);
});
test('media upstream failures reveal neither the bot URL nor network error details', async () => {
  const body = await upload('broken.png');
  assert.equal(body.errors, undefined);
  const response = await fetch(`${url}${body.data.updateProfile.avatarUrl}`);
  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: 'MEDIA_UPSTREAM_ERROR' });
  assert.ok(!logs.includes(token));
});
test('a malformed media route cannot crash the shared server', async () => {
  const response = await fetch(`${url}/media/%ZZ`);
  assert.equal(response.status, 500);
  assert.equal((await fetch(`${url}/health`)).status, 200);
});
