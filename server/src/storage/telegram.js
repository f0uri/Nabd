/**
 * Server-side Telegram media storage. The phone only receives /media/:id URLs.
 * Never distribute the bot token or Telegram file URLs to clients.
 *
 * This adapter deliberately limits new uploads to 18 MiB for the temporary setup;
 * verify the current Bot API limits before changing that application policy.
 */
export const MAX_TELEGRAM_UPLOAD_BYTES = 18 * 1024 * 1024;

export class TelegramStorage {
  constructor({ token, chatId, apiBase = 'https://api.telegram.org' }) {
    this.token = token;
    this.chatId = chatId;
    this.apiBase = apiBase.replace(/\/$/, '');
    this._pathCache = new Map();
  }

  get configured() { return Boolean(this.token && this.chatId); }

  url(method) { return `${this.apiBase}/bot${this.token}/${method}`; }

  async #call(method, init = {}) {
    const operation = method.split('?')[0].toUpperCase();
    try {
      const res = await fetch(this.url(method), {
        ...init, redirect: 'error', signal: AbortSignal.timeout(30000),
      });
      if (!res.ok) throw new Error('UPSTREAM_HTTP_ERROR');
      const json = await res.json();
      if (!json.ok || !json.result) throw new Error('UPSTREAM_API_ERROR');
      return json.result;
    } catch {
      // Telegram descriptions, URLs and network errors may contain credentials.
      // Only stable non-sensitive codes cross this boundary (including logs).
      throw new Error(`TELEGRAM_${operation}_FAILED`);
    }
  }

  async checkConnection() {
    const bot = await this.#call('getMe');
    const chat = await this.#call('getChat', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chat_id: this.chatId }),
    });
    if (bot.is_bot !== true || !chat.id) throw new Error('TELEGRAM_INVALID_DESTINATION');
    if (chat.type !== 'private' && (chat.username || chat.active_usernames?.length)) {
      throw new Error('TELEGRAM_CHAT_MUST_BE_PRIVATE');
    }
    return true;
  }

  async upload({ buffer, filename, mime, caption }) {
    if (!buffer?.byteLength) throw new Error('EMPTY_UPLOAD');
    if (buffer.byteLength > MAX_TELEGRAM_UPLOAD_BYTES) throw new Error('UPLOAD_TOO_LARGE');
    const form = new FormData();
    form.append('chat_id', String(this.chatId));
    form.append('document', new Blob([buffer], { type: mime || 'application/octet-stream' }), filename || 'upload');
    if (caption) form.append('caption', caption);
    form.append('disable_notification', 'true');
    const message = await this.#call('sendDocument', { method: 'POST', body: form });
    const doc = message.document ?? message.video ?? message.photo?.at(-1);
    if (!doc?.file_id || !message.message_id) throw new Error('TELEGRAM_INVALID_UPLOAD_RESPONSE');
    return {
      provider: 'telegram', fileId: doc.file_id, fileUniqueId: doc.file_unique_id,
      size: doc.file_size ?? buffer.byteLength, messageId: message.message_id,
    };
  }

  async getFilePath(fileId) {
    const cached = this._pathCache.get(fileId);
    if (cached && cached.expiresAt > Date.now()) return cached.path;
    const file = await this.#call(`getFile?file_id=${encodeURIComponent(fileId)}`);
    const path = file?.file_path;
    if (typeof path !== 'string' || !path || path.startsWith('/') || path.includes('://') ||
        /[\\?#\u0000-\u001f]/.test(path) || path.split('/').includes('..')) {
      throw new Error('TELEGRAM_INVALID_FILE_PATH');
    }
    this._pathCache.set(fileId, { path, expiresAt: Date.now() + 45 * 60 * 1000 });
    return path;
  }

  async download(fileId) {
    const path = await this.getFilePath(fileId);
    try {
      const res = await fetch(`${this.apiBase}/file/bot${this.token}/${path}`, {
        redirect: 'error', signal: AbortSignal.timeout(60000),
      });
      if (!res.ok || !res.body) throw new Error('UPSTREAM_DOWNLOAD_ERROR');
      return res;
    } catch {
      throw new Error('TELEGRAM_DOWNLOAD_FAILED');
    }
  }
}

export default TelegramStorage;
