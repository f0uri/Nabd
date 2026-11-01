/**
 * فحص جلسة إنستغرام (كوكيز) — قبل ما تشعل الجلب التلقائي.
 *
 * كيفاش تجيب الكوكيز (بلا ما تعطي كلمة السر لأي حد):
 *   1) حلّ instagram.com فتغويم ولا فـمتصفح فحساب **جديد** (ماشي حسابك الأساسي).
 *   2) F12 → Application/Storage → Cookies → https://www.instagram.com
 *   3) خُد: sessionid · csrftoken · ds_user_id → حطهم فـ server/.env
 *
 * التشغيل: npm run ig:check
 */
import {
  hasSession,
  sessionInfo,
  webHeaders,
  collectShortcodes,
  SESSION_ENDPOINTS,
  hashtagPaths,
} from '../src/instagramSession.js';
import { fetchReelMeta } from '../src/instagram.js';

const info = sessionInfo();

if (!hasSession()) {
  console.log('⚠️  ما كايناش جلسة (IG_SESSION_ID خاوي).');
  console.log('   حط sessionid + csrftoken + ds_user_id فـ server/.env من بعد عاود هاد الأمر.');
  console.log('   ⚠️ استعمل حساب جديد ماشي حسابك الأساسي — إنستغرام كيقدر يوقف الحساب إلا شاف أتمتة.');
  process.exit(0);
}

console.log(`🔑 جلسة إنستغرام: ${info.fingerprint} · csrftoken=${info.hasCsrf ? '✓' : '✗'} · ds_user_id=${info.hasUserId ? '✓' : '✗'}`);

const allCodes = new Set();

async function check(name, path) {
  try {
    const res = await fetch(`https://www.instagram.com${path}`, { headers: webHeaders() });
    if (!res.ok) {
      console.log(`  ✗ ${name.padEnd(16)} HTTP ${res.status}${res.status === 401 || res.status === 403 ? ' (الجلسة ماشي صالحة ولا محبوسة)' : ''}`);
      return;
    }
    const json = await res.json();
    const codes = collectShortcodes(json);
    codes.forEach((code) => allCodes.add(code));
    console.log(`  ${codes.size ? '✓' : '·'} ${name.padEnd(16)} ${codes.size} ريل`);
  } catch (error) {
    console.log(`  ✗ ${name.padEnd(16)} ${error.message}`);
  }
}

console.log('\n📡 القنوات (بلا أسماء حسابات):');
for (const endpoint of SESSION_ENDPOINTS) {
  await check(endpoint.name, endpoint.path);
  await new Promise((resolve) => setTimeout(resolve, 500));
}

console.log('\n#️⃣ هاشتاغات:');
for (const tag of ['marrakech', 'travel']) {
  for (const candidate of hashtagPaths(tag)) {
    await check(candidate.name, candidate.path);
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
}

console.log('\n👤 حسابات (بالجلسة):');
for (const username of ['natgeo', 'nasa']) {
  await check(`profile:${username}`, `/api/v1/users/web_profile_info/?username=${username}`);
  await new Promise((resolve) => setTimeout(resolve, 500));
}

console.log(`\n📊 المجموع: ${allCodes.size} كود ريل`);

if (allCodes.size) {
  const sample = [...allCodes].slice(0, 3);
  console.log('🔎 كنتحققو من عينة…');
  for (const code of sample) {
    try {
      const meta = await fetchReelMeta(code);
      console.log(`  ✓ ${code} · @${meta.author ?? '—'} · ${Math.round(meta.duration)}s · ${meta.width}×${meta.height}`);
    } catch (error) {
      console.log(`  ✗ ${code} · ${error.message}`);
    }
  }
  console.log('\n✅ الجلسة خدامة — الجلب التلقائي غادي يمشي بوحدو. عاود شعل الخادم (npm start).');
} else {
  console.log('\n⚠️  الجلسة مقروءة ولكن ما خرج حتى ريل — ممكن إنستغرام حبس الـ IP، ولا الجلسة قديمة.');
  console.log('   جرّب تعاود تسجيل الدخول فالطغويم وتاخد كوكيز جديدة.');
}
