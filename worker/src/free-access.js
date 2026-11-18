// Updates and voice packs for cars without a key while the server is in free mode.
// A valid key always passes (requireLicense); a keyless device passes only in free mode, when it is
// not blocked and stays under a small daily quota, so a script cannot drain GITHUB_TOKEN or R2.
// The admin can switch this off ("Cập nhật cho máy chưa có key").
import { normalizeRequestCode } from './codes.js';
import { clientIp, json, requireLicense } from './license.js';
import { DAY } from './lockout.js';
import { getFreeUpdates, getLicenseMode } from './settings.js';

// Per UTC day for keyless devices: [per device, per IP].
export const FREE_QUOTA = {
  check: [30, 100], // /api/update, /api/model (each reads GitHub / D1)
  download: [5, 10], // /api/update/apk, /api/model/file
};

async function ensureTable(env) {
  await env.DB.prepare('CREATE TABLE IF NOT EXISTS free_quota (key TEXT PRIMARY KEY, day INTEGER NOT NULL, n INTEGER NOT NULL)').run();
}

/** Adds one use to [key] for today; returns the count after adding. */
async function bump(env, key, day) {
  await env.DB.prepare(
    'INSERT INTO free_quota (key, day, n) VALUES (?, ?, 1) ON CONFLICT(key) DO UPDATE SET n = CASE WHEN day = excluded.day THEN n + 1 ELSE 1 END, day = excluded.day'
  ).bind(key, day).run();
  return (await env.DB.prepare('SELECT n FROM free_quota WHERE key = ?').bind(key).first())?.n ?? 0;
}

const unlicensed = () => json(401, { ok: false, code: 'unlicensed', message: 'License không hợp lệ.' });

/**
 * null = allowed (valid key, or keyless device in free mode within quota); otherwise the error Response.
 * kind: 'check' or 'download'.
 */
export async function updateAccessDenied(request, env, now, kind) {
  if (await requireLicense(request, env, now)) return null;
  const req = normalizeRequestCode(request.headers.get('x-tht-request') || '');
  if (!req || (await getLicenseMode(env)) !== 'free' || !(await getFreeUpdates(env))) return unlicensed();
  const device = await env.DB.prepare('SELECT status FROM devices WHERE request_code = ?').bind(req).first();
  // Only devices the server has seen through /api/policy, and never a blocked one.
  if (!device || device.status === 'blocked') return unlicensed();
  await ensureTable(env);
  const day = Math.floor(now / DAY);
  const [perDevice, perIp] = FREE_QUOTA[kind];
  const byDevice = await bump(env, `${kind}:dev:${req}`, day);
  const byIp = await bump(env, `${kind}:ip:${clientIp(request)}`, day);
  if (byDevice > perDevice || byIp > perIp) {
    return json(429, { ok: false, code: 'rate_limited', message: 'Đã tải quá nhiều lần hôm nay, thử lại ngày mai.' });
  }
  return null;
}
