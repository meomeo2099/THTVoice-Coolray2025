// App update (GitHub Release proxy) and voice pack download (R2), both license-gated.
// GITHUB_TOKEN stays a Worker secret; the app only ever talks to this Worker.
import { json, requireLicense } from './license.js';
import { latestReleaseInfo } from './notice.js';

const GH = 'https://api.github.com';
const MP3_MAX_BYTES = 8 * 1024 * 1024;
const MP3_ASSET_HOSTS = new Set(['release-assets.githubusercontent.com', 'objects.githubusercontent.com']);

function ghHeaders(env, accept = 'application/vnd.github+json') {
  return {
    accept,
    'user-agent': 'THTVoice-Update-Worker',
    'x-github-api-version': '2022-11-28',
    ...(env.GITHUB_TOKEN ? { authorization: `Bearer ${env.GITHUB_TOKEN}` } : {}),
  };
}

async function latestRelease(env) {
  if (!env.GITHUB_REPO) return null;
  const res = await fetch(`${GH}/repos/${env.GITHUB_REPO}/releases/latest`, { headers: ghHeaders(env) });
  if (!res.ok) return null;
  return res.json();
}

async function assetBody(env, assetId) {
  return fetch(`${GH}/repos/${env.GITHUB_REPO}/releases/assets/${assetId}`, {
    headers: ghHeaders(env, 'application/octet-stream'),
    redirect: 'follow',
  });
}

/** update.json of the latest release, for the admin version table (null when unreadable). */
export async function fetchReleaseInfo(env) {
  const release = await latestRelease(env);
  if (!release) return null;
  const asset = (release.assets || []).find((a) => a.name === 'update.json');
  if (!asset) return null;
  const res = await assetBody(env, asset.id);
  if (!res.ok) return null;
  const m = await res.json();
  return {
    versionName: String(m.versionName || ''),
    versionCode: parseInt(m.versionCode, 10) || 0,
    changelog: String(m.changelog || '').slice(0, 2000),
    forceUpdate: Boolean(m.forceUpdate),
    publishedAt: release.published_at ? Date.parse(release.published_at) || 0 : 0,
    tag: release.tag_name || '',
  };
}

// GET /api/update?current_code=N
export async function handleUpdate(request, env, url, now) {
  if (!(await requireLicense(request, env, now))) return json(401, { ok: false, code: 'unlicensed', message: 'License không hợp lệ.' });
  const current = parseInt(url.searchParams.get('current_code') || '0', 10) || 0;
  // Cars checking for updates also keep the admin's "bản mới nhất" fresh (10-minute cache).
  try { await latestReleaseInfo(env, now, fetchReleaseInfo); } catch { /* admin info only */ }
  const release = await latestRelease(env);
  if (!release) return json(503, { ok: false, message: 'Máy chủ chưa đọc được bản phát hành.' });
  const manifestAsset = (release.assets || []).find((a) => a.name === 'update.json');
  if (!manifestAsset) return json(200, { ok: true, updateAvailable: false });
  const manifestRes = await assetBody(env, manifestAsset.id);
  if (!manifestRes.ok) return json(503, { ok: false, message: 'Không đọc được update.json.' });
  let manifest;
  try {
    manifest = await manifestRes.json();
  } catch {
    return json(503, { ok: false, message: 'update.json không hợp lệ.' });
  }
  const versionCode = parseInt(manifest.versionCode, 10) || 0;
  if (versionCode <= current) return json(200, { ok: true, updateAvailable: false });
  const apk = (release.assets || []).find((a) => a.name === manifest.apk);
  if (!apk || !/^[0-9a-fA-F]{64}$/.test(manifest.sha256 || '')) return json(503, { ok: false, message: 'Bản phát hành thiếu APK hoặc SHA-256.' });
  return json(200, {
    ok: true,
    updateAvailable: true,
    versionCode,
    versionName: String(manifest.versionName || ''),
    sha256: manifest.sha256.toLowerCase(),
    sizeBytes: apk.size,
    changelog: String(manifest.changelog || ''),
    forceUpdate: Boolean(manifest.forceUpdate),
    releaseTag: release.tag_name || '',
    downloadUrl: `${url.origin}/api/update/apk?asset_id=${apk.id}`,
  });
}

// GET /api/update/apk?asset_id=N — only assets of the latest release are served.
export async function handleUpdateApk(request, env, url, now) {
  if (!(await requireLicense(request, env, now))) return json(401, { ok: false, code: 'unlicensed', message: 'License không hợp lệ.' });
  const id = url.searchParams.get('asset_id') || '';
  if (!/^[1-9][0-9]*$/.test(id)) return json(400, { ok: false, message: 'asset_id không hợp lệ.' });
  const release = await latestRelease(env);
  const asset = (release?.assets || []).find((a) => String(a.id) === id && a.name.endsWith('.apk'));
  if (!asset) return json(404, { ok: false, message: 'Không có APK này.' });
  const res = await assetBody(env, id);
  if (!res.ok) return json(502, { ok: false, message: `GitHub HTTP ${res.status}` });
  return new Response(res.body, {
    status: 200,
    headers: {
      'content-type': 'application/vnd.android.package-archive',
      'content-length': String(asset.size),
      'cache-control': 'no-store',
    },
  });
}

function packQuery(url) {
  const name = url.searchParams.get('name') || '';
  const sha = (url.searchParams.get('sha256') || '').toLowerCase();
  if (!/^[a-z0-9_]{1,40}$/.test(name) || !/^[0-9a-f]{64}$/.test(sha)) return null;
  return { name, sha };
}

/**
 * Voice pack published by the release workflow: a GitHub Release holding voice-pack.json
 * ({name, file, sha256, size}) and the zip. Needs no R2 (Cloudflare free plan) and no admin step.
 */
async function readAsset(env, asset) {
  const body = await assetBody(env, asset.id);
  return body.ok ? body.text() : null;
}

/**
 * Finds the pack in the GitHub Releases, in this order per release:
 * 1. a THTVoice-kokoro-vi-*.zip whose GitHub asset digest is sha256:<sha> (any release, also one
 *    created by hand on github.com — just upload the zip);
 * 2. voice-pack.json ({name, file, sha256, size}) written by the release workflows;
 * 3. SHA256.txt ("<sha>  <file>") uploaded next to the zip by hand.
 */
async function githubPack(env, name, sha) {
  if (!env.GITHUB_REPO) return null;
  // Fast path: the pack's own release "voice-<name>-<sha12>" (found by tag, so it never falls out
  // of the "latest 20 releases" window as app releases pile up).
  const byTag = await fetch(`${GH}/repos/${env.GITHUB_REPO}/releases/tags/voice-${name}-${sha.slice(0, 12)}`, { headers: ghHeaders(env) });
  if (byTag.ok) {
    const release = await byTag.json();
    const prefix = name === 'kokoro_vi' ? 'THTVoice-kokoro-vi-' : `THTVoice-${name}-`;
    const zips = (release.assets || []).filter((a) => a.name.startsWith(prefix) && a.name.endsWith('.zip'));
    const zip = zips.find((a) => String(a.digest || '').toLowerCase() === `sha256:${sha}`) || (zips.length === 1 ? zips[0] : null);
    if (zip) return { name, sha256: sha, size_bytes: zip.size, github_asset: zip.id };
  }
  const res = await fetch(`${GH}/repos/${env.GITHUB_REPO}/releases?per_page=20`, { headers: ghHeaders(env) });
  if (!res.ok) {
    console.error('github_releases_failed', res.status);
    return {
      error: 502,
      message: `Máy chủ không đọc được GitHub Releases (HTTP ${res.status}). Kiểm tra GITHUB_TOKEN và GITHUB_REPO.`,
    };
  }
  const found = (zip) => ({ name, sha256: sha, size_bytes: zip.size, github_asset: zip.id });
  const packPrefix = name === 'kokoro_vi' ? 'THTVoice-kokoro-vi-' : `THTVoice-${name}-`;
  for (const release of await res.json()) {
    if (release.draft) continue;
    const assets = release.assets || [];
    const zips = assets.filter((a) => a.name.startsWith(packPrefix) && a.name.endsWith('.zip'));
    const byDigest = zips.find((a) => String(a.digest || '').toLowerCase() === `sha256:${sha}`);
    if (byDigest) return found(byDigest);

    const manifest = assets.find((a) => a.name === 'voice-pack.json');
    if (manifest) {
      try {
        const info = JSON.parse((await readAsset(env, manifest)) || '');
        if (info?.name === name && String(info?.sha256 || '').toLowerCase() === sha) {
          const zip = assets.find((a) => a.name === info.file);
          if (zip && zip.size === info.size) return found(zip);
        }
      } catch {
        // fall through
      }
    }

    const shaTxt = zips.length ? assets.find((a) => a.name === 'SHA256.txt') : null;
    if (shaTxt) {
      const text = (await readAsset(env, shaTxt)) || '';
      for (const line of text.split(/\r?\n/)) {
        const m = /^([0-9a-fA-F]{64})\s+\*?(\S+)\s*$/.exec(line.trim());
        if (!m || m[1].toLowerCase() !== sha) continue;
        const zip = zips.find((a) => a.name === m[2]);
        if (zip) return found(zip);
      }
    }
  }
  return null;
}

/** Registered pack (admin page + R2) first, then the GitHub Releases. */
async function findPack(env, url) {
  const q = packQuery(url);
  if (!q) return null;
  const row = await env.DB.prepare('SELECT * FROM voice_packs WHERE name = ? AND sha256 = ?').bind(q.name, q.sha).first();
  if (row && env.MODELS) return row;
  return githubPack(env, q.name, q.sha);
}

// GET /api/model?name=kokoro_vi&sha256=…
export async function handleModel(request, env, url, now) {
  if (!(await requireLicense(request, env, now))) return json(401, { ok: false, code: 'unlicensed', message: 'License không hợp lệ.' });
  const pack = await findPack(env, url);
  if (pack?.error) return json(pack.error, { ok: false, code: 'github_unavailable', message: pack.message });
  if (!pack) {
    const sha = packQuery(url)?.sha || '';
    return json(404, { ok: false, code: 'no_pack', message: `Máy chủ chưa có gói giọng đọc cho bản này (mã ${sha.slice(0, 12)}).` });
  }
  return json(200, {
    ok: true,
    sha256: pack.sha256,
    sizeBytes: pack.size_bytes,
    downloadUrl: `${url.origin}/api/model/file?name=${encodeURIComponent(pack.name)}&sha256=${pack.sha256}`,
  });
}

// GET /api/model/file?name=…&sha256=…
export async function handleModelFile(request, env, url, now) {
  if (!(await requireLicense(request, env, now))) return json(401, { ok: false, code: 'unlicensed', message: 'License không hợp lệ.' });
  const pack = await findPack(env, url);
  if (pack?.error) return json(pack.error, { ok: false, code: 'github_unavailable', message: pack.message });
  if (!pack) return json(404, { ok: false, message: 'Không có gói này.' });
  if (pack.github_asset) {
    const res = await assetBody(env, pack.github_asset);
    if (!res.ok) return json(502, { ok: false, message: `GitHub HTTP ${res.status}` });
    return new Response(res.body, {
      status: 200,
      headers: { 'content-type': 'application/zip', 'content-length': String(pack.size_bytes), 'cache-control': 'no-store' },
    });
  }
  const object = await env.MODELS.get(pack.r2_key);
  if (!object) return json(404, { ok: false, message: 'Tệp gói giọng đọc chưa được tải lên R2.' });
  return new Response(object.body, {
    headers: { 'content-type': 'application/zip', 'content-length': String(object.size), 'cache-control': 'no-store' },
  });
}

// Media releases are separate from Coolray APK/Kokoro releases. Shared M4A uses only its dedicated token.
export async function handleMp3Voice(request, env, url, now, voice, download) {
  if (!(await requireLicense(request, env, now))) {
    return json(401, { ok: false, code: 'unlicensed', message: 'License không hợp lệ.' });
  }
  const requestedSha = url.searchParams.get('sha256') || '';
  if (download && !/^[0-9a-f]{64}$/.test(requestedSha)) {
    return json(400, { ok: false, message: 'SHA-256 không hợp lệ.' });
  }
  const m4a = url.searchParams.get('format') === 'm4a';
  const repo = m4a ? (env.M4A_GITHUB_REPO || 'meomeo2099/THTVoice-Coolray2025') : env.MP3_GITHUB_REPO || 'meomeo2099/THTVoice-Coolray2025';
  const tag = m4a ? (env.M4A_RELEASE_TAG || 'voice-mp3-packs') : (env.MP3_RELEASE_TAG || 'voice-mp3-packs');
  const credentials = { GITHUB_TOKEN: m4a ? env.M4A_GITHUB_TOKEN : (env.MP3_GITHUB_TOKEN || env.GITHUB_TOKEN) };
  const github = `${GH}/repos/${repo}/releases`;
  const unavailable = () => json(502, { ok: false, message: 'Máy chủ không tải được gói giọng MP3.' });

  try {
    const release = await fetch(`${github}/tags/${encodeURIComponent(tag)}`, {
      headers: ghHeaders(credentials), redirect: 'manual',
    });
    if (release.status === 404) return json(404, { ok: false, message: 'Chưa tìm thấy bản phát hành MP3.' });
    if (!release.ok) return unavailable();
    const name = new RegExp(`^THTVoice-${m4a ? 'M4A' : 'MP3'}-${voice}-r([1-9][0-9]{0,8})\\.zip$`);
    const { assets = [] } = await release.json();
    const packs = [];
    for (const asset of assets) {
      const version = name.exec(asset.name)?.[1];
      const sha = /^sha256:([0-9a-f]{64})$/i.exec(asset.digest || '')?.[1]?.toLowerCase();
      if (version && sha && asset.state === 'uploaded' && Number.isSafeInteger(asset.id) && asset.id > 0 &&
          Number.isInteger(asset.size) && asset.size > 0 && asset.size <= (m4a ? 24 * 1024 * 1024 : MP3_MAX_BYTES)) {
        packs.push({ asset, version: Number(version), sha });
      }
    }
    packs.sort((a, b) => b.version - a.version);
    const pack = download ? packs.find(p => p.sha === requestedSha) : packs[0];
    if (!pack) return json(404, { ok: false, message: 'Chưa có gói MP3 phù hợp.' });
    if (!download) {
      return json(200, {
        schema: 1, ...(m4a ? { format: 'm4a' } : {}), version: `r${pack.version}`, sha256: pack.sha, size: pack.asset.size,
        url: `${url.origin}/api/voice/${voice}/file?${m4a ? 'format=m4a&' : ''}sha256=${pack.sha}`,
      });
    }

    let response = await fetch(`${github}/assets/${pack.asset.id}`, {
      headers: ghHeaders(credentials, 'application/octet-stream'), redirect: 'manual',
    });
    if (response.status === 302) {
      const target = new URL(response.headers.get('location') || '');
      if (target.protocol !== 'https:' || target.username || target.password || target.port ||
          !MP3_ASSET_HOSTS.has(target.hostname)) return unavailable();
      // The GitHub bearer token is scoped to the API call. Never forward it to a CDN.
      response = await fetch(target.href, { headers: {}, redirect: 'manual' });
    }
    if (response.status !== 200 || !response.body) return unavailable();
    return new Response(response.body, {
      headers: { 'content-type': 'application/zip', 'content-length': String(pack.asset.size), 'cache-control': 'no-store' },
    });
  } catch {
    return unavailable();
  }
}
