// App-facing license endpoints: verify (activate), status (renew/block sync), restore, unlock.
import { codeHint, formatCode, normalizeLicenseCode, normalizeRequestCode } from './codes.js';
import { protectVin, sha256Hex, signLicenseToken } from './crypto.js';
import { clearKeys, describeWait, DAY, HOUR, lockedFor, recordFailure } from './lockout.js';
import { getLicenseMode, getVinMode } from './settings.js';
import { noticeFor, recordDelivered, recordSeen } from './notice.js';

export const json = (status, body, extraHeaders = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...extraHeaders },
  });

export function clientIp(request) {
  return request.headers.get('cf-connecting-ip') || request.headers.get('x-real-ip') || '0.0.0.0';
}

function header(request, name) {
  return request.headers.get(`x-tht-${name}`) || request.headers.get(`x-geely-${name}`) || '';
}

async function readJson(request) {
  try {
    const text = await request.text();
    if (text.length > 4096) return null;
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function logAttempt(db, now, kind, requestCode, ip, code, ok, reason) {
  await db.prepare('INSERT INTO attempts (at, kind, request_code, ip, code_hint, ok, reason) VALUES (?, ?, ?, ?, ?, ?, ?)')
    .bind(now, kind, requestCode || null, ip, codeHint(code), ok ? 1 : 0, reason || null).run();
}

function lockedResponse(ms) {
  return json(429, {
    ok: false,
    code: 'locked',
    retry_after_ms: ms,
    message: `Nhập sai quá nhiều lần. Thử lại sau ${describeWait(ms)} hoặc nhập mã mở khóa 8 số do người bán cấp.`,
  }, { 'retry-after': String(Math.ceil(ms / 1000)) });
}

async function failure(db, now, kind, requestCode, ip, code, reason, message) {
  await logAttempt(db, now, kind, requestCode, ip, code, false, reason);
  const keys = requestCode ? [`dev:${requestCode}`, `ip:${ip}`] : [`ip:${ip}`];
  const { lockedMs, failsBeforeLock } = await recordFailure(db, keys, now);
  if (lockedMs) return lockedResponse(lockedMs);
  const hint = failsBeforeLock > 0 && failsBeforeLock <= 3 ? ` Còn ${failsBeforeLock} lần thử trước khi bị khóa tạm.` : '';
  return json(401, { ok: false, code: reason, remaining_attempts: failsBeforeLock, message: message + hint });
}

const VIN_STAT_COLUMNS = [
  'vin_ok_count INTEGER NOT NULL DEFAULT 0',
  'vin_miss_count INTEGER NOT NULL DEFAULT 0',
  'vin_change_count INTEGER NOT NULL DEFAULT 0',
  'vin_last_ok INTEGER',
  'vin_miss_reason TEXT',
  'vin_mismatch_count INTEGER NOT NULL DEFAULT 0',
  'vin_mismatch_at INTEGER',
];
const migratedDbs = new WeakSet();

/** Adds the VIN statistics columns to a database created before they existed (idempotent). */
export async function ensureVinStatColumns(env) {
  if (!env.DB || migratedDbs.has(env.DB)) return;
  for (const col of VIN_STAT_COLUMNS) {
    try {
      await env.DB.prepare(`ALTER TABLE devices ADD COLUMN ${col}`).run();
    } catch {
      // already there
    }
  }
  try {
    // 1 = VIN bound automatically from the car's first check, 0 = typed by the admin.
    await env.DB.prepare('ALTER TABLE licenses ADD COLUMN vin_auto INTEGER NOT NULL DEFAULT 0').run();
  } catch {
    // already there
  }
  migratedDbs.add(env.DB);
}

/** "none:CAR_SERVICE_UNAVAILABLE" from the app → the reason; anything else → null. */
function vinMissReason(status) {
  const m = /^none(?::([A-Z_]{1,40}))?$/.exec(String(status || '').trim());
  return m ? (m[1] || 'UNKNOWN') : null;
}

async function upsertDevice(env, now, requestCode, ip, appVersion, vinRaw, vinStatus) {
  await ensureVinStatColumns(env);
  const vin = await protectVin(env, vinRaw || '');
  const existing = await env.DB.prepare('SELECT request_code, vin_hash FROM devices WHERE request_code = ?').bind(requestCode).first();
  const miss = vin ? null : vinMissReason(vinStatus);
  const changed = Boolean(vin && existing?.vin_hash && existing.vin_hash !== vin.vin_hash);
  if (!existing) {
    await env.DB.prepare(
      'INSERT INTO devices (request_code, status, vin_hash, vin_enc, vin_last4, app_version, last_ip, first_seen, last_seen, ' +
      'vin_ok_count, vin_miss_count, vin_change_count, vin_last_ok, vin_miss_reason) VALUES (?, \'active\', ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)'
    ).bind(requestCode, vin?.vin_hash ?? null, vin?.vin_enc ?? null, vin?.vin_last4 ?? null, appVersion || null, ip, now, now,
      vin ? 1 : 0, miss ? 1 : 0, vin ? now : null, miss).run();
  } else {
    await env.DB.prepare(
      'UPDATE devices SET last_seen = ?, last_ip = ?, app_version = COALESCE(?, app_version), ' +
      'vin_hash = COALESCE(?, vin_hash), vin_enc = COALESCE(?, vin_enc), vin_last4 = COALESCE(?, vin_last4), ' +
      'vin_ok_count = COALESCE(vin_ok_count, 0) + ?, vin_miss_count = COALESCE(vin_miss_count, 0) + ?, ' +
      'vin_change_count = COALESCE(vin_change_count, 0) + ?, vin_last_ok = COALESCE(?, vin_last_ok), ' +
      'vin_miss_reason = COALESCE(?, vin_miss_reason) WHERE request_code = ?'
    ).bind(now, ip, appVersion || null, vin?.vin_hash ?? null, vin?.vin_enc ?? null, vin?.vin_last4 ?? null,
      vin ? 1 : 0, miss ? 1 : 0, changed ? 1 : 0, vin ? now : null, miss, requestCode).run();
  }
  return vin;
}

async function successBody(env, now, license, requestCode, message) {
  const token = await signLicenseToken(env, {
    lid: license.id,
    req: requestCode,
    typ: license.license_type,
    exp: license.expires_at ?? 0,
    iat: now,
  });
  return {
    ok: true,
    token,
    activation_code: license.code,
    activation_code_display: formatCode(license.code),
    license_type: license.license_type,
    expires_at: license.expires_at ?? 0,
    server_time: now,
    message,
    ...(await noticeField(env, now, requestCode)),
  };
}

/** Customer notice from the admin ("Thông báo cho khách"); never breaks the license reply. */
async function noticeField(env, now, requestCode) {
  try {
    const dev = await env.DB.prepare('SELECT app_version FROM devices WHERE request_code = ?').bind(requestCode).first();
    const notice = await noticeFor(env, now, dev?.app_version, requestCode);
    if (notice) await recordDelivered(env, now, notice.id, requestCode);
    return notice ? { notice } : {};
  } catch {
    return {};
  }
}

function isExpired(license, now) {
  return license.expires_at != null && license.expires_at > 0 && license.expires_at < now;
}

/** The validity clock starts at the first activation / restore / status of the code. */
async function startClock(db, license, now) {
  if (license.expires_at != null) return;
  license.expires_at = computeExpiry(license, now);
  license.activated_at = now;
  await db.prepare('UPDATE licenses SET expires_at = ?, activated_at = ?, updated_at = ? WHERE id = ?')
    .bind(license.expires_at, now, now, license.id).run();
}

function computeExpiry(license, now) {
  if (license.duration_mode === 'lifetime') return 0;
  if (license.duration_mode === 'fixed') return license.fixed_expires_at || 0;
  return now + Math.max(1, license.duration_days || 1) * DAY;
}

/**
 * VIN rules shared by activation and the periodic check (docs: worker/README.md "Khóa theo VIN"):
 * - VIP keys are never checked.
 * - No readable VIN → never blocked here (only counted in the device's VIN stats).
 * - Key without a VIN and for one device → the first VIN read is bound to it automatically.
 * - Different VIN → counted on the device; blocked only when the admin chose "Chặn xe".
 * Returns null when the car may continue, or a JSON response to send back.
 */
async function checkVin(env, now, license, requestCode, ip, vinRaw, kind) {
  if (!license || license.license_type === 'vip') return null;
  await ensureVinStatColumns(env);
  const vin = await protectVin(env, vinRaw || '');
  if (!vin) return null;
  const db = env.DB;
  if (!license.vin_hash) {
    if ((license.max_devices || 1) !== 1) return null;
    const res = await db.prepare('UPDATE licenses SET vin_hash = ?, vin_enc = ?, vin_last4 = ?, vin_auto = 1, updated_at = ? WHERE id = ? AND vin_hash IS NULL')
      .bind(vin.vin_hash, vin.vin_enc, vin.vin_last4, now, license.id).run();
    if (res.meta?.changes) {
      license.vin_hash = vin.vin_hash;
      await db.prepare('INSERT INTO admin_log (at, ip, action, target, detail) VALUES (?, ?, ?, ?, ?)')
        .bind(now, ip, 'vin_autobind', formatCode(license.code), `VIN …${vin.vin_last4} từ thiết bị ${requestCode}`).run();
    }
    return null;
  }
  if (vin.vin_hash === license.vin_hash) return null;
  await db.prepare('UPDATE devices SET vin_mismatch_count = COALESCE(vin_mismatch_count, 0) + 1, vin_mismatch_at = ? WHERE request_code = ?')
    .bind(now, requestCode).run();
  await logAttempt(db, now, kind, requestCode, ip, license.code, false, 'vin_mismatch');
  if ((await getVinMode(env)) !== 'block') return null;
  // "device_blocked" makes app versions already on cars stop; a later check with the right VIN releases it.
  return json(403, { ok: false, code: 'device_blocked', reason: 'vin_mismatch',
    message: 'Màn hình đang gắn trên xe khác với xe đã đăng ký bản quyền (VIN không khớp). Liên hệ người bán.' });
}

// POST /api/verify {request_code, activation_code, vin?, app_version?}
export async function handleVerify(request, env, now) {
  const db = env.DB;
  const ip = clientIp(request);
  const body = await readJson(request);
  const requestCode = normalizeRequestCode(body?.request_code);
  if (!requestCode) return json(400, { ok: false, code: 'bad_request', message: 'Mã thiết bị không hợp lệ.' });

  const locked = await lockedFor(db, [`dev:${requestCode}`, `ip:${ip}`], now);
  if (locked) return lockedResponse(locked);

  const parsed = normalizeLicenseCode(body?.activation_code);
  if (!parsed || parsed.typo) {
    return failure(db, now, 'verify', requestCode, ip, parsed?.code, 'invalid_code', 'Mã kích hoạt không đúng. Kiểm tra lại từng chữ số.');
  }

  const device = await db.prepare('SELECT status FROM devices WHERE request_code = ?').bind(requestCode).first();
  if (device?.status === 'blocked') return json(403, { ok: false, code: 'device_blocked', message: 'Thiết bị này đã bị khóa. Liên hệ người bán.' });

  const license = await db.prepare('SELECT * FROM licenses WHERE code = ?').bind(parsed.code).first();
  if (!license) return failure(db, now, 'verify', requestCode, ip, parsed.code, 'invalid_code', 'Mã kích hoạt không tồn tại.');
  if (license.status === 'revoked') {
    await logAttempt(db, now, 'verify', requestCode, ip, parsed.code, false, 'license_revoked');
    return json(403, { ok: false, code: 'license_revoked', message: 'Mã kích hoạt đã bị thu hồi.' });
  }
  if (isExpired(license, now)) {
    await logAttempt(db, now, 'verify', requestCode, ip, parsed.code, false, 'expired');
    return json(403, { ok: false, code: 'expired', expires_at: license.expires_at, message: 'Mã kích hoạt đã hết hạn. Liên hệ người bán để gia hạn.' });
  }

  await ensureVinStatColumns(env);
  if (license.vin_hash && license.license_type !== 'vip' && !license.vin_auto) {
    // VIN typed by the admin: the code only works on that car.
    const vin = await protectVin(env, body?.vin || '');
    if (!vin || vin.vin_hash !== license.vin_hash) {
      await logAttempt(db, now, 'verify', requestCode, ip, parsed.code, false, 'vin_mismatch');
      return json(403, { ok: false, code: 'vin_mismatch', message: 'Mã này chỉ dùng cho một xe khác (số VIN không khớp).' });
    }
  }

  const mine = await db.prepare('SELECT id FROM activations WHERE license_id = ? AND request_code = ? AND status = \'active\'')
    .bind(license.id, requestCode).first();
  if (!mine) {
    let used = await db.prepare('SELECT COUNT(*) AS n FROM activations WHERE license_id = ? AND status = \'active\'').bind(license.id).first();
    if ((used?.n || 0) >= license.max_devices && await releaseSameCar(env, now, license, requestCode, body?.vin)) {
      // Owner 18/10/2026: the same car (same VIN) after a reinstall gets a new device code; typing
      // the old code moves it here instead of "in use on another device".
      used = await db.prepare('SELECT COUNT(*) AS n FROM activations WHERE license_id = ? AND status = \'active\'').bind(license.id).first();
    }
    if ((used?.n || 0) >= license.max_devices) {
      await logAttempt(db, now, 'verify', requestCode, ip, parsed.code, false, 'license_in_use');
      return json(409, { ok: false, code: 'license_in_use', message: 'Mã đã được kích hoạt trên thiết bị khác. Liên hệ người bán để chuyển thiết bị.' });
    }
    await db.prepare('INSERT INTO activations (license_id, request_code, status, activated_at) VALUES (?, ?, \'active\', ?)')
      .bind(license.id, requestCode, now).run();
  }
  // Also for a code the admin issued for this device in advance (activation row already there).
  await startClock(db, license, now);

  await upsertDevice(env, now, requestCode, ip, body?.app_version, body?.vin, body?.vin_status);
  const vinDenied = await checkVin(env, now, license, requestCode, ip, body?.vin, 'verify');
  if (vinDenied) return vinDenied;
  await clearKeys(db, [`dev:${requestCode}`, `ip:${ip}`]);
  await logAttempt(db, now, 'verify', requestCode, ip, parsed.code, true, null);
  const base = license.license_type === 'vip' ? 'Kích hoạt VIP thành công' : 'Kích hoạt thành công';
  return json(200, await successBody(env, now, license, requestCode, base));
}

/**
 * The active license of the car with this VIN, moved from its old device code to [requestCode].
 * Found through the license's own VIN, else through an older device that reported the same VIN.
 * Never moves from a blocked device; only the car's own old code(s) are released.
 */
/** The car's own older device codes for [license] (same VIN), never blocked ones. */
async function sameCarActivations(env, license, requestCode, vinHash) {
  const acts = (await env.DB.prepare(
    'SELECT a.request_code, d.vin_hash, d.status FROM activations a LEFT JOIN devices d ON d.request_code = a.request_code ' +
    'WHERE a.license_id = ? AND a.status = \'active\' AND a.request_code != ?'
  ).bind(license.id, requestCode).all()).results;
  const car = acts.filter((a) => a.vin_hash === vinHash || (!a.vin_hash && license.vin_hash === vinHash && (license.max_devices || 1) <= 1));
  return { all: acts, car, blocked: car.some((a) => a.status === 'blocked') };
}

/** Releases the same car's old device code(s) of [license]; true when something was released. */
async function releaseSameCar(env, now, license, requestCode, vinRaw) {
  const vin = await protectVin(env, vinRaw || '');
  if (!vin) return false;
  const { car, blocked } = await sameCarActivations(env, license, requestCode, vin.vin_hash);
  if (blocked || !car.length) return false;
  for (const a of car) {
    await env.DB.prepare('UPDATE activations SET status = \'released\', released_at = ?, released_reason = ? WHERE license_id = ? AND request_code = ? AND status = \'active\'')
      .bind(now, `vin_restore:${requestCode}`, license.id, a.request_code).run();
  }
  return true;
}

async function moveLicenseByVin(env, now, requestCode, vinRaw) {
  const db = env.DB;
  const vin = await protectVin(env, vinRaw || '');
  if (!vin) return null;
  const h = vin.vin_hash;
  let license = await db.prepare(
    'SELECT * FROM licenses WHERE vin_hash = ? AND status = \'active\' ORDER BY updated_at DESC LIMIT 1'
  ).bind(h).first();
  if (!license) {
    license = await db.prepare(
      'SELECT l.* FROM devices d JOIN activations a ON a.request_code = d.request_code AND a.status = \'active\' ' +
      'JOIN licenses l ON l.id = a.license_id WHERE d.vin_hash = ? AND d.request_code != ? AND l.status = \'active\' ' +
      'ORDER BY a.activated_at DESC LIMIT 1'
    ).bind(h, requestCode).first();
  }
  if (!license) return null;
  const acts = (await db.prepare(
    'SELECT a.request_code, d.vin_hash, d.status FROM activations a LEFT JOIN devices d ON d.request_code = a.request_code ' +
    'WHERE a.license_id = ? AND a.status = \'active\''
  ).bind(license.id).all()).results;
  if (acts.some((a) => a.request_code === requestCode)) return license;
  if (acts.some((a) => a.status === 'blocked' && (a.vin_hash === h || license.vin_hash === h))) return null;
  // Release the car's old code(s): same VIN on the device, or a one-car license bound to this VIN.
  const old = acts.filter((a) => a.vin_hash === h || (!a.vin_hash && license.vin_hash === h && (license.max_devices || 1) <= 1));
  // Only move a license this car is using now. A code the admin released (no active device) or an
  // unused code is never activated by VIN alone: the customer types it, or the admin moves it.
  if (!old.length) return null;
  for (const a of old) {
    await db.prepare('UPDATE activations SET status = \'released\', released_at = ?, released_reason = ? WHERE license_id = ? AND request_code = ? AND status = \'active\'')
      .bind(now, `vin_restore:${requestCode}`, license.id, a.request_code).run();
  }
  await db.prepare('INSERT INTO activations (license_id, request_code, status, activated_at) VALUES (?, ?, \'active\', ?)')
    .bind(license.id, requestCode, now).run();
  return license;
}

async function activeLicenseForDevice(db, requestCode) {
  return db.prepare(
    'SELECT l.* FROM activations a JOIN licenses l ON l.id = a.license_id ' +
    'WHERE a.request_code = ? AND a.status = \'active\' ORDER BY (l.status = \'active\') DESC, a.activated_at DESC LIMIT 1'
  ).bind(requestCode).first();
}

// New devices one IP may add through /api/policy per hour (keeps spam out of the device list).
const POLICY_NEW_DEVICES_PER_IP_HOUR = 20;

/**
 * "Yêu cầu key" mode. With a device code the mode comes back signed for that device
 * ({kind:"policy"}), so the app does not trust an edited or replayed reply. Every device that asks
 * is recorded (admin: "Chưa có key" until it activates), and a device the admin blocked always gets
 * "required", so blocking also stops free-mode installs.
 */
export async function handlePolicy(request, env, now) {
  const mode = await getLicenseMode(env);
  const req = normalizeRequestCode(header(request, 'request') || new URL(request.url).searchParams.get('req') || '');
  const out = { ok: true, license_mode: mode, server_time: now };
  if (!req) return json(200, out);
  let blocked = false;
  try {
    const ip = clientIp(request);
    const device = await env.DB.prepare('SELECT status FROM devices WHERE request_code = ?').bind(req).first();
    blocked = device?.status === 'blocked';
    const recent = device ? null : await env.DB.prepare(
      'SELECT COUNT(*) AS n FROM devices WHERE last_ip = ? AND first_seen > ? AND request_code NOT IN (SELECT request_code FROM activations)'
    ).bind(ip, now - HOUR).first();
    if (device || (recent?.n ?? 0) < POLICY_NEW_DEVICES_PER_IP_HOUR) {
      await upsertDevice(env, now, req, ip, header(request, 'app-version'), header(request, 'vin'), header(request, 'vin-status'));
    }
  } catch { /* recording is best effort; the mode reply still goes out */ }
  if (blocked) out.device_blocked = true;
  try { out.token = await signLicenseToken(env, { kind: 'policy', mode: blocked ? 'required' : mode, req, iat: now }); } catch { /* signing key missing: unsigned reply */ }
  return json(200, out);
}

// GET /api/status (headers X-THT-Request, X-THT-License, optional X-THT-Vin, X-THT-App-Version)
export async function handleStatus(request, env, now) {
  const db = env.DB;
  const ip = clientIp(request);
  const requestCode = normalizeRequestCode(header(request, 'request'));
  const parsed = normalizeLicenseCode(header(request, 'license'));
  if (!requestCode || !parsed) return json(401, { ok: false, code: 'bad_request', message: 'Thiếu thông tin license.' });

  const locked = await lockedFor(db, [`dev:${requestCode}`, `ip:${ip}`], now);
  if (locked) return lockedResponse(locked);

  const device = await db.prepare('SELECT status FROM devices WHERE request_code = ?').bind(requestCode).first();
  if (device?.status === 'blocked') return json(403, { ok: false, code: 'device_blocked', message: 'Thiết bị này đã bị khóa từ xa.' });

  const license = await db.prepare('SELECT * FROM licenses WHERE code = ?').bind(parsed.code).first();
  const mine = license && await db.prepare('SELECT id FROM activations WHERE license_id = ? AND request_code = ? AND status = \'active\'')
    .bind(license.id, requestCode).first();

  // The stored code was reissued ("Xoay key / cấp lại"), revoked or removed, but this device holds
  // another active license: hand that one over so the car switches without typing a code.
  if (!license || license.status === 'revoked' || !mine) {
    const other = await activeLicenseForDevice(db, requestCode);
    if (other && other.status === 'active' && (!license || other.id !== license.id)) {
      await startClock(db, other, now);
      await upsertDevice(env, now, requestCode, ip, header(request, 'app-version'), header(request, 'vin'), header(request, 'vin-status'));
      if (isExpired(other, now)) {
        return json(403, { ok: false, code: 'expired', expires_at: other.expires_at, server_time: now, message: 'License đã hết hạn.' });
      }
      return json(200, await successBody(env, now, other, requestCode, 'Đã nhận mã mới'));
    }
  }

  if (!license) return failure(db, now, 'status', requestCode, ip, parsed.code, 'license_not_found', 'License không còn trên máy chủ.');
  if (license.status === 'revoked') return json(403, { ok: false, code: 'license_revoked', message: 'License đã bị thu hồi.' });

  if (!mine) {
    // An old-server code imported with a device code but never checked in yet: bind it now.
    const used = await db.prepare('SELECT COUNT(*) AS n FROM activations WHERE license_id = ? AND status = \'active\'').bind(license.id).first();
    const pending = license.format === 'hex' && (used?.n || 0) === 0;
    if (!pending) return json(403, { ok: false, code: 'license_moved', message: 'License đã được chuyển sang thiết bị khác.' });
    await db.prepare('INSERT INTO activations (license_id, request_code, status, activated_at) VALUES (?, ?, \'active\', ?)')
      .bind(license.id, requestCode, now).run();
  }
  await startClock(db, license, now);
  await upsertDevice(env, now, requestCode, ip, header(request, 'app-version'), header(request, 'vin'), header(request, 'vin-status'));
  try { await recordSeen(env, now, requestCode, header(request, 'notice-seen')); } catch { /* admin info only */ }
  const vinDenied = await checkVin(env, now, license, requestCode, ip, header(request, 'vin'), 'status');
  if (vinDenied) return vinDenied;
  if (isExpired(license, now)) {
    return json(403, { ok: false, code: 'expired', expires_at: license.expires_at, server_time: now, message: 'License đã hết hạn.' });
  }
  return json(200, await successBody(env, now, license, requestCode, 'License đang hoạt động'));
}

// POST /api/restore {request_code, vin?, app_version?} — reinstall on the same device.
export async function handleRestore(request, env, now) {
  const db = env.DB;
  const ip = clientIp(request);
  const body = await readJson(request);
  const requestCode = normalizeRequestCode(body?.request_code);
  if (!requestCode) return json(400, { ok: false, code: 'bad_request', message: 'Mã thiết bị không hợp lệ.' });

  const locked = await lockedFor(db, [`dev:${requestCode}`, `ip:${ip}`], now);
  if (locked) return lockedResponse(locked);

  const device = await db.prepare('SELECT status FROM devices WHERE request_code = ?').bind(requestCode).first();
  if (device?.status === 'blocked') return json(403, { ok: false, code: 'device_blocked', message: 'Thiết bị này đã bị khóa. Liên hệ người bán.' });

  let license = await activeLicenseForDevice(db, requestCode);
  // Owner 18/10/2026: reinstall with a new signing key (or a factory reset) gives a new device
  // code. The same car (same VIN) gets its license moved to the new code automatically.
  if (!license) license = await moveLicenseByVin(env, now, requestCode, body?.vin);
  if (!license) {
    return failure(db, now, 'restore', requestCode, ip, null, 'no_license', 'Không tìm thấy bản quyền cho thiết bị này. Nếu đã cài lại hệ thống (khôi phục cài đặt gốc), mã thiết bị đã đổi — gửi mã mới cho người bán.');
  }
  if (license.status === 'revoked') return json(403, { ok: false, code: 'license_revoked', message: 'License đã bị thu hồi.' });
  // A code issued for this device in advance: restoring activates it (no typing needed).
  await startClock(db, license, now);
  if (isExpired(license, now)) {
    return json(403, { ok: false, code: 'expired', expires_at: license.expires_at, message: 'Bản quyền của thiết bị đã hết hạn. Liên hệ người bán để gia hạn.' });
  }
  await upsertDevice(env, now, requestCode, ip, body?.app_version, body?.vin, body?.vin_status);
  await logAttempt(db, now, 'restore', requestCode, ip, license.code, true, null);
  return json(200, await successBody(env, now, license, requestCode, 'Đã khôi phục bản quyền'));
}

// POST /api/unlock {request_code, unlock_code}
export async function handleUnlock(request, env, now) {
  const db = env.DB;
  const ip = clientIp(request);
  const body = await readJson(request);
  const requestCode = normalizeRequestCode(body?.request_code);
  const code = typeof body?.unlock_code === 'string' ? body.unlock_code.replace(/\D/g, '') : '';
  if (!requestCode || code.length !== 8) return json(400, { ok: false, code: 'bad_request', message: 'Mã mở khóa gồm 8 chữ số.' });

  const guardKey = `unlock:${requestCode}`;
  const locked = await lockedFor(db, [guardKey], now);
  if (locked) return lockedResponse(locked);

  const hash = await sha256Hex(`unlock|${requestCode}|${code}`);
  const row = await db.prepare('SELECT id FROM unlock_codes WHERE request_code = ? AND code_hash = ? AND used_at IS NULL AND expires_at > ?')
    .bind(requestCode, hash, now).first();
  if (!row) {
    await logAttempt(db, now, 'unlock', requestCode, ip, null, false, 'bad_unlock_code');
    const { lockedMs } = await recordFailure(db, [guardKey], now);
    if (lockedMs) return lockedResponse(lockedMs);
    return json(401, { ok: false, code: 'bad_unlock_code', message: 'Mã mở khóa không đúng hoặc đã hết hạn.' });
  }
  await db.prepare('UPDATE unlock_codes SET used_at = ? WHERE id = ?').bind(now, row.id).run();
  await clearKeys(db, [`dev:${requestCode}`, `ip:${ip}`, guardKey]);
  await logAttempt(db, now, 'unlock', requestCode, ip, null, true, null);
  return json(200, { ok: true, message: 'Đã mở khóa. Có thể nhập lại mã kích hoạt.' });
}

/** For update/model endpoints: the caller must hold an active license on this device. */
export async function requireLicense(request, env, now) {
  const requestCode = normalizeRequestCode(header(request, 'request'));
  const parsed = normalizeLicenseCode(header(request, 'license'));
  if (!requestCode || !parsed || parsed.typo) return null;
  const row = await env.DB.prepare(
    'SELECT l.* FROM licenses l JOIN activations a ON a.license_id = l.id ' +
    'LEFT JOIN devices d ON d.request_code = a.request_code ' +
    'WHERE l.code = ? AND a.request_code = ? AND a.status = \'active\' AND l.status = \'active\' AND COALESCE(d.status, \'active\') = \'active\''
  ).bind(parsed.code, requestCode).first();
  if (!row || isExpired(row, now)) return null;
  return row;
}
