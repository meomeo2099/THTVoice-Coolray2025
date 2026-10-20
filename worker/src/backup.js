// Admin backup / restore of the license database (Công cụ → Sao lưu / Khôi phục).
//
// The file is plain JSON. VINs stay encrypted (vin_enc) and hashed (vin_hash), so a backup only
// restores readable VINs on a server with the same VIN_KEY. Lockouts and the 90-day attempt log are
// left out: they are short-lived and rebuild by themselves.
import { ensureVinStatColumns } from './license.js';

export const BACKUP_FORMAT = 'tht-license-backup';
export const BACKUP_VERSION = 1;
/** Restore order (parents first); deletes run in reverse. */
export const BACKUP_TABLES = ['licenses', 'devices', 'activations', 'unlock_codes', 'voice_packs', 'app_settings', 'admin_log'];
const MAX_LOG_ROWS = 5000;
const CHUNK = 50;

async function ensureOptionalTables(env) {
  await env.DB.prepare('CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER)').run();
  await ensureVinStatColumns(env);
}

async function columnsOf(env, table) {
  const rows = await env.DB.prepare(`PRAGMA table_info(${table})`).all();
  return rows.results.map((r) => r.name);
}

export async function exportBackup(env, now) {
  await ensureOptionalTables(env);
  const tables = {};
  const counts = {};
  for (const t of BACKUP_TABLES) {
    const sql = t === 'admin_log' ? `SELECT * FROM admin_log ORDER BY id DESC LIMIT ${MAX_LOG_ROWS}` : `SELECT * FROM ${t}`;
    const rows = (await env.DB.prepare(sql).all()).results;
    tables[t] = rows;
    counts[t] = rows.length;
  }
  return { format: BACKUP_FORMAT, version: BACKUP_VERSION, created_at: now, key_id: env.LICENSE_KEY_ID || 'k1', counts, tables };
}

/** Checks the file before anything is written; returns the per-table row counts. */
export function checkBackup(data) {
  if (!data || data.format !== BACKUP_FORMAT || typeof data.tables !== 'object' || data.tables === null) {
    throw new Error('Tệp không phải bản sao lưu của máy chủ bản quyền');
  }
  if (data.version > BACKUP_VERSION) throw new Error('Bản sao lưu từ phiên bản máy chủ mới hơn, hãy cập nhật worker.js trước');
  const counts = {};
  for (const t of BACKUP_TABLES) {
    const rows = data.tables[t] ?? [];
    if (!Array.isArray(rows)) throw new Error(`Bảng ${t} trong tệp không hợp lệ`);
    counts[t] = rows.length;
  }
  if (!counts.licenses && !counts.devices) throw new Error('Bản sao lưu không có mã hoặc thiết bị nào');
  return counts;
}

/**
 * mode "merge": keep everything already on the server, add rows that are missing.
 * mode "replace": empty these tables first, then load the file (the admin downloads a fresh backup first).
 */
export async function restoreBackup(env, data, mode) {
  if (mode !== 'merge' && mode !== 'replace') throw new Error('Chế độ khôi phục không hợp lệ');
  const counts = checkBackup(data);
  // Coolray and EX2 servers use different key ids (c1 / k1): never mix their customers.
  const serverKid = env.LICENSE_KEY_ID || 'k1';
  if (data.key_id && data.key_id !== serverKid) {
    throw new Error(`Bản sao lưu của máy chủ khác (khóa ${data.key_id}), máy chủ này dùng khóa ${serverKid}`);
  }
  await ensureOptionalTables(env);
  const db = env.DB;
  const plan = [];
  if (mode === 'replace') {
    for (const t of [...BACKUP_TABLES].reverse()) plan.push(db.prepare(`DELETE FROM ${t}`));
  }
  for (const t of BACKUP_TABLES) {
    const have = new Set(await columnsOf(env, t));
    for (const row of data.tables[t] ?? []) {
      if (!row || typeof row !== 'object') continue;
      const cols = Object.keys(row).filter((c) => have.has(c));
      if (!cols.length) continue;
      const values = cols.map((c) => {
        const v = row[c];
        return v === undefined || (typeof v === 'object' && v !== null) ? null : v;
      });
      plan.push(db.prepare(`INSERT OR IGNORE INTO ${t} (${cols.join(', ')}) VALUES (${cols.map(() => '?').join(', ')})`).bind(...values));
    }
  }
  // D1 runs each batch as one transaction; the first batch holds the DELETEs together with the first rows.
  for (let i = 0; i < plan.length; i += CHUNK) await db.batch(plan.slice(i, i + CHUNK));
  const after = {};
  for (const t of BACKUP_TABLES) after[t] = (await db.prepare(`SELECT COUNT(*) AS n FROM ${t}`).first())?.n || 0;
  return { mode, in_file: counts, now_on_server: after };
}
