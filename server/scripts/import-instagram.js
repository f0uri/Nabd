/**
 * استيراد ريلز إنستغرام بالجملة (روابط عامة) — من الطيرمينال.
 *
 * التشغيل:
 *   node scripts/import-instagram.js <url> [<url> …] [--email a@b.c]
 * مثال:
 *   node scripts/import-instagram.js https://www.instagram.com/reel/DWqdc3WRM8-/ --email l2@nabd.app
 *
 * ملاحظة: الخادم خاصو يكون شغّال. الروابط اللي مستوردة من قبل ما كتتعاودش.
 */
const GQL = 'http://localhost:4000/graphql';

const args = process.argv.slice(2);
const emailIndex = args.indexOf('--email');
const email = emailIndex >= 0 ? args.splice(emailIndex, 2)[1] : 'y2@nabd.app';
const urls = args.filter((arg) => !arg.startsWith('--'));

if (!urls.length) {
  console.error('❌ عطيني على الأقل رابط وحد: node scripts/import-instagram.js <url>');
  process.exit(1);
}

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
  const login = await gql('mutation($e:String!){login(email:$e,password:"nabd1234"){token user{name}}}', { e: email });
  console.log(`👤 ${login.login.user.name} (${email})`);

  let ok = 0;
  for (const url of urls) {
    try {
      const started = Date.now();
      const data = await gql(
        'mutation($u:String!){importInstagramReel(url:$u){id source sourceAuthor media{kind mime}}}',
        { u: url },
        login.login.token,
      );
      const post = data.importInstagramReel;
      ok += 1;
      console.log(
        `✅ ${post.id} · إنستغرام @${post.sourceAuthor ?? '—'} · ${post.media.kind}/${post.media.mime} · ${((Date.now() - started) / 1000).toFixed(1)}s`,
      );
    } catch (error) {
      console.log(`⏭️  ${url} → ${error.message}`);
    }
  }

  const reels = await gql('{reels{posts{id source sourceAuthor}}}', null, login.login.token);
  console.log(
    `\n📊 مستورد ${ok}/${urls.length} · الريلز دابا: ${reels.reels.posts.length} ` +
      `(إنستغرام: ${reels.reels.posts.filter((r) => r.source === 'instagram').length})`,
  );
})().catch((error) => {
  console.error('❌ FAIL', error.message);
  process.exit(1);
});
