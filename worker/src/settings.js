// Small key/value settings kept in D1 (table created on first use, no manual SQL needed).

export const DEFAULT_COPY_FOOTER = 'Nếu bạn cảm thấy hữu ích có thể ủng hộ tác giả ở phần Ủng hộ, cảm ơn bạn nhiều ♥';
export const MAX_COPY_FOOTER = 500;
const COPY_FOOTER_KEY = 'copy_footer';

async function ensureSettingsTable(env) {
  await env.DB.prepare('CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER)').run();
}

/** Sentence sent to the customer under the activation code ("" = none). */
export async function getCopyFooter(env) {
  try {
    await ensureSettingsTable(env);
    const row = await env.DB.prepare('SELECT value FROM app_settings WHERE key = ?').bind(COPY_FOOTER_KEY).first();
    return row ? row.value : DEFAULT_COPY_FOOTER;
  } catch {
    return DEFAULT_COPY_FOOTER;
  }
}

export function cleanCopyFooter(text) {
  return String(text ?? '').replace(/\r\n?/g, '\n').replace(/[\u0000-\u0009\u000b-\u001f\u007f]/g, '').trim().slice(0, MAX_COPY_FOOTER);
}

export async function setCopyFooter(env, now, text) {
  const value = cleanCopyFooter(text);
  await ensureSettingsTable(env);
  await env.DB.prepare(
    'INSERT INTO app_settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at'
  ).bind(COPY_FOOTER_KEY, value, now).run();
  return value;
}

/** What "Sao chép" puts on the clipboard: the code(s) on top, the sentence below. */
export function shareText(codes, footer) {
  const body = codes.filter(Boolean).join('\n');
  const tail = cleanCopyFooter(footer);
  return tail ? `${body}\n\n${tail}` : body;
}

export const REMINDER_KEY = 'reminder_template';
export const MAX_REMINDER = 600;
/** Renewal reminder copied for a customer; {khach} {ma} {ngay} {con} are filled per key. */
export const DEFAULT_REMINDER =
  'Chào {khach}, key THT Voice {ma} của anh/chị sẽ hết hạn ngày {ngay} (còn {con} ngày). Anh/chị nhắn mình để gia hạn nhé.';

export async function getReminderTemplate(env) {
  try {
    await ensureSettingsTable(env);
    const row = await env.DB.prepare('SELECT value FROM app_settings WHERE key = ?').bind(REMINDER_KEY).first();
    return row && row.value ? row.value : DEFAULT_REMINDER;
  } catch {
    return DEFAULT_REMINDER;
  }
}

export async function setReminderTemplate(env, now, text) {
  const value = String(text ?? '').replace(/\r\n?/g, '\n').replace(/[\u0000-\u0009\u000b-\u001f\u007f]/g, '').trim().slice(0, MAX_REMINDER);
  await ensureSettingsTable(env);
  await env.DB.prepare(
    'INSERT INTO app_settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at'
  ).bind(REMINDER_KEY, value, now).run();
  return value || DEFAULT_REMINDER;
}

/** Free mode only: whether cars without a key may check / download updates and data packs. */
const FREE_UPDATES_KEY = 'free_updates';

export async function getFreeUpdates(env) {
  try {
    await ensureSettingsTable(env);
    const row = await env.DB.prepare('SELECT value FROM app_settings WHERE key = ?').bind(FREE_UPDATES_KEY).first();
    return row?.value !== 'off';
  } catch {
    return true;
  }
}

export async function setFreeUpdates(env, now, on) {
  if (typeof on !== 'boolean') throw new Error('Giá trị không hợp lệ');
  await ensureSettingsTable(env);
  await env.DB.prepare(
    'INSERT INTO app_settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at'
  ).bind(FREE_UPDATES_KEY, on ? 'on' : 'off', now).run();
  return on;
}

/** What the server does when a car reports a VIN different from the one bound to its key. */
export const VIN_MODES = ['record', 'block'];
const VIN_MODE_KEY = 'vin_mode';

export async function getVinMode(env) {
  try {
    await ensureSettingsTable(env);
    const row = await env.DB.prepare('SELECT value FROM app_settings WHERE key = ?').bind(VIN_MODE_KEY).first();
    return VIN_MODES.includes(row?.value) ? row.value : 'record';
  } catch {
    return 'record';
  }
}

export async function setVinMode(env, now, mode) {
  if (!VIN_MODES.includes(mode)) throw new Error('Chế độ VIN không hợp lệ');
  await ensureSettingsTable(env);
  await env.DB.prepare(
    'INSERT INTO app_settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at'
  ).bind(VIN_MODE_KEY, mode, now).run();
  return mode;
}

/**
 * Whether the app needs an activation code (owner 25/10/2026): "free" lets every car use THT Voice
 * without a key; "required" (default) locks cars without a valid key. Read by /api/policy.
 */
export const LICENSE_MODES = ['free', 'required'];
const LICENSE_MODE_KEY = 'license_mode';

export async function getLicenseMode(env) {
  try {
    await ensureSettingsTable(env);
    const row = await env.DB.prepare('SELECT value FROM app_settings WHERE key = ?').bind(LICENSE_MODE_KEY).first();
    return LICENSE_MODES.includes(row?.value) ? row.value : 'required';
  } catch {
    return 'required';
  }
}

export async function setLicenseMode(env, now, mode) {
  if (!LICENSE_MODES.includes(mode)) throw new Error('Chế độ bản quyền không hợp lệ');
  await ensureSettingsTable(env);
  await env.DB.prepare(
    'INSERT INTO app_settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at'
  ).bind(LICENSE_MODE_KEY, mode, now).run();
  return mode;
}

/** JSON value in app_settings (null when missing or unreadable). */
export async function getJsonSetting(env, key) {
  try {
    await ensureSettingsTable(env);
    const row = await env.DB.prepare('SELECT value FROM app_settings WHERE key = ?').bind(key).first();
    return row ? JSON.parse(row.value) : null;
  } catch {
    return null;
  }
}

export async function setJsonSetting(env, now, key, value) {
  await ensureSettingsTable(env);
  await env.DB.prepare(
    'INSERT INTO app_settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at'
  ).bind(key, JSON.stringify(value), now).run();
}
