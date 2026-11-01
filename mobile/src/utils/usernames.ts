// A suggestion only; the data provider reserves the final unique handle.
const arabic: Record<string, string> = {
  ا: 'a',
  أ: 'a',
  إ: 'i',
  آ: 'a',
  ب: 'b',
  ت: 't',
  ث: 'th',
  ج: 'j',
  ح: 'h',
  خ: 'kh',
  د: 'd',
  ذ: 'dh',
  ر: 'r',
  ز: 'z',
  س: 's',
  ش: 'sh',
  ص: 's',
  ض: 'd',
  ط: 't',
  ظ: 'z',
  ع: 'a',
  غ: 'gh',
  ف: 'f',
  ق: 'q',
  ك: 'k',
  ل: 'l',
  م: 'm',
  ن: 'n',
  ه: 'h',
  و: 'w',
  ي: 'y',
  ى: 'a',
  ة: 'a',
  ؤ: 'w',
  ئ: 'y',
  ء: '',
  '٠': '0',
  '١': '1',
  '٢': '2',
  '٣': '3',
  '٤': '4',
  '٥': '5',
  '٦': '6',
  '٧': '7',
  '٨': '8',
  '٩': '9',
};
export function usernameBase(name: string): string {
  const latin = name
    .normalize('NFKD')
    .toLowerCase()
    .replace(/[\u0300-\u036f\u064b-\u065f\u0670\u0640]/g, '');
  let base = Array.from(latin, (char) => arabic[char] ?? char)
    .join('')
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 28)
    .replace(/_+$/g, '');
  if (!base) base = 'member';
  if (!/^[a-z]/.test(base)) base = `u_${base}`;
  return base.padEnd(3, '_');
}
export function availableUsername(
  name: string,
  taken: Iterable<string>,
): string {
  const used = new Set(Array.from(taken, (handle) => handle.toLowerCase()));
  const base = usernameBase(name);
  let candidate = base;
  for (let suffix = 2; used.has(candidate); suffix++)
    candidate = `${base}_${suffix}`;
  return candidate;
}
