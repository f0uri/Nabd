import { TelegramStorage } from './telegram.js';
import { LocalStorage } from './local.js';

const token = process.env.TELEGRAM_BOT_TOKEN;
const chatId = process.env.TELEGRAM_CHAT_ID;
const provider = (process.env.MEDIA_PROVIDER ?? (token && chatId ? 'telegram' : 'local')).toLowerCase();

export const storage =
  provider === 'telegram' && token && chatId ? new TelegramStorage({ token, chatId }) : new LocalStorage(process.env.UPLOAD_DIR ?? 'uploads');

export const storageInfo = {
  provider: provider === 'telegram' && token && chatId ? 'telegram' : 'local',
  configured: storage.configured !== false,
  chatId: chatId ?? null,
  note:
    provider === 'telegram' && token && chatId
      ? 'الوسائط تُخزَّن على تلغرام (حد التنزيل 20MB عبر Bot API)'
      : 'تخزين محلي — أضف TELEGRAM_BOT_TOKEN و TELEGRAM_CHAT_ID لتفعيل سحابة تلغرام',
};

/**
 * تخزين «مرآة» للمحتوى اللي كنجيبوه من برا (ريلز إنستغرام):
 * دايماً على القرص المحلي — باش يبقى عندنا ويدعم Range بسرعة.
 */
export const mirrorStorage =
  storage instanceof LocalStorage ? storage : new LocalStorage(process.env.UPLOAD_DIR ?? 'uploads');

export { TelegramStorage, LocalStorage };
