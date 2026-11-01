/**
 * المصادقة — بلا أي تبعية خارجية (scrypt + JWT HS256 مبنية على node:crypto).
 * في الإنتاج: bcrypt/argon2 + refresh tokens + https إلزامي.
 */
import { createHmac, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

import { resolveAuthSecret } from './runtimeConfig.js';

const SECRET = resolveAuthSecret();
const TOKEN_TTL_SECONDS = 60 * 60 * 24 * 30; // 30 يوماً

if (!process.env.AUTH_SECRET) {
  console.warn('⚠️  AUTH_SECRET غير مضبوط — كنستعملو قيمة تطوير. ضبطو فـ .env قبل الإنتاج.');
}

/** scrypt: salt$hash */
export function hashPassword(password) {
  const salt = randomBytes(16).toString('hex');
  const hash = scryptSync(password, salt, 64).toString('hex');
  return `${salt}$${hash}`;
}

export function verifyPassword(password, stored) {
  if (!stored || !stored.includes('$')) return false;
  const [salt, hash] = stored.split('$');
  const candidate = scryptSync(password, salt, 64);
  const expected = Buffer.from(hash, 'hex');
  if (candidate.length !== expected.length) return false;
  return timingSafeEqual(candidate, expected);
}

const b64url = (value) => Buffer.from(value).toString('base64url');

export function signToken(payload, ttl = TOKEN_TTL_SECONDS) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = b64url(
    JSON.stringify({ ...payload, iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + ttl }),
  );
  const signature = createHmac('sha256', SECRET).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${signature}`;
}

export function verifyToken(token) {
  if (!token || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const [header, body, signature] = parts;
  const expected = createHmac('sha256', SECRET).update(`${header}.${body}`).digest('base64url');
  if (signature.length !== expected.length) return null;
  if (!timingSafeEqual(Buffer.from(signature), Buffer.from(expected))) return null;

  try {
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
    if (payload.exp && payload.exp * 1000 < Date.now()) return null;
    return payload;
  } catch {
    return null;
  }
}

export function userFromRequest(request) {
  const header = request?.headers?.get?.('authorization') ?? request?.headers?.authorization ?? '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : null;
  return verifyToken(token);
}
