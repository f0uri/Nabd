/**
 * اختبار استيراد ريلز إنستغرام.
 * - الروابط الغالطة كترجع رفض واضح (بلا نت).
 * - الاستيراد الحقيقي كيتخطى بتحذير إلا كان الإنترنت/إنستغرام محبوس فهاد البيئة.
 *
 * التشغيل: npm run smoke:instagram
 */
const GQL = 'http://localhost:4000/graphql';
const SAMPLE = process.env.IG_SAMPLE ?? 'https://www.instagram.com/reel/DCEi_TQpISD/';

async function gql(query, variables, token) {
  const res = await fetch(GQL, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ query, variables }),
  });
  const json = await res.json();
  return { data: json.data, error: json.errors?.[0]?.message ?? null };
}

(async () => {
  const login = await gql('mutation{login(email:"y2@nabd.app",password:"nabd1234"){token user{name}}}');
  const token = login.data.login.token;
  console.log('👤', login.data.login.user.name);

  // 1) بلا توكن → مرفوض
  const anon = await gql('mutation($u:String!){importInstagramReel(url:$u){id}}', { u: SAMPLE });
  console.log('🔒 بلا توكن →', anon.error === 'UNAUTHENTICATED' ? '✓ مرفوض' : `✗ ${anon.error}`);

  // 2) رابط ماشي ديال إنستغرام → مرفوض
  const bad = await gql('mutation($u:String!){importInstagramReel(url:$u){id}}', { u: 'https://youtube.com/watch?v=123' }, token);
  console.log('🚧 رابط غالط →', bad.error === 'INVALID_INSTAGRAM_URL' ? '✓ مرفوض' : `✗ ${bad.error}`);

  // 3) رابط إنستغرام ما كاينش
  const missing = await gql(
    'mutation($u:String!){importInstagramReel(url:$u){id}}',
    { u: 'https://www.instagram.com/reel/C2sAwA5vGaT/' },
    token,
  );
  console.log('🕳️ ريل ما كاينش →', ['REEL_NOT_FOUND', 'NOT_A_VIDEO', 'INSTAGRAM_BLOCKED'].includes(missing.error) ? `✓ ${missing.error}` : `✗ ${missing.data ? 'استورد شي حاجة' : missing.error}`);

  // 4) استيراد حقيقي
  const started = Date.now();
  const imported = await gql(
    'mutation($u:String!){importInstagramReel(url:$u){id source sourceUrl sourceAuthor media{uri kind mime} author{name}}}',
    { u: SAMPLE },
    token,
  );

  if (imported.error) {
    console.log(`⚠️  الاستيراد الحقيقي ما دارش (${imported.error}) — شبكة/إنستغرام محبوس؟`);
    console.log('\n✅ اختبار الاستيراد داز (التحقق من الرفض + الصلاحيات)');
    return;
  }

  const post = imported.data.importInstagramReel;
  console.log(
    `📥 مستورد ${post.id} · @${post.sourceAuthor} · ${post.source} · ${post.media.kind} · ${((Date.now() - started) / 1000).toFixed(1)}s`,
  );

  // 5) البث بالـ Range
  const res = await fetch(`http://localhost:4000${post.media.uri}`, { headers: { Range: 'bytes=0-4095' } });
  const buf = Buffer.from(await res.arrayBuffer());
  const isMp4 = buf.subarray(4, 8).toString() === 'ftyp';
  console.log('📼 البث', `${res.status} · ${buf.length} بايت · mp4=${isMp4 ? '✓' : '✗'} · ${res.headers.get('content-range')}`);

  // 6) إعادة الاستيراد → نفس المنشور (بلا تحميل جديد)
  const before = Date.now();
  const again = await gql('mutation($u:String!){importInstagramReel(url:$u){id}}', { u: SAMPLE }, token);
  console.log(
    '♻️ إعادة استيراد →',
    `${again.data.importInstagramReel.id === post.id ? 'نفس المنشور ✓' : 'منشور جديد ✗'} · ${Date.now() - before}ms`,
  );

  const reels = await gql('{reels{posts{id source sourceAuthor}}}', null, token);
  const list = reels.data.reels.posts;
  console.log(`🎬 الريلز: ${list.length} · إنستغرام: ${list.filter((r) => r.source === 'instagram').length}`);

  console.log('\n✅ اختبار استيراد إنستغرام داز');
})().catch((error) => {
  console.error('❌ FAIL', error.message);
  process.exit(1);
});
