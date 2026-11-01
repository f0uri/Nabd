import { digest } from './password';
export const isTelegramDirect =
  process.env.EXPO_PUBLIC_DATA_SOURCE?.trim().toLowerCase() === 'telegram';
// These values are deliberately public/extractable in direct mode, even if supplied
// through a GitHub Secret. Do not reuse a bot that has access to other applications.
export const telegramConfig = {
  token: process.env.EXPO_PUBLIC_TELEGRAM_BOT_TOKEN ?? '',
  chatId: process.env.EXPO_PUBLIC_TELEGRAM_CHAT_ID ?? '',
};
export const telegramConfigured =
  /^\d{5,}:[A-Za-z0-9_-]{20,}$/.test(telegramConfig.token) &&
  /^-?[1-9]\d{0,19}$/.test(telegramConfig.chatId);
// Stable across bot-token rotation, but isolated from other chats and GraphQL sessions.
export const telegramNamespace = digest(
  `${telegramConfig.token.split(':')[0]}:${telegramConfig.chatId}`,
).slice(0, 24);
