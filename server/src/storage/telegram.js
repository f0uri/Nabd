/**
 * تخزين الوسائط على تلغرام («سحابة تلغرام»).
 *
 * كيف يشتغل:
 *   1. الخادم يرفع الملف للبوت عبر sendDocument → تلغرام يرجع file_id ثابت.
 *   2. نحفظ file_id في قاعدة البيانات بدل الملف نفسه (توفير كامل لمساحة الخادم).
 *   3. عند العرض نطلب getFile → نحصل على file_path مؤقت، ثم نبثّه للمستخدم عبر /media/:id.
 *
 * ⚠️ حدود يجب معرفتها قبل الإنتاج:
 *   - رفع الملفات للبوت: 50 ميغابايت كحد أقصى (Bot API).
 *   - التنزيل عبر getFile: 20 ميغابايت كحد أقصى.
 *   - الحل للإنتاج: تشغيل Local Bot API Server (يرفع الحد إلى 2000 ميغابايت)،
 *     أو استعمال S3 + CloudFront للفيديو (كما في خطة المرحلة الرابعة).
 *   - file_id يبقى صالحاً ما دام الملف موجوداً في محادثة البوت معك.
 *
 * ملاحظة أمنية: التوكن سرّي. لا تضعه أبداً في الكود أو في تطبيق الموبايل —
 * فقط في متغيّرات البيئة على الخادم.
 */

export class TelegramStorage {
  constructor({ token, chatId, apiBase = 'https://api.telegram.org' }) {
    this.token = token;
    this.chatId = chatId;
    this.apiBase = apiBase;
    this._pathCache = new Map(); // fileId → { path, expiresAt }
  }

  get configured() {
    return Boolean(this.token && this.chatId);
  }

  url(method) {
    return `${this.apiBase}/bot${this.token}/${method}`;
  }

  async #call(method, init) {
    const res = await fetch(this.url(method), init);
    const json = await res.json();
    if (!json.ok) {
      throw new Error(`TELEGRAM_${method.toUpperCase()}_FAILED: ${json.description ?? 'unknown'}`);
    }
    return json.result;
  }

  /**
   * @param {{ buffer: Buffer, filename: string, mime: string, caption?: string }} input
   * @returns {Promise<{ provider: 'telegram', fileId: string, fileUniqueId: string, size: number, messageId: number }>}
   */
  async upload({ buffer, filename, mime, caption }) {
    const form = new FormData();
    form.append('chat_id', String(this.chatId));
    form.append('document', new Blob([buffer], { type: mime || 'application/octet-stream' }), filename);
    if (caption) form.append('caption', caption);
    form.append('disable_notification', 'true');

    const message = await this.#call('sendDocument', { method: 'POST', body: form });
    const doc = message.document ?? message.video ?? message.photo?.at(-1) ?? {};

    return {
      provider: 'telegram',
      fileId: doc.file_id,
      fileUniqueId: doc.file_unique_id,
      size: doc.file_size ?? buffer.byteLength,
      messageId: message.message_id,
    };
  }

  /** يرجّع مسار الملف المؤقت (صالح عادة أكثر من ساعة) */
  async getFilePath(fileId) {
    const cached = this._pathCache.get(fileId);
    if (cached && cached.expiresAt > Date.now()) return cached.path;

    const file = await this.#call(`getFile?file_id=${encodeURIComponent(fileId)}`);
    if (!file?.file_path) throw new Error('TELEGRAM_NO_FILE_PATH');

    this._pathCache.set(fileId, { path: file.file_path, expiresAt: Date.now() + 45 * 60 * 1000 });
    return file.file_path;
  }

  /** يرجّع ReadableStream لملف من سيرفرات تلغرام */
  async download(fileId) {
    const filePath = await this.getFilePath(fileId);
    const res = await fetch(`${this.apiBase}/file/bot${this.token}/${filePath}`);
    if (!res.ok || !res.body) throw new Error(`TELEGRAM_DOWNLOAD_FAILED_${res.status}`);
    return res;
  }
}

export default TelegramStorage;
