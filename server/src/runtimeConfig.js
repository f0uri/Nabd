/** Fail closed when production or Telegram storage was explicitly requested. */
export function resolveAuthSecret(env = process.env) {
  const secret = env.AUTH_SECRET;
  if (env.NODE_ENV === 'production' && (!secret || secret.trim().length < 32 || secret === 'nabd-dev-secret-change-me')) {
    throw new Error('AUTH_SECRET_REQUIRED_IN_PRODUCTION');
  }
  return secret || 'nabd-dev-secret-change-me';
}

export function resolveStorageConfig(env = process.env) {
  const token = (env.TELEGRAM_BOT_TOKEN || '').trim();
  const chatId = (env.TELEGRAM_CHAT_ID || '').trim();
  const provider = (env.MEDIA_PROVIDER || (token || chatId ? 'telegram' : 'local')).trim().toLowerCase();
  if (!['telegram', 'local'].includes(provider)) throw new Error('INVALID_MEDIA_PROVIDER');
  if (provider === 'telegram' && (!token || !chatId)) throw new Error('TELEGRAM_STORAGE_REQUIRES_CREDENTIALS');
  return { provider, token, chatId };
}
