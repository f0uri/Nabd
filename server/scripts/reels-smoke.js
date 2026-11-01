/**
 * اختبار الريلز: رفع فيديو · قائمة الريلز · مشاهدة · Range · تفاعل · حذف.
 * المتطلب: الخادم شغّال + حسابات smoke:chat (y2/l2).
 * التشغيل: npm run smoke:reels
 */
const GQL = 'http://localhost:4000/graphql';

async function q(query, variables, token) {
  const res = await fetch(GQL, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ query, variables }),
  });
  const json = await res.json();
  if (json.errors) throw Object.assign(new Error(json.errors[0].message), { code: json.errors[0].message });
  return json.data;
}

/** ملف "فيديو" تجريبي — المحتوى ماشي مهم، المهم نوع MIME والـ Range */
const VIDEO_BYTES = Buffer.alloc(256 * 1024, 7);

async function uploadVideo({ text, token, mime = 'video/mp4', bytes = VIDEO_BYTES }) {
  const form = new FormData();
  form.append(
    'operations',
    JSON.stringify({
      query:
        'mutation($t:String!,$file:File,$aspectRatio:Float){createPost(text:$t,file:$file,aspectRatio:$aspectRatio){id text media{id uri kind mime aspectRatio} views}}',
      variables: { t: text, file: null, aspectRatio: 9 / 16 },
    }),
  );
  form.append('map', JSON.stringify({ 0: ['variables.file'] }));
  form.append('0', new Blob([bytes], { type: mime }), 'reel.mp4');
  const res = await fetch(GQL, { method: 'POST', body: form, headers: { authorization: `Bearer ${token}` } });
  const json = await res.json();
  if (json.errors) throw new Error(json.errors[0].message);
  return json.data.createPost;
}

(async () => {
  const a = (await q('mutation($e:String!,$p:String!){login(email:$e,password:$p){token user{name}}}', { e: 'y2@nabd.app', p: 'nabd1234' })).login;
  const b = (await q('mutation($e:String!,$p:String!){login(email:$e,password:$p){token user{name}}}', { e: 'l2@nabd.app', p: 'nabd1234' })).login;

  // 1) رفع ريل
  const reel = await uploadVideo({ text: 'ريل تجريبي: أول فيديو فـ نبض', token: a.token });
  console.log('🎬 ريل مرفوع', `${reel.id} · kind=${reel.media.kind} · mime=${reel.media.mime} · ratio=${reel.media.aspectRatio.toFixed(3)}`);

  // 2) قائمة الريلز (من حساب آخر)
  const list = await q('query{reels{nextCursor posts{id text views media{uri kind aspectRatio} author{name}}}}', null, b.token);
  const found = list.reels.posts.find((p) => p.id === reel.id);
  console.log('📜 الريلز', `${list.reels.posts.length} ريل · تلقّى الجديد؟ ${found ? 'نعم ✓' : 'لا ✗'} · kind=${found?.media.kind}`);

  // 3) الـ Range (تحريك الفيديو)
  const origin = 'http://localhost:4000';
  const full = await fetch(`${origin}${reel.media.uri}`);
  const head = await fetch(`${origin}${reel.media.uri}`, { headers: { Range: 'bytes=0-1023' } });
  const body = Buffer.from(await head.arrayBuffer());
  console.log(
    '📼 بث الفيديو',
    `200 → ${full.status} (${full.headers.get('accept-ranges')}) · 206 → ${head.status} · ${body.length} بايت · ${head.headers.get('content-range')}`,
  );

  // 4) مشاهدة
  const v1 = await q('mutation($id:ID!){viewReel(postId:$id)}', { id: reel.id }, b.token);
  const v2 = await q('mutation($id:ID!){viewReel(postId:$id)}', { id: reel.id }, b.token);
  const v3 = await q('mutation($id:ID!){viewReel(postId:$id)}', { id: reel.id }, a.token);
  console.log('👁️ مشاهدات', `1:${v1.viewReel} · 2 (نفس المستخدم):${v2.viewReel} · 3 (مستخدم آخر):${v3.viewReel}`);

  // 5) تفاعل على الريل
  const reacted = await q('mutation($id:ID!){react(postId:$id,reaction:love){reactions{total} myReaction}}', { id: reel.id }, b.token);
  console.log('❤️ تفاعل على الريل', JSON.stringify(reacted.react));

  // 6) فيديو كبير → مرفوض
  const tooBig = await uploadVideo({
    text: 'كبير',
    token: a.token,
    bytes: Buffer.alloc(19 * 1024 * 1024, 1),
  }).catch((error) => error.message);
  console.log('🚧 فيديو 19MB', tooBig === 'VIDEO_TOO_LARGE' ? '✓ مرفوض' : `✗ ${tooBig}`);

  // 7) حذف
  await q('mutation($id:ID!){deletePost(postId:$id)}', { id: reel.id }, a.token);
  const after = await q('query{reels{posts{id}}}', null, b.token);
  console.log('🗑️ بعد الحذف', `باقي فالريلز؟ ${after.reels.posts.some((p) => p.id === reel.id) ? 'نعم ✗' : 'لا ✓'}`);

  console.log('\n✅ اختبار الريلز داز');
})().catch((error) => {
  console.error('❌ FAIL', error.message);
  process.exit(1);
});
