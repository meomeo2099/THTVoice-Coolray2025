// Admin web page (served at /admin). Plain HTML + JS, no build step, no external resources.
// NOTE: ADMIN_JS must not contain backticks or dollar-brace sequences (it lives in a String.raw template).

export const ADMIN_HTML = String.raw`<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cấp mã THT Voice</title>
<style>
:root{color-scheme:dark;--bg:#060913;--bg2:#0b1122;--card:rgba(17,23,41,.82);--card2:rgba(24,32,58,.9);--line:rgba(130,151,255,.18);--line2:rgba(130,151,255,.32);--text:#f3f6ff;--muted:#8f9cbd;--accent:#4de5ff;--accent2:#7d8cff;--ok:#5ef0a8;--warn:#ffc66d;--bad:#ff7d91;--r:18px}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}
body{margin:0;min-height:100dvh;color:var(--text);font:14px/1.5 Inter,system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;background:radial-gradient(1200px 600px at 10% -10%,rgba(77,92,255,.28),transparent 60%),radial-gradient(900px 500px at 110% 110%,rgba(24,200,230,.16),transparent 60%),var(--bg);background-attachment:fixed}
a{color:var(--accent)}
.layout{display:grid;grid-template-columns:240px minmax(0,1fr);min-height:100dvh}.layout>*{min-width:0}
aside{position:sticky;top:0;height:100dvh;padding:22px 14px;border-right:1px solid var(--line);background:linear-gradient(180deg,rgba(11,17,34,.92),rgba(6,9,19,.92));backdrop-filter:blur(12px);display:flex;flex-direction:column;gap:18px}
.brand{display:flex;align-items:center;gap:10px;padding:0 6px}.brand svg{flex:none}.brand b{display:block;font-size:15px;letter-spacing:.02em}.brand small{display:block;color:var(--muted);font-size:11px;letter-spacing:.12em;text-transform:uppercase}
nav{display:flex;flex-direction:column;gap:4px}
nav button{display:flex;align-items:center;gap:10px;width:100%;text-align:left;background:transparent;border:1px solid transparent;color:var(--muted);border-radius:12px;padding:10px 12px;font-weight:650}
nav button svg{width:18px;height:18px;flex:none;opacity:.85}
nav button:hover{background:rgba(255,255,255,.04);color:var(--text)}
nav button.on{color:var(--text);background:linear-gradient(135deg,rgba(77,229,255,.16),rgba(125,140,255,.16));border-color:var(--line2);box-shadow:inset 3px 0 0 var(--accent)}
.side-foot{margin-top:auto;display:flex;flex-direction:column;gap:8px}.side-foot .muted{font-size:11px;padding:0 6px}
main{padding:26px 28px 40px;max-width:1240px;width:100%}
.page-head{display:flex;align-items:flex-end;justify-content:space-between;gap:12px;margin-bottom:18px;flex-wrap:wrap}.page-head h2{margin:0;font-size:24px;letter-spacing:-.01em}.page-head p{margin:4px 0 0;color:var(--muted)}
.card{background:var(--card);border:1px solid var(--line);border-radius:var(--r);padding:20px;margin-bottom:16px;box-shadow:0 18px 60px rgba(0,0,0,.35);backdrop-filter:blur(8px)}
h3{margin:0 0 12px;font-size:17px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:12px}
.stat{position:relative;overflow:hidden;margin:0!important;padding:16px 16px 14px!important;box-shadow:none!important;background:var(--card2)!important}
.stat:before{content:"";position:absolute;inset:0 auto 0 0;width:3px;background:var(--c,var(--accent))}
.stat span{color:var(--muted);font-size:12px}.stat b{display:block;font-size:26px;margin-top:4px;letter-spacing:-.02em}.stat b.txt{font-size:17px;margin-top:8px}
label{display:block;color:#cdd6f6;font-weight:650;font-size:12.5px;margin:14px 0 6px}
input,select,textarea{width:100%;background:rgba(3,7,17,.66);border:1px solid var(--line2);color:var(--text);border-radius:12px;padding:11px 12px;font:inherit;outline:none;transition:border-color .15s,box-shadow .15s}
input:focus,select:focus,textarea:focus{border-color:var(--accent);box-shadow:0 0 0 3px rgba(77,229,255,.14)}
button{background:linear-gradient(135deg,var(--accent),var(--accent2));border:0;color:#051019;border-radius:12px;padding:10px 16px;cursor:pointer;font:inherit;font-weight:800;transition:transform .08s,filter .15s}
button:hover{filter:brightness(1.08)}button:active{transform:translateY(1px)}
button.ghost{background:rgba(255,255,255,.06);border:1px solid var(--line2);color:var(--text)}
button.bad{background:linear-gradient(135deg,#ff6b81,#d1234a);color:#fff}button.small{padding:6px 11px;font-size:12px;border-radius:10px}
.row{display:flex;gap:12px;flex-wrap:wrap;align-items:end}.row>*{flex:1;min-width:150px}
.card:has(table){padding:6px 6px 2px;overflow-x:auto}
table{width:100%;border-collapse:separate;border-spacing:0}
td,th{padding:12px 12px;text-align:left;vertical-align:top;font-size:13px;border-bottom:1px solid rgba(130,151,255,.1)}
th{color:var(--muted);font-size:11px;text-transform:uppercase;letter-spacing:.09em;font-weight:700;position:sticky;top:0;background:rgba(17,23,41,.98)}
tbody tr{transition:background .12s}tbody tr:hover{background:rgba(125,140,255,.06)}tbody tr:last-child td{border-bottom:0}
.mono{font-family:"JetBrains Mono",Consolas,"SFMono-Regular",ui-monospace,monospace}
.pill{display:inline-block;padding:3px 10px;border-radius:999px;font-size:11px;font-weight:800;border:1px solid var(--line2);background:rgba(255,255,255,.05);letter-spacing:.02em}
.ok{color:var(--ok)}.warn{color:var(--warn)}.bad-t{color:var(--bad)}.muted{color:var(--muted)}.hide{display:none!important}
#msg{position:fixed;bottom:20px;right:20px;max-width:420px;padding:13px 16px;border-radius:14px;background:rgba(17,23,41,.98);border:1px solid var(--line2);border-left:4px solid var(--accent);display:none;white-space:pre-wrap;box-shadow:0 16px 50px rgba(0,0,0,.55);z-index:9}
.actions{white-space:normal}.actions button{margin:2px}
.pager{display:flex;flex-wrap:wrap;gap:8px;align-items:center;justify-content:center;padding:12px 6px}.pager:empty{display:none}.pager select{width:auto;padding:6px 8px}.pager button[disabled]{opacity:.35;cursor:default}
.chips{gap:8px!important}.chips>*{flex:0 0 auto!important;min-width:0!important}.chips button{border-radius:999px;padding:8px 14px}.chips button.on{border-color:transparent;color:#051019;background:linear-gradient(135deg,var(--accent),var(--accent2))}
details summary{cursor:pointer;margin-top:12px;color:var(--muted)}
#c_result{border-color:rgba(94,240,168,.35)!important;background:linear-gradient(180deg,rgba(94,240,168,.1),rgba(94,240,168,.03))!important}#c_res_code{color:var(--text)}
.login-wrap{min-height:100dvh;display:grid;place-items:center;padding:20px}.login-card{width:min(420px,100%);text-align:left}.login-card .brand{padding:0;margin-bottom:18px}
.empty{padding:28px;text-align:center;color:var(--muted)}
@media(max-width:860px){
 .layout{grid-template-columns:minmax(0,1fr)}
 aside{position:sticky;top:0;height:auto;z-index:5;padding:10px 12px;flex-direction:row;align-items:center;gap:10px;border-right:0;border-bottom:1px solid var(--line)}
 .brand small{display:none}.side-foot{margin:0}.side-foot .muted{display:none}
 aside{min-width:0;overflow:hidden}nav{flex-direction:row;overflow-x:auto;flex:1;min-width:0;scrollbar-width:none}.brand div{display:none}nav::-webkit-scrollbar{display:none}
 nav button{width:auto;white-space:nowrap;padding:8px 11px}nav button.on{box-shadow:none}
 main{padding:16px 12px 30px}.page-head h2{font-size:20px}
 .card{padding:14px;border-radius:16px}.card:has(table){padding:0;background:transparent;border:0;box-shadow:none}
 table,thead,tbody,tr,td{display:block;width:100%}thead{display:none}
 tbody tr{margin:0 0 10px;padding:6px 12px;border:1px solid var(--line);border-radius:14px;background:var(--card)}
 td{border:0;padding:7px 0}
 td:before{content:attr(data-label);display:block;color:var(--muted);font-size:10.5px;text-transform:uppercase;letter-spacing:.07em;margin-bottom:3px}
 td[colspan]{display:block}td[colspan]:before{content:none}
}
</style></head><body>
<div id="login" class="login-wrap"><section class="card login-card">
  <div class="brand"><svg width="40" height="40" viewBox="0 0 40 40" aria-hidden="true"><defs><linearGradient id="lg1" x1="0" y1="40" x2="40" y2="0"><stop offset="0" stop-color="#7d8cff"/><stop offset="1" stop-color="#4de5ff"/></linearGradient></defs><rect width="40" height="40" rx="11" fill="url(#lg1)"/><path d="M11 12h18v4h-7v13h-4V16h-7z" fill="#061019"/></svg><div><b>THT Voice</b><small>Quản lý bản quyền</small></div></div>
  <h3 style="font-size:20px">Đăng nhập</h3><p class="muted" style="margin:-6px 0 4px">Nhập PIN quản trị (ADMIN_PASSWORD).</p>
  <label>PIN quản trị</label><input id="pw" type="password" autocomplete="current-password" placeholder="••••••••">
  <p><button id="loginBtn" style="width:100%;padding:12px">Đăng nhập</button></p><p class="muted" style="font-size:12px;margin-bottom:0">Sai 5 lần sẽ khóa 15 phút theo IP. <a href="/">Trang tạo mã nhanh →</a></p>
</section></div>
<div id="app" class="layout hide">
<aside><div class="brand"><svg width="34" height="34" viewBox="0 0 40 40" aria-hidden="true"><defs><linearGradient id="lg2" x1="0" y1="40" x2="40" y2="0"><stop offset="0" stop-color="#7d8cff"/><stop offset="1" stop-color="#4de5ff"/></linearGradient></defs><rect width="40" height="40" rx="11" fill="url(#lg2)"/><path d="M11 12h18v4h-7v13h-4V16h-7z" fill="#061019"/></svg><div><b>THT Voice</b><small>Quản lý bản quyền</small></div></div>
<nav id="tabs"></nav>
<div class="side-foot"><button class="ghost small" id="logout">Đăng xuất</button><span class="muted">Máy chủ bản quyền · D1</span></div></aside>
<main><div class="page-head"><div><h2 id="pageTitle"></h2><p id="pageSub"></p></div></div>
<section data-tab="overview"><div class="card"><div class="grid" id="stats"></div></div>
<div class="card"><h3 style="margin-top:0">Phiên bản app trên xe (online trong 30 ngày)</h3><div id="rel" class="muted"></div>
  <table><thead><tr><th>Phiên bản</th><th>Số xe</th><th>Online gần nhất</th><th></th></tr></thead><tbody id="ver_rows"></tbody></table></div></section>
<section data-tab="create" class="hide"><div class="card" style="max-width:640px;margin:0 auto">
  <h3 style="margin-top:0">Cấp mã kích hoạt</h3>
  <label>Mã yêu cầu trên màn xe (mã thiết bị)</label><input id="c_rc" class="mono" placeholder="XXXX-XXXX-XXXX-XXXX" maxlength="19" autocomplete="off">
  <p class="muted" style="margin:4px 0 0;font-size:12px">Có mã yêu cầu: mã chỉ dùng cho đúng xe đó, khách nhập mã hoặc bấm Khôi phục bản quyền. Để trống: tạo mã rời để bán sau.</p>
  <div class="row"><div><label>Tên xe / khách hàng</label><input id="c_customer"></div><div><label>Ghi chú (tùy chọn)</label><input id="c_note"></div></div>
  <label>Thời hạn</label>
  <div class="row chips" id="c_dur" style="gap:6px">
    <button class="ghost small" data-dur="lifetime">Vĩnh viễn</button><button class="ghost small" data-dur="30">30 ngày</button><button class="ghost small" data-dur="90">90 ngày</button><button class="ghost small on" data-dur="365">365 ngày</button><button class="ghost small" data-dur="custom">Tùy chỉnh</button>
  </div>
  <div id="c_custom" class="row hide"><div><label>Số ngày (tính từ lúc kích hoạt)</label><input id="c_days" type="number" min="1" placeholder="vd 180"></div><div><label>hoặc đến ngày</label><input id="c_fixed" type="date"></div></div>
  <div class="row"><div><label>Loại</label><select id="c_type"><option value="standard">Thường</option><option value="vip">VIP (không cần VIN)</option></select></div>
  <div id="c_count_box"><label>Số lượng mã rời</label><input id="c_count" type="number" value="1" min="1" max="500"></div></div>
  <details style="margin-top:8px"><summary class="muted">Tùy chọn khác</summary><div class="row"><div><label>Số thiết bị tối đa</label><input id="c_max" type="number" value="1" min="1"></div><div><label>Chỉ cho xe có VIN</label><input id="c_vin" maxlength="17" class="mono"></div></div></details>
  <div class="row" style="margin-top:14px"><button id="createBtn" style="padding:12px">TẠO MÃ KÍCH HOẠT</button><button id="reissueBtn" class="ghost" style="padding:12px">XOAY KEY / CẤP LẠI</button></div>
  <div id="c_result" class="card hide" style="margin-top:14px;text-align:center;background:#0e1426"><div class="muted" id="c_res_label"></div><div id="c_res_code" class="mono" style="font-size:24px;letter-spacing:1px;margin:8px 0;word-break:break-all"></div><div class="muted" id="c_res_info"></div><div id="c_res_footer" style="margin-top:10px;white-space:pre-wrap"></div><p><button class="small" id="c_copy">Sao chép mã + lời nhắn</button></p></div>
  <textarea id="c_out" rows="5" class="mono hide" readonly></textarea>
  <label style="margin-top:12px">Lời nhắn gửi kèm khi sao chép (hiện dưới mã, để trống nếu không cần)</label>
  <textarea id="c_footer" rows="2" maxlength="500"></textarea>
  <div class="row" style="margin-top:6px"><button class="ghost small" id="c_footer_save" style="flex:0">Lưu lời nhắn</button><button class="ghost small" id="c_footer_default" style="flex:0">Dùng câu mặc định</button></div>
  <p style="text-align:right;margin-bottom:0"><a href="#" id="c_to_devices" style="color:var(--accent)">Quản lý thiết bị →</a></p>
</div></section>
<section data-tab="licenses" class="hide"><div class="card"><div class="row" style="align-items:center"><div style="flex:3"><b>Yêu cầu key</b><div class="muted" style="font-size:13px">Tắt: ai cài THT Voice cũng dùng thẳng, không cần key. Bật: xe chưa có key hợp lệ bị khóa ở lần kiểm tra kế tiếp (lúc mở app hoặc mỗi 12 giờ). Xe đã có key không bị ảnh hưởng. Chỉ áp dụng cho bản app từ 1.0.11.</div></div><div><select id="l_mode"><option value="free">Tắt — dùng miễn phí</option><option value="required">Bật — bắt buộc key</option></select></div></div><div class="row" style="align-items:center;margin-top:12px"><div style="flex:3"><b>Cập nhật cho xe chưa có key</b><div class="muted" style="font-size:13px">Chỉ có tác dụng khi đang dùng miễn phí. Bật: xe chưa có key được kiểm tra và tải bản mới, gói giọng (tối đa 5 lần tải/ngày/xe). Tắt: chỉ xe có key cập nhật được. Xe có key luôn cập nhật bình thường. Từ bản app 1.0.11.</div></div><div><select id="l_freeupd"><option value="on">Bật — cho cập nhật</option><option value="off">Tắt — không cập nhật</option></select></div></div></div><div class="card"><div class="row"><div style="flex:3"><label>Tìm theo mã, VIN, mã thiết bị, khách hàng, ghi chú</label><input id="l_q"></div>
  <div><label>Trạng thái</label><select id="l_status"><option value="">Tất cả</option><option value="active">Đang dùng</option><option value="unused">Chưa kích hoạt</option><option value="revoked">Đã thu hồi</option><option value="expiring">Sắp hết hạn (30 ngày)</option><option value="expired">Đã hết hạn (90 ngày qua)</option></select></div><div style="flex:0"><button id="l_go">Tìm</button></div></div>
  <details id="r_box" style="margin-top:10px"><summary class="muted" style="cursor:pointer">Mẫu tin nhắc gia hạn (nút "Nhắc khách")</summary>
  <p class="muted" style="margin:6px 0">Chỗ trống tự điền: <span class="mono">{khach}</span> tên khách, <span class="mono">{ma}</span> mã, <span class="mono">{ngay}</span> ngày hết hạn, <span class="mono">{con}</span> số ngày còn lại.</p>
  <textarea id="r_tpl" rows="3" maxlength="600"></textarea>
  <div class="row" style="margin-top:6px"><button class="ghost small" id="r_save" style="flex:0">Lưu mẫu</button><button class="ghost small" id="r_default" style="flex:0">Dùng mẫu mặc định</button></div></details></div>
  <div class="card"><table><thead><tr><th>Mã</th><th>Loại / hạn</th><th>Khách / VIN</th><th>Thiết bị</th><th>Thao tác</th></tr></thead><tbody id="l_rows"></tbody></table><div class="pager" id="l_pager"></div></div></section>
<section data-tab="devices" class="hide"><div class="card"><div class="row"><div style="flex:3"><label>Tìm theo mã thiết bị, tên, VIN</label><input id="d_q"></div><div id="d_verbox" class="hide" style="flex:0;white-space:nowrap"><label>Phiên bản</label><button class="small ghost" id="d_verchip"></button></div><div><label>Lọc</label><select id="d_filter"><option value="">Tất cả</option><option value="nokey">Chưa có key (30 ngày)</option><option value="vin">VIN bất thường</option></select></div><div style="flex:0"><button id="d_go">Tìm</button></div></div>
  <div class="row" style="margin-top:12px;align-items:center"><div style="flex:3"><b>Khi xe báo VIN khác VIN của mã</b><div class="muted" style="font-size:13px">Mã thường cho 1 xe tự gắn VIN của xe đầu tiên. Không đọc được VIN thì không bao giờ bị chặn; mã VIP không kiểm tra VIN.</div></div>
  <div><select id="d_vinmode"><option value="record">Chỉ ghi lại (không chặn)</option><option value="block">Chặn xe đó</option></select></div></div></div>
  <div class="card"><table><thead><tr><th>Thiết bị</th><th>Khách / VIN / app</th><th>Bản quyền</th><th>Khóa</th><th>Thao tác</th></tr></thead><tbody id="d_rows"></tbody></table><div class="pager" id="d_pager"></div></div></section>
<section data-tab="security" class="hide"><div class="card"><h3>Đang bị khóa / nhập sai gần đây</h3><table><thead><tr><th>Khóa</th><th>Số lần sai</th><th>Khóa đến</th><th></th></tr></thead><tbody id="k_rows"></tbody></table><div class="pager" id="k_pager"></div></div>
  <div class="card"><h3>Lượt thử gần đây</h3><table><thead><tr><th>Lúc</th><th>Loại</th><th>Thiết bị</th><th>IP</th><th>Mã</th><th>Kết quả</th></tr></thead><tbody id="a_rows"></tbody></table><div class="pager" id="a_pager"></div></div></section>
<section data-tab="log" class="hide"><div class="card"><table><thead><tr><th>Lúc</th><th>Thao tác</th><th>Đối tượng</th><th>Chi tiết</th></tr></thead><tbody id="g_rows"></tbody></table><div class="pager" id="g_pager"></div></div></section>
<section data-tab="tools" class="hide"><div class="card"><h3>Thông báo cho khách</h3>
  <p class="muted">Hiện một lần trong màn hình chính THT Voice khi khách mở app lúc xe đứng yên (không bật đè app khác, không đọc ra loa). Xe nhận ở lần kiểm tra bản quyền kế tiếp. Cần app 1.0.6 trở lên.</p>
  <textarea id="n_text" rows="3" maxlength="500" placeholder="Ví dụ: Bản 1.0.6 đã có: chia màn hình bằng giọng nói. Vào Cài đặt → Cập nhật để cài."></textarea>
  <div class="row" style="margin-top:8px"><div><label>Gửi cho</label><select id="n_target"><option value="">Tất cả xe (mọi phiên bản)</option><option value="below">Chỉ xe dưới bản…</option><option value="device">Chỉ một thiết bị (để thử)</option></select></div>
  <div id="n_verbox" class="hide"><label>Dưới bản</label><input id="n_ver" placeholder="1.0.6"></div>
  <div id="n_devbox" class="hide"><label>Mã thiết bị</label><input id="n_dev" class="mono" placeholder="XXXX-XXXX-XXXX-XXXX"></div>
  <div><label>Hiện đến ngày (để trống = không hết hạn)</label><input id="n_until" type="date"></div></div>
  <div class="row" style="margin-top:10px;align-items:center"><div style="flex:0"><button id="n_send">Gửi thông báo</button></div><div style="flex:0"><button class="ghost" id="n_off">Tắt thông báo</button></div><div id="n_state"></div></div>
  <div id="n_stats" style="margin-top:10px"></div>
  <details id="n_listbox" class="hide" style="margin-top:6px"><summary class="muted" style="cursor:pointer">Danh sách xe đã nhận</summary>
  <table><thead><tr><th>Thiết bị</th><th>Khách</th><th>Bản app</th><th>Đã nhận</th><th>Đã xem</th></tr></thead><tbody id="n_rows"></tbody></table></details></div>
<div class="card"><h3>Sao lưu / khôi phục dữ liệu</h3>
  <p class="muted">Tệp sao lưu gồm toàn bộ mã, thiết bị, kích hoạt, mã mở khóa, cài đặt và 5000 dòng nhật ký gần nhất. VIN vẫn ở dạng mã hóa: chỉ đọc lại được trên máy chủ có cùng VIN_KEY. Cất tệp ở nơi an toàn.</p>
  <p><button id="b_down">Tải bản sao lưu</button></p>
  <label style="margin-top:12px">Khôi phục từ tệp sao lưu (.json)</label><input type="file" id="b_file" accept=".json,application/json">
  <div class="row" style="margin-top:8px;align-items:center"><div><select id="b_mode"><option value="merge">Gộp: giữ dữ liệu đang có, thêm những gì còn thiếu</option><option value="replace">Thay toàn bộ bằng dữ liệu trong tệp</option></select></div><div style="flex:0"><button class="ghost" id="b_restore">Khôi phục</button></div></div>
  <pre id="b_out" class="muted"></pre></div>
<div class="card"><h3>Nhập mã từ máy chủ cũ</h3><p class="muted">Mỗi dòng: <span class="mono">MÃ_16_KÝ_TỰ,MÃ_THIẾT_BỊ(tùy chọn),NGÀY_HẾT_HẠN yyyy-mm-dd hoặc 0 = vĩnh viễn,vip(tùy chọn),ghi chú</span></p>
  <textarea id="i_text" rows="8" class="mono"></textarea><p><button id="i_go">Nhập</button></p><pre id="i_out" class="muted"></pre></div>
  <div class="card"><h3>Gói giọng đọc</h3><div class="row"><div><label>Tên</label><input id="v_name" value="kokoro_vi"></div><div><label>SHA-256 (trong PACK.txt)</label><input id="v_sha" class="mono"></div><div><label>Kích thước (byte)</label><input id="v_size" type="number"></div><div><label>Khóa R2</label><input id="v_key" placeholder="packs/THTVoice-kokoro-vi-xxxx.zip"></div></div>
  <p><button id="v_go">Lưu</button></p><table><tbody id="v_rows"></tbody></table></div></section>
</main></div><div id="msg"></div>
<script src="/admin/app.js"></script></body></html>`;

export const ADMIN_JS = String.raw`(function(){
'use strict';
var TABS=[['create','Cấp mã','Tạo mã cho một xe hoặc mã rời để bán sau','M12 5v14M5 12h14'],
 ['overview','Tổng quan','Số liệu bản quyền và trạng thái máy chủ','M4 13h4v7H4zM10 8h4v12h-4zM16 4h4v16h-4z'],
 ['licenses','Mã kích hoạt','Gia hạn, thu hồi, đổi loại, gắn VIN','M15 7a4 4 0 1 1-3.9 5H8v3H5v-3H3v-3h8.1A4 4 0 0 1 15 7z'],
 ['devices','Thiết bị','Khóa, chuyển, cấp mã mở khóa cho từng xe','M4 5h16v11H4zM8 20h8M12 16v4'],
 ['security','Khóa & lượt thử','Thiết bị/IP đang bị khóa do nhập sai','M6 11V8a6 6 0 1 1 12 0v3M5 11h14v10H5z'],
 ['log','Nhật ký','Mọi thao tác quản trị','M5 4h14v16H5zM8 8h8M8 12h8M8 16h5'],
 ['tools','Công cụ','Thông báo cho khách, sao lưu / khôi phục, nhập mã cũ','M14.7 6.3a4 4 0 0 0-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.1-.6-.6-2.1z']];
function icon(d){return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="'+d+'"/></svg>';}
var pw=sessionStorage.getItem('tht_admin')||'';
function $(id){return document.getElementById(id);}
function esc(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function dt(ms){if(ms===0)return 'Vĩnh viễn';if(!ms)return '—';var d=new Date(ms);return d.toLocaleString('vi-VN');}
function day(ms){if(ms===0)return 'Vĩnh viễn';if(ms==null)return 'Chưa kích hoạt';return new Date(ms).toLocaleDateString('vi-VN');}
function toast(t,bad){var m=$('msg');m.textContent=t;m.style.display='block';m.style.borderColor=bad?'#ff5a5a':'#26304a';clearTimeout(toast.t);toast.t=setTimeout(function(){m.style.display='none';},bad?7000:3500);}
function api(path,body){
  return fetch('/admin/api/'+path,{method:body?'POST':'GET',headers:{'authorization':'Bearer '+pw,'content-type':'application/json'},body:body?JSON.stringify(body):undefined})
  .then(function(r){return r.json().then(function(j){if(r.status===401||r.status===429){if(r.status===401){pw='';sessionStorage.removeItem('tht_admin');showLogin();}throw new Error(j.message||'Bị từ chối');}if(!j.ok)throw new Error(j.message||'Lỗi');return j;});});
}
function showLogin(){$('login').classList.remove('hide');$('app').classList.add('hide');}
function showApp(){$('login').classList.add('hide');$('app').classList.remove('hide');tab('create');loadFooter();}
function tab(name){
  document.querySelectorAll('section[data-tab]').forEach(function(s){s.classList.toggle('hide',s.getAttribute('data-tab')!==name);});
  document.querySelectorAll('#tabs button').forEach(function(b){b.classList.toggle('on',b.getAttribute('data-t')===name);});
  TABS.forEach(function(t){if(t[0]===name){$('pageTitle').textContent=t[1];$('pageSub').textContent=t[2];}});
  var onb=document.querySelector('#tabs button.on');if(onb&&onb.scrollIntoView)try{onb.scrollIntoView({inline:'center',block:'nearest'});}catch(e){}
  if(name==='overview')loadOverview();if(name==='licenses'){loadLicenses();loadReminder();loadLicenseMode();}if(name==='devices'){loadDevices();loadVinMode();}if(name==='security')loadSecurity();if(name==='log')loadLog();if(name==='tools'){loadPacks();loadNotice();}
}
TABS.forEach(function(t){var b=document.createElement('button');b.innerHTML=icon(t[3])+'<span></span>';b.lastChild.textContent=t[1];b.setAttribute('data-t',t[0]);b.onclick=function(){tab(t[0]);};$('tabs').appendChild(b);});
function labelize(tb){var tbl=tb.closest('table');if(!tbl)return;var hs=[].map.call(tbl.querySelectorAll('thead th'),function(th){return th.textContent;});
  [].forEach.call(tb.rows,function(tr){[].forEach.call(tr.cells,function(td,i){if(hs[i]!==undefined)td.setAttribute('data-label',hs[i]);});});}
if(window.MutationObserver)document.querySelectorAll('tbody').forEach(function(tb){new MutationObserver(function(){labelize(tb);}).observe(tb,{childList:true});});
$('loginBtn').onclick=function(){pw=$('pw').value;api('overview').then(function(){sessionStorage.setItem('tht_admin',pw);showApp();}).catch(function(e){toast(e.message,true);});};
$('pw').onkeydown=function(e){if(e.key==='Enter')$('loginBtn').click();};
$('logout').onclick=function(){pw='';sessionStorage.removeItem('tht_admin');showLogin();};
function loadVersions(){api('versions').then(function(j){
  var r=j.release,latest=r&&r.versionName||'';
  var head=r?('<div style="color:var(--text)"><b>Bản mới nhất: '+esc(latest)+'</b> (build '+esc(r.versionCode)+')'+(r.publishedAt?' · phát hành '+esc(day(r.publishedAt)):'')+' · bắt buộc cập nhật: '+(r.forceUpdate?'<span class="bad-t">có</span>':'không')+'</div>'+
    (r.changelog?'<div style="white-space:pre-line;margin:6px 0">'+esc(r.changelog)+'</div>':''))
    :'<div>Chưa đọc được bản phát hành trên GitHub (trang quản lý riêng tự cập nhật sau khi xe kiểm tra cập nhật hoặc mỗi ngày một lần).</div>';
  if(j.up_to_date!=null&&j.total)head+='<div style="margin:6px 0"><b>Đã lên bản mới nhất: '+j.up_to_date+'/'+j.total+' xe ('+Math.round(j.up_to_date*100/j.total)+'%)</b></div>';
  $('rel').innerHTML=head;
  $('ver_rows').innerHTML=j.versions.map(function(v){var isLatest=latest&&v.version===latest;
    return '<tr><td class="mono">'+(v.version?esc(v.version):'<span class="muted">không rõ</span>')+(isLatest?' <span class="pill">mới nhất</span>':'')+'</td><td>'+v.cars+'</td><td>'+esc(dt(v.last_seen))+'</td><td><button class="small ghost" data-ver="'+esc(v.version)+'">Xem xe</button></td></tr>';}).join('')||'<tr><td colspan="4" class="muted">Chưa có xe nào online trong 30 ngày</td></tr>';
  $('ver_rows').querySelectorAll('button[data-ver]').forEach(function(b){b.onclick=function(){DVER=b.getAttribute('data-ver');$('d_q').value='';$('d_filter').value='';PG.d.page=1;tab('devices');};});
}).catch(function(){$('rel').textContent='Không đọc được thống kê phiên bản.';});}
function loadOverview(){loadVersions();api('overview').then(function(j){
  var items=[['Tổng số mã',j.licenses],['Chưa kích hoạt',j.unused],['Thiết bị đang dùng',j.active_devices],['Thiết bị bị khóa',j.blocked_devices],['Xe chưa có key (30 ngày)',j.nokey_devices,'nokey'],['Hết hạn trong 7 ngày',j.expiring_7d],['Sắp hết hạn (30 ngày)',j.expiring_30d,'expiring'],['Đã hết hạn (30 ngày qua)',j.expired_30d,'expired'],['VIN bất thường',j.vin_anomalies,'vin'],['Đang bị khóa do nhập sai',j.locked_now],['Nhập sai 24 giờ',j.failed_24h],['Khóa ký license',j.signing_ready===null?'Ở máy chủ license':(j.signing_ready?('Sẵn sàng ('+j.key_id+')'):'THIẾU')],['Khóa VIN',j.vin_key_ready?'Sẵn sàng':'THIẾU']];
  var cols=['#4de5ff','#7d8cff','#5ef0a8','#ff7d91','#7d8cff','#ffc66d','#ffc66d','#ff7d91','#ff7d91','#ff7d91','#ffc66d','#5ef0a8','#5ef0a8'];$('stats').innerHTML=items.map(function(i,k){return '<div class="stat card"'+(i[2]?' data-go="'+i[2]+'" style="cursor:pointer;--c:':' style="--c:')+cols[k%cols.length]+'"><span>'+esc(i[0])+(i[2]?' →':'')+'</span><b'+(typeof i[1]==='number'?'':' class="txt"')+'>'+esc(i[1])+'</b></div>';}).join('');
  $('stats').querySelectorAll('[data-go]').forEach(function(el){el.onclick=function(){var g=el.getAttribute('data-go');
    if(g==='vin'||g==='nokey'){$('d_q').value='';$('d_filter').value=g;PG.d.page=1;tab('devices');return;}
    $('l_q').value='';$('l_status').value=g;PG.l.page=1;tab('licenses');};});
}).catch(function(e){toast(e.message,true);});}
var dur='365';
document.querySelectorAll('[data-dur]').forEach(function(b){b.onclick=function(){dur=b.getAttribute('data-dur');document.querySelectorAll('[data-dur]').forEach(function(x){x.classList.toggle('on',x===b);});$('c_custom').classList.toggle('hide',dur!=='custom');};});
function rcValue(){return $('c_rc').value.trim();}
$('c_rc').oninput=function(){$('c_count_box').classList.toggle('hide',rcValue().length>0);};
function durationBody(){
  if(dur==='lifetime')return {duration_mode:'lifetime'};
  if(dur==='custom'){if($('c_fixed').value)return {duration_mode:'fixed',fixed_expires_at:$('c_fixed').value};return {duration_mode:'days',days:$('c_days').value};}
  return {duration_mode:'days',days:dur};
}
function showResult(label,codes,info){
  $('c_result').classList.remove('hide');$('c_res_label').textContent=label;$('c_res_code').textContent=codes[0]||'';$('c_res_info').textContent=info||'';
  $('c_out').classList.toggle('hide',codes.length<2);$('c_out').value=codes.join('\n');
}
var DEFAULT_FOOTER='';
function footerText(){return $('c_footer').value.trim();}
function syncFooter(){$('c_res_footer').textContent=footerText();}
function loadFooter(){api('settings').then(function(j){DEFAULT_FOOTER=j.default_copy_footer||'';$('c_footer').value=j.copy_footer;syncFooter();}).catch(function(){});}
$('c_footer').oninput=syncFooter;
$('c_footer_save').onclick=function(){api('settings/copy-footer',{copy_footer:$('c_footer').value}).then(function(j){$('c_footer').value=j.result.copy_footer;syncFooter();toast('Đã lưu lời nhắn');}).catch(function(e){toast(e.message,true);});};
$('c_footer_default').onclick=function(){$('c_footer').value=DEFAULT_FOOTER;syncFooter();};
$('c_copy').onclick=function(){var t=$('c_out').classList.contains('hide')?$('c_res_code').textContent:$('c_out').value;var f=footerText();if(f)t=t.trim()+'\n\n'+f;
  if(navigator.clipboard)navigator.clipboard.writeText(t).then(function(){toast('Đã sao chép mã + lời nhắn');},function(){window.prompt('Sao chép:',t);});else window.prompt('Sao chép:',t);};
$('c_to_devices').onclick=function(e){e.preventDefault();tab('devices');};
$('createBtn').onclick=function(){
  var b={license_type:$('c_type').value,customer:$('c_customer').value,note:$('c_note').value,max_devices:$('c_max').value,vin:$('c_vin').value.trim(),request_code:rcValue(),count:$('c_count').value};
  var d=durationBody();for(var k in d)b[k]=d[k];
  api('licenses/create',b).then(function(j){var r=j.result;
    showResult(r.request_code?('Mã kích hoạt cho thiết bị '+r.request_code.replace(/(.{4})(?=.)/g,'$1-')):('Đã tạo '+r.codes.length+' mã'),r.codes,
      r.request_code?'Khách nhập mã này, hoặc bấm Khôi phục bản quyền trên xe. Thời hạn tính từ lúc kích hoạt.':'Mỗi mã dùng cho 1 xe.');
    toast('Đã tạo mã');}).catch(function(e){toast(e.message,true);});
};
$('reissueBtn').onclick=function(){
  var rc=rcValue();if(!rc){toast('Nhập mã yêu cầu của xe cần cấp lại',true);return;}
  if(!confirm('Cấp mã mới cho thiết bị '+rc+'? Mã cũ sẽ bị thu hồi, hạn dùng giữ nguyên.'))return;
  api('devices/action',{request_code:rc,action:'reissue',customer:$('c_customer').value,note:$('c_note').value}).then(function(j){var r=j.result;
    showResult('Mã mới cho thiết bị '+r.request_code.replace(/(.{4})(?=.)/g,'$1-'),[r.code],'Mã cũ '+r.old_code+' đã thu hồi. Hạn: '+day(r.expires_at)+'. Xe tự nhận mã mới ở lần kiểm tra kế tiếp (Bản quyền → Kiểm tra lại).');
    toast('Đã cấp lại');}).catch(function(e){toast(e.message,true);});
};
var PG={l:{page:1,size:20},d:{page:1,size:20},k:{page:1,size:20},a:{page:1,size:20},g:{page:1,size:20}};
function pq(k){return 'page='+PG[k].page+'&size='+PG[k].size;}
function pager(k,j,reload){var el=$(k+'_pager');if(!el)return;var pages=Math.max(1,Math.ceil((j.total||0)/(j.size||20)));PG[k].page=Math.min(j.page||1,pages);
  if(!j.total){el.innerHTML='';return;}
  el.innerHTML='<button class="small ghost" data-p="first"'+(PG[k].page<=1?' disabled':'')+'>«</button><button class="small ghost" data-p="prev"'+(PG[k].page<=1?' disabled':'')+'>‹ Trước</button>'+
    '<span class="muted">Trang <b>'+PG[k].page+'</b>/'+pages+' · '+j.total+' mục</span>'+
    '<button class="small ghost" data-p="next"'+(PG[k].page>=pages?' disabled':'')+'>Sau ›</button><button class="small ghost" data-p="last"'+(PG[k].page>=pages?' disabled':'')+'>»</button>'+
    '<select data-p="size">'+[20,50,100].map(function(n){return '<option value="'+n+'"'+(n===PG[k].size?' selected':'')+'>'+n+' / trang</option>';}).join('')+'</select>';
  el.querySelectorAll('button[data-p]').forEach(function(b){b.onclick=function(){var a=b.getAttribute('data-p');
    PG[k].page=a==='first'?1:a==='last'?pages:a==='prev'?Math.max(1,PG[k].page-1):Math.min(pages,PG[k].page+1);reload();
    var tb=$(k+'_rows');if(tb&&tb.closest('.card')&&tb.closest('.card').scrollIntoView)try{tb.closest('.card').scrollIntoView({block:'start'});}catch(e){}};});
  el.querySelector('select').onchange=function(){PG[k].size=+this.value;PG[k].page=1;reload();};}
var LIC={},REMINDER='',DEFAULT_REMINDER='';
function reminderText(l){var left=l.expires_at>0?Math.max(0,Math.ceil((l.expires_at-Date.now())/86400000)):0;
  return (REMINDER||DEFAULT_REMINDER).split('{khach}').join(l.customer||'anh/chị').split('{ma}').join(l.code_display||'').split('{ngay}').join(day(l.expires_at)).split('{con}').join(String(left));}
function copyText(t,msg){if(navigator.clipboard)navigator.clipboard.writeText(t).then(function(){toast(msg);},function(){window.prompt('Sao chép:',t);});else window.prompt('Sao chép:',t);}
function loadReminder(){api('settings').then(function(j){DEFAULT_REMINDER=j.default_reminder_template||'';REMINDER=j.reminder_template||DEFAULT_REMINDER;$('r_tpl').value=REMINDER;}).catch(function(){});}
$('r_save').onclick=function(){api('settings/reminder',{reminder_template:$('r_tpl').value}).then(function(j){REMINDER=j.result.reminder_template;$('r_tpl').value=REMINDER;toast('Đã lưu mẫu tin nhắc');}).catch(function(e){toast(e.message,true);});};
$('r_default').onclick=function(){$('r_tpl').value=DEFAULT_REMINDER;};
function licAction(id,action,value,extra){var b={id:id,action:action,value:value};if(extra)for(var k in extra)b[k]=extra[k];return api('licenses/update',b).then(function(){toast('Đã lưu');loadLicenses();}).catch(function(e){toast(e.message,true);});}
function loadLicenses(){
  api('licenses?q='+encodeURIComponent($('l_q').value)+'&status='+encodeURIComponent($('l_status').value)+'&'+pq('l')).then(function(j){pager('l',j,loadLicenses);
    LIC={};j.items.forEach(function(l){LIC[l.id]=l;});
    $('l_rows').innerHTML=j.items.map(function(l){
      var exp=l.expires_at==null?(l.duration_mode==='days'?(l.duration_days+' ngày từ lúc kích hoạt'):l.duration_mode==='fixed'?('đến '+day(l.fixed_expires_at)):'Vĩnh viễn'):day(l.expires_at);
      var expired=l.expires_at>0&&l.expires_at<Date.now();
      var left=l.expires_at>0?Math.ceil((l.expires_at-Date.now())/86400000):null;
      var leftTxt=left==null?'':(left>0?'còn '+left+' ngày':(left===0?'hết hạn hôm nay':'đã hết hạn '+(-left)+' ngày'));
      var devs=l.activations.map(function(a){return '<div class="mono'+(a.status!=='active'?' muted':'')+'">'+esc(a.request_code)+(a.status!=='active'?' (đã gỡ)':'')+(a.vin?' • '+esc(a.vin):'')+(a.device_status==='blocked'?' <span class="bad-t">khóa</span>':'')+'</div>';}).join('')||'<span class="muted">—</span>';
      return '<tr><td class="mono" style="white-space:nowrap">'+esc(l.code_display)+(l.format==='hex'?' <span class="pill">mã cũ</span>':'')+'</td>'+
      '<td><span class="pill">'+(l.license_type==='vip'?'VIP':'Thường')+'</span> '+(l.status==='revoked'?'<span class="bad-t">Thu hồi</span>':'')+'<div class="'+(expired?'bad-t':'')+'">'+esc(exp)+'</div>'+(leftTxt?'<div class="'+(expired||left<=7?'bad-t':'muted')+'">'+esc(leftTxt)+'</div>':'')+'<div class="muted">tối đa '+l.max_devices+' TB</div></td>'+
      '<td>'+esc(l.customer||'')+'<div class="muted">'+esc(l.note||'')+'</div>'+(l.vin?'<div class="mono">VIN: '+esc(l.vin)+(l.vin_auto?' <span class="pill" title="Tự gắn từ xe kích hoạt đầu tiên">tự gắn</span>':'')+'</div>':'')+'</td><td>'+devs+'</td>'+
      '<td class="actions" data-id="'+l.id+'">'+(l.expires_at>0?'<button class="small" data-a="r30">+30 ngày</button><button class="small" data-a="r365">+1 năm</button><button class="small ghost" data-a="remind">Nhắc khách</button>':'')+'<button class="small'+(l.expires_at>0?' ghost':'')+'" data-a="renew">Gia hạn</button><button class="small ghost" data-a="date">Đặt ngày</button><button class="small ghost" data-a="lifetime">Vĩnh viễn</button>'+
      (l.status==='revoked'?'<button class="small ghost" data-a="restore">Bỏ thu hồi</button>':'<button class="small bad" data-a="revoke">Thu hồi</button>')+
      '<button class="small ghost" data-a="type">'+(l.license_type==='vip'?'→ Thường':'→ VIP')+'</button><button class="small ghost" data-a="vin">VIN</button><button class="small ghost" data-a="note">Ghi chú</button><button class="small ghost" data-a="max">Số TB</button><button class="small ghost" data-a="release">Gỡ thiết bị</button><button class="small bad" data-a="delete">Xóa</button></td></tr>';
    }).join('')||'<tr><td colspan="5" class="muted">Không có kết quả</td></tr>';
    $('l_rows').querySelectorAll('button[data-a]').forEach(function(b){b.onclick=function(){
      var id=+b.parentNode.getAttribute('data-id'),a=b.getAttribute('data-a'),v;
      if(a==='renew'){v=prompt('Gia hạn thêm bao nhiêu ngày?','365');if(v)licAction(id,'renew_days',v);}
      else if(a==='r30'){if(confirm('Gia hạn thêm 30 ngày?'))licAction(id,'renew_days','30');}
      else if(a==='r365'){if(confirm('Gia hạn thêm 1 năm (365 ngày)?'))licAction(id,'renew_days','365');}
      else if(a==='remind'){var lic=LIC[id];if(lic)copyText(reminderText(lic),'Đã sao chép tin nhắc gia hạn');}
      else if(a==='date'){v=prompt('Ngày hết hạn mới (yyyy-mm-dd)');if(v)licAction(id,'set_expiry',v);}
      else if(a==='lifetime'){if(confirm('Chuyển mã này thành vĩnh viễn?'))licAction(id,'lifetime');}
      else if(a==='revoke'){if(confirm('Thu hồi mã? Thiết bị sẽ bị khóa ở lần kiểm tra kế tiếp.'))licAction(id,'revoke');}
      else if(a==='restore')licAction(id,'restore');
      else if(a==='type')licAction(id,'set_type',b.textContent.indexOf('VIP')>=0?'vip':'standard');
      else if(a==='vin'){v=prompt('VIN (17 ký tự) để khóa mã theo xe; để trống để bỏ');if(v===null)return;licAction(id,v.trim()?'set_vin':'clear_vin',v.trim());}
      else if(a==='note'){var c=prompt('Khách hàng');if(c===null)return;v=prompt('Ghi chú');if(v!==null)licAction(id,'set_note',v,{customer:c});}
      else if(a==='max'){v=prompt('Số thiết bị tối đa','1');if(v)licAction(id,'set_max_devices',v);}
      else if(a==='release'){if(confirm('Gỡ mã khỏi mọi thiết bị? Mã có thể kích hoạt lại trên thiết bị khác.'))licAction(id,'release_devices');}
      else if(a==='delete'){if(confirm('Xóa hẳn mã này? Không hoàn tác được.'))licAction(id,'delete');}
    };});
  }).catch(function(e){toast(e.message,true);});
}
function searchLicenses(){PG.l.page=1;loadLicenses();}
$('l_go').onclick=searchLicenses;$('l_status').onchange=searchLicenses;$('l_q').onkeydown=function(e){if(e.key==='Enter')searchLicenses();};
function devAction(rc,action,value){return api('devices/action',{request_code:rc,action:action,value:value}).then(function(j){
  if(action==='issue_unlock_code')alert('Mã mở khóa cho '+rc+': '+j.result.unlock_code+'\nDùng một lần, hết hạn sau 24 giờ.');else toast('Đã lưu');loadDevices();}).catch(function(e){toast(e.message,true);});}
function vinStats(d){
  var ok=d.vin_ok_count||0,miss=d.vin_miss_count||0,chg=d.vin_change_count||0,mm=d.vin_mismatch_count||0;
  if(!ok&&!miss&&!chg&&!mm)return '<div class="muted">VIN: chưa có thống kê</div>';
  var bad=miss>0||chg>0||mm>0;
  return '<div class="'+(bad?'bad-t':'muted')+'" title="Mỗi lần app kiểm tra bản quyền">VIN đọc được '+ok+' • trượt '+miss+(miss&&d.vin_miss_reason?' ('+esc(d.vin_miss_reason)+')':'')+' • khác '+chg+'</div>'+
    (mm?'<div class="bad-t">Không khớp VIN của mã: '+mm+' lần'+(d.vin_mismatch_at?' • gần nhất '+esc(dt(d.vin_mismatch_at)):'')+'</div>':'');
}
function loadLicenseMode(){api('settings').then(function(j){$('l_mode').value=j.license_mode||'required';$('l_freeupd').value=j.free_updates===false?'off':'on';}).catch(function(){});}
$('l_freeupd').onchange=function(){var on=this.value==='on';
  api('settings/free-updates',{free_updates:on}).then(function(j){$('l_freeupd').value=j.result.free_updates?'on':'off';toast(on?'Đã cho xe chưa có key cập nhật':'Đã tắt cập nhật cho xe chưa có key');}).catch(function(e){toast(e.message,true);loadLicenseMode();});};
$('l_mode').onchange=function(){var v=this.value;
  if(v==='required'&&!confirm('Bật yêu cầu key?\nXe chưa có key hợp lệ sẽ bị khóa ở lần kiểm tra kế tiếp. Xe đã có key vẫn dùng bình thường.')){this.value='free';return;}
  api('settings/license-mode',{license_mode:v}).then(function(j){$('l_mode').value=j.result.license_mode;toast(v==='required'?'Đã bật yêu cầu key':'Đã tắt yêu cầu key, dùng miễn phí');}).catch(function(e){toast(e.message,true);loadLicenseMode();});};
function loadVinMode(){api('settings').then(function(j){$('d_vinmode').value=j.vin_mode||'record';}).catch(function(){});}
$('d_vinmode').onchange=function(){var v=this.value;
  if(v==='block'&&!confirm('Chặn xe báo VIN khác VIN của mã?\nXe không đọc được VIN và mã VIP vẫn dùng bình thường. Đổi lại "Chỉ ghi lại" là xe dùng lại được ở lần kiểm tra kế tiếp.')){this.value='record';return;}
  api('settings/vin-mode',{vin_mode:v}).then(function(j){$('d_vinmode').value=j.result.vin_mode;toast(v==='block'?'Đã bật chặn theo VIN':'Chỉ ghi lại, không chặn');}).catch(function(e){toast(e.message,true);loadVinMode();});};
function loadDevices(){
  api('devices?q='+encodeURIComponent($('d_q').value)+'&filter='+encodeURIComponent($('d_filter').value)+(DVER!==null?'&version='+encodeURIComponent(DVER):'')+'&'+pq('d')).then(function(j){
    $('d_verbox').classList.toggle('hide',DVER===null);$('d_verchip').textContent=(DVER||'không rõ')+' ✕';pager('d',j,loadDevices);
    $('d_rows').innerHTML=j.items.map(function(d){
      var locked=d.lock&&d.lock.locked_until>Date.now();
      return '<tr><td class="mono">'+esc(d.request_code)+'<div class="muted">'+esc(d.label||'')+'</div>'+(d.status==='blocked'?'<span class="bad-t">Đã khóa</span> '+esc(d.blocked_reason||''):'<span class="ok">Hoạt động</span>')+'</td>'+
      '<td>'+(function(){var names=[];(d.licenses||[]).forEach(function(l){if(l.customer&&names.indexOf(l.customer)<0)names.push(l.customer);});
        var note=(d.licenses||[]).map(function(l){return l.note;}).filter(Boolean)[0];
        return '<div style="font-weight:600">'+(names.length?esc(names.join(', ')):'<span class="muted">Chưa có tên khách</span>')+'</div>'+(note?'<div class="muted">'+esc(note)+'</div>':'');})()+
      '<div class="mono">VIN: '+esc(d.vin||'—')+'</div><div class="muted">app '+esc(d.app_version||'?')+' • '+esc(dt(d.last_seen))+'</div>'+vinStats(d)+'</td>'+
      '<td>'+(d.licenses.map(function(l){return '<div class="mono">'+esc(l.code)+' <span class="muted">'+day(l.expires_at)+'</span></div>';}).join('')||'<span class="muted">Chưa có key</span>')+'</td>'+
      '<td>'+(d.lock?('<span class="'+(locked?'bad-t':'muted')+'">'+d.lock.fail_count+' lần sai'+(locked?' • đến '+dt(d.lock.locked_until):'')+'</span>'):'—')+'</td>'+
      '<td class="actions" data-rc="'+esc(d.request_code)+'">'+(d.status==='blocked'?'<button class="small" data-a="unblock">Mở khóa TB</button>':'<button class="small bad" data-a="block">Khóa TB</button>')+
      '<button class="small ghost" data-a="unlock">Xóa lần sai</button><button class="small ghost" data-a="code">Mã mở khóa</button><button class="small ghost" data-a="transfer">Chuyển sang TB mới</button><button class="small ghost" data-a="label">Đặt tên</button><button class="small ghost" data-a="vin">VIN</button><button class="small bad" data-a="delete">Xóa TB</button></td></tr>';
    }).join('')||'<tr><td colspan="5" class="muted">Không có kết quả</td></tr>';
    $('d_rows').querySelectorAll('button[data-a]').forEach(function(b){b.onclick=function(){
      var rc=b.parentNode.getAttribute('data-rc'),a=b.getAttribute('data-a'),v;
      if(a==='block'){v=prompt('Lý do khóa (tùy chọn)','');if(v!==null)devAction(rc,'block',v);}
      else if(a==='unblock')devAction(rc,'unblock');
      else if(a==='unlock')devAction(rc,'unlock');
      else if(a==='code')devAction(rc,'issue_unlock_code');
      else if(a==='transfer'){v=prompt('Mã thiết bị mới (16 ký tự trên màn hình kích hoạt)');if(v)devAction(rc,'transfer',v);}
      else if(a==='label'){v=prompt('Tên thiết bị / xe');if(v!==null)devAction(rc,'set_label',v);}
      else if(a==='vin'){v=prompt('VIN (17 ký tự)');if(v)devAction(rc,'set_vin',v);}
      else if(a==='delete'){if(confirm('Xóa thiết bị và gỡ bản quyền khỏi nó?'))devAction(rc,'delete');}
    };});
  }).catch(function(e){toast(e.message,true);});
}
var DVER=null;
function searchDevices(){PG.d.page=1;loadDevices();}
$('d_verchip').onclick=function(){DVER=null;searchDevices();};
$('d_go').onclick=searchDevices;$('d_filter').onchange=searchDevices;$('d_q').onkeydown=function(e){if(e.key==='Enter')searchDevices();};
function loadSecurity(){loadLockouts();loadAttempts();}
function loadLockouts(){
  api('lockouts?'+pq('k')).then(function(j){pager('k',j,loadLockouts);$('k_rows').innerHTML=j.items.map(function(k){var on=k.locked_until>Date.now();return '<tr><td class="mono">'+esc(k.key)+'</td><td>'+k.fail_count+'</td><td class="'+(on?'bad-t':'muted')+'">'+(on?dt(k.locked_until):'—')+'</td><td><button class="small ghost" data-k="'+esc(k.key)+'">Mở khóa</button></td></tr>';}).join('')||'<tr><td colspan="4" class="muted">Không có</td></tr>';
    $('k_rows').querySelectorAll('button[data-k]').forEach(function(b){b.onclick=function(){api('lockouts/clear',{key:b.getAttribute('data-k')}).then(function(){toast('Đã mở khóa');loadLockouts();}).catch(function(e){toast(e.message,true);});};});}).catch(function(e){toast(e.message,true);});
}
function loadAttempts(){
  api('attempts?'+pq('a')).then(function(j){pager('a',j,loadAttempts);$('a_rows').innerHTML=j.items.map(function(a){return '<tr><td>'+esc(dt(a.at))+'</td><td>'+esc(a.kind)+'</td><td class="mono">'+esc(a.request_code||'')+'</td><td class="mono">'+esc(a.ip||'')+'</td><td class="mono">'+esc(a.code_hint||'')+'</td><td class="'+(a.ok?'ok':'bad-t')+'">'+(a.ok?'OK':esc(a.reason||'sai'))+'</td></tr>';}).join('');}).catch(function(){});
}
function loadLog(){api('log?'+pq('g')).then(function(j){pager('g',j,loadLog);$('g_rows').innerHTML=j.items.map(function(g){return '<tr><td>'+esc(dt(g.at))+'</td><td>'+esc(g.action)+'</td><td class="mono">'+esc(g.target||'')+'</td><td class="mono muted">'+esc(g.detail||'')+'</td></tr>';}).join('');}).catch(function(e){toast(e.message,true);});}
$('i_go').onclick=function(){
  var items=$('i_text').value.split(/\r?\n/).map(function(l){return l.trim();}).filter(Boolean).map(function(l){var p=l.split(',').map(function(x){return x.trim();});
    return {code:p[0],request_code:p[1]||'',expires_at:p[2]==='0'?0:(p[2]||''),license_type:(p[3]||'').toLowerCase()==='vip'?'vip':'standard',note:p[4]||'import'};});
  api('import',{items:items}).then(function(j){$('i_out').textContent='Đã nhập '+j.result.imported+' mã'+(j.result.skipped.length?('\nBỏ qua: '+j.result.skipped.join(', ')):'');}).catch(function(e){toast(e.message,true);});
};
function backupName(){var d=new Date(),p=function(n){return (n<10?'0':'')+n;};return 'tht-license-backup-'+d.getFullYear()+p(d.getMonth()+1)+p(d.getDate())+'-'+p(d.getHours())+p(d.getMinutes())+'.json';}
function downloadBackup(){return api('backup').then(function(j){var blob=new Blob([JSON.stringify(j.backup)],{type:'application/json'});var a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=backupName();document.body.appendChild(a);a.click();setTimeout(function(){URL.revokeObjectURL(a.href);a.remove();},1000);
  var c=j.backup.counts;toast('Đã tải bản sao lưu: '+c.licenses+' mã, '+c.devices+' thiết bị');return j.backup;});}
$('b_down').onclick=function(){downloadBackup().catch(function(e){toast(e.message,true);});};
$('b_restore').onclick=function(){var f=$('b_file').files&&$('b_file').files[0];if(!f){toast('Chọn tệp sao lưu trước',true);return;}
  var mode=$('b_mode').value;var rd=new FileReader();rd.onload=function(){var data;try{data=JSON.parse(rd.result);}catch(e){toast('Tệp không đọc được (không phải JSON)',true);return;}
    if(!data||data.format!=='tht-license-backup'||!data.counts){toast('Tệp không phải bản sao lưu của máy chủ bản quyền',true);return;}
    var c=data.counts,when=data.created_at?new Date(data.created_at).toLocaleString('vi-VN'):'?';
    var msg='Tệp sao lưu lúc '+when+': '+c.licenses+' mã, '+c.devices+' thiết bị, '+c.activations+' kích hoạt.\n\n'+(mode==='replace'?'THAY TOÀN BỘ dữ liệu đang có bằng tệp này? Bản sao lưu hiện tại sẽ được tải về trước.':'Gộp vào dữ liệu đang có (không xóa gì)?');
    if(!confirm(msg))return;
    if(mode==='replace'&&prompt('Gõ THAY THẾ để xác nhận')!=='THAY THẾ'){toast('Đã hủy');return;}
    var go=function(){$('b_out').textContent='Đang khôi phục…';return api('restore',{backup:data,mode:mode}).then(function(r){var n=r.result.now_on_server;
      $('b_out').textContent='Xong ('+(mode==='replace'?'thay toàn bộ':'gộp')+'). Trên máy chủ giờ có: '+n.licenses+' mã, '+n.devices+' thiết bị, '+n.activations+' kích hoạt.';toast('Đã khôi phục');});};
    (mode==='replace'?downloadBackup().then(go):go()).catch(function(e){$('b_out').textContent='';toast(e.message,true);});};
  rd.readAsText(f);};
function loadNotice(){api('notice').then(function(j){var n=j.notice||{},s=j.stats||{delivered:0,seen:0,recipients:[]};$('n_text').value=n.text||'';
  var expired=n.until&&n.until<Date.now();
  $('n_state').innerHTML=!n.text?'<span class="muted">Chưa có thông báo</span>':(n.enabled&&!expired?'<span class="ok">● Đang gửi</span>':'<span class="muted">● Đã tắt'+(expired?' (hết hạn)':'')+'</span>')+(n.updated_at?' <span class="muted">· sửa lần cuối '+esc(dt(n.updated_at))+'</span>':'');
  $('n_stats').innerHTML=n.text?('<b>Đã gửi tới '+s.delivered+' xe · Đã xem '+s.seen+' xe</b><div class="muted" style="font-size:13px">Xe nhận khi kiểm tra bản quyền (Bản quyền → Kiểm tra lại, hoặc tự động định kỳ). "Đã xem" cập nhật ở lần kiểm tra kế tiếp sau khi khách bấm Đã xem.</div>'):'';
  $('n_listbox').classList.toggle('hide',!s.recipients.length);
  $('n_rows').innerHTML=s.recipients.map(function(r){return '<tr><td class="mono">'+esc(r.request_code)+(r.label?'<div class="muted">'+esc(r.label)+'</div>':'')+'</td><td>'+esc(r.customer||'—')+'</td><td class="mono">'+esc(r.app_version||'?')+'</td><td>'+esc(dt(r.delivered_at))+'</td><td>'+(r.seen_at?'<span class="ok">'+esc(dt(r.seen_at))+'</span>':'<span class="muted">chưa</span>')+'</td></tr>';}).join('');$('n_target').value=n.request_code?'device':(n.below_version?'below':'');$('n_ver').value=n.below_version||'';$('n_dev').value=n.request_code||'';$('n_devbox').classList.toggle('hide',!n.request_code);
  $('n_until').value=n.until?new Date(n.until).toISOString().slice(0,10):'';$('n_verbox').classList.toggle('hide',!n.below_version);

  if(!n.below_version)api('versions').then(function(v){if(v.release&&v.release.versionName&&!$('n_ver').value)$('n_ver').value=v.release.versionName;}).catch(function(){});}).catch(function(){});}
$('n_target').onchange=function(){$('n_verbox').classList.toggle('hide',this.value!=='below');$('n_devbox').classList.toggle('hide',this.value!=='device');};
function saveNotice(on){var u=$('n_until').value;var body={text:$('n_text').value,enabled:on,below_version:$('n_target').value==='below'?$('n_ver').value.trim():'',request_code:$('n_target').value==='device'?$('n_dev').value.trim():'',until:u?new Date(u+'T23:59:59').getTime():0};
  if(body.enabled&&!body.text.trim()){toast('Nhập nội dung thông báo',true);return;}
  if($('n_target').value==='device'&&!body.request_code){toast('Nhập mã thiết bị để thử',true);return;}
  if(on&&!confirm('Gửi thông báo này tới '+($('n_target').value==='device'?'thiết bị '+body.request_code:($('n_target').value==='below'?'các xe dưới bản '+body.below_version:'tất cả xe'))+'?'))return;
  api('notice',body).then(function(j){toast(j.result.notice.enabled?'Đã bật gửi thông báo':'Đã tắt thông báo');loadNotice();}).catch(function(e){toast(e.message,true);});}
$('n_send').onclick=function(){saveNotice(true);};
$('n_off').onclick=function(){saveNotice(false);};
function loadPacks(){api('voice-packs').then(function(j){$('v_rows').innerHTML=j.items.map(function(p){return '<tr><td>'+esc(p.name)+'</td><td class="mono">'+esc(p.sha256.slice(0,16))+'…</td><td>'+Math.round(p.size_bytes/1048576)+' MB</td><td class="mono">'+esc(p.r2_key)+'</td></tr>';}).join('');}).catch(function(){});}
$('v_go').onclick=function(){api('voice-packs',{name:$('v_name').value,sha256:$('v_sha').value.trim(),size_bytes:+$('v_size').value,r2_key:$('v_key').value.trim()}).then(function(){toast('Đã lưu gói');loadPacks();}).catch(function(e){toast(e.message,true);});};
if(pw){api('overview').then(showApp).catch(showLogin);}else showLogin();
})();`;
