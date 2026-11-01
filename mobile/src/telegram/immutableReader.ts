import { utf8 } from './password';

/** Telegram documents are immutable. Cache ONE bounded document by file_id only,
 * never a user/session or the mutable pinned-message pointer. Every API operation
 * still fetches the live pin and checks its session. Return independent objects
 * so a failed mutation cannot poison another read or a later login.
 */
export function createImmutableReader(
  read: (id: string) => Promise<unknown>,
  maxBytes: number,
) {
  let cached: { id: string; json: string } | null = null;
  const pending = new Map<string, Promise<string>>();
  return async (id: string): Promise<unknown> => {
    if (cached?.id === id) return JSON.parse(cached.json);
    let request = pending.get(id);
    if (!request) {
      request = Promise.resolve()
        .then(() => read(id))
        .then((value) => {
          const json = JSON.stringify(value);
          if (
            typeof json !== 'string' ||
            json.length > maxBytes ||
            utf8(json).length > maxBytes
          )
            throw Error('TELEGRAM_VAULT_FULL');
          cached = { id, json };
          return json;
        });
      pending.set(id, request);
    }
    try {
      return JSON.parse(await request);
    } finally {
      if (pending.get(id) === request) pending.delete(id);
    }
  };
}
