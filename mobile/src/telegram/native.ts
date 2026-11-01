import * as FS from 'expo-file-system';
import { uuid } from 'expo-modules-core';
import { getAuthToken } from '../api/client';
import type { LocalFile } from '../api/client';
import { createTelegramApi } from './createTelegramApi';
import { createTransport } from './transport';
import { telegramConfig } from './config';
import { telegramNamespace } from './config';

export const MAX_IMAGE_BYTES = 128 * 1024;
export async function inlineImage(file: LocalFile): Promise<string> {
  if (
    !/^image\/(jpeg|png|webp)$/.test(file.type) ||
    !/^(file|content):\/\//.test(file.uri)
  )
    throw new Error('TELEGRAM_IMAGE_ONLY');
  const info = await FS.getInfoAsync(file.uri);
  if (!info.exists || info.isDirectory || info.size > MAX_IMAGE_BYTES)
    throw new Error('TELEGRAM_IMAGE_TOO_LARGE');
  const base64 = await FS.readAsStringAsync(file.uri, {
    encoding: FS.EncodingType.Base64,
  });
  if (
    base64.length > Math.ceil(MAX_IMAGE_BYTES / 3) * 4 ||
    !/^[A-Za-z0-9+/=]+$/.test(base64)
  )
    throw new Error('TELEGRAM_IMAGE_TOO_LARGE');
  return `data:${file.type};base64,${base64}`;
}
export const telegramApi = createTelegramApi({
  namespace: telegramNamespace,
  random: () => uuid.v4(),
  getToken: getAuthToken,
  avatar: inlineImage,
  transport: createTransport({
    ...telegramConfig,
    fetch: (...args) => fetch(...args),
    async uploadJson(url, chatId, json, signal) {
      if (!FS.cacheDirectory) throw new Error('TELEGRAM_CACHE_UNAVAILABLE');
      const uri = `${FS.cacheDirectory}nabd-vault-${uuid.v4()}.json`;
      try {
        await FS.writeAsStringAsync(uri, json, {
          encoding: FS.EncodingType.UTF8,
        });
        const body = new FormData();
        body.append('chat_id', chatId);
        body.append('caption', 'nabd-direct-telegram-v1');
        body.append('disable_notification', 'true');
        body.append('document', {
          uri,
          name: 'nabd-vault.json',
          type: 'application/json',
        } as unknown as Blob);
        // Let native fetch supply the multipart boundary. Keep the temporary file
        // until fetch has consumed it, including when the request fails.
        return await fetch(url, {
          method: 'POST',
          body,
          signal,
          redirect: 'error',
        });
      } finally {
        await FS.deleteAsync(uri, { idempotent: true }).catch(() => undefined);
      }
    },
  }),
});
