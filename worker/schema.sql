-- THT Voice license server (Cloudflare D1). Apply with:
--   npx wrangler d1 execute tht-voice-license --remote --file=worker/schema.sql
-- All times are Unix milliseconds. Safe to re-run (IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS licenses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL UNIQUE,              -- 20 digits (new) or 16 HEX (imported old code)
  format TEXT NOT NULL DEFAULT 'numeric', -- numeric | hex
  license_type TEXT NOT NULL DEFAULT 'standard', -- standard | vip
  duration_mode TEXT NOT NULL DEFAULT 'days',    -- days (from first activation) | fixed | lifetime
  duration_days INTEGER,
  fixed_expires_at INTEGER,
  expires_at INTEGER,                     -- NULL until first activation; 0 = lifetime
  max_devices INTEGER NOT NULL DEFAULT 1,
  status TEXT NOT NULL DEFAULT 'active',  -- active | revoked
  customer TEXT,
  note TEXT,
  vin_hash TEXT,                          -- optional: code only for this car (HMAC of VIN)
  vin_enc TEXT,                           -- AES-GCM encrypted VIN (admin can read it)
  vin_last4 TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  activated_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_licenses_vin ON licenses(vin_hash);
CREATE INDEX IF NOT EXISTS idx_licenses_customer ON licenses(customer);

CREATE TABLE IF NOT EXISTS devices (
  request_code TEXT PRIMARY KEY,          -- 16 HEX device code shown in the app
  status TEXT NOT NULL DEFAULT 'active',  -- active | blocked
  label TEXT,
  blocked_reason TEXT,
  vin_hash TEXT,
  vin_enc TEXT,
  vin_last4 TEXT,
  app_version TEXT,
  last_ip TEXT,
  first_seen INTEGER NOT NULL,
  last_seen INTEGER NOT NULL,
  vin_ok_count INTEGER NOT NULL DEFAULT 0,      -- checks that carried a valid VIN
  vin_miss_count INTEGER NOT NULL DEFAULT 0,    -- checks where the app could not read the VIN
  vin_change_count INTEGER NOT NULL DEFAULT 0,  -- a valid VIN different from the stored one
  vin_last_ok INTEGER,
  vin_miss_reason TEXT
);
CREATE INDEX IF NOT EXISTS idx_devices_vin ON devices(vin_hash);

CREATE TABLE IF NOT EXISTS activations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  license_id INTEGER NOT NULL,
  request_code TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'active',  -- active | released
  activated_at INTEGER NOT NULL,
  released_at INTEGER,
  released_reason TEXT
);
CREATE INDEX IF NOT EXISTS idx_act_license ON activations(license_id, status);
CREATE INDEX IF NOT EXISTS idx_act_device ON activations(request_code, status);

CREATE TABLE IF NOT EXISTS attempts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at INTEGER NOT NULL,
  kind TEXT NOT NULL,                     -- verify | status | restore | unlock | admin
  request_code TEXT,
  ip TEXT,
  code_hint TEXT,                         -- first/last digits only
  ok INTEGER NOT NULL,
  reason TEXT
);
CREATE INDEX IF NOT EXISTS idx_attempts_at ON attempts(at);

CREATE TABLE IF NOT EXISTS lockouts (
  key TEXT PRIMARY KEY,                   -- dev:<request> | ip:<ip> | unlock:<request> | admin:<ip>
  fail_count INTEGER NOT NULL DEFAULT 0,
  locked_until INTEGER NOT NULL DEFAULT 0,
  last_fail_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS unlock_codes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  request_code TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  used_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_unlock_device ON unlock_codes(request_code);

CREATE TABLE IF NOT EXISTS admin_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at INTEGER NOT NULL,
  action TEXT NOT NULL,
  target TEXT,
  detail TEXT,
  ip TEXT
);
CREATE INDEX IF NOT EXISTS idx_admin_log_at ON admin_log(at);

CREATE TABLE IF NOT EXISTS voice_packs (
  name TEXT NOT NULL,                     -- kokoro_vi
  sha256 TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  r2_key TEXT NOT NULL,                   -- object key in the MODELS R2 bucket
  created_at INTEGER NOT NULL,
  PRIMARY KEY (name, sha256)
);
