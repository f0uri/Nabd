import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';

function key() {
  const value = process.env.IG_SESSION_ENCRYPTION_KEY ?? '';
  if (!/^[a-f0-9]{64}$/i.test(value)) throw new Error('INSTAGRAM_SESSION_KEY_REQUIRED');
  return Buffer.from(value, 'hex');
}

/** Dedicated key must be supplied by the host, not stored beside the ciphertext. */
export function encryptSession(session) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key(), iv);
  cipher.setAAD(Buffer.from('nabd:ig_session:v1'));
  const data = Buffer.concat([cipher.update(JSON.stringify(session), 'utf8'), cipher.final()]);
  return JSON.stringify({ v: 1, iv: iv.toString('base64'), tag: cipher.getAuthTag().toString('base64'), data: data.toString('base64') });
}

export function decryptSession(raw) {
  const encryptionKey = key();
  try {
    const value = JSON.parse(raw);
    if (value.v !== 1) throw new Error();
    const decipher = createDecipheriv('aes-256-gcm', encryptionKey, Buffer.from(value.iv, 'base64'));
    decipher.setAAD(Buffer.from('nabd:ig_session:v1'));
    decipher.setAuthTag(Buffer.from(value.tag, 'base64'));
    return JSON.parse(Buffer.concat([decipher.update(Buffer.from(value.data, 'base64')), decipher.final()]).toString('utf8'));
  } catch {
    throw new Error('INSTAGRAM_SESSION_UNREADABLE');
  }
}
