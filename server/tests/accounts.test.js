import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, cp, symlink, writeFile, readFile, rm, mkdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { PGlite } from '@electric-sql/pglite';
import { graphql } from 'graphql';
import { normalizeUsername } from '../src/usernames.js';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
let dir, store, db, schema;
const originalUpload = process.env.UPLOAD_DIR;
const originalProvider = process.env.MEDIA_PROVIDER;
const originalDatabase = process.env.DATABASE_URL;
before(async () => {
  // Run actual DB/store/schema code against an isolated persistent legacy database.
  // No developer database, credentials or real user media are touched.
  dir = await mkdtemp(join(tmpdir(), 'nabd-accounts-'));
  await cp(join(root, 'src'), join(dir, 'src'), { recursive: true });
  await symlink(join(root, 'node_modules'), join(dir, 'node_modules'), 'dir');
  await writeFile(join(dir, 'package.json'), '{"type":"module"}');
  await mkdir(join(dir, 'data'), { recursive: true });
  const pg = new PGlite(join(dir, 'data', 'pg'));
  const ddl = await readFile(join(root, 'src/schema.sql'), 'utf8');
  await pg.exec(ddl.split('/* Stable handles for existing accounts;')[0]);
  for (const id of ['legacy_a', 'legacy_b']) {
    await pg.query(`insert into users (id,name,email,password_hash,initial,avatar_media_id) values ($1,'ياسين',$2,'test-hash','ي','m_legacy')`, [id, `${id}@example.test`]);
  }
  await pg.close();
  delete process.env.DATABASE_URL;
  process.env.UPLOAD_DIR = join(dir, 'uploads');
  process.env.MEDIA_PROVIDER = 'local';
  db = await import(pathToFileURL(join(dir, 'src/db.js')));
  store = await import(pathToFileURL(join(dir, 'src/store.js')));
  schema = (await import(pathToFileURL(join(dir, 'src/schema.js')))).schema;
  await db.initDb();
  await store.loadCaches();
});
after(async () => {
  await db?.closeDb();
  if (dir) await rm(dir, { recursive: true, force: true });
  for (const [name, value] of [['UPLOAD_DIR', originalUpload], ['MEDIA_PROVIDER', originalProvider], ['DATABASE_URL', originalDatabase]]) {
    if (value === undefined) delete process.env[name]; else process.env[name] = value;
  }
});
const create = (username, email) => store.createUser({ name: 'اسم مكرر', email: email ?? `${username}@example.test`, password: 'synthetic-password', color: '#123456', username });

test('legacy accounts receive distinct stable handles without losing their avatars', () => {
  const a = store.users.get('legacy_a'); const b = store.users.get('legacy_b');
  assert.notEqual(a.username, b.username);
  assert.match(a.username, /^[a-z][a-z0-9_]{2,39}$/);
  assert.equal(store.publicUser(a).avatarUrl, '/media/m_legacy');
});
test('handles normalize @, whitespace and case; invalid handles are rejected', () => {
  assert.equal(normalizeUsername(' @Yassine_24 '), 'yassine_24');
  for (const value of ['', 'ab', '9abc', 'a.b', 'two names', 'مستخدم', 'x'.repeat(41), null]) {
    assert.throws(() => normalizeUsername(value), /INVALID_USERNAME/);
  }
});
test('new accounts get persisted generated handles when registration omits one', async () => {
  const a = await create(undefined, 'generated-a@example.test');
  const b = await create(undefined, 'generated-b@example.test');
  assert.notEqual(a.username, b.username);
  assert.match(a.username, /^[a-z][a-z0-9_]{2,39}$/);
});
test('database enforces case-insensitive uniqueness, including concurrent registrations', async () => {
  const results = await Promise.allSettled([create('Unique_One', 'unique-a@example.test'), create('UNIQUE_ONE', 'unique-b@example.test')]);
  assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1);
  assert.equal(results.find((r) => r.status === 'rejected').reason.message, 'USERNAME_TAKEN');
});
test('duplicate display names can be disambiguated by handle and @handle search', async () => {
  const a = await create('lookup_alpha'); const b = await create('lookup_beta');
  assert.deepEqual(store.searchUsers({ search: '@LOOKUP_ALPHA' }).map((u) => u.id), [a.id]);
  assert.ok(store.searchUsers({ search: 'اسم مكرر', limit: 100 }).some((u) => u.id === b.id));
  assert.equal(store.searchUsers({ search: 'lookup_alpha' })[0].id, a.id);
});
test('users cannot claim an occupied handle and failed updates preserve the original', async () => {
  const a = await create('occupied_handle'); const b = await create('original_handle');
  await assert.rejects(store.updateUserProfile(b.id, { username: a.username.toUpperCase() }), /USERNAME_TAKEN/);
  assert.equal(store.users.get(b.id).username, 'original_handle');
  assert.equal((await db.queryOne('select username from users where id=$1', [b.id])).username, 'original_handle');
});
test('GraphQL profile updates require authentication', async () => {
  const response = await graphql({ schema, source: 'mutation { updateProfile(username:"unauthorized") { username } }', contextValue: {} });
  assert.match(response.errors[0].message, /UNAUTHENTICATED/);
});
test('a real avatar upload and handle survive database/cache restart and repeat migration', async () => {
  const account = await create('avatar_owner');
  const response = await graphql({
    schema,
    source: 'mutation($file:File){updateProfile(username:"Avatar_Updated",file:$file){id username avatarUrl}}',
    contextValue: { userId: account.id },
    variableValues: { file: new File([Buffer.from('synthetic-image')], 'avatar.png', { type: 'image/png' }) },
  });
  assert.equal(response.errors, undefined);
  const saved = response.data.updateProfile;
  assert.equal(saved.username, 'avatar_updated');
  assert.match(saved.avatarUrl, /^\/media\/m_/);
  const media = await db.queryOne('select * from media where id=$1', [saved.avatarUrl.split('/').at(-1)]);
  assert.equal((await readFile(join(dir, 'uploads', media.file_id))).toString(), 'synthetic-image');
  const before = store.users.get('legacy_a').username;
  await db.closeDb(); await db.initDb(); await store.loadCaches();
  assert.equal(store.users.get('legacy_a').username, before);
  const refreshed = await graphql({ schema, source: '{me {username avatarUrl}}', contextValue: { userId: account.id } });
  assert.equal(refreshed.errors, undefined);
  assert.equal(refreshed.data.me.username, 'avatar_updated');
  assert.equal(refreshed.data.me.avatarUrl, saved.avatarUrl);
});
test('legacy registration arguments still work and expose a unique handle', async () => {
  const response = await graphql({ schema, source: 'mutation{register(name:"حساب جديد",email:"graphql@example.test",password:"test-password"){user{id username}}}' });
  assert.equal(response.errors, undefined);
  assert.ok(response.data.register.user.username);
});
