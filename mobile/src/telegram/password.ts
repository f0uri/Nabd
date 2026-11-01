// Vendored reviewed algorithms, not a new home-grown password hash.
import { sha256 } from './vendor/sha256.js';
import { pbkdf2Async } from './vendor/pbkdf2.js';
export const PASSWORD_ROUNDS = 210000;
export function utf8(text: string): Uint8Array {
  const bytes: number[] = [];
  for (const char of text) {
    let n = char.codePointAt(0)!;
    if (n >= 0xd800 && n <= 0xdfff) n = 0xfffd;
    if (n < 128) bytes.push(n);
    else if (n < 2048) bytes.push(192 | (n >> 6), 128 | (n & 63));
    else if (n < 65536)
      bytes.push(224 | (n >> 12), 128 | ((n >> 6) & 63), 128 | (n & 63));
    else
      bytes.push(
        240 | (n >> 18),
        128 | ((n >> 12) & 63),
        128 | ((n >> 6) & 63),
        128 | (n & 63),
      );
  }
  return Uint8Array.from(bytes);
}
export const hex = (bytes: Uint8Array): string =>
  Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
export const digest = (value: string): string => hex(sha256(utf8(value)));
export async function passwordDigest(
  password: string,
  salt: string,
): Promise<string> {
  if (password.length > 128 || !/^[a-f0-9]{32}$/.test(salt))
    throw new Error('INVALID_CREDENTIALS');
  // Use the platform implementation when WebCrypto already exists. Never add
  // a native dependency, lower the work factor or change legacy hash bytes.
  const subtle = globalThis.crypto?.subtle;
  if (subtle) {
    try {
      const key = await subtle.importKey(
        'raw',
        utf8(password),
        'PBKDF2',
        false,
        ['deriveBits'],
      );
      const bytes = await subtle.deriveBits(
        {
          name: 'PBKDF2',
          hash: 'SHA-256',
          salt: Uint8Array.from(salt.match(/../g)!.map((n) => parseInt(n, 16))),
          iterations: PASSWORD_ROUNDS,
        },
        key,
        256,
      );
      return hex(new Uint8Array(bytes));
    } catch (error) {
      if ((error as Error).name !== 'NotSupportedError')
        throw new Error('PASSWORD_DERIVATION_FAILED');
    }
  }
  return hex(
    await pbkdf2Async(
      sha256,
      utf8(password),
      Uint8Array.from(salt.match(/../g)!.map((n) => parseInt(n, 16))),
      { c: PASSWORD_ROUNDS, dkLen: 32, asyncTick: 10 },
    ),
  );
}
