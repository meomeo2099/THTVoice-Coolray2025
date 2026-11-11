// THT Voice license server — Cloudflare Worker entry.
import { handleAdminApi } from './admin.js';
import { ADMIN_HTML, ADMIN_JS } from './admin-page.js';
import { handleRestore, handleStatus, handleUnlock, handleVerify, json } from './license.js';
import { fetchReleaseInfo, handleModel, handleModelFile, handleMp3Voice, handleUpdate, handleUpdateApk } from './update.js';
import { latestReleaseInfo } from './notice.js';
import { handleLite } from './lite-page.js';

export const SERVER_VERSION = '2.0.0';

export const PAGE_HEADERS = {
  'content-security-policy': "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'no-referrer',
  'cache-control': 'no-store',
};

/**
 * Official APK certificate(s): RELEASE_CERT_SHA256 = "<sha256>[,<sha256>…]" (colons allowed).
 * Release builds send X-THT-Cert; a value outside this list means a repacked, re-signed copy.
 * Not set, or no header (older/test builds) → no check.
 */
export function certRejected(request, env) {
  const allowed = String(env.RELEASE_CERT_SHA256 || '')
    .split(',')
    .map((v) => v.toLowerCase().replace(/[^0-9a-f]/g, ''))
    .filter((v) => v.length === 64);
  if (!allowed.length) return false;
  const sent = request.headers.get('x-tht-cert');
  if (sent === null) return false;
  return !allowed.includes(sent.toLowerCase().replace(/[^0-9a-f]/g, ''));
}

export async function route(request, env, now = Date.now()) {
  const url = new URL(request.url);
  const { pathname } = url;
  const method = request.method;

  if (pathname.startsWith('/api/') && pathname !== '/api/health' && certRejected(request, env)) {
    console.warn('cert_rejected', pathname);
    return json(403, {
      ok: false,
      code: 'tampered',
      message: 'Bản THT Voice này không phải bản chính thức. Hãy cài bản chính thức từ người bán.',
    });
  }

  if (pathname === '/api/verify' && method === 'POST') return handleVerify(request, env, now);
  if (pathname === '/api/status' && method === 'GET') return handleStatus(request, env, now);
  if (pathname === '/api/restore' && method === 'POST') return handleRestore(request, env, now);
  if (pathname === '/api/unlock' && method === 'POST') return handleUnlock(request, env, now);
  if (pathname === '/api/health') return json(200, { ok: true, server: 'tht-voice-license', version: SERVER_VERSION, server_time: now });
  if (pathname === '/api/update' && method === 'GET') return handleUpdate(request, env, url, now);
  if (pathname === '/api/update/apk' && method === 'GET') return handleUpdateApk(request, env, url, now);
  if (pathname === '/api/model' && method === 'GET') return handleModel(request, env, url, now);
  if (pathname === '/api/model/file' && method === 'GET') return handleModelFile(request, env, url, now);
  const mp3 = /^\/api\/voice\/(ngoc-huyen|diem-trinh)(\/file)?$/.exec(pathname);
  if (mp3 && method === 'GET') return handleMp3Voice(request, env, url, now, mp3[1], Boolean(mp3[2]));
  if (pathname === '/api/stt' || pathname === '/api/llm') {
    return json(410, { ok: false, code: 'gone', message: 'THT Voice chạy offline; máy chủ không còn dịch vụ này.' });
  }

  const adminUi = String(env.ADMIN_UI || 'on').toLowerCase() !== 'off';
  if (!adminUi && pathname.startsWith('/admin')) return json(404, { ok: false, code: 'not_found', message: 'Không có đường dẫn này.' });
  if (pathname === '/admin' || pathname === '/admin/') {
    return new Response(ADMIN_HTML, { headers: { 'content-type': 'text/html; charset=utf-8', ...PAGE_HEADERS } });
  }
  if (pathname === '/admin/app.js') {
    return new Response(ADMIN_JS, { headers: { 'content-type': 'text/javascript; charset=utf-8', ...PAGE_HEADERS } });
  }
  if (pathname.startsWith('/admin/api/')) return handleAdminApi(request, env, url, now);

  if (adminUi) {
    const lite = await handleLite(request, env, url, now);
    if (lite) return lite;
  }
  if (pathname === '/') return new Response('THT Voice license server', { headers: { 'content-type': 'text/plain; charset=utf-8' } });
  return json(404, { ok: false, code: 'not_found', message: 'Không có đường dẫn này.' });
}

export default {
  async fetch(request, env) {
    try {
      return await route(request, env);
    } catch (e) {
      console.error('unhandled', e && e.stack ? e.stack : e);
      return json(500, { ok: false, code: 'server_error', message: 'Lỗi máy chủ, thử lại sau.' });
    }
  },
  // Daily clean-up of old attempt rows and spent unlock codes (cron in wrangler.toml).
  async scheduled(_event, env) {
    const now = Date.now();
    await env.DB.prepare('DELETE FROM attempts WHERE at < ?').bind(now - 90 * 24 * 3600 * 1000).run();
    await env.DB.prepare('DELETE FROM unlock_codes WHERE expires_at < ?').bind(now - 7 * 24 * 3600 * 1000).run();
    await env.DB.prepare('DELETE FROM lockouts WHERE locked_until < ? AND last_fail_at < ?').bind(now, now - 24 * 3600 * 1000).run();
    // Keep the latest release info fresh for the separate admin worker's version table.
    if (env.GITHUB_REPO) await latestReleaseInfo(env, now, fetchReleaseInfo);
  },
};
