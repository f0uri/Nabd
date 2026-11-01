/**
 * تنظيف الريلز المستوردة الثقيلة (تخزين) — كيتشغّل والخادم **واقف**.
 *
 * كيحيد المنشورات المستوردة من إنستغرام اللي الفيديو ديالها أكبر من حد معين،
 * وكيمسح الملفات من `uploads/` باش ما تتعمرش البلاصة.
 *
 * التشغيل:
 *   node scripts/prune-reels.js            # الحد الافتراضي 20MB
 *   node scripts/prune-reels.js --max-mb 12 --keep 6
 */
import { PGlite } from '@electric-sql/pglite';
import { readFile, unlink, stat } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const DATA_DIR = join(root, 'data', 'pg');
const UPLOAD_DIR = join(root, process.env.UPLOAD_DIR ?? 'uploads');

const args = process.argv.slice(2);
const flag = (name, fallback) => {
  const index = args.indexOf(`--${name}`);
  return index >= 0 ? Number(args[index + 1]) : fallback;
};
const maxMb = flag('max-mb', 20);
const keep = flag('keep', 10);

const db = new PGlite(DATA_DIR);
await db.waitReady;

// كل الريلز المستوردة مرتبة من الأحدث
const rows = await db.query(`
  select p.id, p.text, p.source_author, p.source_url, p.media_id, m.file_id, m.size, p.created_at
  from posts p join media m on m.id = p.media_id
  where p.source = 'instagram'
  order by p.created_at desc
`);

const limitBytes = maxMb * 1024 * 1024;
const toRemove = rows.rows.filter((row, index) => Number(row.size ?? 0) > limitBytes || index >= keep);

let removedBytes = 0;
for (const row of toRemove) {
  if (row.file_id) {
    const path = join(UPLOAD_DIR, row.file_id);
    try {
      const info = await stat(path);
      removedBytes += info.size;
      await unlink(path);
    } catch {
      /* الملف ما كاينش */
    }
  }
  await db.query('delete from reel_views where post_id = $1', [row.id]);
  await db.query('delete from posts where id = $1', [row.id]);
  await db.query('delete from media where id = $1', [row.media_id]);
  console.log(
    `🧹 تحيد ${row.id} · ${(Number(row.size ?? 0) / 1048576).toFixed(1)}MB · @${row.source_author ?? '—'} · ${String(row.text ?? '').slice(0, 40)}`,
  );
}

const left = await db.query(`select count(*)::int as n, coalesce(sum(size),0)::bigint as bytes from media where mime like 'video/%'`);
console.log(
  `📊 بقاو ${left.rows[0].n} فيديو · ${(Number(left.rows[0].bytes) / 1048576).toFixed(1)}MB · ` +
    `تحيد ${(removedBytes / 1048576).toFixed(1)}MB`,
);

await db.close();
