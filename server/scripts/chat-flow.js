// اختبار مسارات التطبيق بالضبط (نفس استعلامات mobile/src/api/client.ts):
// conversations(search, limit) · messages · unreadMessagesCount · startConversation · sendMessage · markConversationRead
// المتطلب: تكون دوزت npm run smoke:chat (كيدخل بالحسابات y2/l2/g).
// التشغيل: npm run smoke:flow
// اختبار مسار التطبيق بالضبط: نفس الاستعلامات اللي فـ client.ts
const URL = 'http://localhost:4000/graphql';
const AUTH = { 'content-type': 'application/json' };
async function q(query, variables, token) {
  const r = await fetch(URL, {
    method: 'POST',
    headers: { ...AUTH, ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ query, variables }),
  });
  const j = await r.json();
  if (j.errors) throw new Error(JSON.stringify(j.errors.map((e) => e.message)));
  return j.data;
}
const AUTHOR = 'id name initial color avatarUrl verified bio email friendsCount postsCount isFriend isMe';
const MSG = 'id conversationId senderId text createdAt read';
const CONV = `id createdAt lastAt unreadCount other { ${AUTHOR} } lastMessage { ${MSG} }`;
const CONVERSATIONS = `query Conversations($search: String, $limit: Int) { conversations(search: $search, limit: $limit) { ${CONV} } }`;
const MESSAGES = `query Messages($conversationId: ID!, $cursor: String, $limit: Int) { messages(conversationId: $conversationId, cursor: $cursor, limit: $limit) { nextCursor messages { ${MSG} } } }`;
const UNREAD = `query UnreadMessages { unreadMessagesCount }`;
const START = `mutation StartConversation($userId: ID!) { startConversation(userId: $userId) { ${CONV} } }`;
const SEND = `mutation SendMessage($conversationId: ID!, $text: String!) { sendMessage(conversationId: $conversationId, text: $text) { ${MSG} } }`;
const MARK = `mutation MarkConversationRead($conversationId: ID!) { markConversationRead(conversationId: $conversationId) }`;
const LOGIN = `mutation Login($email: String!, $password: String!) { login(email: $email, password: $password) { token user { id name } } }`;

(async () => {
  const a = await q(LOGIN, { email: 'y2@nabd.app', password: 'nabd1234' });
  const b = await q(LOGIN, { email: 'l2@nabd.app', password: 'nabd1234' });
  const ya = a.login.token, lu = b.login.token;
  console.log('A', a.login.user.name, '| B', b.login.user.name);

  // 1) التطبيق: startConversation مع limit=50
  const start = await q(START, { userId: b.login.user.id }, ya);
  const convId = start.startConversation.id;
  console.log('START', convId, '| other:', start.startConversation.other.name, '| unread:', start.startConversation.unreadCount);

  // 2) sendMessage من A
  const s1 = await q(SEND, { conversationId: convId, text: 'salam، واش وصلات الرسالة؟' }, ya);
  console.log('SEND', s1.sendMessage.id, '| read:', s1.sendMessage.read);

  // 3) محادثات B + unreadMessagesCount (الشارة)
  const convs = await q(CONVERSATIONS, { search: '', limit: 50 }, lu);
  const unread = await q(UNREAD, undefined, lu);
  console.log('CONVS', convs.conversations.length, '| unread:', convs.conversations[0].unreadCount, '| badge:', unread.unreadMessagesCount);

  // 4) messages + markConversationRead
  const page = await q(MESSAGES, { conversationId: convId, cursor: null, limit: 30 }, lu);
  console.log('MESSAGES', page.messages.messages.length, '| nextCursor:', page.messages.nextCursor);
  await q(MARK, { conversationId: convId }, lu);
  const unread2 = await q(UNREAD, undefined, lu);
  console.log('AFTER READ badge:', unread2.unreadMessagesCount);

  // 5) رد من B → A عندو 1
  await q(SEND, { conversationId: convId, text: 'وصلات، صافي.' }, lu);
  const unreadA = await q(UNREAD, undefined, ya);
  const convsA = await q(CONVERSATIONS, { search: 'لبنى', limit: 50 }, ya);
  console.log('A badge:', unreadA.unreadMessagesCount, '| search hit:', convsA.conversations.length, '| preview:', convsA.conversations[0].lastMessage.text);

  // 6) ترتيب المحادثات: الأحدث فوق
  const b2 = await q(LOGIN, { email: 'g@nabd.app', password: 'nabd1234' });
  const cv2 = await q(START, { userId: a.login.user.id }, b2.login.token);
  await q(SEND, { conversationId: cv2.startConversation.id, text: 'أهلا ياسين' }, b2.login.token);
  const ordered = await q(CONVERSATIONS, { search: null, limit: 50 }, ya);
  console.log('ORDER', ordered.conversations.map((c) => `${c.other.name}@${c.lastAt.slice(11, 19)}`).join(' , '));

  // 7) الأمان: G ما عندوش حق يقرا محادثة A-B
  const r = await fetch(URL, {
    method: 'POST',
    headers: { ...AUTH, authorization: `Bearer ${b2.login.token}` },
    body: JSON.stringify({ query: MESSAGES, variables: { conversationId: convId, cursor: null, limit: 30 } }),
  }).then((x) => x.json());
  console.log('SECURITY', r.errors ? `✓ مرفوض: ${r.errors[0].extensions?.code ?? r.errors[0].message}` : '✗ تسرب!');
})().catch((e) => {
  console.error('FAIL', e.message);
  process.exit(1);
});
