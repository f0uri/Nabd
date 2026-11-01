/**
 * جلسة إنستغرام بالكوكيز (اختياري) — باش الجلب يخدم بالحساب ديالك:
 *   IG_SESSION_ID=...   (sessionid)
 *   IG_CSRFTOKEN=...    (csrftoken)
 *   IG_DS_USER_ID=...   (ds_user_id)
 *
 * بهاد الجلسة كنتقدرو نجيبو **ريلز عامة بلا ما نكتبو حتى اسم حساب**:
 * الفيد الرئيسي، الاستكشاف، والهاشتاغات — كلها كترجع روابط ريلز.
 *
 * ⚠️ الكوكيز = مفتاح الحساب. ما كتتبعتش أبداً للتطبيق ولا للتلغرام، وكتبقى غير فـ`.env`.
 * ⚠️ استعمل حساب جديد ماشي حسابك الأساسي: إنستغرام كيقدر يوقف الحساب إلا شاف أتمتة.
 */
/**
 * الجلسة كتجي من:
 *   1) **لوحة الأدمن فالتطبيق** (كتخزن فـ app_settings — الأولوية)
 *   2) ولا من `.env` (للمطورين)
 */
let SESSION = {
  sessionId: (process.env.IG_SESSION_ID ?? '').trim(),
  csrf: (process.env.IG_CSRFTOKEN ?? '').trim(),
  dsUserId: (process.env.IG_DS_USER_ID ?? '').trim(),
  savedAt: null,
  source: (process.env.IG_SESSION_ID ?? '').trim() ? 'env' : null,
};

/** كيبدّل الجلسة فالذاكرة (من بعد ما تتقرا من قاعدة البيانات ولا من التطبيق) */
export function setSession({ sessionId = '', csrfToken = '', dsUserId = '' } = {}, source = 'admin') {
  SESSION = {
    sessionId: String(sessionId ?? '').trim(),
    csrf: String(csrfToken ?? '').trim(),
    dsUserId: String(dsUserId ?? '').trim(),
    savedAt: new Date().toISOString(),
    source,
  };
  return sessionInfo();
}

/**
 * كيتقبل نص الكوكيز كامل كيف ما كوبي من المتصفح:
 *   "sessionid=abc; csrftoken=xyz; ds_user_id=123"
 * ولا غير القيمة ديال sessionid بوحدها.
 */
export function parseCookieInput(raw = '') {
  const text = String(raw ?? '').trim();
  if (!text) return { sessionId: '', csrfToken: '', dsUserId: '' };

  const value = (name) => {
    const match = new RegExp(`${name}\\s*=\\s*([^;\\s]+)`, 'i').exec(text);
    return match ? decodeURIComponent(match[1]) : '';
  };

  const hasNames = /sessionid\s*=/i.test(text);
  return {
    sessionId: hasNames ? value('sessionid') : text.split(';')[0].trim(),
    csrfToken: value('csrftoken'),
    dsUserId: value('ds_user_id'),
  };
}

export const IG_APP_ID = '936619743392459';

export const hasSession = () => SESSION.sessionId.length > 20;

/** الكوكيز كنص — كتبقى داخل الخادم فقط، وما كتطبعش أبداً فاللوغ */
export function cookieHeader() {
  const parts = [`sessionid=${SESSION.sessionId}`];
  if (SESSION.csrf) parts.push(`csrftoken=${SESSION.csrf}`);
  if (SESSION.dsUserId) parts.push(`ds_user_id=${SESSION.dsUserId}`);
  return parts.join('; ');
}

/** معلومات آمنة (بلا الكوكيز) باش نعرضو الحالة فالتطبيق/اللوغ */
export function sessionInfo() {
  return {
    configured: hasSession(),
    source: SESSION.source,
    savedAt: SESSION.savedAt,
    hasCsrf: Boolean(SESSION.csrf),
    hasUserId: Boolean(SESSION.dsUserId),
    /** آخر 4 حروف فقط — باش تتعرف الجلسة بلا ما تنكشف */
    fingerprint: hasSession() ? `…${SESSION.sessionId.slice(-4)}` : null,
  };
}

/** headers ديال الويب ديال إنستغرام، بالجلسة إلا كانت */
export function webHeaders({ userAgent, referer = 'https://www.instagram.com/' } = {}) {
  const headers = {
    'User-Agent':
      userAgent ??
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15',
    'Accept': '*/*',
    'Accept-Language': 'en-US,en;q=0.9',
    'x-ig-app-id': IG_APP_ID,
    'X-Requested-With': 'XMLHttpRequest',
    Referer: referer,
    'X-ASBD-ID': '129477',
    'X-IG-WWW-Claim': '0',
  };

  if (hasSession()) {
    headers.Cookie = cookieHeader();
    if (SESSION.csrf) headers['x-csrftoken'] = SESSION.csrf;
  }

  return headers;
}

/**
 * كيقرا JSON ديال إنستغرام وكيجمع **كل** الأكواد اللي كيبان بلي ريلز.
 * كنمشيو بالاحتياط: كل كود كيتحط فالطابور وكيتأكد وحدو من بعد.
 */
export function collectShortcodes(json, output = new Set()) {
  if (!json || typeof json !== 'object') return output;

  if (Array.isArray(json)) {
    json.forEach((item) => collectShortcodes(item, output));
    return output;
  }

  const code = json.code ?? json.shortcode;
  const isVideoish =
    json.media_type === 2 ||
    json.product_type === 'clips' ||
    json.is_video === true ||
    json.video_versions !== undefined ||
    json.media_product_type === 'REELS';

  if (typeof code === 'string' && /^[A-Za-z0-9_-]{8,15}$/.test(code) && isVideoish) {
    output.add(code);
  }

  Object.values(json).forEach((value) => {
    if (value && typeof value === 'object') collectShortcodes(value, output);
  });

  return output;
}

/**
 * قنوات مبنية على الجلسة — كلها بلا حتى اسم حساب.
 * إنستغرام كيبدّل المسارات بين النسخ، لهذا كنحاولو عدة مرشحين وكنخدو اللي كيرجع JSON.
 */
export const SESSION_ENDPOINTS = [
  { name: 'home-feed', path: '/api/v1/feed/timeline/?count=12' },
  { name: 'explore', path: '/api/v1/feed/explore/?count=12' },
  { name: 'discover', path: '/api/v1/discover/explore/?is_prefetch=false' },
  { name: 'clips-home', path: '/api/v1/clips/home/?count=12' },
  { name: 'clips-discover', path: '/api/v1/clips/discover/?count=12' },
];

/** مسارات الهاشتاغ المرشحة (كنجرّبهم كلهم) */
export const hashtagPaths = (tag) => {
  const clean = encodeURIComponent(tag);
  return [
    { name: `tag-feed:${tag}`, path: `/api/v1/feed/tag/${clean}/?count=12` },
    { name: `tag-sections:${tag}`, path: `/api/v1/tags/${clean}/sections/` },
    { name: `tag-webinfo:${tag}`, path: `/api/v1/tags/web_info/?tag_name=${clean}` },
  ];
};

/** المسار القديم — كنخليوه للتوافق */
export const hashtagPath = (tag) => hashtagPaths(tag)[0].path;
