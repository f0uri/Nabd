import { utf8 } from './password';
export const MAX_VAULT_BYTES = 2 * 1024 * 1024;
export type Chat = {
  id: number;
  type: string;
  username?: string;
  active_usernames?: string[];
  pinned_message?: {
    message_id: number;
    caption?: string;
    document?: { file_id: string; file_name?: string; file_size?: number };
  };
};
export interface TelegramTransport {
  chat(): Promise<Chat>;
  read(fileId: string): Promise<unknown>;
  upload(json: string): Promise<{ messageId: number }>;
  pin(messageId: number): Promise<void>;
}

export function createTransport(options: {
  token: string;
  chatId: string;
  fetch: typeof fetch;
  uploadJson: (
    url: string,
    chatId: string,
    json: string,
    signal: AbortSignal,
  ) => Promise<Response>;
}): TelegramTransport {
  const { token, chatId, fetch: request } = options;
  async function timed<T>(
    ms: number,
    work: (signal: AbortSignal) => Promise<T>,
  ): Promise<T> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), ms);
    try {
      return await work(controller.signal);
    } finally {
      clearTimeout(timer);
    }
  }
  const base = `https://api.telegram.org/bot${token}`;
  async function call(
    method: string,
    payload: Record<string, unknown> = {},
  ): Promise<any> {
    try {
      return await timed(20000, async (signal) => {
        const response = await request(`${base}/${method}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
          signal,
          redirect: 'error',
        });
        if (!response.ok) throw 0;
        const json = await response.json();
        if (!json.ok || json.result === undefined) throw 0;
        return json.result;
      });
    } catch {
      throw new Error('TELEGRAM_REQUEST_FAILED');
    }
  }
  let botVerified = false;
  return {
    async chat() {
      if (!botVerified) {
        const bot = await call('getMe');
        if (!bot?.is_bot || String(bot.id) !== token.split(':')[0])
          throw new Error('TELEGRAM_INVALID_BOT');
        botVerified = true;
      }
      const chat = (await call('getChat', { chat_id: chatId })) as Chat;
      if (
        !chat ||
        String(chat.id) !== chatId ||
        !['private', 'channel', 'group', 'supergroup'].includes(chat.type)
      )
        throw new Error('TELEGRAM_INVALID_CHAT');
      if (
        chat.type !== 'private' &&
        (chat.username || chat.active_usernames?.length)
      )
        throw new Error('TELEGRAM_PRIVATE_CHAT_REQUIRED');
      return chat;
    },
    async read(fileId) {
      const meta = await call('getFile', { file_id: fileId });
      if (
        !meta ||
        typeof meta.file_path !== 'string' ||
        !/^[A-Za-z0-9_./-]+$/.test(meta.file_path) ||
        !meta.file_path ||
        /[\\?#\u0000-\u001f]/.test(meta.file_path) ||
        meta.file_path.startsWith('/') ||
        meta.file_path.includes('://') ||
        meta.file_path
          .split('/')
          .some((part: string) => part === '..' || part === '.') ||
        !Number.isSafeInteger(meta.file_size) ||
        meta.file_size < 0 ||
        meta.file_size > MAX_VAULT_BYTES
      )
        throw new Error('TELEGRAM_INVALID_VAULT');
      try {
        return await timed(20000, async (signal) => {
          const response = await request(
            `https://api.telegram.org/file/bot${token}/${meta.file_path}`,
            { signal, redirect: 'error' },
          );
          if (!response.ok) throw 0;
          // Native fetch buffers the body; metadata is checked before download.
          // Also reject oversized decoded content (not a streaming memory limit).
          const text = await response.text();
          if (
            text.length > MAX_VAULT_BYTES ||
            utf8(text).length > MAX_VAULT_BYTES
          )
            throw 0;
          return JSON.parse(text);
        });
      } catch {
        throw new Error('TELEGRAM_READ_FAILED');
      }
    },
    async upload(json) {
      if (utf8(json).length > MAX_VAULT_BYTES)
        throw new Error('TELEGRAM_VAULT_FULL');
      try {
        return await timed(30000, async (signal) => {
          const response = await options.uploadJson(
            `${base}/sendDocument`,
            chatId,
            json,
            signal,
          );
          if (!response.ok) throw 0;
          const result = await response.json();
          if (
            !result.ok ||
            !Number.isSafeInteger(result.result?.message_id) ||
            result.result.message_id <= 0 ||
            result.result?.document?.file_name !== 'nabd-vault.json' ||
            !result.result?.document?.file_id
          )
            throw 0;
          return { messageId: result.result.message_id };
        });
      } catch {
        throw new Error('TELEGRAM_WRITE_FAILED');
      }
    },
    async pin(messageId) {
      await call('pinChatMessage', {
        chat_id: chatId,
        message_id: messageId,
        disable_notification: true,
      });
    },
  };
}
