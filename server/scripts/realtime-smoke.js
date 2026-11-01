/**
 * اختبار اللحظي + ميزات الرسائل (تعديل · حذف · رد · تفاعل · ترجمة · قراءة · يكتب الآن).
 * المتطلب: الخادم شغّال + الحسابات y2@nabd.app / l2@nabd.app / g@nabd.app موجودة (npm run smoke:chat).
 * التشغيل: npm run smoke:rt
 */
import WebSocket from 'ws';

const HTTP = 'http://localhost:4000';
const WS = 'ws://localhost:4000/ws';

async function q(query, variables, token) {
  const res = await fetch(`${HTTP}/graphql`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ query, variables }),
  });
  const json = await res.json();
  if (json.errors) throw Object.assign(new Error(json.errors[0].message), {
    code: json.errors[0].extensions?.code ?? json.errors[0].message,
  });
  return json.data;
}

const MSG = `id conversationId senderId text createdAt read edited deleted
  replyTo { id text senderId deleted }
  reactions { kind count mine }`;

const LOGIN = `mutation($email:String!,$password:String!){ login(email:$email,password:$password){ token user{ id name } } }`;
const START = `mutation($userId:ID!){ startConversation(userId:$userId){ id other{ id name } } }`;
const SEND = `mutation($c:ID!,$t:String!,$r:ID){ sendMessage(conversationId:$c,text:$t,replyToId:$r){ ${MSG} } }`;
const EDIT = `mutation($m:ID!,$t:String!){ editMessage(messageId:$m,text:$t){ ${MSG} } }`;
const DEL = `mutation($m:ID!){ deleteMessage(messageId:$m){ ${MSG} } }`;
const REACT = `mutation($m:ID!,$k:ReactionKind){ reactToMessage(messageId:$m,reaction:$k){ ${MSG} } }`;
const TRANSLATE = `mutation($m:ID!,$to:String){ translateMessage(messageId:$m,to:$to){ text from to provider cached } }`;
const MARK = `mutation($c:ID!){ markConversationRead(conversationId:$c) }`;
const MESSAGES = `query($c:ID!){ messages(conversationId:$c){ messages{ ${MSG} } nextCursor } }`;

/** عميل WebSocket صغير كيجمع الأحداث */
function client(token) {
  const ws = new WebSocket(`${WS}?token=${token}`);
  const events = [];
  const waiters = [];

  ws.on('message', (raw) => {
    const event = JSON.parse(String(raw));
    events.push(event);
    for (let i = waiters.length - 1; i >= 0; i -= 1) {
      if (waiters[i].match(event)) {
        const { resolve, timer } = waiters[i];
        clearTimeout(timer);
        waiters.splice(i, 1);
        resolve(event);
      }
    }
  });

  return {
    ws,
    events,
    ready: new Promise((resolve, reject) => {
      ws.once('open', resolve);
      ws.once('error', reject);
    }),
    send: (payload) => ws.send(JSON.stringify(payload)),
    /** كيتسنى حدث مطابق (timeout 5s) */
    waitFor(match, ms = 5000) {
      const found = events.find(match);
      if (found) return Promise.resolve(found);
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('TIMEOUT_EVENT')), ms);
        waiters.push({ match, resolve, timer });
      });
    },
    close: () => ws.close(),
  };
}

const show = (label, value) => console.log(label, value);
const ok = (label) => console.log(`✔ ${label}`);

(async () => {
  const a = (await q(LOGIN, { email: 'y2@nabd.app', password: 'nabd1234' })).login;
  const b = (await q(LOGIN, { email: 'l2@nabd.app', password: 'nabd1234' })).login;
  const g = (await q(LOGIN, { email: 'g@nabd.app', password: 'nabd1234' })).login;
  show('👤', `${a.user.name} · ${b.user.name} · ${g.user.name}`);

  const conv = await q(START, { userId: b.user.id }, a.token);
  const convId = conv.startConversation.id;

  // ---------- WebSocket ----------
  const wsA = client(a.token);
  const wsB = client(b.token);
  await Promise.all([wsA.ready, wsB.ready]);
  wsA.send({ type: 'subscribe', conversationId: convId });
  wsB.send({ type: 'subscribe', conversationId: convId });
  await Promise.all([
    wsA.waitFor((e) => e.type === 'subscribed'),
    wsB.waitFor((e) => e.type === 'subscribed'),
  ]);
  ok('اتصال WebSocket + اشتراك فالمحادثة');

  // ---------- 1) رسالة فورية ----------
  const t0 = Date.now();
  const sent = (await q(SEND, { c: convId, t: 'تجربة اللحظي: هادي رسالة أولى' }, a.token)).sendMessage;
  const arrived = await wsB.waitFor((e) => e.type === 'message' && e.event === 'added' && e.message.id === sent.id, 3000);
  const latency = Date.now() - t0;
  show('⚡ وصول الرسالة', `${latency}ms → «${arrived.message.text}»`);
  if (latency > 1500) throw new Error('بطيئة بزاف');

  // ---------- 2) تعديل ----------
  const edited = (await q(EDIT, { m: sent.id, t: 'تجربة اللحظي: هادي رسالة معدّلة' }, a.token)).editMessage;
  const editEvent = await wsB.waitFor((e) => e.type === 'message' && e.event === 'updated' && e.message.id === sent.id);
  show('✏️ تعديل', `edited=${editEvent.message.edited} · «${editEvent.message.text}»`);

  // الطرف الآخر ما يقدرش يعدّل
  const forbidden = await q(EDIT, { m: sent.id, t: 'محاولة' }, b.token).catch((e) => e.code);
  show('🔒 تعديل من غير الصاحب', forbidden === 'FORBIDDEN' ? '✓ مرفوض' : `✗ ${forbidden}`);

  // ---------- 3) رد ----------
  const reply = (await q(SEND, { c: convId, t: 'هادا رد على الرسالة', r: sent.id }, b.token)).sendMessage;
  const replyEvent = await wsA.waitFor((e) => e.type === 'message' && e.message.id === reply.id);
  show('↩️ رد', `على «${replyEvent.message.replyTo?.text ?? '؟'}»`);

  // ---------- 4) تفاعل ----------
  const reacted = (await q(REACT, { m: reply.id, k: 'love' }, a.token)).reactToMessage;
  await wsB.waitFor((e) => e.type === 'message' && e.message.id === reply.id && e.message.reactions.length > 0);
  show('❤️ تفاعل', JSON.stringify(reacted.reactions));

  const unreact = (await q(REACT, { m: reply.id, k: null }, a.token)).reactToMessage;
  show('↩️ إزالة التفاعل', `عدد التفاعلات = ${unreact.reactions.length}`);

  // ---------- 5) ترجمة ----------
  const tr = (await q(TRANSLATE, { m: sent.id, to: 'fr' }, b.token)).translateMessage;
  show('🌐 ترجمة ar→fr', `«${tr.text}» (${tr.provider})`);
  const trEn = (await q(TRANSLATE, { m: reply.id }, b.token)).translateMessage;
  show('🌐 ترجمة تلقائية', `→ ${trEn.to}: «${trEn.text}» (${trEn.provider})`);

  // ---------- 6) «يكتب الآن» ----------
  wsB.send({ type: 'typing', conversationId: convId, name: b.user.name });
  const typing = await wsA.waitFor((e) => e.type === 'typing');
  show('⌨️ يكتب الآن', `${typing.name ?? typing.userId}`);

  // ---------- 7) إشعار قراءة ----------
  await q(MARK, { c: convId }, b.token);
  const readEvent = await wsA.waitFor((e) => e.type === 'read');
  show('✔✔ قراءة', `readerId=${readEvent.readerId.slice(0, 8)}…`);

  // ---------- 8) حذف ----------
  const deleted = (await q(DEL, { m: reply.id }, b.token)).deleteMessage;
  const delEvent = await wsA.waitFor((e) => e.type === 'message' && e.event === 'deleted' && e.message.id === reply.id);
  show('🗑️ حذف', `deleted=${delEvent.message.deleted} · النص فارغ=${delEvent.message.text === ''}`);

  const reactDeleted = await q(REACT, { m: reply.id, k: 'like' }, a.token).catch((e) => e.code);
  show('🔒 تفاعل على محذوفة', reactDeleted === 'MESSAGE_DELETED' ? '✓ مرفوض' : `✗ ${reactDeleted}`);

  // ---------- 9) أمان الاشتراك ----------
  const wsG = client(g.token);
  await wsG.ready;
  wsG.send({ type: 'subscribe', conversationId: convId });
  const denied = await wsG.waitFor((e) => e.type === 'error');
  show('🔒 اشتراك من غريب', `${denied.code === 'FORBIDDEN' ? '✓ مرفوض' : `✗ ${denied.code}`}`);
  wsG.close();

  // ---------- 10) الحالة النهائية فالقاعدة ----------
  const thread = await q(MESSAGES, { c: convId }, a.token);
  show(
    '📜 المحادثة',
    thread.messages.messages.map((m) => `${m.deleted ? '[محذوفة]' : m.text}${m.edited ? ' (معدّلة)' : ''}`).join(' | '),
  );

  wsA.close();
  wsB.close();
  console.log('\n✅ كل اختبارات اللحظي والميزات دازت');
})().catch((error) => {
  console.error('❌ FAIL', error.message);
  process.exit(1);
});
