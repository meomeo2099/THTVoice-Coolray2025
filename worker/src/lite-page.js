// Basic issuing page at "/" and a simple device list at "/thiet-bi" (server-rendered forms, like
// the previous dashboard). The full admin page stays at /admin.
import { createLicenses, deviceAction, listDevices, log, MIN_PASSWORD } from './admin.js';
import { formatCode, normalizeRequestCode } from './codes.js';
import { b64urlEncode, safeEqual } from './crypto.js';
import { clearKeys, HOUR, lockedFor, recordFailure } from './lockout.js';
import { clientIp } from './license.js';
import { cleanCopyFooter, getCopyFooter, MAX_COPY_FOOTER, setCopyFooter } from './settings.js';

const DAYS_OPTIONS = ['7', '30', '90', '180', '365', '0'];
const SESSION_MS = 4 * HOUR;
const COOKIE = 'tht_admin';
const enc = new TextEncoder();

const HEADERS = {
  'content-security-policy': "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'no-referrer',
  'cache-control': 'no-store',
};

const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const clean = (v, max) => String(v ?? '').replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, max);
const html = (body, status = 200, extra = {}) => new Response(body, { status, headers: { 'content-type': 'text/html; charset=utf-8', ...HEADERS, ...extra } });
const redirect = (location, extra = {}) => new Response(null, { status: 303, headers: { location, ...HEADERS, ...extra } });

function fmtDate(ms) {
  if (ms === 0) return 'Vĩnh viễn';
  if (ms == null) return 'Chưa kích hoạt';
  return new Date(ms).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}

function fmtLast(ms) {
  if (!ms) return '—';
  return new Date(ms).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}

// ---- auth -------------------------------------------------------------------------------------

async function checkPin(request, env, now, pin) {
  const key = `admin:${clientIp(request)}`;
  const locked = await lockedFor(env.DB, [key], now);
  if (locked) return `Sai quá nhiều lần, thử lại sau ${Math.ceil(locked / 60000)} phút.`;
  const password = String(env.ADMIN_PASSWORD || '');
  if (password.length < MIN_PASSWORD) return `Máy chủ chưa đặt ADMIN_PASSWORD (tối thiểu ${MIN_PASSWORD} ký tự).`;
  if (!(await safeEqual(String(pin || ''), password))) {
    await recordFailure(env.DB, [key], now);
    return 'Mã PIN không đúng.';
  }
  await clearKeys(env.DB, [key]);
  return null;
}

async function sessionMac(env, value) {
  const key = await crypto.subtle.importKey('raw', enc.encode(`tht-lite-session|${env.ADMIN_PASSWORD}`), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return b64urlEncode(new Uint8Array(await crypto.subtle.sign('HMAC', key, enc.encode(value))));
}

async function makeCookie(env, now) {
  const exp = String(now + SESSION_MS);
  return `${COOKIE}=${exp}.${await sessionMac(env, exp)}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${SESSION_MS / 1000}`;
}

async function hasSession(request, env, now) {
  if (String(env.ADMIN_PASSWORD || '').length < MIN_PASSWORD) return false;
  const cookie = (request.headers.get('cookie') || '').split(/;\s*/).find((c) => c.startsWith(`${COOKIE}=`));
  if (!cookie) return false;
  const [exp, mac] = cookie.slice(COOKIE.length + 1).split('.');
  if (!exp || !mac || !(Number(exp) > now)) return false;
  return safeEqual(mac, await sessionMac(env, exp));
}

function sameOrigin(request, url) {
  const origin = request.headers.get('origin');
  return !origin || origin === url.origin;
}

// ---- pages ------------------------------------------------------------------------------------

const CSS = `:root{color-scheme:dark;--bg:#070a12;--panel:rgba(19,25,43,.94);--line:rgba(130,151,255,.25);--text:#f5f7ff;--muted:#9aa7c7;--cyan:#4de5ff;--green:#68f0ad;--red:#ff7d91;--amber:#ffc66d}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}body{min-height:100dvh;margin:0;padding:max(18px,env(safe-area-inset-top)) 14px 24px;color:var(--text);font-family:Inter,system-ui,-apple-system,"Segoe UI",sans-serif;background:radial-gradient(circle at 15% 15%,rgba(57,78,225,.3),transparent 34%),radial-gradient(circle at 85% 80%,rgba(22,197,224,.16),transparent 30%),var(--bg)}
a{color:var(--cyan);text-decoration:none;font-weight:700}.muted,.small{color:var(--muted)}.small{font-size:12px;margin-top:4px}
.shell{width:min(620px,100%);margin:0 auto}.brand small{color:var(--cyan);letter-spacing:.14em;text-transform:uppercase;font-weight:800}.brand h1{margin:7px 0 5px;font-size:clamp(26px,7vw,40px);line-height:1.1}.brand p{margin:0;color:var(--muted)}.links{display:flex;gap:14px;margin-top:12px}
.card{margin-top:18px;padding:clamp(16px,4.5vw,28px);border:1px solid var(--line);border-radius:22px;background:var(--panel);box-shadow:0 24px 80px rgba(0,0,0,.42)}
label{display:block;margin-bottom:7px;font-weight:750}.field+.field{margin-top:15px}
input,select,textarea,button{width:100%;border-radius:13px;font:inherit}input,select,textarea{padding:13px;color:var(--text);border:1px solid rgba(150,166,255,.28);outline:none;background:rgba(4,8,19,.72)}textarea{min-height:64px;resize:vertical}
.mono,#request_code{font-family:Consolas,"SFMono-Regular",monospace}#request_code{font-size:clamp(15px,4.7vw,21px);text-align:center;letter-spacing:.04em}
button{margin-top:18px;padding:14px;border:0;color:#061019;font-weight:850;cursor:pointer;background:linear-gradient(135deg,var(--cyan),#7d8cff)}.secondary{margin-top:10px;color:var(--text);background:rgba(255,255,255,.08);border:1px solid var(--line)}
.msg{margin-top:16px;padding:13px;border-radius:14px}.error{color:#ffdce2;border:1px solid rgba(255,125,145,.35);background:rgba(255,125,145,.1)}.info{color:var(--muted);border:1px solid var(--line);background:rgba(255,255,255,.04)}
.result{margin-top:16px;padding:14px;border-radius:15px;border:1px solid rgba(104,240,173,.35);background:rgba(104,240,173,.08)}.result b{color:var(--green)}.code{margin:12px 0 4px;padding:12px 6px;border-radius:12px;background:rgba(0,0,0,.22);font-family:Consolas,monospace;font-size:clamp(14px,4.4vw,24px);font-weight:750;text-align:center;letter-spacing:.01em;white-space:nowrap;overflow:hidden}
.hint{margin-top:6px;color:var(--muted);font-size:13px}.status{margin-top:14px;text-align:center;color:var(--muted);font-size:12.5px}.dot{display:inline-block;width:8px;height:8px;margin-right:6px;border-radius:99px;background:var(--green)}
.admin{width:min(1180px,100%);margin:0 auto}.top{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap}.top h1{margin:0}.top a{display:inline-block;padding:11px 14px;border-radius:12px;color:var(--text);background:rgba(255,255,255,.08)}
.stats{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0}.stat{padding:10px 13px;border:1px solid var(--line);border-radius:13px;background:var(--panel)}
.toolbar{display:flex;gap:8px;margin-bottom:12px}.toolbar input{flex:1}.toolbar button{width:auto;margin:0;padding:12px 18px}
.tablewrap{overflow:auto;border:1px solid var(--line);border-radius:16px;background:var(--panel)}table{width:100%;min-width:900px;border-collapse:collapse}th,td{padding:12px;border-bottom:1px solid rgba(130,151,255,.13);text-align:left;vertical-align:top}th{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.08em}
.req{font-family:Consolas,monospace;font-weight:800}.edit{display:grid;grid-template-columns:1fr 1fr auto;gap:6px}.edit input{padding:8px;border-radius:9px}.mini{width:auto;margin:0;padding:8px 10px;border-radius:9px;font-size:13px}.actions{display:flex;gap:7px;flex-wrap:wrap}.actions form{margin:0}
.danger{background:#ff7d91;color:#22030a}.delete{background:#b61f36;color:#fff}.ok{background:#68f0ad;color:#052014}.pill{display:inline-block;padding:5px 8px;border-radius:999px;font-size:11px;font-weight:900}.p-active{color:var(--green);background:rgba(104,240,173,.1)}.p-blocked{color:#ff8c9d;background:rgba(255,125,145,.1)}.p-expired,.p-none{color:var(--amber);background:rgba(255,198,109,.1)}.empty{text-align:center;padding:30px}
.login{width:min(440px,100%);margin:8vh auto 0}
@media(max-width:640px){.edit{grid-template-columns:1fr}}`;

function page(title, body) {
  return `<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="theme-color" content="#111936"><title>${esc(title)}</title><style>${CSS}</style></head><body>${body}<script src="/lite.js"></script></body></html>`;
}

function renderCreate({ v = {}, error = '', result = null, footer = '' }) {
  const shownFooter = v.copy_footer ?? footer;
  const days = v.days ?? '30';
  const isCustom = days === 'custom';
  const options = DAYS_OPTIONS.map((d) => `<option value="${d}"${!isCustom && d === days ? ' selected' : ''}>${d === '0' ? 'Vĩnh viễn' : `${d} ngày`}</option>`).join('')
    + `<option value="custom"${isCustom ? ' selected' : ''}>Tùy chỉnh số ngày…</option>`;
  const res = result ? `<div class="result"><b>Tạo mã thành công</b><div class="code" id="activationCode">${esc(result.code)}</div>
    <div class="hint">Thiết bị: <span class="mono">${esc(result.device)}</span> · Hạn: ${esc(result.term)}</div>
    <div class="hint">Khách nhập mã này trên xe, hoặc chỉ cần bấm <b style="color:inherit">Khôi phục bản quyền</b>.${result.replaced ? ` Mã cũ ${esc(result.replaced)} đã được thay.` : ''}</div>
    ${shownFooter ? `<div class="hint" id="copyFooter" style="white-space:pre-wrap;margin-top:10px">${esc(shownFooter)}</div>` : ''}
    <button class="secondary" type="button" id="copyButton">SAO CHÉP MÃ + LỜI NHẮN</button></div>` : '';
  return page('THT Voice – Tạo mã kích hoạt', `<main class="shell"><header class="brand"><small>THT Voice · License</small><h1>Tạo mã kích hoạt</h1>
<p>Tạo mã theo thiết bị. Ghi tên khách để quản lý trong danh sách.</p><div class="links"><a href="/thiet-bi">Quản lý thiết bị →</a><a href="/admin">Quản trị đầy đủ →</a></div></header>
<section class="card"><form method="post" action="/" autocomplete="off">
<div class="field"><label for="request_code">Mã yêu cầu trên xe</label><input id="request_code" name="request_code" value="${esc(v.request_code)}" maxlength="19" autocapitalize="characters" spellcheck="false" placeholder="XXXX-XXXX-XXXX-XXXX" required autofocus></div>
<div class="field"><label for="customer_name">Tên khách / tên xe (tùy chọn)</label><input id="customer_name" name="customer_name" maxlength="80" value="${esc(v.customer_name)}" placeholder="Ví dụ: Anh Nam - xe trắng"></div>
<div class="field"><label for="note">Ghi chú (tùy chọn)</label><textarea id="note" name="note" maxlength="180" placeholder="SĐT, biển số hoặc ghi chú nội bộ">${esc(v.note)}</textarea></div>
<div class="field"><label for="days">Thời hạn sử dụng</label><select id="days" name="days">${options}</select><div class="hint">Tính từ lúc xe kích hoạt.</div></div>
<div class="field" id="custom_days_field"${isCustom ? '' : ' hidden'}><label for="custom_days">Số ngày tùy chỉnh</label><input id="custom_days" name="custom_days" type="number" inputmode="numeric" min="1" max="3650" value="${esc(v.custom_days)}" placeholder="Ví dụ: 45"><div class="hint">Từ 1 đến 3650 ngày.</div></div>
<div class="field"><label for="license_type">Loại mã</label><select id="license_type" name="license_type"><option value="standard">Thường</option><option value="vip"${v.license_type === 'vip' ? ' selected' : ''}>VIP (không cần VIN)</option></select></div>
<div class="field"><label for="copy_footer">Lời nhắn gửi kèm khi sao chép (hiện dưới mã)</label><textarea id="copy_footer" name="copy_footer" maxlength="${MAX_COPY_FOOTER}" placeholder="Để trống nếu không cần">${esc(shownFooter)}</textarea><div class="hint">Sửa ở đây là lưu luôn cho các lần tạo mã sau.</div></div>
<div class="field"><label for="pin">Mã PIN quản trị</label><input id="pin" name="pin" type="password" autocomplete="current-password" placeholder="Nhập PIN quản trị" required></div>
<button type="submit">TẠO MÃ KÍCH HOẠT</button></form>
${error ? `<div class="msg error">${esc(error)}</div>` : ''}${res}</section>
<p class="status"><span class="dot"></span>License theo thiết bị · có chữ ký máy chủ · khóa khi nhập sai</p></main>`);
}

function renderLogin(error) {
  return page('THT Voice – Quản lý thiết bị', `<main class="shell login"><form class="card" method="post" action="/thiet-bi"><h1 style="margin:0 0 8px">Quản lý thiết bị</h1>
<div class="muted">Đăng nhập bằng PIN quản trị.</div><div class="field" style="margin-top:14px"><input name="pin" type="password" placeholder="PIN quản trị" required autofocus></div>
<button>ĐĂNG NHẬP</button>${error ? `<div class="msg error">${esc(error)}</div>` : ''}<p style="margin:14px 0 0"><a href="/">← Tạo mã</a></p></form></main>`);
}

function deviceState(d, now) {
  const lic = d.licenses.find((l) => l.status === 'active') || d.licenses[0];
  if (d.status === 'blocked') return { key: 'blocked', label: 'KHÓA', lic };
  if (!lic) return { key: 'none', label: 'CHƯA CÓ MÃ', lic };
  if (lic.expires_at > 0 && lic.expires_at < now) return { key: 'expired', label: 'HẾT HẠN', lic };
  return { key: 'active', label: 'HOẠT ĐỘNG', lic };
}

function actionForm(rc, action, label, cls, confirm) {
  return `<form method="post" action="/thiet-bi/action"${confirm ? ` data-confirm="${esc(confirm)}"` : ''}><input type="hidden" name="action" value="${action}"><input type="hidden" name="request_code" value="${esc(rc)}"><button class="mini ${cls}">${label}</button></form>`;
}

function pagerLinks(base, q, page, size, total) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1) return '';
  const link = (p, label) => `<a href="${base}?${new URLSearchParams({ ...(q ? { q } : {}), page: String(p), size: String(size) })}">${label}</a>`;
  return `<div class="toolbar" style="justify-content:center;gap:12px">${page > 1 ? link(page - 1, '‹ TRƯỚC') : ''}<span class="small">Trang ${page}/${pages} · ${total} thiết bị</span>${page < pages ? link(page + 1, 'SAU ›') : ''}</div>`;
}

function renderDevices(result, q, flash, now) {
  const devices = result.items;
  const states = devices.map((d) => ({ d, s: deviceState(d, now) }));
  const count = (k) => states.filter((x) => x.s.key === k).length;
  const rows = states.map(({ d, s }) => {
    const lic = s.lic;
    const rc = d.request_code;
    const term = lic ? (lic.expires_at == null ? (lic.duration_mode === 'lifetime' ? 'Vĩnh viễn (chưa kích hoạt)' : `${lic.duration_days || '?'} ngày (chưa kích hoạt)`) : fmtDate(lic.expires_at)) : '—';
    return `<tr><td><div class="req">${esc(formatCode(rc))}</div><div class="small mono">${esc(lic?.code || '')}${lic?.license_type === 'vip' ? ' · VIP' : ''}</div>${d.vin ? `<div class="small mono">VIN ${esc(d.vin)}</div>` : ''}</td>
<td><form method="post" action="/thiet-bi/action" class="edit"><input type="hidden" name="action" value="edit"><input type="hidden" name="request_code" value="${esc(rc)}"><input name="customer_name" maxlength="80" value="${esc(lic?.customer || d.label || '')}" placeholder="Tên khách"><input name="note" maxlength="180" value="${esc(lic?.note || '')}" placeholder="Ghi chú"><button class="mini secondary">LƯU</button></form></td>
<td><span class="pill p-${s.key}">${s.label}</span><div class="small">Hạn: ${esc(term)}</div><div class="small">Online: ${esc(fmtLast(d.last_seen))}</div>${d.lock?.locked_until > now ? '<div class="small" style="color:var(--red)">Đang khóa do nhập sai</div>' : ''}</td>
<td><div class="actions">${d.status === 'blocked' ? actionForm(rc, 'unblock', 'MỞ KHÓA', 'ok') : actionForm(rc, 'block', 'KHÓA', 'danger', 'Khóa thiết bị này? Xe bị chặn ở lần kiểm tra máy chủ kế tiếp.')}
${lic ? actionForm(rc, 'reissue', 'CẤP LẠI', 'secondary', 'Cấp mã mới cho xe này (giữ nguyên hạn), thu hồi mã cũ?') : ''}
${d.lock?.fail_count ? actionForm(rc, 'unlock', 'XÓA LẦN SAI', 'secondary') : ''}
${actionForm(rc, 'delete', 'XÓA', 'delete', 'Xóa thiết bị này? Bản quyền bị gỡ khỏi xe khi xe online lại.')}</div></td></tr>`;
  }).join('') || '<tr><td colspan="4" class="empty">Chưa có thiết bị.</td></tr>';
  return page('THT Voice – Thiết bị', `<main class="admin"><div class="top"><div><h1>Thiết bị THT Voice</h1><div class="muted">Khóa, xóa, cấp lại có hiệu lực khi xe online lại.</div></div>
<div><a href="/">+ TẠO MÃ</a> <a href="/admin">QUẢN TRỊ ĐẦY ĐỦ</a> <a href="/thiet-bi/logout">ĐĂNG XUẤT</a></div></div>
${flash ? `<div class="msg info">${esc(flash)}</div>` : ''}
<div class="stats"><div class="stat">Tổng: <b>${result.total}</b></div><div class="stat">Trang này – Hoạt động: <b>${count('active')}</b></div><div class="stat">Khóa: <b>${count('blocked')}</b></div><div class="stat">Hết hạn: <b>${count('expired')}</b></div><div class="stat">Chưa có mã: <b>${count('none')}</b></div></div>
<form class="toolbar" method="get" action="/thiet-bi"><input name="q" value="${esc(q)}" placeholder="Tìm mã thiết bị, tên khách, ghi chú, VIN"><button>TÌM</button></form>
<div class="tablewrap"><table><thead><tr><th>Thiết bị / mã</th><th>Khách / ghi chú</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>${rows}</tbody></table></div>
${pagerLinks('/thiet-bi', q, result.page, result.size, result.total)}</main>`);
}

export const LITE_JS = `(function(){
var i=document.getElementById('request_code');
if(i)i.addEventListener('input',function(){var c=i.value.toUpperCase().replace(/[^0-9A-F]/g,'').slice(0,16);var m=c.match(/.{1,4}/g);i.value=m?m.join('-'):'';});
var ds=document.getElementById('days'),f=document.getElementById('custom_days_field'),cd=document.getElementById('custom_days');
function sync(){if(!ds)return;var on=ds.value==='custom';f.hidden=!on;cd.required=on;}
if(ds){ds.addEventListener('change',function(){sync();if(ds.value==='custom')cd.focus();});sync();}
var b=document.getElementById('copyButton');
if(b)b.addEventListener('click',function(){var c=document.getElementById('activationCode').textContent.trim();
  var ft=document.getElementById('copyFooter');if(ft&&ft.textContent.trim())c=c+'\\n\\n'+ft.textContent.trim();
  if(navigator.clipboard)navigator.clipboard.writeText(c).then(function(){b.textContent='ĐÃ SAO CHÉP';},function(){window.prompt('Sao chép mã này:',c);});else window.prompt('Sao chép mã này:',c);});
document.querySelectorAll('form[data-confirm]').forEach(function(fm){fm.addEventListener('submit',function(e){if(!window.confirm(fm.getAttribute('data-confirm')))e.preventDefault();});});
})();`;

// ---- handlers ---------------------------------------------------------------------------------

async function handleCreate(request, env, now) {
  const form = await request.formData();
  const v = {
    request_code: clean(form.get('request_code'), 19),
    customer_name: clean(form.get('customer_name'), 80),
    note: clean(form.get('note'), 180),
    days: clean(form.get('days'), 8) || '30',
    custom_days: clean(form.get('custom_days'), 5),
    license_type: form.get('license_type') === 'vip' ? 'vip' : 'standard',
    copy_footer: cleanCopyFooter(form.get('copy_footer')),
  };
  const pinError = await checkPin(request, env, now, form.get('pin'));
  if (pinError) return html(renderCreate({ v, error: pinError }), 401);
  const rc = normalizeRequestCode(v.request_code);
  if (!rc) return html(renderCreate({ v, error: 'Mã yêu cầu phải gồm đúng 16 ký tự 0–9 hoặc A–F.' }), 400);
  const days = v.days === 'custom' ? Number(v.custom_days) : Number(v.days);
  if (!Number.isInteger(days) || days < 0 || days > 3650 || (v.days === 'custom' && days < 1)) {
    return html(renderCreate({ v, error: 'Thời hạn không hợp lệ. Số ngày tùy chỉnh từ 1 đến 3650.' }), 400);
  }

  // A new code for a device that already has one replaces it (the car switches at its next check).
  const old = await env.DB.prepare(
    'SELECT l.id, l.code FROM activations a JOIN licenses l ON l.id = a.license_id WHERE a.request_code = ? AND a.status = \'active\' AND l.status = \'active\''
  ).bind(rc).all();
  for (const o of old.results) {
    await env.DB.prepare('UPDATE licenses SET status = \'revoked\', updated_at = ? WHERE id = ?').bind(now, o.id).run();
    await env.DB.prepare('UPDATE activations SET status = \'released\', released_at = ?, released_reason = \'replaced\' WHERE license_id = ? AND status = \'active\'').bind(now, o.id).run();
  }
  // A device deleted earlier gets a clean start.
  await env.DB.prepare('UPDATE devices SET status = \'active\', blocked_reason = NULL WHERE request_code = ?').bind(rc).run();

  const input = {
    request_code: rc,
    license_type: v.license_type,
    customer: v.customer_name,
    note: v.note,
    ...(days === 0 ? { duration_mode: 'lifetime' } : { duration_mode: 'days', days }),
  };
  try {
    const created = await createLicenses(env, now, input);
    await log(env, now, request, 'lite:create', rc, { days, license_type: v.license_type, replaced: old.results.length });
    // PIN already checked: an edited sentence is kept for the next codes.
    if (v.copy_footer !== (await getCopyFooter(env))) await setCopyFooter(env, now, v.copy_footer);
    return html(renderCreate({
      v: { ...v, request_code: formatCode(rc) },
      result: {
        code: created.codes[0],
        device: formatCode(rc),
        term: days === 0 ? 'Vĩnh viễn' : `${days} ngày (tính từ lúc kích hoạt)`,
        replaced: old.results.map((o) => formatCode(o.code)).join(', '),
      },
    }));
  } catch (e) {
    return html(renderCreate({ v, error: e.message || 'Không tạo được mã.' }), 400);
  }
}

async function handleDeviceAction(request, env, url, now) {
  if (!sameOrigin(request, url)) return new Response('Forbidden', { status: 403 });
  if (!(await hasSession(request, env, now))) return redirect('/thiet-bi');
  const form = await request.formData();
  const action = String(form.get('action') || '');
  const rc = normalizeRequestCode(String(form.get('request_code') || ''));
  if (!rc) return redirect('/thiet-bi');
  let flash = '';
  try {
    if (action === 'edit') {
      const customer = clean(form.get('customer_name'), 80);
      const note = clean(form.get('note'), 180);
      await env.DB.prepare(
        'UPDATE licenses SET customer = ?, note = ?, updated_at = ? WHERE id IN (SELECT license_id FROM activations WHERE request_code = ? AND status = \'active\')'
      ).bind(customer || null, note || null, now, rc).run();
      await env.DB.prepare('UPDATE devices SET label = ? WHERE request_code = ?').bind(customer || null, rc).run();
      flash = `Đã lưu ${formatCode(rc)}.`;
    } else if (['block', 'unblock', 'delete', 'unlock', 'reissue'].includes(action)) {
      const r = await deviceAction(env, now, { request_code: rc, action });
      flash = action === 'reissue' ? `Mã mới cho ${formatCode(rc)}: ${r.code} (mã cũ ${r.old_code} đã thu hồi).`
        : action === 'delete' ? `Đã xóa ${formatCode(rc)}.`
          : action === 'unlock' ? `Đã xóa lần nhập sai của ${formatCode(rc)}.`
            : `Đã ${action === 'block' ? 'khóa' : 'mở khóa'} ${formatCode(rc)}.`;
    } else {
      return redirect('/thiet-bi');
    }
    await log(env, now, request, `lite:${action}`, rc, null);
  } catch (e) {
    flash = e.message || 'Lỗi';
  }
  return redirect(`/thiet-bi?msg=${encodeURIComponent(flash)}`);
}

/** Returns a Response for the lite pages, or null when the path is not one of them. */
export async function handleLite(request, env, url, now) {
  const { pathname } = url;
  const method = request.method;
  if (pathname === '/lite.js') return new Response(LITE_JS, { headers: { 'content-type': 'text/javascript; charset=utf-8', ...HEADERS } });
  if (pathname === '/') {
    if (method === 'GET') return html(renderCreate({ footer: await getCopyFooter(env) }));
    if (method === 'POST') return handleCreate(request, env, now);
    return new Response('Method Not Allowed', { status: 405 });
  }
  if (pathname === '/thiet-bi/logout') return redirect('/thiet-bi', { 'set-cookie': `${COOKIE}=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0` });
  if (pathname === '/thiet-bi/action' && method === 'POST') return handleDeviceAction(request, env, url, now);
  if (pathname === '/thiet-bi') {
    if (method === 'POST') {
      if (!sameOrigin(request, url)) return new Response('Forbidden', { status: 403 });
      const form = await request.formData();
      const error = await checkPin(request, env, now, form.get('pin'));
      if (error) return html(renderLogin(error), 401);
      return redirect('/thiet-bi', { 'set-cookie': await makeCookie(env, now) });
    }
    if (!(await hasSession(request, env, now))) return html(renderLogin(''));
    const devices = await listDevices(env, url, now);
    return html(renderDevices(devices, url.searchParams.get('q') || '', clean(url.searchParams.get('msg'), 300), now));
  }
  return null;
}

