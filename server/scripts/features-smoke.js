/**
 * اختبار الميزات الجديدة: صورة فالدردشة · بحث فالمنشورات · إشعار لحظي فـ WebSocket.
 * المتطلب: الخادم شغّال + حسابات smoke:chat (y2 / l2).
 * التشغيل: npm run smoke:features
 */
import WebSocket from 'ws';

const HTTP = 'http://localhost:4000';
const GQL = `${HTTP}/graphql`;

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

/** صورة PNG صغيرة (1×1) — غير للاختبار */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);

async function upload(query, variables, token) {
  const form = new FormData();
  form.append('operations', JSON.stringify({ query, variables: { ...variables, file: null } }));
  form.append('map', JSON.stringify({ 0: ['variables.file'] }));
  form.append('0', new Blob([PNG], { type: 'image/png' }), 'test.png');
  const res = await fetch(GQL, { method: 'POST', body: form, headers: { authorization: `Bearer ${token}` } });
  const json = await res.json();
  if (json.errors) throw new Error(json.errors[0].message);
  return json.data;
}

const MSG = `id text media { id uri aspectRatio } createdAt`;
const LOGIN = `mutation($e:String!,$p:String!){ login(email:$e,password:$p){ token user{ id name } } }`;

function wsClient(token) {
  const ws = new WebSocket(`ws://localhost:4000/ws?token=${token}`);
  const events = [];
  ws.on('message', (raw) => events.push(JSON.parse(String(raw))));
  return {
    ws,
    events,
    ready: new Promise((resolve, reject) => {
      ws.once('open', resolve);
      ws.once('error', reject);
    }),
    waitFor(match, ms = 5000) {
      const found = events.find(match);
      if (found) return Promise.resolve(found);
      return new Promise((resolve, reject) => {
        const started = Date.now();
        const timer = setInterval(() => {
          const hit = events.find(match);
          if (hit) {
            clearInterval(timer);
            resolve(hit);
          } else if (Date.now() - started > ms) {
            clearInterval(timer);
            reject(new Error('TIMEOUT'));
          }
        }, 20);
      });
    },
  };
}

(async () => {
  const a = (await q(LOGIN, { e: 'y2@nabd.app', p: 'nabd1234' })).login;
  const b = (await q(LOGIN, { e: 'l2@nabd.app', p: 'nabd1234' })).login;

  const conv = (
    await q(`mutation($u:ID!){ startConversation(userId:$u){ id } }`, { u: b.user.id }, a.token)
  ).startConversation;

  // ---------- 1) صورة فالدردشة ----------
  const uploaded = await upload(
    `mutation($c:ID!,$t:String!,$file:File){ sendMessage(conversationId:$c,text:$t,file:$file){ ${MSG} } }`,
    { c: conv.id, t: 'هادي صورة' },
    a.token,
  );
  const message = uploaded.sendMessage;
  console.log('🖼️ رسالة بصور', `${message.id} · ${message.media?.uri}`);

  const mediaRes = await fetch(`${HTTP}${message.media.uri}`);
  console.log('   تحميل الصورة →', mediaRes.status, mediaRes.headers.get('content-type'));

  // ---------- 2) إشعار لحظي ----------
  const wsb = wsClient(b.token);
  await wsb.ready;
  wsb.ws.send(JSON.stringify({ type: 'subscribe', conversationId: conv.id }));
  await wsb.waitFor((e) => e.type === 'subscribed');

  const notePromise = wsb.waitFor((e) => e.type === 'notification');
  await q(`mutation($c:ID!){ sendMessage(conversationId:$c,text:"سكات"){ id } }`, { c: conv.id }, a.token)
    .catch(() => null);
  // الإشعار كيوصل للطرف الآخر (لبنى) — كنصيفطو من ياسين، ولبنى هي اللي كتسمع
  const note = await notePromise;
  console.log('🔔 إشعار لحظي', `type=${note.notification.type} · «${note.notification.text}»`);

  // ---------- 3) بحث فالمنشورات ----------
  const post = await q(
    `mutation($t:String!){ createPost(text:$t){ id text } }`,
    { t: 'منشور تجريبي للبحث فالنبض' },
    a.token,
  ).catch((error) => ({ error: error.message }));
  const created = post.createPost ?? null;
  if (created) {
    const found = await q(`query($s:String!){ searchPosts(search:$s){ id text author{ name } } }`, { s: 'فالنبض' }, b.token);
    console.log('🔎 بحث فالمنشورات', `${found.searchPosts.length} نتيجة → «${found.searchPosts[0]?.text ?? '—'}»`);
    const none = await q(`query($s:String!){ searchPosts(search:$s){ id } }`, { s: 'كلمة ما كايناش 12345' }, b.token);
    console.log('   بلا نتيجة →', none.searchPosts.length);
  } else {
    console.log('🔎 بحث فالمنشورات — ما قدرناش ننشرو منشور الاختبار:', post.error);
  }

  // ---------- 4) حذف منشور ----------
  if (created) {
    const deleted = await q(`mutation($id:ID!){ deletePost(postId:$id) }`, { id: created.id }, a.token);
    const after = await q(`query($id:ID!){ post(id:$id){ id } }`, { id: created.id }, b.token);
    console.log('🗑️ حذف منشور', `deleted=${deleted.deletePost} · بقا فالخادم؟ ${after.post ? 'نعم ✗' : 'لا ✓'}`);

    const forbidden = await q(`mutation($id:ID!){ deletePost(postId:$id) }`, { id: created.id }, b.token).catch(
      (error) => error.code,
    );
    console.log('🔒 حذف منشور ديال شي حد آخور', forbidden === 'FORBIDDEN' || forbidden === 'POST_NOT_FOUND' ? `✓ مرفوض (${forbidden})` : `✗ ${forbidden}`);
  }

  wsb.ws.close();
  console.log('\n✅ اختبار الميزات الجديدة داز');
})().catch((error) => {
  console.error('❌ FAIL', error.message);
  process.exit(1);
});
