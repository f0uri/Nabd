/**
 * إرسال الإيميلات — Resend عبر HTTP بلا أي تبعية خارجية (نفس فلسفة auth.js).
 *
 * المتغيرات (شوف .env.example):
 *   MAIL_PROVIDER=resend | console     console = كيطبع فالسجل فقط (تطوير)
 *   RESEND_API_KEY=...                 فأسرار الاستضافة فقط، عمرها فالتطبيق
 *   MAIL_FROM="نبض <no-reply@nabd.app>"  خاصك تحقق الدومين فـ Resend
 *   APP_NAME=نبض
 *
 * القالب بجوج اللغات (عربي + إنجليزي) والرمز باين كبير، مع التذكير بـ 10 دقائق.
 */
import { CODE_TTL_SECONDS } from './emailCodes.js';

const FROM = process.env.MAIL_FROM || 'نبض <onboarding@resend.dev>';
const APP_NAME = process.env.APP_NAME || 'نبض';
const PROVIDER = (process.env.MAIL_PROVIDER || 'console').toLowerCase();
const RESEND_KEY = process.env.RESEND_API_KEY || '';

const minutes = Math.round(CODE_TTL_SECONDS / 60);

/** الرمز مقسوم على جوج: سهل يتقرا ويتنسخ. */
const spaced = (code) => `${String(code).slice(0, 3)} ${String(code).slice(3)}`;

function template({ code, purpose }) {
  const whatAr = purpose === 'reset' ? 'إعادة تعيين كلمة المرور' : 'تفعيل حسابك';
  const whatEn = purpose === 'reset' ? 'reset your password' : 'verify your account';
  return {
    subject: `${APP_NAME} — رمز التحقق ${spaced(code)}`,
    text: [
      `${APP_NAME} — ${whatAr} / ${APP_NAME}: ${whatEn}`,
      '',
      `رمز التحقق ديالك: ${code}`,
      `Your verification code: ${code}`,
      '',
      `صالح لمدة ${minutes} دقائق. / Valid for ${minutes} minutes.`,
      `إلا ما طلبتيش هاد الرمز، تجاهل هاد الرسالة. / If you did not request it, ignore this message.`,
    ].join('\n'),
    html: `<div dir="rtl" style="font-family:system-ui,Segoe UI,Tahoma,sans-serif;background:#0B1220;color:#E9EEF7;padding:28px;border-radius:18px">
  <h1 style="margin:0 0 6px;font-size:22px">${APP_NAME} — ${whatAr}</h1>
  <p style="margin:0 0 18px;color:#9AA7BD;font-size:14px">${whatEn}</p>
  <div style="font-size:40px;letter-spacing:10px;font-weight:800;background:#111C33;border:1px solid #22304F;border-radius:16px;padding:18px;text-align:center;color:#7FB2FF">${spaced(code)}</div>
  <p style="margin:18px 0 0;font-size:14px">صالح لمدة <b>${minutes} دقائق</b> · Valid for <b>${minutes} minutes</b></p>
  <p style="margin:8px 0 0;color:#9AA7BD;font-size:12.5px">إلا ما طلبتيش هاد الرمز، تجاهل هاد الرسالة.<br/>If you did not request this code, you can ignore this email.</p>
</div>`,
  };
}

/**
 * كيصيفط الإيميل. ما كيلقيش استثناء: الفشل كيرجّع { delivered: false }
 * باش الخادم يبقى كيجاوب نفس الجواب وما يبيّن والو.
 */
export async function sendCodeEmail({ email, code, purpose }) {
  const body = template({ code, purpose });
  if (PROVIDER === 'console' || !RESEND_KEY) {
    console.log(`[mailer] ${PROVIDER} → ${email} · ${purpose} · ${code}`);
    return { delivered: PROVIDER === 'console', provider: 'console' };
  }
  if (PROVIDER !== 'resend') return { delivered: false, provider: PROVIDER };

  try {
    const response = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: { authorization: `Bearer ${RESEND_KEY}`, 'content-type': 'application/json' },
      body: JSON.stringify({ from: FROM, to: [email], subject: body.subject, text: body.text, html: body.html }),
    });
    return { delivered: response.ok, provider: 'resend', status: response.status };
  } catch (error) {
    console.warn('[mailer] تعذّر الإرسال / send failed:', error?.message ?? error);
    return { delivered: false, provider: 'resend' };
  }
}

export const mailerConfigured = () => PROVIDER === 'resend' && Boolean(RESEND_KEY);

/**
 * بريد «دخول بـ Google»: حساب بلا كلمة مرور طلب استعادة.
 * ما كنبيّنوش والو على وجود الحساب: كيتصيفط غير إلا كان فعلاً.
 */
export async function sendGoogleOnlyNotice({ email }) {
  const text = [
    `${APP_NAME} — طلب استعادة كلمة المرور / password reset request`,
    '',
    'هاد الحساب كيدخل بـ Google، وما عندو حتى كلمة مرور.',
    'This account uses Google sign-in, so it has no password.',
    '',
    'باش تدخل، استعمل زر «المتابعة بحساب Google» فشاشة الدخول.',
    'To sign in, use the "Continue with Google" button on the sign-in screen.',
  ].join('\n');
  const html = `<div dir="rtl" style="font-family:system-ui,Segoe UI,Tahoma,sans-serif;background:#0B1220;color:#E9EEF7;padding:28px;border-radius:18px">
  <h1 style="margin:0 0 12px;font-size:22px">${APP_NAME} — استعادة كلمة المرور</h1>
  <p style="margin:0 0 14px;font-size:15px">هاد الحساب كيدخل <b>بـ Google</b>، وما عندو حتى كلمة مرور.</p>
  <p style="margin:0 0 6px;color:#9AA7BD;font-size:14px">This account uses <b>Google sign-in</b>, so it has no password.</p>
  <p style="margin:18px 0 0;font-size:14px">باش تدخل، استعمل زر «المتابعة بحساب Google» فشاشة الدخول.</p>
  <p style="margin:6px 0 0;color:#9AA7BD;font-size:13px">To sign in, use the "Continue with Google" button on the sign-in screen.</p>
</div>`;
  const subject = `${APP_NAME} — حسابك كيدخل بـ Google`;
  if (PROVIDER === 'console' || !RESEND_KEY) {
    console.log(`[mailer] ${PROVIDER} → ${email} · google-only notice`);
    return { delivered: PROVIDER === 'console', provider: 'console' };
  }
  try {
    const response = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: { authorization: `Bearer ${RESEND_KEY}`, 'content-type': 'application/json' },
      body: JSON.stringify({ from: FROM, to: [email], subject, text, html }),
    });
    return { delivered: response.ok, provider: 'resend', status: response.status };
  } catch (error) {
    console.warn('[mailer] تعذّر الإرسال / send failed:', error?.message ?? error);
    return { delivered: false, provider: 'resend' };
  }
}
