/**
 * تنظيف بيانات الاختبار (منشورات دخانية) — كيتشغّل والخادم **واقف**
 * (PGlite كيسمح باتصال واحد فقط).
 *
 * التشغيل: node scripts/clean-test-data.js
 */
import { PGlite } from '@electric-sql/pglite';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(here, '..', 'data', 'pg');

// كل النصوص اللي كتكتبها اختبارات الدخان (features · persist · sweep · reels)
const TEST_TEXTS = [
  'curl post',
  'منشور بصور',
  'منشور تجريبي للبحث فالنبض',
  'منشور للأصدقاء فقط',
  'أول منشور محفوظ فـ PostgreSQL',
  'منشور ديال الاختبار — كيتحيد',
  'ريل تجريبي: أول فيديو فـ نبض',
];

const db = new PGlite(DATA_DIR);
await db.waitReady;

const removed = await db.query(
  `delete from posts where text = any($1::text[]) returning id, text`,
  [TEST_TEXTS],
);
console.log(`🧹 تحيدو ${removed.rows.length} منشور ديال الاختبار`);
removed.rows.forEach((row) => console.log('   -', row.text));

// تنضيف الإشعارات والتعليقات المعلّقة (احتياط)
await db.query(`delete from notifications where text like '%تجريبي%' or text like '%curl%'`);
await db.query(`delete from comments where text in ('تعليق محفوظ')`);
// الريلز اللي بقاو من الاختبار (فيديو بلا مشاهدات حقيقية)
await db.query(`delete from posts where media_id in (select id from media where mime like 'video/%') and text like '%تجريبي%'`);

const counts = await db.query(`
  select
    (select count(*)::int from users)          as users,
    (select count(*)::int from posts)          as posts,
    (select count(*)::int from messages)       as messages,
    (select count(*)::int from notifications)  as notifications
`);
console.log('📊', counts.rows[0]);
await db.close();
