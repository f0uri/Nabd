export declare function pbkdf2Async(hash: unknown, password: Uint8Array, salt: Uint8Array, options: { c: number; dkLen: number; asyncTick?: number }): Promise<Uint8Array>;
