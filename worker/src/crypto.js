// Signing (ECDSA P-256 / SHA-256), VIN protection and small helpers.
// ECDSA P-256 is used instead of Ed25519 because Android 9 (the head unit's minSdk 28)
// verifies it natively; Ed25519 needs Android 13.

const enc = new TextEncoder();

export function b64urlEncode(bytes) {
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function b64Decode(text) {
  const clean = text.replace(/-/g, '+').replace(/_/g, '/').replace(/\s+/g, '');
  const padded = clean + '='.repeat((4 - (clean.length % 4)) % 4);
  const bin = atob(padded);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function hex(bytes) {
  return [...new Uint8Array(bytes)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export async function sha256Hex(text) {
  return hex(await crypto.subtle.digest('SHA-256', enc.encode(text)));
}

/** Constant-time string comparison via HMAC digests. */
export async function safeEqual(a, b) {
  const key = await crypto.subtle.importKey('raw', crypto.getRandomValues(new Uint8Array(32)), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const [x, y] = await Promise.all([
    crypto.subtle.sign('HMAC', key, enc.encode(String(a))),
    crypto.subtle.sign('HMAC', key, enc.encode(String(b))),
  ]);
  const xa = new Uint8Array(x);
  const ya = new Uint8Array(y);
  let diff = 0;
  for (let i = 0; i < xa.length; i++) diff |= xa[i] ^ ya[i];
  return diff === 0;
}

const signingKeys = new Map();

async function signingKey(env) {
  const raw = env.LICENSE_SIGNING_KEY;
  if (!raw) throw new Error('LICENSE_SIGNING_KEY secret is missing');
  if (!signingKeys.has(raw)) {
    signingKeys.set(raw, await crypto.subtle.importKey('pkcs8', b64Decode(raw), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign']));
  }
  return signingKeys.get(raw);
}

/**
 * License token = base64url(payload JSON) "." base64url(raw 64-byte r||s signature).
 * The app verifies it offline with the public key(s) built into the APK.
 */
export async function signLicenseToken(env, payload) {
  const body = b64urlEncode(enc.encode(JSON.stringify({ v: 1, kid: env.LICENSE_KEY_ID || 'k1', ...payload })));
  const sig = await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, await signingKey(env), enc.encode(body));
  return `${body}.${b64urlEncode(new Uint8Array(sig))}`;
}

export async function verifyLicenseToken(publicKeySpkiB64, token) {
  const [body, sig] = String(token).split('.');
  if (!body || !sig) return null;
  const key = await crypto.subtle.importKey('spki', b64Decode(publicKeySpkiB64), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']);
  const ok = await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, b64Decode(sig), enc.encode(body));
  return ok ? JSON.parse(new TextDecoder().decode(b64Decode(body))) : null;
}

// ---- VIN ----------------------------------------------------------------------------------

export function normalizeVin(raw) {
  if (typeof raw !== 'string') return null;
  const vin = raw.toUpperCase().replace(/[\s\-]/g, '');
  return /^[A-HJ-NPR-Z0-9]{17}$/.test(vin) ? vin : null;
}

const vinKeys = new Map();

async function vinKeyMaterial(env) {
  const raw = env.VIN_KEY;
  if (!raw) throw new Error('VIN_KEY secret is missing');
  if (!vinKeys.has(raw)) {
    const master = await crypto.subtle.importKey('raw', b64Decode(raw), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
    const derive = async (label) => new Uint8Array(await crypto.subtle.sign('HMAC', master, enc.encode(label)));
    const macKey = await crypto.subtle.importKey('raw', await derive('tht-vin-mac-v1'), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
    const aesKey = await crypto.subtle.importKey('raw', await derive('tht-vin-enc-v1'), { name: 'AES-GCM' }, false, ['encrypt', 'decrypt']);
    vinKeys.set(raw, { macKey, aesKey });
  }
  return vinKeys.get(raw);
}

/** Stable lookup value for a VIN (keyed, so the table alone does not reveal VINs). */
export async function vinHash(env, vin) {
  const { macKey } = await vinKeyMaterial(env);
  return hex(await crypto.subtle.sign('HMAC', macKey, enc.encode(`vin|${vin}`)));
}

export async function encryptVin(env, vin) {
  const { aesKey } = await vinKeyMaterial(env);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, aesKey, enc.encode(vin)));
  return `v1.${b64urlEncode(iv)}.${b64urlEncode(ct)}`;
}

export async function decryptVin(env, value) {
  if (!value) return null;
  try {
    const [v, iv, ct] = value.split('.');
    if (v !== 'v1') return null;
    const { aesKey } = await vinKeyMaterial(env);
    return new TextDecoder().decode(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: b64Decode(iv) }, aesKey, b64Decode(ct)));
  } catch {
    return null;
  }
}

/** {vin_hash, vin_enc, vin_last4} for a valid VIN, or null. */
export async function protectVin(env, raw) {
  const vin = normalizeVin(raw);
  if (!vin) return null;
  return { vin_hash: await vinHash(env, vin), vin_enc: await encryptVin(env, vin), vin_last4: vin.slice(-4) };
}
