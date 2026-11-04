// Validate Kotlin's emitted snapshot with the existing, unchanged TypeScript reader.
// Synthetic users/tokens only. No network, production bot or vault mutation.
const fs = require('node:fs');
const path = require('node:path');
const load = require('../../mobile/tests/helpers/load-source.cjs')();
const { createTelegramApi } = load('telegram/createTelegramApi.ts');
const input = JSON.parse(fs.readFileSync(path.join(__dirname, '../app/build/native-interop.json'), 'utf8'));
const api = createTelegramApi({
  namespace: input.namespace, getToken: () => input.token, random: () => 'e'.repeat(32),
  now: () => input.now, avatar: async () => { throw Error('not used'); },
  transport: {
    chat: async () => ({ pinned_message: { message_id: 100, caption: 'nabd-direct-telegram-v1', document: { file_name: 'nabd-vault.json', file_id: 'synthetic', file_size: Buffer.byteLength(JSON.stringify(input.vault)) } } }),
    read: async () => JSON.parse(JSON.stringify(input.vault)),
    upload: async () => { throw Error('No live or synthetic writes expected'); },
    pin: async () => { throw Error('No writes expected'); },
  },
});
(async () => {
  const me = await api.me();
  const page = await api.fetchFeed();
  const conversations = await api.conversations();
  if (me.username !== 'alice_native' || page.posts.length !== 1 || page.posts[0].comments.length !== 1 || conversations.length !== 1) throw Error('Interop state mismatch');
  const messages = await api.messages(conversations[0].id);
  if (messages.messages.length !== 2 || !messages.messages[0].edited || !messages.messages[1].media || !messages.messages[1].replyTo || page.posts[0].comments[0].likes !== 1) throw Error('Rich-chat/comment interop mismatch');
  console.log('Existing TypeScript v1 reader accepts Kotlin native account/post/comment/friendship/chat writes. No live data used.');
})().catch(() => { console.error('Kotlin/legacy protocol compatibility failed'); process.exitCode = 1; });
