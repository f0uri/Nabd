/**
 * طبقة الاتصال بقاعدة PostgreSQL.
 *
 * - فالإنتاج: حط `DATABASE_URL=postgres://user:pass@host:5432/nabd` → كيتستعمل السيرفر الحقيقي.
 * - بالتطوير بلا سيرفر: كيتستعمل PGlite (نفس PostgreSQL مُصرَّف لـ WebAssembly) وكيخزّن
 *   الملفات فـ `server/data/pg` — يعني البيانات كتبقى حتى بعد إعادة تشغيل الخادم.
 *
 * الـ API واحد فالحالتين: query(sql, params) بنفس صيغة `$1, $2 …`.
 */
import { readFile } from 'node:fs/promises';
import { mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCHEMA_FILE = join(__dirname, 'schema.sql');
const DATA_DIR = join(__dirname, '..', 'data', 'pg');

let client = null;
let driver = 'none';

/** إنشاء الاتصال + تنفيذ مخطط SQL */
export async function initDb() {
  if (client) return client;

  const url = process.env.DATABASE_URL;

  if (url) {
    const { default: pg } = await import('pg');
    const pool = new pg.Pool({ connectionString: url, max: 10 });
    client = {
      query: async (sql, params = []) => {
        const res = await pool.query(sql, params);
        return { rows: res.rows };
      },
      exec: async (sql) => pool.query(sql),
      close: () => pool.end(),
    };
    driver = 'postgres';
  } else {
    const { PGlite } = await import('@electric-sql/pglite');
    mkdirSync(DATA_DIR, { recursive: true });
    const pg = new PGlite(DATA_DIR);
    await pg.waitReady;
    client = {
      query: async (sql, params = []) => {
        const res = await pg.query(sql, params);
        return { rows: res.rows };
      },
      exec: async (sql) => pg.exec(sql),
      close: () => pg.close(),
    };
    driver = 'pglite';
  }

  const ddl = await readFile(SCHEMA_FILE, 'utf8');
  await client.exec(ddl);

  return client;
}

/** استعلام واحد */
export async function query(sql, params = []) {
  if (!client) await initDb();
  const res = await client.query(sql, params);
  return res.rows;
}

/** أول صف ولا null */
export async function queryOne(sql, params = []) {
  const rows = await query(sql, params);
  return rows[0] ?? null;
}

/** معلومات مختصرة عن القاعدة (للـ /health و GraphQL) */
export async function dbInfo() {
  if (!client) await initDb();
  const rows = await query(
    `select current_database() as database, version() as version, now() as now,
            (select count(*) from users) as users, (select count(*) from posts) as posts`,
  );
  const row = rows[0] ?? {};
  return {
    driver,
    persistent: driver === 'postgres' || driver === 'pglite',
    database: row.database,
    version: String(row.version ?? '').split(' ').slice(0, 2).join(' '),
    users: Number(row.users ?? 0),
    posts: Number(row.posts ?? 0),
  };
}

export async function closeDb() {
  if (client?.close) await client.close();
  client = null;
  driver = 'none';
}
