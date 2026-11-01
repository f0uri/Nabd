/** Parse session exports as data only. Never persist the full export (passwords, device IDs, etc.). */
export const MAX_SESSION_BYTES = 64 * 1024;
const invalid = () => { throw new Error('INVALID_INSTAGRAM_COOKIE'); };
const instagramDomain = (domain) => typeof domain === 'string' &&
  /^(?:\.)?(?:[a-z0-9-]+\.)*instagram\.com$/i.test(domain);

export function parseSessionInput(raw) {
  if (typeof raw !== 'string' || Buffer.byteLength(raw, 'utf8') > MAX_SESSION_BYTES) invalid();
  const text = raw.replace(/^\uFEFF/, '').trim();
  if (!text) invalid();
  const values = {};
  const names = new Set(['sessionid', 'csrftoken', 'ds_user_id']);
  const put = (name, value) => {
    if (!names.has(name)) return;
    if (typeof value !== 'string' && typeof value !== 'number') invalid();
    value = String(value);
    if (values[name] && values[name] !== value) invalid(); // Ambiguous multi-account export.
    values[name] = value;
  };
  const cookies = (entries) => {
    if (!Array.isArray(entries)) invalid();
    for (const entry of entries) {
      if (!entry || typeof entry !== 'object') invalid();
      if (entry.domain !== undefined && !instagramDomain(entry.domain)) continue;
      if (!names.has(entry.name)) continue;
      const expires = entry.expirationDate ?? entry.expires;
      if (expires !== undefined && (!Number.isFinite(Number(expires)) ||
          (Number(expires) > 0 && Number(expires) <= Date.now() / 1000))) {
        if (entry.name === 'sessionid') throw new Error('INSTAGRAM_SESSION_EXPIRED');
        continue;
      }
      put(entry.name, entry.value);
    }
  };

  if (text.startsWith('{') || text.startsWith('[')) {
    let json;
    try { json = JSON.parse(text); } catch { invalid(); }
    if (Array.isArray(json)) cookies(json);
    else if (json && typeof json === 'object') {
      if (Array.isArray(json.cookies)) cookies(json.cookies);
      else {
        const source = json.authorization_data ?? json.cookies ?? json;
        if (!source || typeof source !== 'object' || Array.isArray(source)) invalid();
        put('sessionid', source.sessionid ?? source.sessionId ?? '');
        put('csrftoken', source.csrftoken ?? source.csrfToken ?? '');
        put('ds_user_id', source.ds_user_id ?? source.dsUserId ?? '');
      }
    } else invalid();
  } else if (text.includes('\t')) {
    // Netscape cookies.txt, including HttpOnly entries.
    for (const rawLine of text.split(/\r?\n/)) {
      const line = rawLine.replace(/^#HttpOnly_/, '');
      if (!line || line.startsWith('#')) continue;
      const fields = line.split('\t');
      if (fields.length !== 7) invalid();
      const [domain, , , , expires, name, value] = fields;
      if (!instagramDomain(domain) || !names.has(name)) continue;
      cookies([{ domain, expires, name, value }]);
    }
  } else if (/(?:^|;)\s*sessionid\s*=/i.test(text)) {
    if (/[\r\n]/.test(text)) invalid();
    for (const part of text.split(';')) {
      const index = part.indexOf('=');
      if (index < 0) continue;
      put(part.slice(0, index).trim().toLowerCase(), part.slice(index + 1).trim());
    }
  } else {
    put('sessionid', text);
  }

  const { sessionid = '', csrftoken = '', ds_user_id = '' } = values;
  // Preserve URL-encoding; reject header injection, separators and malformed escapes.
  const safe = (value) => {
    try {
      return /^[A-Za-z0-9%:_=+./-]*$/.test(value) && !/%(?![a-f0-9]{2})/i.test(value) &&
        !/[\x00-\x20\x7f;]/.test(decodeURIComponent(value));
    } catch { return false; }
  };
  if (sessionid.length < 20 || sessionid.length > 4096 || !safe(sessionid) ||
      csrftoken.length > 1024 || !safe(csrftoken) || (ds_user_id && !/^\d{1,30}$/.test(ds_user_id))) invalid();
  return { sessionId: sessionid, csrfToken: csrftoken, dsUserId: ds_user_id };
}
