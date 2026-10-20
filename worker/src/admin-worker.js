// Separate management site (e.g. https://keythtvoice.<account>.workers.dev): only the admin page
// and its API, on the same D1 database as the license server. The license server can then turn
// its own /admin off (ADMIN_UI = "off" in wrangler.toml).
import { handleAdminApi } from './admin.js';
import { ADMIN_HTML, ADMIN_JS } from './admin-page.js';
import { json } from './license.js';
import { PAGE_HEADERS } from './index.js';

export async function routeAdmin(request, env, now = Date.now()) {
  const url = new URL(request.url);
  const { pathname } = url;
  if (pathname === '/' || pathname === '/admin' || pathname === '/admin/') {
    return new Response(ADMIN_HTML, { headers: { 'content-type': 'text/html; charset=utf-8', ...PAGE_HEADERS } });
  }
  if (pathname === '/admin/app.js') {
    return new Response(ADMIN_JS, { headers: { 'content-type': 'text/javascript; charset=utf-8', ...PAGE_HEADERS } });
  }
  if (pathname.startsWith('/admin/api/')) return handleAdminApi(request, env, url, now);
  return json(404, { ok: false, code: 'not_found', message: 'Không có đường dẫn này.' });
}

export default {
  async fetch(request, env) {
    try {
      return await routeAdmin(request, env);
    } catch (e) {
      console.error('unhandled', e && e.stack ? e.stack : e);
      return json(500, { ok: false, code: 'server_error', message: 'Lỗi máy chủ, thử lại sau.' });
    }
  },
};
