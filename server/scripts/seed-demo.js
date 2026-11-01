/**
 * تعمير البيانات التوضيحية (منشورات + ريلز حقيقية) — باش التطبيق يوصل عندك عامر، ماشي خاوي.
 * كيتشغّل والخادم **شغّال**، وكيتعاود بلا مشكل (ما كيعاودش يرفع اللي كاين).
 *
 * التشغيل: npm run seed:demo
 */
const GQL = 'http://localhost:4000/graphql';
const PASSWORD = 'nabd1234';

const ACCOUNTS = {
  youssef: 'y2@nabd.app',
  loubna: 'l2@nabd.app',
};

const SEED = [
  {
    account: 'youssef_placeholder',
    kind: 'image',
    file: 'data/seed/marrakech-sunset.jpg',
    mime: 'image/jpeg',
    ratio: 4 / 5,
    text: 'غروب الكتبية اليوم… مراكش كتعرف كيفاش تسالي النهار 🌇',
  },
  {
    account: 'loubna',
    kind: 'image',
    file: 'data/seed/cafe-nous-nous.jpg',
    mime: 'image/jpeg',
    ratio: 1,
    text: 'نص نص فالصباح، وبعدها نبدأو الخدمة. عادة ما كتتبدلش.',
  },
  {
    account: 'youssef',
    kind: 'video',
    file: 'data/seed/flower.mp4',
    mime: 'video/mp4',
    ratio: 4 / 4,
    text: 'هدوء الصباح — 6 ثواني بلا مونتاج',
  },
  {
    account: 'loubna',
    kind: 'video',
    file: 'data/seed/clip-5s.mp4',
    mime: 'video/mp4',
    ratio: 16 / 9,
    text: 'تجربة سريعة بكاميرا التيليفون',
  },
];

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

async function upload({ token, file, mime, text, ratio }) {
  const { readFile } = await import('node:fs/promises');
  const buffer = await readFile(file);
  const name = file.split('/').pop();

  const form = new FormData();
  form.append(
    'operations',
    JSON.stringify({
      query:
        'mutation($t:String!,$file:File,$aspectRatio:Float){createPost(text:$t,file:$file,aspectRatio:$aspectRatio){id media{id kind mime}}}',
      variables: { t: text, file: null, aspectRatio: ratio },
    }),
  );
  form.append('map', JSON.stringify({ 0: ['variables.file'] }));
  form.append('0', new Blob([buffer], { type: mime }), name);

  const res = await fetch(GQL, { method: 'POST', body: form, headers: { authorization: `Bearer ${token}` } });
  const json = await res.json();
  if (json.errors) throw new Error(json.errors[0].message);
  return json.data.createPost;
}

(async () => {
  const tokens = {};
  for (const [key, email] of Object.entries(ACCOUNTS)) {
    const data = await gql('mutation($e:String!){login(email:$e,password:"nabd1234"){token user{name}}}', { e: email });
    tokens[key] = data.login.token;
    console.log(`👤 ${key} → ${data.login.user.name}`);
  }

  const existing = await gql('{feed(limit:50){posts{text}}}', null, tokens.youssef).catch(() => ({ feed: { posts: [] } }));
  const existingTexts = new Set(existing.feed.posts.map((p) => p.text));

  let created = 0;
  for (const item of SEED) {
    const accountKey = item.account === 'youssef_placeholder' ? 'youssef' : item.account;
    if (existingTexts.has(item.text)) {
      console.log(`⏭️  كاين من قبل: ${item.text.slice(0, 32)}…`);
      continue;
    }
    const post = await upload({
      token: tokens[accountKey],
      file: item.file,
      mime: item.mime,
      text: item.text,
      ratio: item.ratio,
    });
    created += 1;
    console.log(
      `✅ ${item.kind === 'video' ? '🎬 ريل' : '🖼️ منشور'} ${post.id} · ${post.media.kind} · ${post.media.mime}`,
    );
  }

  const reels = await gql('{reels{posts{id text media{kind}}}}', null, tokens.youssef);
  const feed = await gql('{feed(limit:20){posts{id text}}}', null, tokens.youssef);
  console.log(`\n📊 تزادو ${created} · الريلز دابا: ${reels.reels.posts.length} · الفيد: ${feed.feed.posts.length}`);
})().catch((error) => {
  console.error('❌ FAIL', error.message);
  process.exit(1);
});
