// No configuration values are printed: the bot credential is extractable from a
// direct-mode bundle, but must not also be exposed accidentally in CI logs.
const mode = process.env.EXPO_PUBLIC_DATA_SOURCE || 'auto';
if (!['auto', 'graphql', 'mock', 'telegram'].includes(mode)) {
  throw Error(
    'Invalid EXPO_PUBLIC_DATA_SOURCE. Use auto, graphql, mock or telegram.',
  );
}
if (mode === 'telegram') {
  if (
    !/^\d{5,}:[A-Za-z0-9_-]{20,}$/.test(
      process.env.EXPO_PUBLIC_TELEGRAM_BOT_TOKEN || '',
    ) ||
    !/^-?[1-9]\d{0,19}$/.test(process.env.EXPO_PUBLIC_TELEGRAM_CHAT_ID || '')
  ) {
    throw Error(
      'Telegram-direct publication requires a dedicated bot token and private chat ID. Configure GitHub settings; never paste credentials into chat.',
    );
  }
  console.log(
    'WARNING: Telegram-direct embeds a PUBLIC, extractable bot credential. This is experimental shared storage, not secure multi-user hosting.',
  );
} else if (process.env.EXPO_PUBLIC_TELEGRAM_BOT_TOKEN) {
  throw Error(
    'Refusing to embed a direct-mode bot credential in another data-source mode.',
  );
}
