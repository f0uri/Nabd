/**
 * الترجمة — بلا مفاتيح، مع سلسلة مزوّدين وذاكرة مؤقتة.
 *
 * الترتيب:
 *   1) TRANSLATE_URL  (أي خدمة LibreTranslate: POST {q, source, target, format})
 *   2) Google (نقطة gtx العمومية — بلا مفتاح)
 *   3) MyMemory (بلا مفتاح، حد 500 حرف)
 *
 * كل مزوّد عندو مهلة 6 ثواني. إلا فشلو كاملين → خطأ TRANSLATION_UNAVAILABLE
 * (التطبيق كيورّي Toast ومكتبقاش يطلب الترجمة بلا داعي).
 */

const TIMEOUT_MS = 6000;
/** حد أقصى للذاكرة المؤقتة (ترجمة) */
const CACHE_MAX = 800;
const cache = new Map();

/** كشف بسيط: واش النص عربي؟ (باش نختارو الاتجاه الافتراضي) */
export function detectLang(text) {
  const arabic = (text.match(/[\u0600-\u06FF]/g) ?? []).length;
  const latin = (text.match(/[A-Za-zÀ-ÿ]/g) ?? []).length;
  if (arabic === 0 && latin === 0) return 'ar';
  return arabic >= latin ? 'ar' : 'en';
}

/** اللغة الافتراضية للترجمة: عربي → فرنسي، وغير عربي → عربي */
export function defaultTarget(text, preferred = null) {
  if (preferred && preferred !== 'auto') return preferred;
  return detectLang(text) === 'ar' ? 'fr' : 'ar';
}

const cacheKey = (text, to) => `${to}::${text}`;
const cacheGet = (text, to) => cache.get(cacheKey(text, to)) ?? null;
const cacheSet = (text, to, value) => {
  if (cache.size >= CACHE_MAX) cache.delete(cache.keys().next().value);
  cache.set(cacheKey(text, to), value);
};

async function postJson(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json', accept: 'application/json' },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (!res.ok) throw new Error(`HTTP_${res.status}`);
  return res.json();
}

async function getJson(url) {
  const res = await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS) });
  if (!res.ok) throw new Error(`HTTP_${res.status}`);
  return res.json();
}

/* ------------------------------- المزوّدون ------------------------------- */

async function viaLibre(text, to) {
  const url = process.env.TRANSLATE_URL;
  if (!url) throw new Error('NOT_CONFIGURED');
  const data = await postJson(url, {
    q: text,
    source: 'auto',
    target: to,
    format: 'text',
    ...(process.env.TRANSLATE_KEY ? { api_key: process.env.TRANSLATE_KEY } : {}),
  });
  const translated = data?.translatedText;
  if (!translated) throw new Error('EMPTY');
  return { text: translated, from: data?.detectedLanguage?.language ?? detectLang(text) };
}

async function viaGoogle(text, to) {
  const params = new URLSearchParams({ client: 'gtx', sl: 'auto', tl: to, dt: 't', q: text });
  const data = await getJson(`https://translate.googleapis.com/translate_a/single?${params.toString()}`);
  if (!Array.isArray(data?.[0])) throw new Error('BAD_RESPONSE');
  const translated = data[0].map((chunk) => chunk?.[0] ?? '').join('');
  if (!translated) throw new Error('EMPTY');
  return { text: translated, from: data[2] ?? detectLang(text) };
}

async function viaMyMemory(text, to) {
  const from = detectLang(text);
  if (from === to) throw new Error('SAME_LANG');
  const params = new URLSearchParams({ q: text.slice(0, 500), langpair: `${from}|${to}` });
  const data = await getJson(`https://api.mymemory.translated.net/get?${params.toString()}`);
  const translated = data?.responseData?.translatedText;
  if (!translated || /^[A-Z_ ]+$/.test(translated)) throw new Error('EMPTY');
  // MyMemory كيرجّع أحياناً «أشكال عرض» عربية (U+FExx) → كنحوّلوها للحروف العادية
  return { text: String(translated).normalize('NFKC'), from };
}

const PROVIDERS = [
  { name: 'libre', run: viaLibre },
  { name: 'google', run: viaGoogle },
  { name: 'mymemory', run: viaMyMemory },
];

/**
 * يترجم نصاً. كيرجّع { text, from, to, provider, cached }
 * وكيرمي TRANSLATION_UNAVAILABLE إلا فشلو كاملين.
 */
export async function translateText(text, to) {
  const clean = String(text ?? '').trim().slice(0, 2000);
  if (!clean) return { text: '', from: detectLang(''), to, provider: 'none', cached: false };

  const cached = cacheGet(clean, to);
  if (cached) return { ...cached, cached: true };

  const errors = [];
  for (const provider of PROVIDERS) {
    try {
      const result = await provider.run(clean, to);
      const value = { text: result.text, from: result.from ?? detectLang(clean), to, provider: provider.name };
      cacheSet(clean, to, value);
      return { ...value, cached: false };
    } catch (error) {
      errors.push(`${provider.name}:${error.message}`);
    }
  }

  const error = new Error('TRANSLATION_UNAVAILABLE');
  error.detail = errors.join(' · ');
  throw error;
}
