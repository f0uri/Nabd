/**
 * التحقق من هوية Google — كامل فالخادم، عمرنا ما كنتيقو فالتطبيق.
 *
 * التطبيق كيجيب غير idToken من Credential Manager، والخادم هو اللي:
 *   - كيفحقق التوقيع والمُصدر (Google) والمُستقبِل (GOOGLE_WEB_CLIENT_ID).
 *   - كيفحقق تاريخ الصلاحية.
 *   - كيفرضي بلي البريد مفعل (email_verified).
 *
 * إلا شي وحدة من هادو ما صدقاتش: كنرفضو الطلب.
 */
import { OAuth2Client } from 'google-auth-library';

let client = null;

export const googleConfigured = () => Boolean(process.env.GOOGLE_WEB_CLIENT_ID);

function audience() {
  const id = (process.env.GOOGLE_WEB_CLIENT_ID || '').trim();
  if (!id) throw new Error('GOOGLE_NOT_CONFIGURED');
  return id;
}

/**
 * كيفكّ idToken وكيخرّج منه البريد والاسم والصورة.
 * كيلقى استثناء إلا كان الرمز غالط، قديم، ماشي موجه لينا، ولا البريد مامفعلش.
 */
export async function verifyGoogleIdToken(idToken) {
  const expected = audience();
  const token = String(idToken ?? '').trim();
  if (!token) throw new Error('GOOGLE_INVALID_TOKEN');

  client ??= new OAuth2Client(expected);
  let payload;
  try {
    const ticket = await client.verifyIdToken({ idToken: token, audience: expected });
    payload = ticket.getPayload();
  } catch (error) {
    // الرمز غالط، قديم، مقطوع، ولا ماشي موجه لهاد الخادم.
    console.warn('[google] رفضنا idToken / rejected idToken:', error?.message ?? error);
    throw new Error('GOOGLE_INVALID_TOKEN');
  }

  if (!payload || !payload.email) throw new Error('GOOGLE_INVALID_TOKEN');
  if (payload.email_verified !== true) throw new Error('GOOGLE_EMAIL_NOT_VERIFIED');
  if (!payload.sub) throw new Error('GOOGLE_INVALID_TOKEN');

  return {
    sub: payload.sub,
    email: payload.email.trim().toLowerCase(),
    name: (payload.name || payload.email.split('@')[0]).trim().slice(0, 40),
    picture: payload.picture && payload.picture.startsWith('https://') ? payload.picture : null,
  };
}
