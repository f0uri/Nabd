import { hex, utf8, PASSWORD_ROUNDS } from './password';

// Pinned QuickCrypto 1.1.9 native spec: src/specs/pbkdf2.nitro.ts.
// Bind ONLY its async hybrid, not the root Node/browser polyfill barrel (which
// eagerly initializes unrelated hashes/base64/global shims on React Native).
type Pbkdf2 = {
  pbkdf2(
    password: ArrayBuffer,
    salt: ArrayBuffer,
    rounds: number,
    length: number,
    digest: string,
  ): Promise<ArrayBuffer>;
};
let binding: Pbkdf2 | null | undefined;
let compatibility: Promise<void> | undefined;
let initializationError = '';
export const nativePasswordInitializationError = () => initializationError;
function nativeBinding(): Pbkdf2 | null {
  if (binding !== undefined) return binding;
  try {
    const { NitroModules } = require('react-native-nitro-modules');
    binding = NitroModules.createHybridObject('Pbkdf2');
  } catch (error) {
    initializationError = String(
      (error as Error)?.message ?? 'NATIVE_BINDING_UNAVAILABLE',
    ).slice(0, 800);
    // Expo Go / an older native shell must still use the compatible JS path.
    binding = null;
  }
  return binding ?? null;
}
async function derive(
  native: Pbkdf2,
  password: Uint8Array,
  salt: Uint8Array,
  rounds: number,
): Promise<Uint8Array> {
  if (rounds !== PASSWORD_ROUNDS || salt.length !== 16)
    throw new Error('PASSWORD_DERIVATION_FAILED');
  try {
    // Fresh, exact-sized ArrayBuffers; no Buffer/string polyfill or offset loss.
    const result = await native.pbkdf2(
      Uint8Array.from(password).buffer,
      Uint8Array.from(salt).buffer,
      rounds,
      32,
      'sha256',
    );
    if (result.byteLength !== 32) throw new Error('PASSWORD_DERIVATION_FAILED');
    return Uint8Array.from(new Uint8Array(result));
  } catch {
    throw new Error('PASSWORD_DERIVATION_FAILED');
  }
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
