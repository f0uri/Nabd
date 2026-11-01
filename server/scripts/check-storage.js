// Run on the hosting server; no token, destination ID or raw Telegram URL is printed.
import '../src/load-env.js';
import { resolveAuthSecret } from '../src/runtimeConfig.js';
import { storage, storageInfo } from '../src/storage/index.js';
resolveAuthSecret();
if (storageInfo.provider === 'telegram') {
  await storage.checkConnection();
  console.log('Telegram bot authentication and private destination verified. Test an actual upload before inviting users.');
} else {
  console.log('Local storage is configured; no Telegram request was made.');
}
