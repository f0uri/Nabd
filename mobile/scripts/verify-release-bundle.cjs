const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');

function verifyReleaseBundle(
  directory,
  telegram = process.env.EXPO_PUBLIC_DATA_SOURCE === 'telegram',
) {
  const root = path.resolve(directory);
  const metadata = JSON.parse(
    fs.readFileSync(path.join(root, 'metadata.json'), 'utf8'),
  );
  const relative = metadata.fileMetadata?.android?.bundle;
  if (typeof relative !== 'string')
    throw Error('Android bundle metadata missing.');
  const file = path.resolve(root, relative);
  const location = path.relative(root, file);
  if (location.startsWith('..') || path.isAbsolute(location))
    throw Error('Invalid bundle path.');
  const bytes = fs.readFileSync(file);
  if (!bytes.includes(Buffer.from('nabd-glass-one-tap-v1')))
    throw Error('Fresh one-tap UI is absent from exported bundle.');
  if (telegram && !bytes.includes(Buffer.from('nabd-auto-connect-v2')))
    throw Error('Automatic Telegram startup is absent.');
  for (const text of [
    'فهمت المخاطر وباغي نكمل التجربة',
    'تلغرام مباشر — وضع مؤقت',
    'يوجد تحديث متوافق مع نسختك. يمكنك تنزيله هنا دون إعادة تثبيت التطبيق.',
  ]) {
    if (
      bytes.includes(Buffer.from(text)) ||
      bytes.includes(Buffer.from(text, 'utf16le'))
    )
      throw Error('Obsolete warning/update window remains in export.');
  }
  return {
    uiRevision: 'glass-one-tap-v1',
    bundleSha256: createHash('sha256').update(bytes).digest('hex'),
  };
}
if (require.main === module) {
  try {
    console.log(
      JSON.stringify(
        verifyReleaseBundle(
          process.argv[2] || path.resolve(__dirname, '../dist'),
        ),
      ),
    );
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
module.exports = { verifyReleaseBundle };
