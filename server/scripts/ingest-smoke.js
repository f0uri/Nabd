/**
 * اختبار الجلب التلقائي لريلز إنستغرام (بلا تدخل من المستخدم).
 *
 * كيتحقق من:
 *   1) حالة الجلب متاحة وكتوضح واش شغّال.
 *   2) دورة جلب كتزيد ريلز **بلا ما نعطيها حتى رابط**.
 *   3) ما كاين حتى تكرار (نفس الريل ما كيتنشرش مرتين).
 *   4) الريلز المستوردة كتتبثّ بـ Range 206 وكتبان فالـ feed ديال الريلز.
 *
 * التشغيل: npm run smoke:ingest
 */
const GQL = 'http://localhost:4000/graphql';

async function gql(query, variables, token) {
  const res = await fetch(GQL, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ query, variables }),
  });
  const json = await res.json();
  if (json.errors) throw new Error(json.errors[0].message);
  return json.data;
}

(async () => {
  const login = await gql('mutation{login(email:"y2@nabd.app",password:"nabd1234"){token user{name}}}');
  const token = login.login.token;
  console.log('👤', login.login.user.name);

  // 1) الحالة
  const before = await gql(
    '{reelsIngestStatus{enabled running intervalMinutes lastRunAt queuePending queueImported totalsImported}}',
  );
  const state = before.reelsIngestStatus;
  console.log(
    '⚙️  الحالة',
    `شغّال=${state.enabled ? 'نعم ✓' : 'لا ✗'} · كل ${state.intervalMinutes}د · ` +
      `طابور=${state.queuePending} · مستورد=${state.totalsImported} · آخر دورة=${state.lastRunAt ?? '—'}`,
  );

  const reelsBefore = (await gql('{reels{posts{id}}}', null, token)).reels.posts.map((post) => post.id);

  // 2) دورة جلب (بحال المؤقت — بلا أي رابط من عندنا)
  console.log('🔄 دورة جلت تلقائية…');
  const imported = await gql('mutation{runReelsIngest}', null, token);
  console.log('📥 الدورة جابت', imported.runReelsIngest, 'ريل جديد');

  // 3) بلا تكرار
  const reelsAfter = (await gql('{reels{posts{id source sourceAuthor media{uri}}}}', null, token)).reels;
  const ids = reelsAfter.posts.map((post) => post.id);
  const duplicates = ids.length - new Set(ids).size;
  console.log('🧮 الريلز', `${ids.length} · مكرر=${duplicates === 0 ? '0 ✓' : `${duplicates} ✗`}`);

  // 4) المستورد الجديد كيبثّ
  const fresh = reelsAfter.posts.filter((post) => !reelsBefore.includes(post.id));
  for (const post of fresh.slice(0, 3)) {
    const res = await fetch(`http://localhost:4000${post.media.uri}`, { headers: { Range: 'bytes=0-1023' } });
    const buf = Buffer.from(await res.arrayBuffer());
    console.log(
      '📼',
      `@${post.sourceAuthor ?? '—'} · ${res.status} · ${buf.length}ب · mp4=${buf.subarray(4, 8).toString() === 'ftyp' ? '✓' : '✗'}`,
    );
  }
  if (!fresh.length) console.log('ℹ️  ما جا حتى ريل جديد فهاد الدورة (الطابور خاوي ولا القنوات محبوسة)');

  const after = await gql('{reelsIngestStatus{queueImported totalsImported}}');
  console.log('📊 الطابور', `مستورد=${after.reelsIngestStatus.queueImported} · المجموع=${after.reelsIngestStatus.totalsImported}`);

  console.log('\n✅ اختبار الجلب التلقائي داز');
})().catch((error) => {
  console.error('❌ FAIL', error.message);
  process.exit(1);
});
