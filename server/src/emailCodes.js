/**
 * رموز التحقق — القواعد كاملة هنا، فالخادم، ماشي فالتطبيق.
 *
 *  - الرمز 6 أرقام، صالح 10 دقائق.
 *  - 5 محاولات غالطة والرمز كيتبطل.
 *  - كيستعمل مرة وحدة: من بعد النجاح كيتمسح.
 *  - إعادة الإرسال: 60 ثانية بين الرمز والآخر.
 *  - تحديد المعدّل: 5 رموز فالساعة للبريد، و20 فالساعة لكل IP.
 *  - ما كيتخزّن غير توقيع الرمز (sha256)، عمرنا ما كنكتبو الرمز صريح.
 *
 * ولا مرة ما كنجاوبو واش البريد كاين ولا لا: كلشي كيخدم على النص اللي جا.
 */
import { createHash, randomInt, timingSafeEqual } from 'node:crypto';

import { query, queryOne } from './db.js';
import { resolveAuthSecret } from './runtimeConfig.js';

const SECRET = resolveAuthSecret();
export const CODE_TTL_SECONDS = 10 * 60;
export const RESEND_COOLDOWN_SECONDS = 60;
export const MAX_ATTEMPTS = 5;
const MAX_PER_EMAIL_PER_HOUR = 5;
const MAX_PER_IP_PER_HOUR = 20;

export const PURPOSES = { VERIFY: 'verify', RESET: 'reset' };

const normalize = (email) => String(email ?? '').trim().toLowerCase();

/** توقيع الرمز: sha256(السر + البريد + الغرض + الرمز). الرمز ما كيتقراش من قاعدة البيانات. */
function digest(email, purpose, code) {
  return createHash('sha256').update(`${SECRET}|${email}|${purpose}|${code}`).digest('hex');
}

function randomCode() {
  return String(randomInt(0, 1_000_000)).padStart(6, '0');
}

const safeEqual = (a, b) => {
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  return left.length === right.length && timingSafeEqual(left, right);
};

/** شحال باقي من الثواني قبل ما تقدر تعاود تصيفط (0 = دابا تقدر). */
export async function secondsUntilResend(email, purpose) {
  const clean = normalize(email);
  const row = await queryOne(
    `select created_at from email_codes
      where email = $1 and purpose = $2 and used_at is null
      order by created_at desc limit 1`,
    [clean, purpose],
  );
  if (!row) return 0;
  const created = new Date(row.created_at).getTime();
  const elapsed = Math.floor((Date.now() - created) / 1000);
  return Math.max(0, RESEND_COOLDOWN_SECONDS - elapsed);
}

async function countRecent(email, purpose, ip) {
  const since = new Date(Date.now() - 60 * 60 * 1000).toISOString();
  const perEmail = await queryOne(
    `select count(*)::int as total from email_codes where email = $1 and created_at > $2`,
    [normalize(email), since],
  );
  const perIp = await queryOne(
    `select count(*)::int as total from email_codes where request_ip = $1 and created_at > $2`,
    [String(ip ?? 'unknown'), since],
  );
  return { perEmail: perEmail?.total ?? 0, perIp: perIp?.total ?? 0 };
}

/**
 * كيصايب رمز ويصيفطو. كيرجّع { sent, retryAfter } — sent كيبقى true واخا البريد
 * ما كاينش، حيت هاداك بالضبط اللي كيحمي خصوصية الحسابات.
 */
export async function issueCode({ email, purpose, ip }) {
  const clean = normalize(email);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(clean)) throw new Error('INVALID_EMAIL');
  if (!Object.values(PURPOSES).includes(purpose)) throw new Error('INVALID_PURPOSE');

  const wait = await secondsUntilResend(clean, purpose);
  if (wait > 0) return { sent: true, retryAfter: wait, throttled: true, code: null };

  const { perEmail, perIp } = await countRecent(clean, purpose, ip);
  if (perEmail >= MAX_PER_EMAIL_PER_HOUR || perIp >= MAX_PER_IP_PER_HOUR) {
    return { sent: true, retryAfter: RESEND_COOLDOWN_SECONDS, throttled: true, code: null };
  }

  const code = randomCode();
  await query(
    `insert into email_codes (id, email, purpose, code_hash, expires_at, attempts, request_ip, created_at)
     values ($1, $2, $3, $4, $5, 0, $6, now())
     on conflict (email, purpose) do update
       set code_hash = excluded.code_hash, expires_at = excluded.expires_at, attempts = 0,
           used_at = null, request_ip = excluded.request_ip, created_at = now()`,
    [
      createHash('sha256').update(`${clean}:${purpose}:${code}:${Date.now()}`).digest('hex').slice(0, 32),
      clean,
      purpose,
      digest(clean, purpose, code),
      new Date(Date.now() + CODE_TTL_SECONDS * 1000).toISOString(),
      String(ip ?? 'unknown'),
    ],
  );
  return { sent: true, retryAfter: RESEND_COOLDOWN_SECONDS, throttled: false, code };
}

/**
 * كيفحقق الرمز بلا ما يستهلكو. كيرجّع { ok: true } ولا كيلقى واحد من:
 * NO_CODE · EXPIRED · USED · TOO_MANY_ATTEMPTS · WRONG_CODE
 */
export async function checkCode({ email, purpose, code }) {
  const clean = normalize(email);
  const row = await queryOne(
    `select code_hash, expires_at, attempts, used_at from email_codes where email = $1 and purpose = $2`,
    [clean, purpose],
  );
  if (!row) return { ok: false, reason: 'NO_CODE' };
  if (row.used_at) return { ok: false, reason: 'USED' };
  if (new Date(row.expires_at).getTime() < Date.now()) {
    await query(`delete from email_codes where email = $1 and purpose = $2`, [clean, purpose]);
    return { ok: false, reason: 'EXPIRED' };
  }
  if (Number(row.attempts) >= MAX_ATTEMPTS) return { ok: false, reason: 'TOO_MANY_ATTEMPTS' };

  if (!safeEqual(digest(clean, purpose, String(code ?? '').trim()), row.code_hash)) {
    const attempts = Number(row.attempts) + 1;
    if (attempts >= MAX_ATTEMPTS) {
      await query(`delete from email_codes where email = $1 and purpose = $2`, [clean, purpose]);
      return { ok: false, reason: 'TOO_MANY_ATTEMPTS' };
    }
    await query(`update email_codes set attempts = $3 where email = $1 and purpose = $2`, [clean, purpose, attempts]);
    return { ok: false, reason: 'WRONG_CODE', remaining: MAX_ATTEMPTS - attempts };
  }
  return { ok: true };
}

/** كيستهلك الرمز: مرة وحدة وكيتمسح. */
export async function consumeCode({ email, purpose, code }) {
  const result = await checkCode({ email, purpose, code });
  if (!result.ok) return result;
  await query(`update email_codes set used_at = now() where email = $1 and purpose = $2`, [
    normalize(email),
    purpose,
  ]);
  return { ok: true };
}

/** تنقية الرموز القديمة (كيتمسحو مع كل إرسال، واخا ما تناداوش هادي). */
export async function pruneCodes() {
  await query(`delete from email_codes where expires_at < now() or used_at is not null`);
}
