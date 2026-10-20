// Admin JSON API under /admin/api/*. Auth: "Authorization: Bearer <ADMIN_PASSWORD>".
import { formatCode, generateNumericCode, generateUnlockCode, normalizeLicenseCode, normalizeRequestCode } from './codes.js';
import { DEFAULT_COPY_FOOTER, DEFAULT_REMINDER, getCopyFooter, getReminderTemplate, getVinMode, setCopyFooter, setReminderTemplate, setVinMode } from './settings.js';
import { decryptVin, normalizeVin, protectVin, safeEqual, sha256Hex, vinHash } from './crypto.js';
import { clearKeys, DAY, HOUR, lockedFor, recordFailure } from './lockout.js';
import { clientIp, ensureVinStatColumns, json } from './license.js';
import { exportBackup, restoreBackup } from './backup.js';
import { compareVersions, getNotice, latestReleaseInfo, noticeStats, setNotice } from './notice.js';
import { fetchReleaseInfo } from './update.js';

const MAX_BATCH = 500;
const PAGE_SIZES = [20, 50, 100];
/** Cars worth a look before switching VIN blocking on: wrong VIN, VIN changed, or no VIN read for 7 days. */
const VIN_ANOMALY = '(COALESCE(vin_mismatch_count, 0) > 0 OR COALESCE(vin_change_count, 0) > 0 OR ' +
  '(COALESCE(vin_miss_count, 0) > 0 AND (vin_last_ok IS NULL OR vin_last_ok < ?)))';

/** ?page=1&size=20 → LIMIT/OFFSET; size is one of 20/50/100. */
function pageOf(url) {
  const size = PAGE_SIZES.includes(parseInt(url.searchParams.get('size'), 10)) ? parseInt(url.searchParams.get('size'), 10) : PAGE_SIZES[0];
  const page = Math.max(1, Math.min(parseInt(url.searchParams.get('page'), 10) || 1, 100000));
  return { size, page, offset: (page - 1) * size };
}

async function paged(env, url, table, where, args, order, map = async (r) => r) {
  const { size, page, offset } = pageOf(url);
  const w = where.length ? 'WHERE ' + where.join(' AND ') : '';
  const total = (await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${table} ${w}`).bind(...args).first())?.n || 0;
  const rows = await env.DB.prepare(`SELECT * FROM ${table} ${w} ORDER BY ${order} LIMIT ? OFFSET ?`).bind(...args, size, offset).all();
  const items = [];
  for (const r of rows.results) items.push(await map(r));
  return { items, total, page, size };
}
/** 8+ characters; with the 5-tries lockout per IP a PIN of 8+ digits cannot be guessed. */
export const MIN_PASSWORD = 8;

async function authorize(request, env, now) {
  const ip = clientIp(request);
  const key = `admin:${ip}`;
  const locked = await lockedFor(env.DB, [key], now);
  if (locked) return json(429, { ok: false, code: 'locked', retry_after_ms: locked, message: 'Sai mật khẩu quá nhiều lần, thử lại sau.' });
  const password = env.ADMIN_PASSWORD || '';
  const given = (request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
  if (password.length < MIN_PASSWORD || !(await safeEqual(given, password))) {
    await recordFailure(env.DB, [key], now);
    return json(401, { ok: false, code: 'unauthorized', message: password.length < MIN_PASSWORD ? `ADMIN_PASSWORD chưa đặt (tối thiểu ${MIN_PASSWORD} ký tự).` : 'Sai mật khẩu quản trị.' });
  }
  await clearKeys(env.DB, [key]);
  return null;
}

export async function log(env, now, request, action, target, detail) {
  await env.DB.prepare('INSERT INTO admin_log (at, action, target, detail, ip) VALUES (?, ?, ?, ?, ?)')
    .bind(now, action, target == null ? null : String(target), detail == null ? null : JSON.stringify(detail), clientIp(request)).run();
}

async function body(request) {
  try {
    return JSON.parse(await request.text());
  } catch {
    return {};
  }
}

function parseDate(value) {
  if (value == null || value === '') return null;
  if (typeof value === 'number') return value;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value));
  if (m) return Date.UTC(+m[1], +m[2] - 1, +m[3], 16, 59, 59); // 23:59:59 Vietnam time
  const t = Date.parse(value);
  return Number.isNaN(t) ? null : t;
}

async function withVin(env, row) {
  if (!row) return row;
  const vin = await decryptVin(env, row.vin_enc);
  const { vin_enc, vin_hash, ...rest } = row;
  return { ...rest, vin };
}

async function licenseView(env, row) {
  const acts = await env.DB.prepare(
    'SELECT a.request_code, a.status, a.activated_at, a.released_at, d.status AS device_status, d.label, d.vin_enc, d.last_seen ' +
    'FROM activations a LEFT JOIN devices d ON d.request_code = a.request_code WHERE a.license_id = ? ORDER BY a.activated_at DESC LIMIT 20'
  ).bind(row.id).all();
  const activations = [];
  for (const a of acts.results) {
    const { vin_enc, ...rest } = a;
    activations.push({ ...rest, vin: await decryptVin(env, vin_enc) });
  }
  return { ...(await withVin(env, row)), code_display: formatCode(row.code), activations };
}

export async function createLicenses(env, now, input) {
  const count = Math.min(Math.max(parseInt(input.count, 10) || 1, 1), MAX_BATCH);
  const type = input.license_type === 'vip' ? 'vip' : 'standard';
  const mode = ['days', 'fixed', 'lifetime'].includes(input.duration_mode) ? input.duration_mode : 'days';
  const days = mode === 'days' ? Math.max(1, Math.min(parseInt(input.days, 10) || 0, 36500)) : null;
  if (mode === 'days' && !days) throw new Error('Số ngày không hợp lệ');
  const fixed = mode === 'fixed' ? parseDate(input.fixed_expires_at) : null;
  if (mode === 'fixed' && !(fixed > now)) throw new Error('Ngày hết hạn phải ở tương lai');
  const maxDevices = Math.max(1, Math.min(parseInt(input.max_devices, 10) || 1, 50));
  const vin = input.vin ? await protectVin(env, input.vin) : null;
  if (input.vin && !vin) throw new Error('VIN không hợp lệ (17 ký tự, không có I, O, Q)');
  // Issue for one car: bound to the device code shown on its activation screen.
  let requestCode = null;
  if (input.request_code && String(input.request_code).trim()) {
    requestCode = normalizeRequestCode(String(input.request_code));
    if (!requestCode) throw new Error('Mã yêu cầu (mã thiết bị) gồm 16 ký tự 0-9, A-F');
    const existing = await env.DB.prepare(
      'SELECT l.code FROM activations a JOIN licenses l ON l.id = a.license_id WHERE a.request_code = ? AND a.status = \'active\' AND l.status = \'active\''
    ).bind(requestCode).first();
    if (existing) throw new Error(`Thiết bị đã có mã ${formatCode(existing.code)}. Dùng Gia hạn hoặc Xoay key / cấp lại.`);
  }
  const codes = [];
  const total = requestCode ? 1 : count;
  for (let i = 0; i < total; i++) {
    for (let tries = 0; tries < 5; tries++) {
      const code = generateNumericCode();
      const res = await env.DB.prepare(
        'INSERT OR IGNORE INTO licenses (code, format, license_type, duration_mode, duration_days, fixed_expires_at, expires_at, max_devices, status, customer, note, vin_hash, vin_enc, vin_last4, created_at, updated_at) ' +
        'VALUES (?, \'numeric\', ?, ?, ?, ?, ?, ?, \'active\', ?, ?, ?, ?, ?, ?, ?)'
      ).bind(code, type, mode, days, fixed, mode === 'lifetime' ? 0 : mode === 'fixed' ? fixed : null, maxDevices,
        input.customer || null, input.note || null, vin?.vin_hash ?? null, vin?.vin_enc ?? null, vin?.vin_last4 ?? null, now, now).run();
      if (res.meta.changes) {
        codes.push(formatCode(code));
        if (requestCode) await bindToDevice(env, now, res.meta.last_row_id, requestCode);
        break;
      }
    }
  }
  return { codes, license_type: type, duration_mode: mode, days, fixed_expires_at: fixed, request_code: requestCode };
}

async function bindToDevice(env, now, licenseId, requestCode) {
  await env.DB.prepare('INSERT INTO activations (license_id, request_code, status, activated_at) VALUES (?, ?, \'active\', ?)')
    .bind(licenseId, requestCode, now).run();
  // last_seen 0 = never online yet (the car has not checked in).
  await env.DB.prepare('INSERT OR IGNORE INTO devices (request_code, status, first_seen, last_seen) VALUES (?, \'active\', ?, 0)')
    .bind(requestCode, now).run();
}

/**
 * "Xoay key / cấp lại": a new code for the device's current license (same type, expiry,
 * customer); the old code is revoked. The car switches by itself at its next server check.
 */
async function reissue(env, now, rc, input) {
  const db = env.DB;
  const old = await db.prepare(
    'SELECT l.* FROM activations a JOIN licenses l ON l.id = a.license_id WHERE a.request_code = ? AND a.status = \'active\' ' +
    'ORDER BY (l.status = \'active\') DESC, a.activated_at DESC LIMIT 1'
  ).bind(rc).first();
  if (!old) throw new Error('Thiết bị chưa có bản quyền để cấp lại. Dùng Tạo mã kích hoạt.');
  for (let tries = 0; tries < 5; tries++) {
    const code = generateNumericCode();
    const res = await db.prepare(
      'INSERT OR IGNORE INTO licenses (code, format, license_type, duration_mode, duration_days, fixed_expires_at, expires_at, max_devices, status, customer, note, vin_hash, vin_enc, vin_last4, created_at, updated_at, activated_at) ' +
      'VALUES (?, \'numeric\', ?, ?, ?, ?, ?, ?, \'active\', ?, ?, ?, ?, ?, ?, ?, ?)'
    ).bind(code, old.license_type, old.duration_mode, old.duration_days, old.fixed_expires_at, old.expires_at, old.max_devices,
      input.customer || old.customer, input.note || old.note, old.vin_hash, old.vin_enc, old.vin_last4, now, now, old.activated_at).run();
    if (!res.meta.changes) continue;
    await db.prepare('UPDATE licenses SET status = \'revoked\', note = COALESCE(note, \'\') || ?, updated_at = ? WHERE id = ?')
      .bind(` [cấp lại → ${formatCode(code)}]`, now, old.id).run();
    await db.prepare('UPDATE activations SET status = \'released\', released_at = ?, released_reason = \'reissued\' WHERE license_id = ? AND request_code = ? AND status = \'active\'')
      .bind(now, old.id, rc).run();
    await bindToDevice(env, now, res.meta.last_row_id, rc);
    return { request_code: rc, code: formatCode(code), old_code: formatCode(old.code), expires_at: old.expires_at, license_type: old.license_type };
  }
  throw new Error('Không tạo được mã mới, thử lại');
}

async function updateLicense(env, now, input) {
  const db = env.DB;
  const row = input.id
    ? await db.prepare('SELECT * FROM licenses WHERE id = ?').bind(input.id).first()
    : await db.prepare('SELECT * FROM licenses WHERE code = ?').bind(normalizeLicenseCode(input.code || '')?.code || '').first();
  if (!row) throw new Error('Không tìm thấy mã');
  const set = (sql, ...args) => db.prepare(`UPDATE licenses SET ${sql}, updated_at = ? WHERE id = ?`).bind(...args, now, row.id).run();
  switch (input.action) {
    case 'renew_days': {
      const days = parseInt(input.value, 10);
      if (!(days > 0 && days <= 36500)) throw new Error('Số ngày không hợp lệ');
      if (row.expires_at === 0) throw new Error('Mã vĩnh viễn, không cần gia hạn');
      if (row.expires_at == null) {
        // Not activated yet: extend the planned duration instead.
        if (row.duration_mode === 'fixed') await set('fixed_expires_at = ?', row.fixed_expires_at + days * DAY);
        else await set('duration_days = ?', (row.duration_days || 0) + days);
      } else {
        await set('expires_at = ?', Math.max(row.expires_at, now) + days * DAY);
      }
      break;
    }
    case 'set_expiry': {
      const at = parseDate(input.value);
      if (!at) throw new Error('Ngày không hợp lệ');
      await set('expires_at = ?, duration_mode = \'fixed\', fixed_expires_at = ?', at, at);
      break;
    }
    case 'lifetime': await set('expires_at = 0, duration_mode = \'lifetime\''); break;
    case 'revoke': await set('status = \'revoked\''); break;
    case 'restore': await set('status = \'active\''); break;
    case 'set_type': await set('license_type = ?', input.value === 'vip' ? 'vip' : 'standard'); break;
    case 'set_max_devices': await set('max_devices = ?', Math.max(1, Math.min(parseInt(input.value, 10) || 1, 50))); break;
    case 'set_note': await set('note = ?, customer = ?', input.value ?? null, input.customer ?? row.customer ?? null); break;
    case 'set_vin': {
      const vin = await protectVin(env, input.value || '');
      if (!vin) throw new Error('VIN không hợp lệ');
      await set('vin_hash = ?, vin_enc = ?, vin_last4 = ?', vin.vin_hash, vin.vin_enc, vin.vin_last4);
      break;
    }
    case 'clear_vin': await set('vin_hash = NULL, vin_enc = NULL, vin_last4 = NULL'); break;
    case 'release_devices':
      await db.prepare('UPDATE activations SET status = \'released\', released_at = ?, released_reason = \'admin\' WHERE license_id = ? AND status = \'active\'').bind(now, row.id).run();
      break;
    case 'delete':
      await db.prepare('DELETE FROM activations WHERE license_id = ?').bind(row.id).run();
      await db.prepare('DELETE FROM licenses WHERE id = ?').bind(row.id).run();
      return { deleted: true, code: formatCode(row.code) };
    default: throw new Error('Thao tác không hợp lệ');
  }
  return licenseView(env, await db.prepare('SELECT * FROM licenses WHERE id = ?').bind(row.id).first());
}

export async function deviceAction(env, now, input) {
  const db = env.DB;
  const rc = normalizeRequestCode(input.request_code || '');
  if (!rc) throw new Error('Mã thiết bị không hợp lệ');
  const ensure = () => db.prepare('INSERT OR IGNORE INTO devices (request_code, status, first_seen, last_seen) VALUES (?, \'active\', ?, ?)').bind(rc, now, now).run();
  switch (input.action) {
    case 'block':
      await ensure();
      await db.prepare('UPDATE devices SET status = \'blocked\', blocked_reason = ? WHERE request_code = ?').bind(input.value || null, rc).run();
      return { request_code: rc, status: 'blocked' };
    case 'unblock':
      await db.prepare('UPDATE devices SET status = \'active\', blocked_reason = NULL WHERE request_code = ?').bind(rc).run();
      return { request_code: rc, status: 'active' };
    case 'delete':
      await db.prepare('UPDATE activations SET status = \'released\', released_at = ?, released_reason = \'device_deleted\' WHERE request_code = ? AND status = \'active\'').bind(now, rc).run();
      await db.prepare('DELETE FROM devices WHERE request_code = ?').bind(rc).run();
      await clearKeys(db, [`dev:${rc}`, `unlock:${rc}`]);
      return { request_code: rc, deleted: true };
    case 'unlock':
      await clearKeys(db, [`dev:${rc}`, `unlock:${rc}`]);
      if (input.ip) await clearKeys(db, [`ip:${input.ip}`]);
      return { request_code: rc, unlocked: true };
    case 'issue_unlock_code': {
      const code = generateUnlockCode();
      await db.prepare('INSERT INTO unlock_codes (request_code, code_hash, created_at, expires_at) VALUES (?, ?, ?, ?)')
        .bind(rc, await sha256Hex(`unlock|${rc}|${code}`), now, now + 24 * HOUR).run();
      return { request_code: rc, unlock_code: code, expires_at: now + 24 * HOUR };
    }
    case 'reissue':
      return reissue(env, now, rc, input);
    case 'transfer': {
      const to = normalizeRequestCode(input.value || '');
      if (!to || to === rc) throw new Error('Mã thiết bị mới không hợp lệ');
      const acts = await db.prepare('SELECT license_id FROM activations WHERE request_code = ? AND status = \'active\'').bind(rc).all();
      if (!acts.results.length) throw new Error('Thiết bị cũ không có bản quyền đang dùng');
      for (const a of acts.results) {
        await db.prepare('UPDATE activations SET status = \'released\', released_at = ?, released_reason = ? WHERE license_id = ? AND request_code = ? AND status = \'active\'')
          .bind(now, `transfer:${to}`, a.license_id, rc).run();
        await db.prepare('INSERT INTO activations (license_id, request_code, status, activated_at) VALUES (?, ?, \'active\', ?)').bind(a.license_id, to, now).run();
      }
      await db.prepare('INSERT OR IGNORE INTO devices (request_code, status, first_seen, last_seen) VALUES (?, \'active\', ?, ?)').bind(to, now, now).run();
      return { from: rc, to, moved: acts.results.length };
    }
    case 'set_label':
      await ensure();
      await db.prepare('UPDATE devices SET label = ? WHERE request_code = ?').bind(input.value || null, rc).run();
      return { request_code: rc, label: input.value || null };
    case 'set_vin': {
      const vin = await protectVin(env, input.value || '');
      if (!vin) throw new Error('VIN không hợp lệ');
      await ensure();
      await db.prepare('UPDATE devices SET vin_hash = ?, vin_enc = ?, vin_last4 = ? WHERE request_code = ?').bind(vin.vin_hash, vin.vin_enc, vin.vin_last4, rc).run();
      return { request_code: rc, vin_last4: vin.vin_last4 };
    }
    default: throw new Error('Thao tác không hợp lệ');
  }
}

async function listLicenses(env, url, now = Date.now()) {
  const q = (url.searchParams.get('q') || '').trim();
  const status = url.searchParams.get('status') || '';
  const where = [];
  const args = [];
  if (q) {
    const vin = normalizeVin(q);
    const code = normalizeLicenseCode(q);
    const rc = normalizeRequestCode(q);
    const ors = ['customer LIKE ?', 'note LIKE ?', 'code LIKE ?'];
    args.push(`%${q}%`, `%${q}%`, `%${q.replace(/\D/g, '') || q}%`);
    if (code) { ors.push('code = ?'); args.push(code.code); }
    if (rc) { ors.push('id IN (SELECT license_id FROM activations WHERE request_code = ?)'); args.push(rc); }
    if (vin) {
      const h = await vinHash(env, vin);
      ors.push('vin_hash = ?', 'id IN (SELECT a.license_id FROM activations a JOIN devices d ON d.request_code = a.request_code WHERE d.vin_hash = ?)');
      args.push(h, h);
    }
    where.push(`(${ors.join(' OR ')})`);
  }
  if (status === 'active' || status === 'revoked') { where.push('status = ?'); args.push(status); }
  if (status === 'unused') where.push('expires_at IS NULL');
  // "Sắp hết hạn": active keys ending within N days (default 30), soonest first.
  // "Đã hết hạn": active keys that ended within the last 90 days, most recent first.
  let order = 'id DESC';
  if (status === 'expiring') {
    const days = Math.max(1, Math.min(parseInt(url.searchParams.get('days'), 10) || 30, 365));
    where.push('status = \'active\'', 'expires_at > ?', 'expires_at < ?');
    args.push(now, now + days * DAY);
    order = 'expires_at ASC';
  }
  if (status === 'expired') {
    where.push('status = \'active\'', 'expires_at > 0', 'expires_at <= ?', 'expires_at > ?');
    args.push(now, now - 90 * DAY);
    order = 'expires_at DESC';
  }
  return paged(env, url, 'licenses', where, args, order, (r) => licenseView(env, r));
}

export async function listDevices(env, url, now = Date.now()) {
  await ensureVinStatColumns(env);
  const q = (url.searchParams.get('q') || '').trim();
  const args = [];
  const where = [];
  if (q) {
    const vin = normalizeVin(q);
    where.push('(request_code LIKE ? OR label LIKE ? OR request_code IN (SELECT a.request_code FROM activations a JOIN licenses l ON l.id = a.license_id WHERE l.customer LIKE ? OR l.note LIKE ?)' + (vin ? ' OR vin_hash = ?' : '') + ')');
    args.push(`%${q.toUpperCase().replace(/-/g, '')}%`, `%${q}%`, `%${q}%`, `%${q}%`);
    if (vin) args.push(await vinHash(env, vin));
  }
  let order = 'last_seen DESC';
  const version = url.searchParams.get('version');
  if (version !== null && url.searchParams.has('version')) {
    where.push('COALESCE(app_version, \'\') = ?', 'last_seen > ?');
    args.push(version, now - 30 * DAY);
  }
  if (url.searchParams.get('filter') === 'vin') {
    where.push(VIN_ANOMALY);
    args.push(now - 7 * DAY);
    order = 'COALESCE(vin_mismatch_at, 0) DESC, last_seen DESC';
  }
  return paged(env, url, 'devices', where, args, order, async (r) => {
    const lock = await env.DB.prepare('SELECT fail_count, locked_until FROM lockouts WHERE key = ?').bind(`dev:${r.request_code}`).first();
    const lic = await env.DB.prepare(
      'SELECT l.id, l.code, l.license_type, l.expires_at, l.status, l.customer, l.note, l.duration_mode, l.duration_days FROM activations a JOIN licenses l ON l.id = a.license_id WHERE a.request_code = ? AND a.status = \'active\''
    ).bind(r.request_code).all();
    return { ...(await withVin(env, r)), lock, licenses: lic.results.map((l) => ({ ...l, code: formatCode(l.code) })) };
  });
}

async function importLegacy(env, now, items) {
  if (!Array.isArray(items) || items.length > 5000) throw new Error('Danh sách import không hợp lệ (tối đa 5000 dòng)');
  let imported = 0;
  const skipped = [];
  for (const item of items) {
    const parsed = normalizeLicenseCode(String(item.code || ''));
    if (!parsed || parsed.typo) { skipped.push(item.code); continue; }
    const expires = item.expires_at === 0 || item.lifetime ? 0 : parseDate(item.expires_at);
    const mode = expires === 0 ? 'lifetime' : expires ? 'fixed' : 'days';
    const days = mode === 'days' ? Math.max(1, parseInt(item.days, 10) || 30) : null;
    const res = await env.DB.prepare(
      'INSERT OR IGNORE INTO licenses (code, format, license_type, duration_mode, duration_days, fixed_expires_at, expires_at, max_devices, status, customer, note, created_at, updated_at) ' +
      'VALUES (?, ?, ?, ?, ?, ?, ?, 1, \'active\', ?, ?, ?, ?)'
    ).bind(parsed.code, parsed.format, item.license_type === 'vip' ? 'vip' : 'standard', mode, days,
      mode === 'fixed' ? expires : null, mode === 'days' ? null : expires, item.customer || null, item.note || 'import', now, now).run();
    if (!res.meta.changes) { skipped.push(item.code); continue; }
    imported++;
    const rc = normalizeRequestCode(String(item.request_code || ''));
    if (rc) {
      const lic = await env.DB.prepare('SELECT id FROM licenses WHERE code = ?').bind(parsed.code).first();
      await env.DB.prepare('INSERT INTO activations (license_id, request_code, status, activated_at) VALUES (?, ?, \'active\', ?)').bind(lic.id, rc, now).run();
      await env.DB.prepare('INSERT OR IGNORE INTO devices (request_code, status, first_seen, last_seen) VALUES (?, \'active\', ?, ?)').bind(rc, now, now).run();
    }
  }
  return { imported, skipped };
}

export async function handleAdminApi(request, env, url, now) {
  const denied = await authorize(request, env, now);
  if (denied) return denied;
  const path = url.pathname.replace(/^\/admin\/api\/?/, '');
  const db = env.DB;
  try {
    if (request.method === 'GET' && path === 'overview') {
      await ensureVinStatColumns(env);
      const one = async (sql, ...a) => (await db.prepare(sql).bind(...a).first())?.n || 0;
      return json(200, {
        ok: true,
        licenses: await one('SELECT COUNT(*) AS n FROM licenses'),
        unused: await one('SELECT COUNT(*) AS n FROM licenses WHERE expires_at IS NULL AND status = \'active\''),
        active_devices: await one('SELECT COUNT(DISTINCT request_code) AS n FROM activations WHERE status = \'active\''),
        blocked_devices: await one('SELECT COUNT(*) AS n FROM devices WHERE status = \'blocked\''),
        expiring_7d: await one('SELECT COUNT(*) AS n FROM licenses WHERE expires_at > ? AND expires_at < ?', now, now + 7 * DAY),
        expiring_30d: await one('SELECT COUNT(*) AS n FROM licenses WHERE status = \'active\' AND expires_at > ? AND expires_at < ?', now, now + 30 * DAY),
        vin_anomalies: await one(`SELECT COUNT(*) AS n FROM devices WHERE ${VIN_ANOMALY}`, now - 7 * DAY),
        expired_30d: await one('SELECT COUNT(*) AS n FROM licenses WHERE status = \'active\' AND expires_at > 0 AND expires_at <= ? AND expires_at > ?', now, now - 30 * DAY),
        locked_now: await one('SELECT COUNT(*) AS n FROM lockouts WHERE locked_until > ?', now),
        failed_24h: await one('SELECT COUNT(*) AS n FROM attempts WHERE ok = 0 AND at > ?', now - DAY),
        signing_ready: env.ROLE === 'admin' ? null : Boolean(env.LICENSE_SIGNING_KEY), vin_key_ready: Boolean(env.VIN_KEY), key_id: env.LICENSE_KEY_ID || 'k1',
      });
    }
    if (request.method === 'GET' && path === 'settings') return json(200, {
      ok: true, copy_footer: await getCopyFooter(env), default_copy_footer: DEFAULT_COPY_FOOTER,
      reminder_template: await getReminderTemplate(env), default_reminder_template: DEFAULT_REMINDER,
      vin_mode: await getVinMode(env),
    });
    if (request.method === 'GET' && path === 'licenses') return json(200, { ok: true, ...(await listLicenses(env, url, now)) });
    if (request.method === 'GET' && path === 'devices') return json(200, { ok: true, ...(await listDevices(env, url, now)) });
    if (request.method === 'GET' && path === 'lockouts') {
      return json(200, { ok: true, ...(await paged(env, url, 'lockouts', ['(locked_until > ? OR fail_count > 0)'], [now - DAY], 'last_fail_at DESC')) });
    }
    if (request.method === 'GET' && path === 'attempts') {
      return json(200, { ok: true, ...(await paged(env, url, 'attempts', [], [], 'id DESC')) });
    }
    if (request.method === 'GET' && path === 'log') {
      return json(200, { ok: true, ...(await paged(env, url, 'admin_log', [], [], 'id DESC')) });
    }
    if (request.method === 'GET' && path === 'versions') {
      // Cars seen in the last 30 days, grouped by app version; newest version first.
      const canFetch = env.ROLE !== 'admin' && env.GITHUB_REPO;
      const release = await latestReleaseInfo(env, now, canFetch ? fetchReleaseInfo : null);
      const rows = (await db.prepare(
        'SELECT COALESCE(app_version, \'\') AS version, COUNT(*) AS cars, MAX(last_seen) AS last_seen FROM devices WHERE last_seen > ? GROUP BY COALESCE(app_version, \'\')'
      ).bind(now - 30 * DAY).all()).results;
      rows.sort((a, b) => compareVersions(b.version, a.version));
      const total = rows.reduce((s, r) => s + r.cars, 0);
      const latest = release?.versionName || '';
      const upToDate = latest ? rows.filter((r) => r.version && compareVersions(r.version, latest) >= 0).reduce((s, r) => s + r.cars, 0) : null;
      return json(200, { ok: true, release, versions: rows, total, up_to_date: upToDate });
    }
    if (request.method === 'GET' && path === 'notice') {
      const notice = await getNotice(env);
      return json(200, { ok: true, notice, stats: await noticeStats(env, notice.id) });
    }
    if (request.method === 'GET' && path === 'backup') {
      const data = await exportBackup(env, now);
      await log(env, now, request, 'backup', null, data.counts);
      return json(200, { ok: true, backup: data });
    }
    if (request.method === 'GET' && path === 'voice-packs') {
      const rows = await db.prepare('SELECT * FROM voice_packs ORDER BY created_at DESC').all();
      return json(200, { ok: true, items: rows.results });
    }
    if (request.method !== 'POST') return json(404, { ok: false, message: 'Không có API này' });
    const input = await body(request);
    let result;
    switch (path) {
      case 'licenses/create': result = await createLicenses(env, now, input); break;
      case 'licenses/update': result = await updateLicense(env, now, input); break;
      case 'devices/action': result = await deviceAction(env, now, input); break;
      case 'lockouts/clear': await clearKeys(db, [String(input.key || '')]); result = { cleared: input.key }; break;
      case 'import': result = await importLegacy(env, now, input.items); break;
      case 'notice': result = { notice: await setNotice(env, now, input) }; break;
      case 'restore': result = await restoreBackup(env, input.backup, input.mode); break;
      case 'settings/copy-footer': result = { copy_footer: await setCopyFooter(env, now, input.copy_footer) }; break;
      case 'settings/vin-mode': result = { vin_mode: await setVinMode(env, now, input.vin_mode) }; break;
      case 'settings/reminder': result = { reminder_template: await setReminderTemplate(env, now, input.reminder_template) }; break;
      case 'voice-packs': {
        const sha = String(input.sha256 || '').toLowerCase();
        if (!/^[0-9a-f]{64}$/.test(sha) || !(input.size_bytes > 0) || !input.r2_key) throw new Error('Thiếu sha256/size_bytes/r2_key');
        await db.prepare('INSERT OR REPLACE INTO voice_packs (name, sha256, size_bytes, r2_key, created_at) VALUES (?, ?, ?, ?, ?)')
          .bind(input.name || 'kokoro_vi', sha, input.size_bytes, input.r2_key, now).run();
        result = { saved: true };
        break;
      }
      default: return json(404, { ok: false, message: 'Không có API này' });
    }
    const safeInput = { ...input };
    delete safeInput.items;
    delete safeInput.backup;
    const mask = (v) => (typeof v === 'string' && v.length > 4 ? `…${v.slice(-4)}` : v);
    if (safeInput.vin) safeInput.vin = mask(safeInput.vin);
    if (input.action === 'set_vin') safeInput.value = mask(safeInput.value);
    await log(env, now, request, path + (input.action ? `:${input.action}` : ''), input.id || input.code || input.request_code || null, safeInput);
    return json(200, { ok: true, result });
  } catch (e) {
    return json(400, { ok: false, message: e.message || 'Lỗi' });
  }
}
