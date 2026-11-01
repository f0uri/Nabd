/** Account handles are independent from display names and canonicalized before storage. */
export function normalizeUsername(value) {
  if (typeof value !== 'string') throw new Error('INVALID_USERNAME');
  const username = value.trim().replace(/^@/, '').toLowerCase();
  if (!/^[a-z][a-z0-9_]{2,39}$/.test(username)) throw new Error('INVALID_USERNAME');
  return username;
}

export function rethrowUsernameConflict(error) {
  if (error.code === '23505' && error.constraint === 'users_email_key') throw new Error('EMAIL_TAKEN');
  if (error.code === '23505' && error.constraint === 'users_username_lower_key') {
    throw new Error('USERNAME_TAKEN');
  }
  throw error;
}

// Keep transliteration aligned with mobile/src/utils/usernames.ts.
const arabic = {'ا':'a','أ':'a','إ':'i','آ':'a','ب':'b','ت':'t','ث':'th','ج':'j','ح':'h','خ':'kh','د':'d','ذ':'dh','ر':'r','ز':'z','س':'s','ش':'sh','ص':'s','ض':'d','ط':'t','ظ':'z','ع':'a','غ':'gh','ف':'f','ق':'q','ك':'k','ل':'l','م':'m','ن':'n','ه':'h','و':'w','ي':'y','ى':'a','ة':'a','ؤ':'w','ئ':'y','ء':'','٠':'0','١':'1','٢':'2','٣':'3','٤':'4','٥':'5','٦':'6','٧':'7','٨':'8','٩':'9'};
export function usernameBase(name) {
  const latin = name.normalize('NFKD').toLowerCase().replace(/[\u0300-\u036f\u064b-\u065f\u0670\u0640]/g, '');
  let base = Array.from(latin, (char) => arabic[char] ?? char).join('')
    .replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 28).replace(/_+$/g, '');
  if (!base) base = 'member';
  if (!/^[a-z]/.test(base)) base = `u_${base}`;
  return base.padEnd(3, '_');
}
