/**
 * تحميل ملف .env بلا أي تبعية خارجية (Node 20+).
 * يُستورد أولاً في index.js حتى تكون كل المتغيّرات جاهزة قبل تحميل باقي الوحدات.
 */
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const envPath = resolve(process.cwd(), '.env');

if (existsSync(envPath)) {
  const content = readFileSync(envPath, 'utf8');
  for (const rawLine of content.split('\n')) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const index = line.indexOf('=');
    if (index === -1) continue;
    const key = line.slice(0, index).trim();
    let value = line.slice(index + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env)) process.env[key] = value;
  }
  console.log(`⚙️  تم تحميل الإعدادات من .env`);
} else {
  console.log(`⚙️  لا يوجد ملف .env — سنستعمل القيم الافتراضية ومتغيّرات البيئة`);
}
