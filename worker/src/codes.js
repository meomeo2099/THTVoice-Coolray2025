// License code format: 20 digits = 19 random digits + 1 Damm check digit.
// Damm catches every single wrong digit and every swap of two neighbouring digits,
// so the app can reject a typo before it ever reaches the server.

const DAMM = [
  [0, 3, 1, 7, 5, 9, 8, 6, 4, 2],
  [7, 0, 9, 2, 1, 5, 4, 8, 6, 3],
  [4, 2, 0, 6, 8, 7, 1, 3, 5, 9],
  [1, 7, 5, 0, 9, 8, 3, 4, 2, 6],
  [6, 1, 2, 3, 0, 4, 5, 9, 7, 8],
  [3, 6, 7, 4, 2, 0, 9, 5, 8, 1],
  [5, 8, 6, 9, 7, 2, 0, 1, 3, 4],
  [8, 9, 4, 5, 3, 6, 2, 0, 1, 7],
  [9, 4, 3, 8, 6, 1, 7, 2, 0, 5],
  [2, 5, 8, 1, 4, 3, 6, 7, 9, 0],
];

export function dammDigit(digits) {
  let interim = 0;
  for (const ch of digits) interim = DAMM[interim][ch.charCodeAt(0) - 48];
  return interim;
}

export function dammValid(digits) {
  return /^[0-9]+$/.test(digits) && dammDigit(digits) === 0;
}

export function generateNumericCode(randomBytes = (n) => crypto.getRandomValues(new Uint8Array(n))) {
  // Rejection sampling keeps every digit uniform.
  let body = '';
  while (body.length < 19) {
    for (const b of randomBytes(32)) {
      if (b < 250 && body.length < 19) body += String(b % 10);
    }
  }
  if (body[0] === '0') body = '1' + body.slice(1); // never looks like a shorter number
  return body + String(dammDigit(body));
}

/**
 * Returns {format, code} for a user-typed code, or null.
 * - 20 digits with a valid check digit → numeric
 * - 16 hex characters → hex (codes issued by the old server, imported by the admin)
 */
export function normalizeLicenseCode(raw) {
  if (typeof raw !== 'string') return null;
  const compact = raw.toUpperCase().replace(/[\s\-_.]/g, '');
  if (/^[0-9]{20}$/.test(compact)) return dammValid(compact) ? { format: 'numeric', code: compact } : { format: 'numeric', code: compact, typo: true };
  if (/^[0-9A-F]{16}$/.test(compact)) return { format: 'hex', code: compact };
  return null;
}

export function formatCode(code) {
  return code.replace(/(.{4})(?=.)/g, '$1-');
}

export function normalizeRequestCode(raw) {
  if (typeof raw !== 'string') return null;
  const compact = raw.toUpperCase().replace(/[\s\-]/g, '');
  return /^[0-9A-F]{16}$/.test(compact) ? compact : null;
}

export function codeHint(code) {
  if (!code) return null;
  return code.length <= 6 ? '***' : `${code.slice(0, 2)}…${code.slice(-2)}`;
}

export function generateUnlockCode() {
  const bytes = crypto.getRandomValues(new Uint32Array(1));
  return String(bytes[0] % 100000000).padStart(8, '0');
}
