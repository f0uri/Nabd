/**
 * الجلب التلقائي لريلز إنستغرام — بلا أي تدخل من المستخدم.
 *
 * كيفاش كيدور الجلب (cycle):
 *   1) **الاكتشاف**: كيجمع أكواد ريلز جديدة من عدة قنوات (اللي كتكون شغالة):
 *      - `account` : واجهة الويب العمومية ديال إنستغرام (`web_profile_info`) لحساب معين.
 *      - `search`  : بحث عمومي (DuckDuckGo HTML) على `site:instagram.com/reel <موضوع>`.
 *      - `catalog` : لائحة أكواد محفوظة فـ `data/ig-catalog.json` (شحن احتياطي).
 *   2) **الطابور**: كل كود كيتسجل فـ `ig_queue` (بلا تكرار).
 *   3) **المعالجة**: كيتقرا الريل، كيتحمّل، كيتخزّن عندنا (mirror)، وكيتنشر باسم حساب
 *      «ريلز نبض» مع شارة `source=instagram` + `sourceUrl` + `sourceAuthor`.
 *   4) **التكرار**: كل `IG_INTERVAL_MINUTES` (افتراضياً 45 دقيقة) وبـ backoff على الأخطاء.
 *
 * كلشي كيتضبط من `.env` — بلا حتى زر فالتطبيق:
 *   IG_AUTO_IMPORT=1
 *   IG_ACCOUNTS=nasa,natgeo,visitmorocco     (حسابات عامة كتجيب منهم)
 *   IG_TOPICS=marrakech,morocco,food,travel  (مواضيع للبحث)
 *   IG_INTERVAL_MINUTES=45
 *   IG_MAX_PER_CYCLE=3
 *   IG_MAX_PER_DAY=30
 *   IG_ACCESS_TOKEN=...                       (اختياري: Graph API الرسمي)
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseShortcode, fetchReelMeta, downloadReelVideo, instagramLimits } from './instagram.js';
import {
  hasSession,
  sessionInfo,
  setSession,
  parseCookieInput,
  webHeaders,
  collectShortcodes,
  SESSION_ENDPOINTS,
  hashtagPaths,
} from './instagramSession.js';
import {
  createPost as dbCreatePost,
  createUser as dbCreateUser,
  countReels,
  deleteSetting,
  enqueueIgCode,
  getSetting,
  setSetting,
  finishIgCode,
  findPostBySourceUrl,
  findUserByEmail,
  igQueueStats,
  isIgCodeKnown,
  requeueTransientIgCodes,
  nextIgQueue,
  insertMedia,
} from './store.js';
import { mirrorStorage } from './storage/index.js';
import { randomUUID } from 'node:crypto';

const here = dirname(fileURLToPath(import.meta.url));
const CATALOG_PATH = join(here, '..', 'data', 'ig-catalog.json');

const SYSTEM_EMAIL = 'reels@nabd.app';
const USER_AGENT =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15';
const BROWSER_UA =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';

/**
 * مصادر افتراضية **مدمجة فالكود** — التطبيق كيتعمّر بوحدو بلا حتى إعداد.
 * الحسابات والمواضيع كتدور بالتناوب (3 حسابات + 4 مواضيع فكل دورة) باش ما نحرقوش إنستغرام.
 */
const DEFAULT_ACCOUNTS = [
  'natgeo',
  'nasa',
  'bbcearth',
  'visitmorocco',
  'morocco.world',
  'cookingwithlynja',
  '9gag',
  'fifa',
  'nike',
  'gopro',
  'airbnb',
  'spotify',
  'redbull',
  'canonusa',
  'lonelyplanet',
];

const DEFAULT_TOPICS = [
  'marrakech',
  'morocco',
  'casablanca',
  'moroccan food',
  'travel',
  'football',
  'cats',
  'fitness',
  'street food',
  'comedy',
  'fashion',
  'music',
];

const envList = (value) =>
  (value ?? '')
    .split(',')
    .map((entry) => entry.trim().replace(/^@/, ''))
    .filter(Boolean);

const config = {
  enabled: process.env.IG_AUTO_IMPORT !== '0',
  accounts: envList(process.env.IG_ACCOUNTS).length ? envList(process.env.IG_ACCOUNTS) : DEFAULT_ACCOUNTS,
  topics: envList(process.env.IG_TOPICS).length ? envList(process.env.IG_TOPICS) : DEFAULT_TOPICS,
  /** باش نضمنو أن البلاصة ديال الريلز ما كتبقاش خاوية */
  keepMin: Math.max(3, Number(process.env.IG_KEEP_MIN ?? 8)),
  intervalMinutes: Math.max(5, Number(process.env.IG_INTERVAL_MINUTES ?? 45)),
  maxPerCycle: Math.max(1, Number(process.env.IG_MAX_PER_CYCLE ?? 3)),
  maxPerDay: Math.max(1, Number(process.env.IG_MAX_PER_DAY ?? 30)),
  /** حد حجم الريل الواحد (MB) — باش ما نعمروش القرص */
  maxMb: Math.max(2, Number(process.env.IG_MAX_MB ?? 18)),
  accessToken: process.env.IG_ACCESS_TOKEN ?? null,
  businessId: process.env.IG_BUSINESS_ACCOUNT_ID ?? null,
};

const daily = { day: new Date().toISOString().slice(0, 10), count: 0 };

const status = {
  enabled: config.enabled,
  running: false,
  lastRunAt: null,
  lastRunReason: null,
  lastError: null,
  nextRunAt: null,
  cycle: { discovered: 0, imported: 0, failed: 0 },
  todayImported: 0,
  totals: { imported: 0, failed: 0 },
  channels: {},
  installedAt: new Date().toISOString(),
};

let timer = null;
/** حسابات تعلمناها من الريلز اللي جابو (كتوسّع الاكتشاف بوحدها) */
const learnedAccounts = new Set();
let accountCursor = 0;
let topicCursor = 0;
let systemAuthorId = null;
let started = false;

/* ------------------------------- أدوات صغيرة ------------------------------- */

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** حساب ناشر الريلز المستوردة (كيتصنع مرة واحدة) */
async function ensureSystemAuthor() {
  if (systemAuthorId) return systemAuthorId;
  const existing = findUserByEmail(SYSTEM_EMAIL);
  if (existing) {
    systemAuthorId = existing.id;
    return systemAuthorId;
  }
  const user = await dbCreateUser({
    name: 'ريلز نبض',
    email: SYSTEM_EMAIL,
    password: randomUUID(),
    color: '#1877F2',
    verified: true,
    bio: 'ريلز كتتجاب أوتوماتيكياً من إنستغرام — المحتوى ملك أصحابو.',
  });
  systemAuthorId = user.id;
  return systemAuthorId;
}

/* --------------------------- جلسة إنستغرام (كوكيز) --------------------------- */

/** كيقرا الجلسة من قاعدة البيانات (اللي كتحطها من التطبيق) — كيتنادى فكل دورة */
export async function loadInstagramSession() {
  const raw = await getSetting('ig_session');
  if (!raw) return sessionInfo();

  try {
    const parsed = JSON.parse(raw);
    if (parsed?.sessionId) setSession(parsed, 'admin');
  } catch {
    /* إعداد خايب → كنعتمد على .env */
  }
  return sessionInfo();
}

/** كيتحقق من الجلسة: كيجرب قنوات قليلة وكيرجع النتيجة بالتفصيل */
export async function probeInstagramSession() {
  const started = Date.now();
  const channels = [];

  if (!hasSession()) {
    return { configured: false, valid: false, totalCodes: 0, channels, checkedAt: new Date().toISOString() };
  }

  const tryChannel = async (name, path) => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 9000);
    try {
      const res = await fetch(`https://www.instagram.com${path}`, {
        headers: webHeaders(),
        signal: controller.signal,
      });
      if (!res.ok) {
        channels.push({ name, ok: false, found: 0, error: `http:${res.status}` });
        return 0;
      }
      const json = await res.json();
      const found = collectShortcodes(json).size;
      channels.push({ name, ok: found > 0, found, error: null });
      return found;
    } catch (error) {
      channels.push({ name, ok: false, found: 0, error: error.name === 'AbortError' ? 'timeout' : error.message });
      return 0;
    } finally {
      clearTimeout(timer);
    }
  };

  let total = 0;
  total += await tryChannel('الفيد الرئيسي', SESSION_ENDPOINTS[0].path);
  total += await tryChannel('ريلز (clips)', SESSION_ENDPOINTS[3].path);
  total += await tryChannel('هاشتاغ marrakech', hashtagPaths('marrakech')[0].path);

  // اختبار البروفايل — أقوى إشارة على أن الجلسة صالحة
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 9000);
  try {
    const res = await fetch('https://www.instagram.com/api/v1/users/web_profile_info/?username=natgeo', {
      headers: webHeaders({ referer: 'https://www.instagram.com/natgeo/' }),
      signal: controller.signal,
    });
    if (!res.ok) {
      channels.push({ name: 'بروفايل natgeo', ok: false, found: 0, error: `http:${res.status}` });
    } else {
      const json = await res.json();
      const edges = json?.data?.user?.edge_owner_to_timeline_media?.edges ?? [];
      const codes = edges.filter((edge) => edge?.node?.is_video || edge?.node?.product_type === 'clips').length;
      channels.push({ name: 'بروفايل natgeo', ok: codes > 0, found: codes, error: null });
      total += codes;
    }
  } catch (error) {
    channels.push({ name: 'بروفايل natgeo', ok: false, found: 0, error: error.message });
  } finally {
    clearTimeout(timer);
  }

  const sessionAlive = channels.some((channel) => channel.ok);
  status.channels.session = { ok: sessionAlive, found: total, endpoints: channels };

  return {
    configured: true,
    valid: sessionAlive,
    totalCodes: total,
    channels,
    checkedAt: new Date().toISOString(),
    tookMs: Date.now() - started,
  };
}

/** كيتسجل الكوكيز اللي كيحطها الأدمن فالتطبيق */
export async function saveInstagramSessionFromInput(cookieInput) {
  const parsed = parseCookieInput(cookieInput);
  if (!parsed.sessionId || parsed.sessionId.length < 20) {
    throw new Error('INVALID_INSTAGRAM_COOKIE');
  }

  await setSetting('ig_session', JSON.stringify(parsed));
  setSession(parsed, 'admin');

  const probe = await probeInstagramSession();

  // إلا كانت الجلسة صالحة وفيه ريلز → كنشغلو دورة جلب فالحين
  if (probe.valid) {
    runIngestCycle('admin-save').catch(() => {});
  }

  return probe;
}

/** حذف الجلسة */
export async function clearInstagramSession() {
  await deleteSetting('ig_session');
  setSession({}, null);
  return true;
}

/** حالة كاملة للأدمن */
export async function instagramAdminStatus() {
  const info = sessionInfo();
  const queue = await igQueueStats().catch(() => ({ pending: 0, imported: 0, failed: 0, skipped: 0 }));
  const reels = await countReels().catch(() => 0);
  return {
    configured: info.configured,
    fingerprint: info.fingerprint,
    savedFrom: info.source,
    savedAt: info.savedAt,
    autoImport: config.enabled,
    intervalMinutes: config.intervalMinutes,
    keepMin: config.keepMin,
    reelsCount: reels,
    queuePending: queue.pending ?? 0,
    queueImported: queue.imported ?? 0,
    lastRunAt: status.lastRunAt,
    lastError: status.lastError,
    channels: status.channels.session?.endpoints ?? [],
  };
}

/* ------------------------------- الاكتشاف ------------------------------- */

/** كود ريل جديد من Graph API الرسمي ديال إنستغرام (إلا كان التوكن) */
async function discoverFromGraph() {
  if (!config.accessToken || !config.businessId) return [];
  try {
    const url =
      `https://graph.facebook.com/v21.0/${config.businessId}/media` +
      `?fields=permalink,media_type,media_product_type&limit=25&access_token=${config.accessToken}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`graph:${res.status}`);
    const json = await res.json();
    const codes = (json.data ?? [])
      .filter((item) => item.media_product_type === 'REELS' || item.media_type === 'VIDEO')
      .map((item) => parseShortcode(item.permalink))
      .filter(Boolean);
    status.channels.graph = { ok: true, found: codes.length };
    return codes;
  } catch (error) {
    status.channels.graph = { ok: false, error: error.message };
    return [];
  }
}

/**
 * جلسة خفيفة: كناخدو كوكيز (csrftoken/mid) من الصفحة الرئيسية باش الواجهة تقبل الطلب،
 * وكنحاولو عدة User-Agents. إلا رجع 429 كنوقفوه لهاد الدورة (بلا ما نحرقو الحساب).
 */
let cookieJar = { value: '', csrf: '', at: 0 };
const blockedUntil = new Map(); // username → timestamp

async function warmCookies() {
  if (cookieJar.value && Date.now() - cookieJar.at < 10 * 60_000) return cookieJar;
  try {
    const res = await fetch('https://www.instagram.com/', {
      headers: { 'User-Agent': USER_AGENT, 'Accept-Language': 'en-US,en;q=0.9' },
    });
    const raw = typeof res.headers.getSetCookie === 'function' ? res.headers.getSetCookie() : [];
    const value = raw.map((item) => item.split(';')[0]).join('; ');
    cookieJar = { value, csrf: /csrftoken=([^;]+)/.exec(value)?.[1] ?? '', at: Date.now() };
  } catch {
    cookieJar = { value: '', csrf: '', at: Date.now() };
  }
  return cookieJar;
}

/**
 * الجلسة ديال الحساب (كوكيز): كتجيب ريلز عامة من الفيد/الاستكشاف/الهاشتاغات
 * — بلا حتى اسم حساب مكتوب فالكود.
 */
async function discoverFromSession(topics = []) {
  if (!hasSession()) {
    status.channels.session = { ok: false, error: 'not-configured' };
    return [];
  }

  const codes = new Set();
  const channels = {};

  const request = async (path, name) => {
    try {
      const res = await fetch(`https://www.instagram.com${path}`, { headers: webHeaders({ referer: 'https://www.instagram.com/' }) });
      if (!res.ok) {
        channels[name] = { ok: false, error: `http:${res.status}` };
        if (res.status === 401 || res.status === 403) status.channels.session = { ok: false, error: 'session-expired' };
        return;
      }
      const json = await res.json();
      const found = collectShortcodes(json);
      channels[name] = { ok: true, found: found.size };
      found.forEach((code) => codes.add(code));
    } catch (error) {
      channels[name] = { ok: false, error: error.message };
    }
  };

  for (const endpoint of SESSION_ENDPOINTS) {
    await request(endpoint.path, endpoint.name);
    await sleep(500);
  }

  // هاشتاغات المواضيع الافتراضية (كنجرّبو كل مسار مرشح)
  for (const topic of topics.slice(0, 2)) {
    const tag = topic.replace(/\s+/g, '').toLowerCase();
    for (const candidate of hashtagPaths(tag)) {
      await request(candidate.path, candidate.name);
      await sleep(400);
    }
  }

  const expired = Object.values(status.channels.session?.endpoints ?? {}).some?.(
    (entry) => entry?.error === 'session-expired',
  );

  status.channels.session = {
    ok: codes.size > 0,
    expired: Boolean(expired),
    found: codes.size,
    endpoints: channels,
    fingerprint: sessionInfo().fingerprint,
  };

  return [...codes];
}

/** أكواد ريلز ديال حساب عام (واجهة الويب ديال إنستغرام) */
async function discoverFromAccount(username) {
  const key = `account:${username}`;
  if ((blockedUntil.get(username) ?? 0) > Date.now()) {
    status.channels[key] = { ok: false, error: 'throttled' };
    return [];
  }

  const jar = await warmCookies();

  for (const userAgent of [USER_AGENT, BROWSER_UA]) {
    try {
      const res = await fetch(
        `https://www.instagram.com/api/v1/users/web_profile_info/?username=${encodeURIComponent(username)}`,
        {
          headers: {
            ...webHeaders({ userAgent, referer: `https://www.instagram.com/${username}/` }),
            // إلا ما كانتش جلسة، كنستعملو الكوكيز المؤقتة ديال الصفحة الرئيسية
            ...(hasSession() ? {} : { 'x-csrftoken': jar.csrf, ...(jar.value ? { Cookie: jar.value } : {}) }),
          },
        },
      );

      if (res.status === 429) {
        // إنستغرام حبس هاد الـ IP → كنريحو ساعة ونعاودو من بعد
        blockedUntil.set(username, Date.now() + 60 * 60_000);
        status.channels[key] = { ok: false, error: 'throttled' };
        return [];
      }

      if (!res.ok) {
        status.channels[key] = { ok: false, error: `http:${res.status}` };
        continue;
      }

      const json = await res.json();
      const edges = json?.data?.user?.edge_owner_to_timeline_media?.edges ?? [];
      const codes = edges
        .map((edge) => edge?.node)
        .filter((node) => node && (node.is_video || node.product_type === 'clips'))
        .map((node) => node.shortcode)
        .filter(Boolean);

      if (codes.length) {
        status.channels[key] = { ok: true, found: codes.length };
        return codes;
      }
      status.channels[key] = { ok: false, error: 'empty' };
    } catch (error) {
      status.channels[key] = { ok: false, error: error.message };
    }
  }

  return [];
}

/** بحث عمومي على روابط ريلز (DuckDuckGo HTML) */
async function discoverFromSearch(query) {
  try {
    const res = await fetch(`https://html.duckduckgo.com/html/?q=${encodeURIComponent(`site:instagram.com/reel ${query}`)}`, {
      headers: { 'User-Agent': BROWSER_UA, 'Accept-Language': 'en-US,en;q=0.9' },
    });
    if (!res.ok) {
      status.channels[`search:${query}`] = { ok: false, error: `http:${res.status}` };
      return [];
    }
    const html = await res.text();
    const codes = [
      ...new Set([...html.matchAll(/instagram\.com\/(?:reel|reels)\/([A-Za-z0-9_-]{6,20})/g)].map((match) => match[1])),
    ];
    status.channels[`search:${query}`] = { ok: true, found: codes.length };
    return codes;
  } catch (error) {
    status.channels[`search:${query}`] = { ok: false, error: error.message };
    return [];
  }
}

/** شحن احتياطي: لائحة أكواد محفوظة فالريبو */
async function discoverFromCatalog() {
  try {
    const raw = await readFile(CATALOG_PATH, 'utf8');
    const json = JSON.parse(raw);
    const codes = (json.codes ?? []).map((code) => parseShortcode(code)).filter(Boolean);
    status.channels.catalog = { ok: true, found: codes.length };
    return codes;
  } catch {
    status.channels.catalog = { ok: false, error: 'no-catalog' };
    return [];
  }
}

/** كيدير دورة اكتشاف كاملة وكيعمّر الطابور */
async function discover() {
  const found = new Map(); // shortcode → source

  const push = (codes, source) => {
    codes.forEach((code) => {
      if (!found.has(code)) found.set(code, source);
    });
  };

  // 1) الجلسة (كوكيز) — أقوى قناة بلا أسماء حسابات
  push(await discoverFromSession(topics), 'session');

  push(await discoverFromGraph(), 'account');

  // كل دورة كتاخد شريحة صغيرة بالتناوب (بلا ما نضربو كلشي فمرة)
  const accounts = [];
  for (let index = 0; index < Math.min(3, config.accounts.length); index += 1) {
    accounts.push(config.accounts[(accountCursor + index) % config.accounts.length]);
  }
  accountCursor = (accountCursor + accounts.length) % Math.max(1, config.accounts.length);

  // زيد حساب وحد تعلمناه (إلا كان) — الاكتشاف كيكبر بوحدو
  const learned = [...learnedAccounts];
  if (learned.length) {
    const pick = learned[Math.floor(Math.random() * learned.length)];
    if (!accounts.includes(pick)) accounts.push(pick);
  }

  const topics = [];
  for (let index = 0; index < Math.min(4, config.topics.length); index += 1) {
    topics.push(config.topics[(topicCursor + index) % config.topics.length]);
  }
  topicCursor = (topicCursor + topics.length) % Math.max(1, config.topics.length);

  for (const username of accounts) {
    push(await discoverFromAccount(username), 'account');
    await sleep(400);
  }

  for (const topic of topics) {
    push(await discoverFromSearch(topic), 'search');
    await sleep(400);
  }

  push(await discoverFromCatalog(), 'catalog');

  // الأكواد اللي فشلت بأخطاء مؤقتة (حجب/شبكة) كترجع للطابور بوحدها بعد 6 سوايع
  const retried = await requeueTransientIgCodes(2).catch(() => []);
  if (retried.length) {
    status.channels.retry = { ok: true, found: retried.length };
    retried.forEach((code) => found.set(code, found.get(code) ?? 'retry'));
  }

  let enqueued = 0;
  for (const [code, source] of found) {
    if (await isIgCodeKnown(code)) continue;
    if (await enqueueIgCode(code, source)) enqueued += 1;
  }

  status.cycle.discovered = found.size;
  status.cycle.enqueued = enqueued;
  return enqueued;
}

/* ------------------------------- الاستيراد ------------------------------- */

/** كيستورد ريل وحد من الطابور ويرجع المنشور */
export async function importInstagramCode(shortcode, { authorId = null, source = 'catalog' } = {}) {
  const permalink = `https://www.instagram.com/reel/${shortcode}/`;

  const existing = await findPostBySourceUrl(permalink);
  if (existing) return { post: existing, cached: true };

  const meta = await fetchReelMeta(shortcode);
  const file = await downloadReelVideo(meta.videoUrl, {
    maxBytes: Math.min(instagramLimits.maxBytes, config.maxMb * 1024 * 1024),
  });

  const uploaded = await mirrorStorage.upload({
    buffer: file.buffer,
    filename: `${shortcode}.mp4`,
    mime: file.mime,
    caption: `إنستغرام · ${meta.author ?? ''}`.trim(),
  });

  const aspectRatio = meta.width && meta.height ? meta.width / meta.height : 9 / 16;
  const mediaId = `m_${randomUUID().slice(0, 8)}`;
  await insertMedia({
    id: mediaId,
    provider: uploaded.provider ?? null,
    fileId: uploaded.fileId ?? null,
    mime: file.mime,
    size: uploaded.size ?? file.size,
    aspectRatio,
    alt: meta.caption.slice(0, 80) || 'ريل من إنستغرام',
  });

  const owner = authorId ?? (await ensureSystemAuthor());
  const text = meta.caption.trim() || `ريل من @${meta.author ?? 'إنستغرام'}`;

  const postId = await dbCreatePost({
    authorId: owner,
    text,
    privacy: 'public',
    mediaId,
    source: 'instagram',
    sourceUrl: permalink,
    sourceAuthor: meta.author,
  });

  return { postId, mediaId, permalink, author: meta.author, duration: meta.duration, size: file.size, cached: false };
}

/** أقصى عدد محاولات لعنصر فالطابور قبل ما نتخلاو عليه */
const MAX_ATTEMPTS = 3;

/** كيعالج عدد من عناصر الطابور */
async function processQueue(limit) {
  const items = await nextIgQueue(limit);
  let imported = 0;
  let failed = 0;

  for (const item of items) {
    try {
      const result = await importInstagramCode(item.shortcode);
      await finishIgCode({
        shortcode: item.shortcode,
        status: 'imported',
        postId: result.postId ?? result.post?.id ?? null,
      });
      // نتعلّمو الحساب باش نجيبو منو ريلز أخرى فدورات جاية
      const author = result.author ?? result.post?.sourceAuthor;
      if (author && learnedAccounts.size < 60 && !config.accounts.includes(author)) learnedAccounts.add(author);
      status.learnedAccounts = learnedAccounts.size;
      imported += 1;
      console.log(`🎬 ريل مستورد تلقائياً: ${item.shortcode} @${result.author ?? result.post?.sourceAuthor ?? '—'}`);
    } catch (error) {
      const message = String(error.message ?? error);
      // أخطاء نهائية: ما كنعاودوهاش
      const definitive = ['NOT_A_VIDEO', 'REEL_TOO_LONG', 'REEL_TOO_LARGE', 'REEL_NOT_FOUND'].includes(message);
      const outOfAttempts = (item.attempts ?? 0) + 1 >= MAX_ATTEMPTS;
      // الأخطاء النهائية ولا اللي كمّلت المحاولات → skipped · الباقي كيبقى pending
      const nextStatus = definitive || outOfAttempts ? 'skipped' : 'pending';
      await finishIgCode({ shortcode: item.shortcode, status: nextStatus, error: message });
      failed += 1;
      status.lastError = message;
    }
    await sleep(600);
  }

  status.cycle.failed = failed;
  return { imported, failed };
}

/* ------------------------------- الدورة ------------------------------- */

/** دورة كاملة: اكتشاف + استيراد (كتنشر تلقائياً بلا تدخل) */
export async function runIngestCycle(reason = 'manual') {
  if (status.running) return { skipped: 'running' };

  status.running = true;
  status.lastRunAt = new Date().toISOString();
  status.lastRunReason = reason;
  status.cycle = { discovered: 0, enqueued: 0, imported: 0, failed: 0 };

  // سقف يومي: ما كنمرّو الحد باش ما نعمروش التخزين ولا نحرقو إنستغرام
  const today = new Date().toISOString().slice(0, 10);
  if (daily.day !== today) {
    daily.day = today;
    daily.count = 0;
  }
  const remainingToday = Math.max(0, config.maxPerDay - daily.count);
  status.todayImported = daily.count;

  try {
    await loadInstagramSession();
    await discover();

    // إلا كانت البلاصة خاوية (ولا فيها قليل)، كنعالجو أكثر باش تتعمّر بوحدها
    const current = await countReels().catch(() => 0);
    const deficit = Math.max(0, config.keepMin - current);
    const budget = Math.min(config.maxPerCycle + deficit, Math.max(config.maxPerCycle, 6), remainingToday);
    const result = await processQueue(budget);
    status.cycle.imported = result.imported;
    status.cycle.failed = result.failed;
    status.totals.imported += result.imported;
    daily.count += result.imported;
    status.todayImported = daily.count;
    status.totals.failed += result.failed;
    status.lastError = result.failed && !result.imported ? status.lastError : null;
    const queue = await igQueueStats().catch(() => null);
    console.log(
      `📥 دورة الجلب (${reason}): اكتشف ${status.cycle.discovered ?? 0} · ` +
        `مستورد ${result.imported} · فشل ${result.failed}` +
        (queue ? ` · الطابور: ${queue.pending} فالانتظار / ${queue.imported} مستورد` : ''),
    );
  } catch (error) {
    status.lastError = error.message;
  } finally {
    status.running = false;
    status.nextRunAt = new Date(Date.now() + config.intervalMinutes * 60_000).toISOString();
  }

  return status.cycle;
}

/** حالة الجلب (للتطبيق: باش يعرف واش الجلب شغّال ومنين آخر تحديث) */
export async function ingestStatus() {
  const queue = await igQueueStats().catch(() => ({ pending: 0, imported: 0, failed: 0, skipped: 0 }));
  return {
    ...status,
    queue,
    sources: {
      accounts: config.accounts,
      topics: config.topics,
      graph: Boolean(config.accessToken && config.businessId),
      session: sessionInfo(),
    },
    intervalMinutes: config.intervalMinutes,
    maxPerCycle: config.maxPerCycle,
    maxPerDay: config.maxPerDay,
  };
}

/** كيشعل الجلب التلقائي (دورة فالبداية + كل مدة) */
export function startIngestLoop() {
  if (started) return;
  started = true;

  if (!config.enabled) {
    status.enabled = false;
    console.log('⏸️  الجلب التلقائي ديال ريلز إنستغرام مطفي (IG_AUTO_IMPORT=0)');
    return;
  }

  status.enabled = true;
  const intervalMs = config.intervalMinutes * 60_000;
  status.nextRunAt = new Date(Date.now() + 5000).toISOString();

  setTimeout(() => {
    runIngestCycle('startup').catch(() => {});
  }, 5000);

  timer = setInterval(() => {
    runIngestCycle('interval').catch(() => {});
  }, intervalMs);
  timer.unref?.();

  console.log(
    `📥 الجلب التلقائي ديال ريلز إنستغرام شغّال — كل ${config.intervalMinutes} دقيقة · ` +
      `${config.maxPerCycle} ريل/دورة · حسابات: ${config.accounts.length || 0} · مواضيع: ${config.topics.length} · ` +
      `جلسة إنستغرام: ${hasSession() ? `نعم ✓ (${sessionInfo().fingerprint})` : 'لا (بلا تسجيل دخول)'}`,
  );
}

export function stopIngestLoop() {
  if (timer) clearInterval(timer);
  timer = null;
  started = false;
}

export const ingestConfig = config;
