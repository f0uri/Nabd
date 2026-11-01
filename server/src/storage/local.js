/**
 * تخزين محلي على القرص — يُستعمل عندما لا يوجد توكن تلغرام.
 * يسمح للخادم بالعمل مباشرة بلا أي إعداد (مفيد للتطوير).
 */
import { createReadStream } from 'node:fs';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import { randomUUID } from 'node:crypto';

export class LocalStorage {
  constructor(dir = 'uploads') {
    this.dir = dir;
    this.configured = true;
  }

  async init() {
    await mkdir(this.dir, { recursive: true });
  }

  async upload({ buffer, filename }) {
    // نضمن وجود المجلد في كل رفع (المجلد قد يُحذف أو يُنشأ الخادم في بيئة جديدة)
    await mkdir(this.dir, { recursive: true });
    const id = `${randomUUID()}${extname(filename || '')}`;
    await writeFile(join(this.dir, id), buffer);
    return { provider: 'local', fileId: id, size: buffer.byteLength };
  }

  async read(fileId) {
    return readFile(join(this.dir, fileId));
  }

  async stream(fileId) {
    const path = join(this.dir, fileId);
    const info = await stat(path);
    return { body: createReadStream(path), size: info.size };
  }

  /** بث جزء محدد (للـ Range — ضروري لتحريك الفيديو للخلف/لقدام) */
  async streamRange(fileId, start, end) {
    const path = join(this.dir, fileId);
    const info = await stat(path);
    const last = Math.min(end, info.size - 1);
    return { body: createReadStream(path, { start, end: last }), size: info.size, start, end: last };
  }

  /** حجم الملف بلا ما نحلّوه */
  async size(fileId) {
    const info = await stat(join(this.dir, fileId));
    return info.size;
  }
}

export default LocalStorage;
