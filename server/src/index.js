import './load-env.js';
import { createServer } from 'node:http';
import { Readable } from 'node:stream';
import { createYoga } from 'graphql-yoga';
import { schema } from './schema.js';
import { userFromRequest } from './auth.js';
import { storage, storageInfo, mirrorStorage } from './storage/index.js';
import { startIngestLoop, loadInstagramSession } from './ingest.js';
import { setUserAdmin, ensureAdminExists, findUserByEmail } from './store.js';
import { getMedia, loadCaches } from './store.js';
import { dbInfo, initDb } from './db.js';
import { attachRealtime, realtimeInfo } from './realtime.js';

const PORT = Number(process.env.PORT ?? 4000);

const yoga = createYoga({
  schema,
  graphqlEndpoint: '/graphql',
  graphiql: process.env.NODE_ENV !== 'production',
  landingPage: false,
  // نُظهر رموز الأخطاء الخاصة بنا (UNAUTHENTICATED, EMAIL_TAKEN…) بدل «Unexpected error»
  maskedErrors: false,
  cors: {
    origin: '*',
    methods: ['GET', 'POST', 'OPTIONS'],
  },
  // كل طلب كيتعرف على المستخدم من التوكن (Authorization: Bearer …)
  context: ({ request }) => {
    const payload = userFromRequest(request);
    return { userId: payload?.sub ?? null };
  },
});

/**
 * بث وسيط (صورة/فيديو) — من تلغرام أو من القرص المحلي.
 * كيدعم HTTP Range (206) باش مشغل الفيديو يقدر يقدّم/يرجّع بلا ما يعاود يحمّل كلشي.
 */
async function handleMedia(req, res, url) {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, { 'Access-Control-Allow-Origin': '*' }).end();
    return;
  }

  const id = decodeURIComponent(url.pathname.replace('/media/', ''));
  const record = await getMedia(id);

  if (!record) {
    res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: 'MEDIA_NOT_FOUND' }));
    return;
  }

  const mime = record.mime ?? 'application/octet-stream';
  const isVideo = mime.startsWith('video/');
  const headers = {
    'Content-Type': mime,
    'Cache-Control': 'public, max-age=31536000, immutable',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges',
    'Accept-Ranges': 'bytes',
  };

  try {
    // ------ قرص محلي: Range مزيان بلا تحميل كامل ------
    // كل مزوّد عندو مسار ديالو: تلغرام كيتحمّل، والقرص المحلي كيتبثّ مباشرة (بالـ Range)
    const local = record.provider === 'telegram' ? null : mirrorStorage;

    if (local && typeof local.streamRange === 'function') {
      const total = await local.size(record.fileId);
      const range = req.headers.range;

      if (range) {
        const match = /^bytes=(\d*)-(\d*)$/.exec(String(range));
        if (match) {
          const start = match[1] ? Number(match[1]) : 0;
          const end = match[2] ? Number(match[2]) : Math.min(start + 2 * 1024 * 1024 - 1, total - 1);
          if (start >= total) {
            res.writeHead(416, { 'Content-Range': `bytes */${total}` }).end();
            return;
          }
          const part = await local.streamRange(record.fileId, start, end);
          res.writeHead(206, {
            ...headers,
            'Content-Length': part.end - part.start + 1,
            'Content-Range': `bytes ${part.start}-${part.end}/${total}`,
          });
          part.body.pipe(res);
          return;
        }
      }

      const full = await local.stream(record.fileId);
      res.writeHead(200, { ...headers, 'Content-Length': total });
      full.body.pipe(res);
      return;
    }

    // ------ تلغرام: كيتحمّل من تلغرام وكنخدموه ------
    const upstream = await storage.download(record.fileId);

    if (isVideo && req.headers.range) {
      // تلغرام ما كيدعمش Range → كنحمّلو ونقسّمو (الفيديوهات محدودة بـ18MB)
      const buffer = Buffer.from(await upstream.arrayBuffer());
      const total = buffer.length;
      const match = /^bytes=(\d*)-(\d*)$/.exec(String(req.headers.range));
      const start = match?.[1] ? Number(match[1]) : 0;
      const end = match?.[2] ? Math.min(Number(match[2]), total - 1) : total - 1;
      if (start >= total) {
        res.writeHead(416, { 'Content-Range': `bytes */${total}` }).end();
        return;
      }
      const slice = buffer.subarray(start, end + 1);
      res.writeHead(206, {
        ...headers,
        'Content-Length': slice.length,
        'Content-Range': `bytes ${start}-${end}/${total}`,
      });
      res.end(slice);
      return;
    }

    const length = record.size && record.size > 0 ? Number(record.size) : null;
    res.writeHead(200, { ...headers, ...(length ? { 'Content-Length': length } : {}) });
    const stream = Readable.fromWeb(upstream.body);
    stream.once('error', () => { console.error('[media] UPSTREAM_STREAM_ERROR'); res.destroy(); });
    stream.pipe(res);
  } catch (error) {
    console.error('[media] MEDIA_UPSTREAM_ERROR');
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: 'MEDIA_UPSTREAM_ERROR' }));
  }
}

const handleRequest = async (req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);

  if (url.pathname === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(
      JSON.stringify({
        status: 'ok',
        uptime: Math.round(process.uptime()),
        storage: storageInfo,
        realtime: realtimeInfo(),
        database: await dbInfo().catch(() => ({ driver: 'unknown' })),
      }),
    );
    return;
  }

  if (url.pathname.startsWith('/media/')) {
    await handleMedia(req, res, url);
    return;
  }

  if (url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!DOCTYPE html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8"><title>Nabd API</title>
<style>body{font-family:system-ui,Tahoma,sans-serif;max-width:680px;margin:40px auto;padding:0 20px;line-height:1.8;color:#111}
code{background:#F0F2F5;padding:2px 6px;border-radius:4px}a{color:#1877F2}
.tag{display:inline-block;background:#E7F3FF;color:#1877F2;padding:2px 10px;border-radius:999px;font-size:12px}</style></head>
<body>
<span class="tag">Nabd Server v1</span>
<h1>خادم «نبض» يعمل ✅</h1>
<ul>
  <li>GraphQL: <a href="/graphql"><code>/graphql</code></a> (${process.env.NODE_ENV === 'production' ? 'API' : 'GraphiQL مفتوح'})</li>
  <li>الوسائط: <code>/media/:id</code></li>
  <li>الحالة: <a href="/health"><code>/health</code></a></li>
</ul>
<p>مزوّد التخزين الحالي: <b>${storageInfo.provider}</b> — ${storageInfo.note}</p>
<h3>جرّب في GraphiQL</h3>
<pre><code>mutation {
  react(postId: "p2", reaction: love) { id myReaction reactions { total } }
}</code></pre>
</body></html>`);
    return;
  }

  await yoga(req, res);
};
const server = createServer((req, res) => {
  handleRequest(req, res).catch(() => {
    console.error('[http] REQUEST_FAILED');
    if (res.headersSent) { res.destroy(); return; }
    res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: 'REQUEST_FAILED' }));
  });
});

// WebSocket للتحديث اللحظي (الرسائل، القراءة، «يكتب الآن…»)
attachRealtime(server);

await storage.init?.();

// Fail before accepting users if Telegram credentials/destination are not usable.
if (storageInfo.provider === 'telegram') await storage.checkConnection();

// تهيئة PostgreSQL + تحميل الكاش قبل ما نستقبلو الطلبات
await initDb();
const cached = await loadCaches();
console.log(`🗄️  PostgreSQL جاهز (${(await dbInfo()).driver}) — ${cached.users} مستخدم · ${cached.friendships} صداقة`);

// لوحة الأدمن: جلسة إنستغرام كتجي من قاعدة البيانات (اللي كيحطها الأدمن من التطبيق)
await loadInstagramSession();

// أول مستخدم كيولّي أدمن أوتوماتيكياً (+ ADMIN_EMAILS إلا كان)
const adminEmails = (process.env.ADMIN_EMAILS ?? '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);
for (const email of adminEmails) {
  const user = findUserByEmail(email);
  if (user && !user.isAdmin) {
    await setUserAdmin(user.id, true);
    console.log(`👑 ${user.name} (<${email}>) ولّا أدمن`);
  }
}
// Never promote an arbitrary first registrant on a public production deployment.
const promoted = process.env.NODE_ENV === 'production' ? null : await ensureAdminExists();
if (promoted) console.log(`👑 أقدم مستخدم ولّا أدمن: ${promoted.name} (${promoted.email})`);

server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 NABD server → http://0.0.0.0:${server.address().port}/graphql`);
  console.log(`📦 media storage → ${storageInfo.provider} (${storageInfo.note})`);
  startIngestLoop();
});

const shutdown = () => {
  console.log('\n👋 إغلاق الخادم…');
  server.close(() => process.exit(0));
};
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
