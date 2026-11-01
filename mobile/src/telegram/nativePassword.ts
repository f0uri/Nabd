import { hex, utf8, PASSWORD_ROUNDS } from './password';

type Pbkdf2 = typeof import('react-native-quick-crypto').pbkdf2;
let binding: Pbkdf2 | null | undefined;
let compatibility: Promise<void> | undefined;
function nativeBinding(): Pbkdf2 | null {
  if (binding !== undefined) return binding;
  try {
    // Lazy public API; do NOT call install() or replace global crypto/Buffer.
    const candidate = require('react-native-quick-crypto').pbkdf2;
    binding = typeof candidate === 'function' ? candidate : null;
  } catch {
    // Expo Go / an older native shell must still use the compatible JS path.
    binding = null;
  }
  return binding ?? null;
}
function derive(
  pbkdf2: Pbkdf2,
  password: Uint8Array,
  salt: Uint8Array,
  rounds: number,
): Promise<Uint8Array> {
  return new Promise((resolve, reject) => {
    // Bytes, not library string encodings: preserve Arabic, emoji, lone-surrogate
    // replacement, embedded NULs, and the existing raw 16-byte salt exactly.
    pbkdf2(password, salt, rounds, 32, 'sha256', (error, result) => {
      if (error || !result || result.length !== 32)
        reject(new Error('PASSWORD_DERIVATION_FAILED'));
      else resolve(Uint8Array.from(result));
    });
  });
}
/** Async native worker, never pbkdf2Sync. No user password/result is retained. */
export async function deriveNativePassword(
  password: Uint8Array,
  salt: Uint8Array,
  rounds: number,
): Promise<Uint8Array | null> {
  const pbkdf2 = nativeBinding();
  if (!pbkdf2) return null;
  await ensureCompatibility(pbkdf2);
  try {
    return await derive(pbkdf2, password, salt, rounds);
  } catch {
    throw new Error('PASSWORD_DERIVATION_FAILED');
  }
}

async function ensureCompatibility(pbkdf2: Pbkdf2): Promise<void> {
  // One known-answer test per process, including a NUL. 0.x QuickCrypto is NOT
  // allowed (GHSA-wrf3-fwx3-8jrv); 1.1.9 is pinned. Reject a broken native build
  // instead of emitting a different legacy password hash or creating bad accounts.
  compatibility ??= derive(
    pbkdf2,
    utf8('Nabd\0عربية 🌍'),
    Uint8Array.from({ length: 16 }, (_, i) => i),
    PASSWORD_ROUNDS,
  ).then((result) => {
    if (
      hex(result) !==
      '4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6'
    )
      throw new Error('PASSWORD_DERIVATION_FAILED');
  });
  await compatibility;
}

export async function prepareNativePassword(): Promise<boolean> {
  const pbkdf2 = nativeBinding();
  if (!pbkdf2) return false;
  await ensureCompatibility(pbkdf2);
  return true;
}
