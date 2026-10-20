// Release info for the admin version table and the customer notice shown in the app.
import { getJsonSetting, setJsonSetting } from './settings.js';
import { normalizeRequestCode } from './codes.js';

const RELEASE_KEY = 'latest_release';
const NOTICE_KEY = 'customer_notice';
const RELEASE_TTL = 10 * 60 * 1000;
export const MAX_NOTICE = 500;

/** "1.0.10" > "1.0.9"; non-numeric parts ("-test") are ignored. */
export function compareVersions(a, b) {
  const pa = String(a || '').split(/[^0-9]+/).filter(Boolean).map(Number);
  const pb = String(b || '').split(/[^0-9]+/).filter(Boolean).map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d) return d < 0 ? -1 : 1;
  }
  return 0;
}

/**
 * Latest GitHub Release (update.json) as {versionName, versionCode, changelog, forceUpdate, publishedAt, tag}.
 * The license server reads GitHub (GITHUB_REPO + GITHUB_TOKEN) and keeps a copy in D1, so the separate
 * admin worker, which has no GitHub token, shows the same thing.
 */
export async function latestReleaseInfo(env, now, fetchRelease) {
  const cached = await getJsonSetting(env, RELEASE_KEY);
  if (cached && (!fetchRelease || now - (cached.checkedAt || 0) < RELEASE_TTL)) return cached;
  if (!fetchRelease) return cached || null;
  try {
    const info = await fetchRelease(env);
    if (!info) return cached || null;
    const value = { ...info, checkedAt: now };
    await setJsonSetting(env, now, RELEASE_KEY, value);
    return value;
  } catch {
    return cached || null;
  }
}

export async function getNotice(env) {
  const n = await getJsonSetting(env, NOTICE_KEY);
  return n && typeof n === 'object' ? n : { enabled: false, text: '', until: 0, below_version: '', id: '' };
}

export async function setNotice(env, now, input) {
  const text = String(input?.text ?? '').replace(/\r\n?/g, '\n').replace(/[\u0000-\u0009\u000b-\u001f\u007f]/g, '').trim().slice(0, MAX_NOTICE);
  const enabled = Boolean(input?.enabled) && text.length > 0;
  const until = Number(input?.until) > 0 ? Number(input.until) : 0;
  const below = String(input?.below_version ?? '').trim().slice(0, 20);
  if (below && !/^\d+(\.\d+){0,3}$/.test(below)) throw new Error('Phiên bản không hợp lệ (ví dụ 1.0.6)');
  const rawDevice = String(input?.request_code ?? '').trim();
  const requestCode = rawDevice ? normalizeRequestCode(rawDevice) : '';
  if (rawDevice && !requestCode) throw new Error('Mã thiết bị không hợp lệ (16 ký tự 0-9, A-F)');
  const old = await getNotice(env);
  // A new id makes the app show the notice again; same text and settings keep the old id.
  const same = old.text === text && old.below_version === below && old.until === until && (old.request_code || '') === (requestCode || '');
  const id = same && old.id ? old.id : String(now);
  const value = { enabled, text, until, below_version: requestCode ? '' : below, request_code: requestCode || '', id, updated_at: now };
  await setJsonSetting(env, now, NOTICE_KEY, value);
  return value;
}

/** The notice for one car, or null. Old app versions ignore the field. */
export async function noticeFor(env, now, appVersion, requestCode) {
  const n = await getNotice(env);
  if (!n.enabled || !n.text) return null;
  if (n.request_code && n.request_code !== requestCode) return null;
  if (n.until && now > n.until) return null;
  if (n.below_version && (!appVersion || compareVersions(appVersion, n.below_version) >= 0)) return null;
  return { id: n.id, text: n.text, until: n.until || 0 };
}

/** Which car got which notice and when it was dismissed ("Đã xem"), for the admin. */
async function ensureNoticeLog(env) {
  await env.DB.prepare(
    'CREATE TABLE IF NOT EXISTS notice_log (notice_id TEXT NOT NULL, request_code TEXT NOT NULL, delivered_at INTEGER, seen_at INTEGER, PRIMARY KEY (notice_id, request_code))'
  ).run();
}

export async function recordDelivered(env, now, noticeId, requestCode) {
  await ensureNoticeLog(env);
  await env.DB.prepare('INSERT OR IGNORE INTO notice_log (notice_id, request_code, delivered_at) VALUES (?, ?, ?)')
    .bind(noticeId, requestCode, now).run();
}

/** The app sends the id it last dismissed (X-THT-Notice-Seen) with each license check. */
export async function recordSeen(env, now, requestCode, seenId) {
  const id = String(seenId || '').trim().slice(0, 40);
  if (!id || !requestCode) return;
  await ensureNoticeLog(env);
  await env.DB.prepare('INSERT OR IGNORE INTO notice_log (notice_id, request_code, delivered_at) VALUES (?, ?, ?)').bind(id, requestCode, now).run();
  await env.DB.prepare('UPDATE notice_log SET seen_at = ? WHERE notice_id = ? AND request_code = ? AND seen_at IS NULL').bind(now, id, requestCode).run();
}

export async function noticeStats(env, noticeId) {
  await ensureNoticeLog(env);
  if (!noticeId) return { delivered: 0, seen: 0, recipients: [] };
  const row = await env.DB.prepare('SELECT COUNT(*) AS delivered, COUNT(seen_at) AS seen FROM notice_log WHERE notice_id = ?').bind(noticeId).first();
  const recipients = (await env.DB.prepare(
    'SELECT n.request_code, n.delivered_at, n.seen_at, d.label, d.app_version, ' +
    '(SELECT l.customer FROM activations a JOIN licenses l ON l.id = a.license_id WHERE a.request_code = n.request_code AND a.status = \'active\' AND l.customer IS NOT NULL AND l.customer <> \'\' LIMIT 1) AS customer ' +
    'FROM notice_log n LEFT JOIN devices d ON d.request_code = n.request_code WHERE n.notice_id = ? ORDER BY n.delivered_at DESC LIMIT 200'
  ).bind(noticeId).all()).results;
  return { delivered: row?.delivered || 0, seen: row?.seen || 0, recipients };
}
