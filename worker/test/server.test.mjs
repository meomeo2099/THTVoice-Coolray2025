import assert from 'node:assert/strict';
import { test } from 'node:test';
import { dammDigit, dammValid, generateNumericCode, normalizeLicenseCode } from '../src/codes.js';
import { verifyLicenseToken } from '../src/crypto.js';
import { ADMIN_JS } from '../src/admin-page.js';
import { lockDurationFor, MINUTE, HOUR, DAY } from '../src/lockout.js';
import { client, DEVICE, makeEnv, OTHER } from './harness.mjs';

const VIN = 'LB37622Z3NX000123';
const OTHER_VIN = 'LB37622Z3NX000999';

async function newCode(c, opts = {}) {
  const r = await c.admin('licenses/create', { count: 1, license_type: 'standard', duration_mode: 'days', days: 30, ...opts });
  assert.equal(r.status, 200, JSON.stringify(r.json));
  return r.json.result.codes[0];
}

const verify = (c, code, device = DEVICE, extra = {}) =>
  c.call('POST', '/api/verify', { body: { request_code: device, activation_code: code, ...extra }, ip: extra.ip });

test('codes: damm check digit catches single errors and neighbour swaps', () => {
  for (let i = 0; i < 200; i++) {
    const code = generateNumericCode();
    assert.match(code, /^[1-9][0-9]{19}$/);
    assert.ok(dammValid(code));
    const digits = code.split('');
    const j = i % 19;
    const typo = [...digits];
    typo[j] = String((+typo[j] + 1 + (i % 9)) % 10);
    assert.equal(dammValid(typo.join('')), false);
    if (digits[j] !== digits[j + 1]) {
      const swap = [...digits];
      [swap[j], swap[j + 1]] = [swap[j + 1], swap[j]];
      assert.equal(dammValid(swap.join('')), false);
    }
  }
  assert.equal(dammDigit('572'), 4);
  assert.deepEqual(normalizeLicenseCode('abcd-ef01-2345-6789'), { format: 'hex', code: 'ABCDEF0123456789' });
  assert.equal(normalizeLicenseCode('12345'), null);
});

test('lockout tiers', () => {
  assert.equal(lockDurationFor(4), 0);
  assert.equal(lockDurationFor(5), 15 * MINUTE);
  assert.equal(lockDurationFor(10), HOUR);
  assert.equal(lockDurationFor(15), DAY);
  assert.equal(lockDurationFor(19, 4), 0);
  assert.equal(lockDurationFor(20, 4), 15 * MINUTE);
});

test('admin page script is embeddable', () => {
  assert.ok(!ADMIN_JS.includes('`'));
  assert.ok(!ADMIN_JS.includes('${'));
  new Function(ADMIN_JS); // parses
});

test('activate → signed token → status → restore', async () => {
  const env = await makeEnv();
  const c = client(env);
  const code = await newCode(c);
  assert.match(code, /^\d{4}(-\d{4}){4}$/);
  const r = await verify(c, code, DEVICE, { vin: VIN, app_version: '1.0' });
  assert.equal(r.status, 200, JSON.stringify(r.json));
  assert.equal(r.json.expires_at, c.clock.now + 30 * DAY);
  const payload = await verifyLicenseToken(env.PUBLIC_KEY, r.json.token);
  assert.equal(payload.req, DEVICE);
  assert.equal(payload.kid, 'k1');
  assert.equal(payload.exp, r.json.expires_at);
  // Flip a character in the middle of the signature (the last base64url char partly holds padding bits).
  const [tb, ts] = r.json.token.split('.');
  const mid = Math.floor(ts.length / 2);
  const flipped = ts.slice(0, mid) + (ts[mid] === 'A' ? 'B' : 'A') + ts.slice(mid + 1);
  assert.equal(await verifyLicenseToken(env.PUBLIC_KEY, `${tb}.${flipped}`), null);

  // Same device again is fine; another device is refused while the code is in use.
  assert.equal((await verify(c, code)).status, 200);
  assert.equal((await verify(c, code, OTHER)).json.code, 'license_in_use');

  const headers = { 'x-tht-request': DEVICE, 'x-tht-license': r.json.activation_code };
  const s = await c.call('GET', '/api/status', { headers });
  assert.equal(s.status, 200);
  assert.ok(s.json.token);
  // Legacy header names still work.
  assert.equal((await c.call('GET', '/api/status', { headers: { 'x-geely-request': DEVICE, 'x-geely-license': r.json.activation_code } })).status, 200);

  const restore = await c.call('POST', '/api/restore', { body: { request_code: DEVICE } });
  assert.equal(restore.status, 200);
  assert.equal(restore.json.activation_code, r.json.activation_code);
  const none = await c.call('POST', '/api/restore', { body: { request_code: OTHER } });
  assert.equal(none.json.code, 'no_license');

  // VIN is stored encrypted and shown to the admin in full.
  const row = env.DB.raw.prepare('SELECT vin_enc, vin_hash, vin_last4 FROM devices WHERE request_code = ?').get(DEVICE);
  assert.ok(!String(row.vin_enc).includes(VIN));
  assert.equal(row.vin_last4, '0123');
  const devices = await c.admin(`devices?q=${VIN}`);
  assert.equal(devices.json.items[0].vin, VIN);
  const byVin = await c.admin(`licenses?q=${VIN}`);
  assert.equal(byVin.json.items.length, 1);
});

test('expiry, renewal, lifetime and fixed-date codes', async () => {
  const env = await makeEnv();
  const c = client(env);
  const code = await newCode(c, { days: 10 });
  const r = await verify(c, code);
  const lic = await c.admin(`licenses?q=${code}`);
  const id = lic.json.items[0].id;
  const headers = { 'x-tht-request': DEVICE, 'x-tht-license': r.json.activation_code };
  c.clock.now += 11 * DAY;
  const expired = await c.call('GET', '/api/status', { headers });
  assert.equal(expired.json.code, 'expired');
  await c.admin('licenses/update', { id, action: 'renew_days', value: 30 });
  const renewed = await c.call('GET', '/api/status', { headers });
  assert.equal(renewed.status, 200);
  assert.equal(renewed.json.expires_at, c.clock.now + 30 * DAY);
  await c.admin('licenses/update', { id, action: 'lifetime' });
  assert.equal((await c.call('GET', '/api/status', { headers })).json.expires_at, 0);

  const fixed = await c.admin('licenses/create', { count: 2, duration_mode: 'fixed', fixed_expires_at: c.clock.now + 5 * DAY });
  assert.equal(fixed.json.result.codes.length, 2);
  const f = await verify(c, fixed.json.result.codes[0], OTHER);
  assert.equal(f.json.expires_at, c.clock.now + 5 * DAY);
  const life = await c.admin('licenses/create', { duration_mode: 'lifetime', license_type: 'vip' });
  const v = await verify(c, life.json.result.codes[0], 'AAAABBBBCCCCDDDD');
  assert.equal(v.json.expires_at, 0);
  assert.equal(v.json.license_type, 'vip');
});

test('wrong codes lock the device (VIP does not bypass) and unlock codes release it', async () => {
  const env = await makeEnv();
  const c = client(env);
  const vip = (await c.admin('licenses/create', { license_type: 'vip', duration_mode: 'lifetime' })).json.result.codes[0];
  for (let i = 0; i < 4; i++) assert.equal((await verify(c, '1234-5678-9012-3456-7890')).status, 401);
  const fifth = await verify(c, '1234-5678-9012-3456-7890');
  assert.equal(fifth.status, 429);
  assert.equal(fifth.json.retry_after_ms, 15 * MINUTE);
  assert.equal((await verify(c, vip)).json.code, 'locked', 'VIP code must not bypass the lock');

  c.clock.now += 16 * MINUTE;
  for (let i = 0; i < 4; i++) {
    assert.equal((await verify(c, 'ABCDEF0123456789')).json.retry_after_ms, 15 * MINUTE, 'each further miss locks again');
    c.clock.now += 16 * MINUTE;
  }
  const tenth = await verify(c, 'ABCDEF0123456789');
  assert.equal(tenth.json.retry_after_ms, HOUR);

  // Wrong unlock code, then an admin-issued one.
  assert.equal((await c.call('POST', '/api/unlock', { body: { request_code: DEVICE, unlock_code: '00000000' } })).json.code, 'bad_unlock_code');
  const issued = await c.admin('devices/action', { request_code: DEVICE, action: 'issue_unlock_code' });
  const unlock = issued.json.result.unlock_code;
  assert.match(unlock, /^\d{8}$/);
  assert.equal((await c.call('POST', '/api/unlock', { body: { request_code: DEVICE, unlock_code: unlock } })).status, 200);
  assert.equal((await c.call('POST', '/api/unlock', { body: { request_code: DEVICE, unlock_code: unlock } })).status, 401, 'single use');
  assert.equal((await verify(c, vip)).status, 200);
  // Success resets the counter.
  assert.equal(env.DB.raw.prepare('SELECT COUNT(*) AS n FROM lockouts WHERE key = ?').get(`dev:${DEVICE}`).n, 0);
});

test('IP lock needs 4x more failures and admin can clear it', async () => {
  const env = await makeEnv();
  const c = client(env);
  let last;
  for (let i = 0; i < 20; i++) {
    const dev = (0x1000000000000000n + BigInt(i)).toString(16).toUpperCase();
    last = await verify(c, '1234-5678-9012-3456-7890', dev);
  }
  assert.equal(last.status, 429);
  const good = await newCode(c);
  assert.equal((await verify(c, good, OTHER)).json.code, 'locked');
  assert.equal((await verify(c, good, OTHER, { ip: '198.51.100.1' })).status, 200);
  await c.admin('lockouts/clear', { key: 'ip:203.0.113.7' });
  assert.equal((await verify(c, good, OTHER)).status, 200);
});

test('VIN-locked code, device block, transfer, revoke, delete', async () => {
  const env = await makeEnv();
  const c = client(env);
  const code = await newCode(c, { vin: VIN });
  assert.equal((await verify(c, code, DEVICE, { vin: OTHER_VIN })).json.code, 'vin_mismatch');
  assert.equal((await verify(c, code)).json.code, 'vin_mismatch');
  const ok = await verify(c, code, DEVICE, { vin: VIN.toLowerCase() });
  assert.equal(ok.status, 200);
  const headers = (dev) => ({ 'x-tht-request': dev, 'x-tht-license': ok.json.activation_code });

  await c.admin('devices/action', { request_code: DEVICE, action: 'block', value: 'test' });
  assert.equal((await c.call('GET', '/api/status', { headers: headers(DEVICE) })).json.code, 'device_blocked');
  await c.admin('devices/action', { request_code: DEVICE, action: 'unblock' });
  assert.equal((await c.call('GET', '/api/status', { headers: headers(DEVICE) })).status, 200);

  const moved = await c.admin('devices/action', { request_code: DEVICE, action: 'transfer', value: OTHER });
  assert.equal(moved.json.result.moved, 1);
  assert.equal((await c.call('GET', '/api/status', { headers: headers(DEVICE) })).json.code, 'license_moved');
  assert.equal((await c.call('POST', '/api/restore', { body: { request_code: OTHER } })).status, 200);

  const id = (await c.admin(`licenses?q=${code}`)).json.items[0].id;
  await c.admin('licenses/update', { id, action: 'revoke' });
  assert.equal((await c.call('GET', '/api/status', { headers: headers(OTHER) })).json.code, 'license_revoked');
  await c.admin('licenses/update', { id, action: 'restore' });
  await c.admin('devices/action', { request_code: OTHER, action: 'delete' });
  assert.equal((await c.call('GET', '/api/status', { headers: headers(OTHER) })).json.code, 'license_moved');
  await c.admin('licenses/update', { id, action: 'delete' });
  assert.equal((await c.call('GET', '/api/status', { headers: headers(OTHER) })).json.code, 'license_not_found');

  const log = await c.admin('log');
  assert.ok(log.json.items.length >= 5);
  assert.ok(!JSON.stringify(log.json).includes(VIN), 'admin log never keeps the full VIN');
});

test('imported old hex codes keep working', async () => {
  const env = await makeEnv();
  const c = client(env);
  const imp = await c.admin('import', { items: [
    { code: 'ABCD-EF01-2345-6789', request_code: DEVICE, expires_at: 0, license_type: 'vip' },
    { code: '1111222233334444', expires_at: '2099-01-01' },
    { code: 'bad' },
  ] });
  assert.equal(imp.json.result.imported, 2);
  assert.deepEqual(imp.json.result.skipped, ['bad']);
  // Bound old code: an old app just calls status with its stored code.
  const s = await c.call('GET', '/api/status', { headers: { 'x-geely-request': DEVICE, 'x-geely-license': 'ABCDEF0123456789' } });
  assert.equal(s.status, 200);
  assert.equal(s.json.license_type, 'vip');
  // Unbound old code: binds on first status check.
  const s2 = await c.call('GET', '/api/status', { headers: { 'x-geely-request': OTHER, 'x-geely-license': '1111222233334444' } });
  assert.equal(s2.status, 200);
  assert.equal((await c.call('GET', '/api/status', { headers: { 'x-geely-request': 'AAAABBBBCCCCDDDD', 'x-geely-license': '1111222233334444' } })).json.code, 'license_moved');
});

test('admin password and lockout', async () => {
  const env = await makeEnv();
  const c = client(env);
  for (let i = 0; i < 4; i++) assert.equal((await c.admin('overview', undefined, 'wrong')).status, 401);
  assert.equal((await c.admin('overview', undefined, 'wrong')).status, 401);
  assert.equal((await c.admin('overview')).status, 429, 'locked after 5 wrong passwords');
  c.clock.now += 16 * MINUTE;
  const ok = await c.admin('overview');
  assert.equal(ok.status, 200);
  assert.equal(ok.json.signing_ready, true);
  const weak = client(await makeEnv({ ADMIN_PASSWORD: 'short' }));
  assert.equal((await weak.admin('overview', undefined, 'short')).status, 401);
  const page = await c.call('GET', '/admin');
  assert.match(page.headers.get('content-security-policy'), /script-src 'self'/);
});

test('update and model endpoints require a license', async () => {
  const env = await makeEnv();
  const c = client(env);
  assert.equal((await c.call('GET', '/api/update?current_code=1')).status, 401);
  assert.equal((await c.call('GET', '/api/model?name=kokoro_vi&sha256=' + 'a'.repeat(64))).status, 401);
  const code = await newCode(c);
  const r = await verify(c, code);
  const headers = { 'x-tht-request': DEVICE, 'x-tht-license': r.json.activation_code };
  const sha = 'b'.repeat(64);
  assert.equal((await c.call('GET', `/api/model?name=kokoro_vi&sha256=${sha}`, { headers })).status, 404);
  await c.admin('voice-packs', { name: 'kokoro_vi', sha256: sha, size_bytes: 3, r2_key: 'packs/x.zip' });
  env.MODELS = { get: async () => ({ body: new Uint8Array([1, 2, 3]), size: 3 }) };
  const m = await c.call('GET', `/api/model?name=kokoro_vi&sha256=${sha}`, { headers });
  assert.equal(m.status, 200);
  assert.equal(m.json.downloadUrl, `https://lic.example.dev/api/model/file?name=kokoro_vi&sha256=${sha}`);
  const f = await c.call('GET', `/api/model/file?name=kokoro_vi&sha256=${sha}`, { headers });
  assert.equal(f.status, 200);
  assert.equal(f.headers.get('content-length'), '3');
  assert.equal((await c.call('GET', '/api/health')).json.ok, true);
});

test('voice pack and OTA come straight from GitHub Releases (no R2)', async () => {
  const env = await makeEnv({ GITHUB_REPO: 'owner/repo', GITHUB_TOKEN: 't' });
  const c = client(env);
  const r = await verify(c, await newCode(c));
  const headers = { 'x-tht-request': DEVICE, 'x-tht-license': r.json.activation_code };
  const sha = 'c'.repeat(64);
  const assets = [
    { id: 11, name: 'voice-pack.json', size: 100 },
    { id: 12, name: 'THTVoice-kokoro-vi-abc.zip', size: 4 },
    { id: 13, name: 'update.json', size: 50 },
    { id: 14, name: 'THTVoice-1.0.1.apk', size: 7 },
  ];
  const calls = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (u, init) => {
    calls.push(String(u));
    assert.equal(init.headers.authorization, 'Bearer t');
    const url = String(u);
    if (url.endsWith('/releases?per_page=20')) return Response.json([{ draft: false, assets }]);
    if (url.endsWith('/releases/latest')) return Response.json({ tag_name: 'release-1.0.1-2', assets });
    if (url.endsWith('/assets/11')) return Response.json({ name: 'kokoro_vi', file: 'THTVoice-kokoro-vi-abc.zip', sha256: sha, size: 4 });
    if (url.endsWith('/assets/12')) return new Response(new Uint8Array([1, 2, 3, 4]));
    if (url.endsWith('/assets/13')) return Response.json({ versionCode: 2, versionName: '1.0.1', apk: 'THTVoice-1.0.1.apk', sha256: 'd'.repeat(64) });
    if (url.endsWith('/assets/14')) return new Response(new Uint8Array(7));
    return new Response('nope', { status: 404 });
  };
  try {
    const m = await c.call('GET', `/api/model?name=kokoro_vi&sha256=${sha}`, { headers });
    assert.equal(m.status, 200, JSON.stringify(m.json));
    assert.equal(m.json.sizeBytes, 4);
    const f = await c.call('GET', `/api/model/file?name=kokoro_vi&sha256=${sha}`, { headers });
    assert.equal(f.status, 200);
    assert.equal(f.headers.get('content-length'), '4');
    assert.equal((await c.call('GET', `/api/model?name=kokoro_vi&sha256=${'e'.repeat(64)}`, { headers })).status, 404);

    const u = await c.call('GET', '/api/update?current_code=1', { headers });
    assert.equal(u.json.updateAvailable, true);
    assert.equal(u.json.versionCode, 2);
    assert.equal(u.json.downloadUrl, 'https://lic.example.dev/api/update/apk?asset_id=14');
    assert.equal((await c.call('GET', '/api/update?current_code=2', { headers })).json.updateAvailable, false);
    const apk = await c.call('GET', '/api/update/apk?asset_id=14', { headers });
    assert.equal(apk.status, 200);
    assert.equal((await c.call('GET', '/api/update/apk?asset_id=12', { headers })).status, 404, 'only .apk assets');
  } finally {
    globalThis.fetch = realFetch;
  }
});

test('issue for one device, restore activates it, reissue switches the car to the new code', async () => {
  const env = await makeEnv();
  const c = client(env);
  const issued = await c.admin('licenses/create', { request_code: '0123-4567-89ab-cdef', duration_mode: 'days', days: 30, customer: 'Xe A', count: 5 });
  assert.equal(issued.status, 200, JSON.stringify(issued.json));
  assert.equal(issued.json.result.codes.length, 1, 'one code for one device');
  const code = issued.json.result.codes[0];
  // Only that device can use it.
  assert.equal((await verify(c, code, OTHER)).json.code, 'license_in_use');
  // A second code for the same device is refused (renew or reissue instead).
  assert.equal((await c.admin('licenses/create', { request_code: DEVICE, duration_mode: 'lifetime' })).status, 400);
  // Restore without typing starts the 30 days now.
  c.clock.now += 5 * DAY;
  const restored = await c.call('POST', '/api/restore', { body: { request_code: DEVICE } });
  assert.equal(restored.status, 200);
  assert.equal(restored.json.expires_at, c.clock.now + 30 * DAY);
  const expires = restored.json.expires_at;

  const re = await c.admin('devices/action', { request_code: DEVICE, action: 'reissue' });
  assert.equal(re.status, 200, JSON.stringify(re.json));
  assert.notEqual(re.json.result.code, restored.json.activation_code_display);
  assert.equal(re.json.result.expires_at, expires, 'expiry kept');
  // The car still holds the old code: its next status check hands over the new one.
  const s = await c.call('GET', '/api/status', { headers: { 'x-tht-request': DEVICE, 'x-tht-license': restored.json.activation_code } });
  assert.equal(s.status, 200, JSON.stringify(s.json));
  assert.equal(s.json.activation_code_display, re.json.result.code);
  assert.equal(s.json.expires_at, expires);
  // The old code is dead elsewhere.
  assert.equal((await verify(c, restored.json.activation_code, OTHER)).json.code, 'license_revoked');
});

test('separate management site serves only the admin page/API; license server can hide /admin', async () => {
  const { routeAdmin } = await import('../src/admin-worker.js');
  const env = await makeEnv({ ROLE: 'admin' });
  const page = await routeAdmin(new Request('https://keythtvoice.example.dev/'), env, 1_800_000_000_000);
  assert.equal(page.status, 200);
  assert.match(await page.text(), /Cấp mã THT Voice/);
  assert.equal((await routeAdmin(new Request('https://keythtvoice.example.dev/api/verify', { method: 'POST' }), env)).status, 404);
  const ov = await routeAdmin(new Request('https://keythtvoice.example.dev/admin/api/overview', { headers: { authorization: `Bearer ${env.ADMIN_PASSWORD}` } }), env);
  assert.equal((await ov.json()).signing_ready, null);

  const lic = client(await makeEnv({ ADMIN_UI: 'off' }));
  assert.equal((await lic.call('GET', '/admin')).status, 404);
  assert.equal((await lic.admin('overview')).status, 404);
  assert.equal((await lic.call('GET', '/api/health')).status, 200);
});

test('basic issuing page and device list', async () => {
  const env = await makeEnv({ ADMIN_PASSWORD: '12345678' });
  const c = client(env);
  const form = (fields, headers = {}) => ({ method: 'POST', body: new URLSearchParams(fields), headers });
  const { route } = await import('../src/index.js');
  const call = async (path, init = {}) => {
    const res = await route(new Request(`https://lic.example.dev${path}`, { ...init, headers: { 'cf-connecting-ip': '203.0.113.9', ...(init.headers || {}) } }), env, c.clock.now);
    return { status: res.status, text: await res.text(), headers: res.headers };
  };
  const home = await call('/');
  assert.equal(home.status, 200);
  assert.match(home.text, /Tạo mã kích hoạt/);
  assert.equal((await call('/lite.js')).status, 200);

  assert.equal((await call('/', form({ request_code: DEVICE, days: '30', pin: 'wrong' }))).status, 401);
  const ok = await call('/', form({ request_code: '0123-4567-89ab-cdef', days: 'custom', custom_days: '45', customer_name: 'Anh Nam', pin: '12345678' }));
  assert.equal(ok.status, 200, ok.text.slice(0, 300));
  const code = /id="activationCode">([0-9-]+)</.exec(ok.text)[1];
  assert.match(ok.text, /45 ngày/);
  const v = await verify(c, code);
  assert.equal(v.status, 200);
  assert.equal(v.json.expires_at, c.clock.now + 45 * DAY);

  // A new code for the same car replaces the old one.
  const again = await call('/', form({ request_code: DEVICE, days: '0', pin: '12345678' }));
  assert.match(again.text, /đã được thay/);
  const s = await c.call('GET', '/api/status', { headers: { 'x-tht-request': DEVICE, 'x-tht-license': v.json.activation_code } });
  assert.equal(s.json.expires_at, 0, 'car switches to the new lifetime code');

  // Device list needs a session.
  assert.match((await call('/thiet-bi')).text, /Đăng nhập bằng PIN/);
  const login = await call('/thiet-bi', form({ pin: '12345678' }));
  assert.equal(login.status, 303);
  const cookie = login.headers.get('set-cookie').split(';')[0];
  const list = await call('/thiet-bi', { headers: { cookie } });
  assert.match(list.text, /0123-4567-89AB-CDEF/);
  assert.match(list.text, /Anh Nam|HOẠT ĐỘNG/);
  const blocked = await call('/thiet-bi/action', form({ action: 'block', request_code: DEVICE }, { cookie, origin: 'https://lic.example.dev' }));
  assert.equal(blocked.status, 303);
  assert.equal((await c.call('GET', '/api/status', { headers: { 'x-tht-request': DEVICE, 'x-tht-license': s.json.activation_code } })).json.code, 'device_blocked');
  assert.equal((await call('/thiet-bi/action', form({ action: 'unblock', request_code: DEVICE }, { cookie, origin: 'https://evil.example' }))).status, 403);
  assert.equal((await call('/thiet-bi/action', form({ action: 'unblock', request_code: DEVICE }))).status, 303, 'no session → back to login');
  assert.equal((await c.call('GET', '/api/status', { headers: { 'x-tht-request': DEVICE, 'x-tht-license': s.json.activation_code } })).json.code, 'device_blocked');
});

test('voice pack uploaded by hand to a GitHub Release (digest or SHA256.txt)', async () => {
  const env = await makeEnv({ GITHUB_REPO: 'owner/repo', GITHUB_TOKEN: 't' });
  const c = client(env);
  const r = await verify(c, await newCode(c));
  const headers = { 'x-tht-request': DEVICE, 'x-tht-license': r.json.activation_code };
  const shaA = 'a1'.repeat(32);
  const shaB = 'b2'.repeat(32);
  const releases = [
    { draft: false, assets: [{ id: 21, name: 'THTVoice-kokoro-vi-aaaa.zip', size: 5, digest: `sha256:${shaA}` }] },
    { draft: false, assets: [{ id: 31, name: 'THTVoice-kokoro-vi-bbbb.zip', size: 6 }, { id: 32, name: 'SHA256.txt', size: 90 }] },
  ];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (u) => {
    const url = String(u);
    if (url.endsWith('/releases?per_page=20')) return Response.json(releases);
    if (url.endsWith('/assets/32')) return new Response(`${shaB}  THTVoice-kokoro-vi-bbbb.zip\r\n`);
    if (url.endsWith('/assets/21')) return new Response(new Uint8Array(5));
    if (url.endsWith('/assets/31')) return new Response(new Uint8Array(6));
    return new Response('nope', { status: 404 });
  };
  try {
    const a = await c.call('GET', `/api/model?name=kokoro_vi&sha256=${shaA}`, { headers });
    assert.equal(a.status, 200, JSON.stringify(a.json));
    assert.equal(a.json.sizeBytes, 5);
    const b = await c.call('GET', `/api/model?name=kokoro_vi&sha256=${shaB}`, { headers });
    assert.equal(b.status, 200, JSON.stringify(b.json));
    const fb = await c.call('GET', `/api/model/file?name=kokoro_vi&sha256=${shaB}`, { headers });
    assert.equal(fb.headers.get('content-length'), '6');
  } finally {
    globalThis.fetch = realFetch;
  }
});

test('voice pack lookup reports a GitHub access failure instead of "no pack"', async () => {
  const env = await makeEnv({ GITHUB_REPO: 'owner/repo', GITHUB_TOKEN: 'bad' });
  const c = client(env);
  const r = await verify(c, await newCode(c));
  const headers = { 'x-tht-request': DEVICE, 'x-tht-license': r.json.activation_code };
  const realFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response('{"message":"Bad credentials"}', { status: 401 });
  try {
    const m = await c.call('GET', `/api/model?name=kokoro_vi&sha256=${'f'.repeat(64)}`, { headers });
    assert.equal(m.status, 502);
    assert.equal(m.json.code, 'github_unavailable');
    assert.match(m.json.message, /HTTP 401/);
  } finally {
    globalThis.fetch = realFetch;
  }
});

test('re-signed APKs are refused when RELEASE_CERT_SHA256 is set', async () => {
  const official = 'ab'.repeat(32);
  const env = await makeEnv({ RELEASE_CERT_SHA256: official.match(/../g).join(':').toUpperCase() });
  const c = client(env);
  const code = await newCode(c);
  const bad = await c.call('POST', '/api/verify', { body: { request_code: DEVICE, activation_code: code }, headers: { 'x-tht-cert': 'cd'.repeat(32) } });
  assert.equal(bad.status, 403);
  assert.equal(bad.json.code, 'tampered');
  // official certificate and builds that do not send the header still work
  const good = await c.call('POST', '/api/verify', { body: { request_code: DEVICE, activation_code: code }, headers: { 'x-tht-cert': official } });
  assert.equal(good.status, 200, JSON.stringify(good.json));
  assert.equal((await c.call('GET', '/api/health', { headers: { 'x-tht-cert': 'cd'.repeat(32) } })).status, 200);
  const noHeader = await c.call('POST', '/api/status', { body: {} });
  assert.notEqual(noHeader.status, 403);
});

test('voice pack is found by its own release tag, beyond the latest-20 window', async () => {
  const env = await makeEnv({ GITHUB_REPO: 'owner/repo', GITHUB_TOKEN: 't' });
  const c = client(env);
  const r = await verify(c, await newCode(c));
  const headers = { 'x-tht-request': DEVICE, 'x-tht-license': r.json.activation_code };
  const sha = '9'.repeat(64);
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (u) => {
    const url = String(u);
    if (url.endsWith(`/releases/tags/voice-kokoro_vi-${sha.slice(0, 12)}`)) {
      return Response.json({ assets: [{ id: 77, name: 'THTVoice-kokoro-vi-abc.zip', size: 5, digest: `sha256:${sha}` }] });
    }
    if (url.endsWith('/releases?per_page=20')) return Response.json([]);
    if (url.endsWith('/assets/77')) return new Response(new Uint8Array(5));
    return new Response('nope', { status: 404 });
  };
  try {
    const m = await c.call('GET', `/api/model?name=kokoro_vi&sha256=${sha}`, { headers });
    assert.equal(m.status, 200, JSON.stringify(m.json));
    assert.equal(m.json.sizeBytes, 5);
    const f = await c.call('GET', `/api/model/file?name=kokoro_vi&sha256=${sha}`, { headers });
    assert.equal(f.status, 200);
  } finally {
    globalThis.fetch = realFetch;
  }
});

test('VIN read statistics per device: ok, miss with reason, change', async () => {
  const env = await makeEnv();
  const c = client(env);
  const r = await verify(c, await newCode(c), DEVICE, { vin: VIN });
  assert.equal(r.status, 200);
  const base = { 'x-tht-request': DEVICE, 'x-tht-license': r.json.activation_code };
  await c.call('GET', '/api/status', { headers: { ...base, 'x-tht-vin': VIN } });
  await c.call('GET', '/api/status', { headers: { ...base, 'x-tht-vin-status': 'none:CAR_SERVICE_UNAVAILABLE' } });
  await c.call('GET', '/api/status', { headers: { ...base } }); // old app: no VIN, no status → not counted
  await c.call('GET', '/api/status', { headers: { ...base, 'x-tht-vin': OTHER_VIN } });
  const row = env.DB.raw.prepare('SELECT vin_ok_count, vin_miss_count, vin_change_count, vin_miss_reason, vin_last_ok FROM devices WHERE request_code = ?').get(DEVICE);
  assert.equal(row.vin_ok_count, 3);
  assert.equal(row.vin_miss_count, 1);
  assert.equal(row.vin_change_count, 1);
  assert.equal(row.vin_miss_reason, 'CAR_SERVICE_UNAVAILABLE');
  assert.ok(row.vin_last_ok > 0);
});

test('VIN statistics columns are added to a database created before them', async () => {
  const env = await makeEnv();
  const raw = env.DB.raw;
  raw.exec('DROP TABLE devices');
  raw.exec("CREATE TABLE devices (request_code TEXT PRIMARY KEY, status TEXT NOT NULL DEFAULT 'active', label TEXT, blocked_reason TEXT, vin_hash TEXT, vin_enc TEXT, vin_last4 TEXT, app_version TEXT, last_ip TEXT, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL)");
  const c = client(env);
  const r = await verify(c, await newCode(c), DEVICE, { vin: VIN });
  assert.equal(r.status, 200, JSON.stringify(r.json));
  assert.equal(raw.prepare('SELECT vin_ok_count FROM devices WHERE request_code = ?').get(DEVICE).vin_ok_count, 1);
});

test('sentence sent with the code: default, edited in admin and on the issuing page', async () => {
  const env = await makeEnv({ ADMIN_PASSWORD: '12345678' });
  const c = client(env);
  const { shareText, DEFAULT_COPY_FOOTER } = await import('../src/settings.js');
  const first = await c.call('GET', '/admin/api/settings', { headers: { authorization: 'Bearer 12345678' } });
  assert.equal(first.json.copy_footer, DEFAULT_COPY_FOOTER);
  assert.equal(first.json.default_copy_footer, DEFAULT_COPY_FOOTER);

  const saved = await c.admin('settings/copy-footer', { copy_footer: '  Cảm ơn anh ♥  ' });
  assert.equal(saved.json.result.copy_footer, 'Cảm ơn anh ♥');
  assert.equal((await c.call('GET', '/admin/api/settings', { headers: { authorization: 'Bearer 12345678' } })).json.copy_footer, 'Cảm ơn anh ♥');
  assert.equal(shareText(['1234-5678'], 'Cảm ơn anh ♥'), '1234-5678\n\nCảm ơn anh ♥');
  assert.equal(shareText(['1234-5678'], ''), '1234-5678');

  const { route } = await import('../src/index.js');
  const call = async (path, init = {}) => {
    const res = await route(new Request(`https://lic.example.dev${path}`, { ...init, headers: { 'cf-connecting-ip': '203.0.113.9', ...(init.headers || {}) } }), env, c.clock.now);
    return { status: res.status, text: await res.text() };
  };
  assert.match((await call('/')).text, /Cảm ơn anh ♥/);
  const ok = await call('/', { method: 'POST', body: new URLSearchParams({ request_code: '0123-4567-89ab-cdef', days: '30', pin: '12345678', copy_footer: 'Ủng hộ tác giả nhé' }) });
  assert.equal(ok.status, 200, ok.text.slice(0, 300));
  assert.match(ok.text, /id="copyFooter"[^>]*>Ủng hộ tác giả nhé</);
  assert.equal((await c.call('GET', '/admin/api/settings', { headers: { authorization: 'Bearer 12345678' } })).json.copy_footer, 'Ủng hộ tác giả nhé');

  const empty = await call('/', { method: 'POST', body: new URLSearchParams({ request_code: '0123-4567-89ab-cdef', days: '30', pin: '12345678', copy_footer: '' }) });
  assert.doesNotMatch(empty.text, /id="copyFooter"/);
});

test('expiring / expired lists, quick renew and renewal reminder template', async () => {
  const env = await makeEnv();
  const c = client(env);
  const soon = await newCode(c, { days: 5 });
  const later = await newCode(c, { days: 200 });
  const gone = await newCode(c, { days: 3 });
  await verify(c, gone, OTHER);
  c.clock.now += 4 * DAY;               // "gone" ended 1 day ago
  await verify(c, soon);                 // ends in 5 days
  const other = '1111222233334444';
  await verify(c, later, other);         // ends in 200 days

  const expiring = await c.admin('licenses?status=expiring');
  assert.deepEqual(expiring.json.items.map((l) => l.code_display), [soon]);
  const expired = await c.admin('licenses?status=expired');
  assert.deepEqual(expired.json.items.map((l) => l.code_display), [gone]);
  const ov = await c.admin('overview');
  assert.equal(ov.json.expiring_30d, 1);
  assert.equal(ov.json.expired_30d, 1);

  // "+30 ngày" on an expired key counts from today.
  await c.admin('licenses/update', { id: expired.json.items[0].id, action: 'renew_days', value: '30' });
  assert.equal((await c.admin('licenses?status=expired')).json.items.length, 0);

  const { DEFAULT_REMINDER } = await import('../src/settings.js');
  const s = await c.admin('settings');
  assert.equal(s.json.reminder_template, DEFAULT_REMINDER);
  assert.match(DEFAULT_REMINDER, /\{ma\}.*\{ngay\}.*\{con\}/);
  const saved = await c.admin('settings/reminder', { reminder_template: '  Key {ma} hết hạn {ngay}  ' });
  assert.equal(saved.json.result.reminder_template, 'Key {ma} hết hạn {ngay}');
  const cleared = await c.admin('settings/reminder', { reminder_template: '' });
  assert.equal(cleared.json.result.reminder_template, DEFAULT_REMINDER);
});

test('admin lists are paged: total, page, size 20/50/100', async () => {
  const env = await makeEnv();
  const c = client(env);
  const r = await c.admin('licenses/create', { count: 45, duration_mode: 'days', days: 30 });
  assert.equal(r.status, 200);
  const p1 = await c.admin('licenses?page=1');
  assert.equal(p1.json.total, 45);
  assert.equal(p1.json.size, 20);
  assert.equal(p1.json.items.length, 20);
  const p3 = await c.admin('licenses?page=3&size=20');
  assert.equal(p3.json.items.length, 5);
  assert.equal(p3.json.page, 3);
  const all = await c.admin('licenses?size=50');
  assert.equal(all.json.items.length, 45);
  assert.equal((await c.admin('licenses?size=7')).json.size, 20); // only 20/50/100
  const ids = new Set([...p1.json.items, ...(await c.admin('licenses?page=2')).json.items, ...p3.json.items].map((l) => l.id));
  assert.equal(ids.size, 45);
  const log = await c.admin('log?page=1&size=20');
  assert.ok(log.json.total >= 1 && Array.isArray(log.json.items));
  for (const path of ['devices', 'lockouts', 'attempts']) {
    const res = await c.admin(`${path}?page=1`);
    assert.equal(res.status, 200, path);
    assert.equal(typeof res.json.total, 'number', path);
  }
});

test('VIN: auto-bind on first check, record-only by default, block when chosen, VIP and missing VIN never blocked', async () => {
  const env = await makeEnv();
  const c = client(env);
  const code = await newCode(c);
  const act = await verify(c, code, DEVICE, { vin: VIN });
  assert.equal(act.status, 200);
  const lic = (await c.admin(`licenses?q=${code}`)).json.items[0];
  assert.equal(lic.vin, VIN);
  assert.equal(lic.vin_auto, 1);
  const st = (vin) => c.call('GET', '/api/status', { headers: { 'x-tht-request': DEVICE, 'x-tht-license': act.json.activation_code, ...(vin ? { 'x-tht-vin': vin } : {}) } });

  // Default "Chỉ ghi lại": the other car keeps working, the mismatch is counted.
  assert.equal((await st(OTHER_VIN)).status, 200);
  const dev = (await c.admin('devices?filter=vin')).json;
  assert.equal(dev.total, 1);
  assert.equal(dev.items[0].vin_mismatch_count, 1);
  assert.equal((await c.admin('overview')).json.vin_anomalies, 1);

  // "Chặn xe đó": wrong VIN → device_blocked (current apps stop); no VIN or the right VIN → fine.
  assert.equal((await c.admin('settings/vin-mode', { vin_mode: 'block' })).json.result.vin_mode, 'block');
  const blocked = await st(OTHER_VIN);
  assert.equal(blocked.status, 403);
  assert.equal(blocked.json.code, 'device_blocked');
  assert.equal(blocked.json.reason, 'vin_mismatch');
  assert.equal((await st(null)).status, 200);
  assert.equal((await st(VIN)).status, 200);
  assert.equal((await c.admin('settings')).json.vin_mode, 'block');
  assert.equal((await c.admin('settings/vin-mode', { vin_mode: 'nope' })).status, 400);

  // Back to record-only → the same car works again.
  await c.admin('settings/vin-mode', { vin_mode: 'record' });
  assert.equal((await st(OTHER_VIN)).status, 200);

  // VIP never checked; multi-device keys are not auto-bound.
  await c.admin('settings/vin-mode', { vin_mode: 'block' });
  const vip = await newCode(c, { license_type: 'vip' });
  const v = await verify(c, vip, OTHER, { vin: VIN });
  assert.equal(v.status, 200);
  assert.equal((await c.call('GET', '/api/status', { headers: { 'x-tht-request': OTHER, 'x-tht-license': v.json.activation_code, 'x-tht-vin': OTHER_VIN } })).status, 200);
  const multi = await newCode(c);
  await c.admin('licenses/update', { code: multi, action: 'set_max_devices', value: 2 });
  assert.equal((await verify(c, multi, '2222333344445555', { vin: VIN })).status, 200);
  assert.equal((await c.admin(`licenses?q=${multi}`)).json.items[0].vin, null);
});

test('backup and restore: download, merge into an empty server, replace, reject foreign files', async () => {
  const env = await makeEnv();
  const c = client(env);
  const code = await newCode(c, { customer: 'Anh A' });
  const act = await verify(c, code, DEVICE, { vin: VIN });
  assert.equal(act.status, 200);
  await c.admin('settings/copy-footer', { copy_footer: 'Cảm ơn ♥' });
  const b = await c.admin('backup');
  assert.equal(b.status, 200);
  const backup = b.json.backup;
  assert.equal(backup.format, 'tht-license-backup');
  assert.equal(backup.counts.licenses, 1);
  assert.equal(backup.counts.devices, 1);
  assert.ok(!JSON.stringify(backup).includes(VIN), 'VIN stays encrypted in the file');

  // A brand-new server with the same VIN_KEY gets everything back; the car keeps working.
  const env2 = await makeEnv({ VIN_KEY: env.VIN_KEY });
  const c2 = client(env2, c.clock);
  const r = await c2.admin('restore', { backup: JSON.parse(JSON.stringify(backup)), mode: 'merge' });
  assert.equal(r.status, 200, JSON.stringify(r.json));
  assert.equal(r.json.result.now_on_server.licenses, 1);
  const lic = (await c2.admin(`licenses?q=${code}`)).json.items[0];
  assert.equal(lic.customer, 'Anh A');
  assert.equal(lic.vin, VIN);
  assert.equal((await c2.admin('settings')).json.copy_footer, 'Cảm ơn ♥');
  const st = await c2.call('GET', '/api/status', { headers: { 'x-tht-request': DEVICE, 'x-tht-license': act.json.activation_code } });
  assert.equal(st.status, 200);
  // The restore itself is in the log, without the file contents.
  const log = (await c2.admin('log')).json.items.find((g) => g.action === 'restore');
  assert.ok(log && !String(log.detail).includes('tables'));

  // Merge twice adds nothing; replace drops what is not in the file.
  await c2.admin('restore', { backup, mode: 'merge' });
  assert.equal((await c2.admin('licenses')).json.total, 1);
  await newCode(c2);
  assert.equal((await c2.admin('licenses')).json.total, 2);
  const rep = await c2.admin('restore', { backup, mode: 'replace' });
  assert.equal(rep.json.result.now_on_server.licenses, 1);

  assert.equal((await c2.admin('restore', { backup: { hello: 1 }, mode: 'merge' })).status, 400);
  assert.equal((await c2.admin('restore', { backup, mode: 'wipe' })).status, 400);
  // A backup from the other product's server (different key id) is refused.
  assert.equal((await c2.admin('restore', { backup: { ...backup, key_id: 'zz' }, mode: 'merge' })).status, 400);
});

test('version table and customer notice (all cars, only older versions, expiry, disabled)', async () => {
  const { compareVersions } = await import('../src/notice.js');
  assert.equal(compareVersions('1.0.10', '1.0.9'), 1);
  assert.equal(compareVersions('1.0.5', '1.0.5-test'), 0);
  assert.equal(compareVersions('1.0.4', '1.0.6'), -1);

  const env = await makeEnv();
  const c = client(env);
  const a = await newCode(c);
  const b = await newCode(c);
  const ra = await verify(c, a, DEVICE, { app_version: '1.0.5' });
  const rb = await verify(c, b, OTHER, { app_version: '1.0.6' });
  assert.equal(ra.json.notice, undefined);

  // No GitHub here: the table still groups cars; the release comes from the cached copy.
  const { setJsonSetting } = await import('../src/settings.js');
  await setJsonSetting(env, c.clock.now, 'latest_release', { versionName: '1.0.6', versionCode: 7, changelog: 'Chia màn hình', forceUpdate: false, checkedAt: c.clock.now });
  const v = (await c.admin('versions')).json;
  assert.deepEqual(v.versions.map((r) => [r.version, r.cars]), [['1.0.6', 1], ['1.0.5', 1]]);
  assert.equal(v.release.versionName, '1.0.6');
  assert.equal(v.total, 2);
  assert.equal(v.up_to_date, 1);
  assert.equal((await c.admin('devices?version=1.0.5')).json.items[0].request_code, DEVICE);

  const st = (dev, code) => c.call('GET', '/api/status', { headers: { 'x-tht-request': dev, 'x-tht-license': code, 'x-tht-app-version': dev === DEVICE ? '1.0.5' : '1.0.6' } });
  const saved = await c.admin('notice', { text: 'Bản 1.0.6 đã có', enabled: true, below_version: '1.0.6', until: 0 });
  assert.equal(saved.status, 200);
  const id = saved.json.result.notice.id;
  assert.deepEqual((await st(DEVICE, ra.json.activation_code)).json.notice, { id, text: 'Bản 1.0.6 đã có', until: 0 });
  assert.equal((await st(OTHER, rb.json.activation_code)).json.notice, undefined, 'already on 1.0.6');

  // Same text keeps the id (shown once); new text → new id; all cars when no version is set.
  c.clock.now += 1000;
  assert.equal((await c.admin('notice', { text: 'Bản 1.0.6 đã có', enabled: true, below_version: '1.0.6' })).json.result.notice.id, id);
  const all = (await c.admin('notice', { text: 'Bảo trì máy chủ tối nay', enabled: true })).json.result.notice;
  assert.notEqual(all.id, id);
  assert.equal((await st(OTHER, rb.json.activation_code)).json.notice.text, 'Bảo trì máy chủ tối nay');

  // Expired or switched off → nothing.
  await c.admin('notice', { text: 'Hết hạn', enabled: true, until: c.clock.now + 1000 });
  c.clock.now += 2000;
  assert.equal((await st(OTHER, rb.json.activation_code)).json.notice, undefined);
  await c.admin('notice', { text: 'Tắt', enabled: false });
  assert.equal((await st(OTHER, rb.json.activation_code)).json.notice, undefined);
  assert.equal((await c.admin('notice', { text: 'x', enabled: true, below_version: 'abc' })).status, 400);

  // Delivery and "Đã xem" tracking for the admin.
  const sent = (await c.admin('notice', { text: 'Theo dõi', enabled: true })).json.result.notice;
  await st(OTHER, rb.json.activation_code);
  let stats = (await c.admin('notice')).json.stats;
  assert.equal(stats.delivered, 1);
  assert.equal(stats.seen, 0);
  await c.call('GET', '/api/status', { headers: { 'x-tht-request': OTHER, 'x-tht-license': rb.json.activation_code, 'x-tht-notice-seen': sent.id } });
  stats = (await c.admin('notice')).json.stats;
  assert.equal(stats.seen, 1);
  assert.equal(stats.recipients[0].request_code, OTHER);

  // "Chỉ một thiết bị (để thử)": only that car gets it, whatever its version.
  const one = (await c.admin('notice', { text: 'Thử thông báo', enabled: true, request_code: OTHER.toLowerCase() })).json.result.notice;
  assert.equal(one.request_code, OTHER);
  assert.equal((await st(OTHER, rb.json.activation_code)).json.notice.text, 'Thử thông báo');
  assert.equal((await st(DEVICE, ra.json.activation_code)).json.notice, undefined);
  assert.equal((await c.admin('notice', { text: 'x', enabled: true, request_code: 'xyz' })).status, 400);

  // Devices list carries the customer name for the "Khách / VIN / app" column.
  await c.admin('licenses/update', { code: b, action: 'set_note', value: 'xe trắng', customer: 'Anh B' });
  const d = (await c.admin(`devices?q=${OTHER}`)).json.items[0];
  assert.equal(d.licenses[0].customer, 'Anh B');
});
