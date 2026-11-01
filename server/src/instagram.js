/**
 * استيراد ريلز إنستغرام (روابط عامة) — بلا مفاتيح وبلا تسجيل دخول.
 *
 * الطريقة: صفحة الـ embed العمومية ديال إنستغرام (`/reel/<code>/embed/captioned/`)
 * كترجّع JSON فيه `video_url` و`display_url` والوصف واسم الحساب. كنقراوها،
 * كنحمّلو الفيديو، وكنخزّنوه عندنا (mirror) حيت روابط CDN ديال إنستغرام كتنتهي صلاحيتها.
 *
 * حدود:
 *  - غير الحسابات **العامة** (الخاصة كترجّع LOGIN_REQUIRED).
 *  - الحد الأقصى للفيديو 40MB و180 ثانية (إنستغرام كيسمح بـ3 دقايق).
 *  - الملكية كتعود لصاحبها: كنحفظو `source_author` و`source_url` وكتظهر فالتطبيق.
 */

/**
 * إنستغرام كيرجّع نسختين مختلفين ديال نفس الصفحة حسب الـ User-Agent:
 * الواحدة (Safari/Googlebot) فيها JSON ديال الفيديو، والأخرى (Chrome desktop) صفحة غنية بلا `video_url`.
 * كنحاولو بيهم بالترتيب حتى نلقاو البيانات.
 */
const USER_AGENTS = [
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15',
  'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)',
  'curl/8.4.0',
];
const UA = USER_AGENTS[0];

const MAX_BYTES = 40 * 1024 * 1024;
const MAX_DURATION = 180;
const TIMEOUT = 15000;

/** يستخرج shortcode من أي شكل ديال رابط إنستغرام */
export function parseShortcode(input) {
  if (typeof input !== 'string') return null;
  const value = input.trim();
  if (/^[A-Za-z0-9_-]{6,20}$/.test(value)) return value;

  const match = /instagram\.com\/(?:reels?|p|tv)\/([A-Za-z0-9_-]{6,20})/i.exec(value);
  if (match) return match[1];

  const short = /^(?:https?:\/\/)?(?:www\.)?instagr\.am\/p\/([A-Za-z0-9_-]{6,20})/i.exec(value);
  return short ? short[1] : null;
}

/** فك التشفير ديال JSON اللي داخل JS string (مرتين فأغلب الأحيان) */
const deepUnescape = (raw) => {
  let text = raw;
  let previous = '';
  let guard = 0;
  while (text !== previous && guard < 4) {
    previous = text;
    text = text
      .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
      .replace(/\\\//g, '/')
      .replace(/\\"/g, '"');
    guard += 1;
  }
  return text;
};

/** تنظيف نص جا من HTML فيه عدة طبقات ديال التشفير */
const cleanText = (raw) => {
  let out = (raw ?? '').replace(/\\+n/g, ' ').replace(/\\+r/g, ' ');
  for (let i = 0; i < 6; i += 1) {
    out = out
      .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
      .replace(/\\(.)/g, '$1');
  }
  return out.replace(/\s+/g, ' ').trim();
};

const pick = (text, regex) => {
  const match = regex.exec(text);
  return match ? match[1] : null;
};

/** كيحاول عدة مسارات وأغلفة باش يلقى JSON ديال الريل */
async function fetchEmbedHtml(shortcode) {
  let lastError = null;

  for (const userAgent of USER_AGENTS) {
    for (const path of ['reel', 'p']) {
      const url = `https://www.instagram.com/${path}/${shortcode}/embed/captioned/`;
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), TIMEOUT);

      try {
        const response = await fetch(url, {
          headers: { 'User-Agent': userAgent, Accept: 'text/html,application/xhtml+xml' },
          redirect: 'follow',
          signal: controller.signal,
        });
        if (!response.ok) {
          lastError = response.status === 404 ? 'REEL_NOT_FOUND' : 'INSTAGRAM_ERROR';
          continue;
        }
        const html = await response.text();
        if (html.includes('video_url')) return html;
        if (/"is_video":false/.test(html)) throw new Error('NOT_A_VIDEO');
        lastError = 'INSTAGRAM_BLOCKED';
      } catch (error) {
        if (error.message === 'NOT_A_VIDEO') throw error;
        lastError = error.name === 'AbortError' ? 'INSTAGRAM_TIMEOUT' : 'INSTAGRAM_UNREACHABLE';
      } finally {
        clearTimeout(timer);
      }
    }
  }

  throw new Error(lastError ?? 'INSTAGRAM_ERROR');
}

/** كيجيب معلومات الريل من صفحة الـ embed */
export async function fetchReelMeta(shortcode) {
  const html = await fetchEmbedHtml(shortcode);
  const text = deepUnescape(html);

  // تأكد أن الـ shortcode هو نفسه (إلا كان الريل محذوف ولا خاص)
  if (!text.includes(`"shortcode":"${shortcode}"`)) throw new Error('REEL_NOT_FOUND');

  const videoUrl = pick(text, /"video_url":"([^"]+)"/);
  const thumbUrl = pick(text, /"display_url":"([^"]+)"/);
  const duration = Number(pick(text, /"video_duration":([0-9.]+)/) ?? 0);
  const height = Number(pick(text, /"dimensions":\{"height":(\d+)/) ?? 0);
  const width = Number(pick(text, /"dimensions":\{"height":\d+,"width":(\d+)/) ?? 0);
  const author = pick(text, /"owner":\{"id":"\d+","username":"([^"]+)"/) ?? pick(text, /"username":"([^"]+)"/);
  const caption = pick(text, /"edge_media_to_caption":\{"edges":\[\{"node":\{"text":"([^"]*)"/) ?? '';
  const viewCount = Number(pick(text, /"video_view_count":(\d+)/) ?? 0);

  if (!videoUrl) throw new Error('NOT_A_VIDEO');
  if (duration > MAX_DURATION) throw new Error('REEL_TOO_LONG');

  return {
    shortcode,
    videoUrl,
    thumbUrl,
    duration,
    width: width || 9,
    height: height || 16,
    author: author ? cleanText(author) : null,
    caption: cleanText(caption).slice(0, 600),
    viewCount,
    permalink: `https://www.instagram.com/reel/${shortcode}/`,
  };
}

/** كيحمّل الفيديو من CDN ديال إنستغرام (بحد أقصى) */
export async function downloadReelVideo(videoUrl, { maxBytes = MAX_BYTES } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 60000);

  try {
    const response = await fetch(videoUrl, {
      headers: { 'User-Agent': UA, Referer: 'https://www.instagram.com/' },
      signal: controller.signal,
    });
    if (!response.ok) throw new Error('INSTAGRAM_MEDIA_ERROR');

    const declared = Number(response.headers.get('content-length') ?? 0);
    if (declared && declared > maxBytes) throw new Error('REEL_TOO_LARGE');

    const buffer = Buffer.from(await response.arrayBuffer());
    if (buffer.length > maxBytes) throw new Error('REEL_TOO_LARGE');
    if (buffer.length < 1024) throw new Error('INSTAGRAM_MEDIA_ERROR');

    return { buffer, mime: response.headers.get('content-type') ?? 'video/mp4', size: buffer.length };
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('INSTAGRAM_TIMEOUT');
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

export const instagramLimits = { maxBytes: MAX_BYTES, maxDuration: MAX_DURATION };
