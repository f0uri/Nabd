import { TelegramStorage } from './telegram.js';
import { LocalStorage } from './local.js';

import { resolveStorageConfig } from '../runtimeConfig.js';

const { token, chatId, provider } = resolveStorageConfig();

export const storage =
  provider === 'telegram' ? new TelegramStorage({ token, chatId }) : new LocalStorage(process.env.UPLOAD_DIR ?? 'uploads');

export const storageInfo = {
  provider: provider === 'telegram' ? 'telegram' : 'local',
  configured: storage.configured !== false,
  chatId: null, // Destination identifiers are server configuration, not public health data.
  note:
    provider === 'telegram'
      ? 'الوسائط تُخزَّن على تلغرام عبر الخادم (حد التطبيق الحالي 18 MiB)'
      : 'تخزين محلي — أضف TELEGRAM_BOT_TOKEN و TELEGRAM_CHAT_ID لتفعيل سحابة تلغرام',
};

/**
 * تخزين «مرآة» للمحتوى اللي كنجيبوه من برا (ريلز إنستغرام):
 * دايماً على القرص المحلي — باش يبقى عندنا ويدعم Range بسرعة.
 */
export const mirrorStorage =
  storage instanceof LocalStorage ? storage : new LocalStorage(process.env.UPLOAD_DIR ?? 'uploads');

export { TelegramStorage, LocalStorage };
