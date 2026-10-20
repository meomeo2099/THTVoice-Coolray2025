#!/usr/bin/env bash
# Writes update.json + SHA256.txt + RELEASE_INFO.txt for the update Worker (docs/SAFE_UPDATE_RELEASE.md).
# Usage: create-update-json.sh <apk> <versionCode> <versionName> <out-dir> [changelog-file]
set -euo pipefail
apk="$1"; code="$2"; name="$3"; out="$4"; changelog_file="${5:-}"
mkdir -p "$out"
file="THTVoice-$name.apk"
cp "$apk" "$out/$file"
sha="$(sha256sum "$out/$file" | awk '{print $1}')"
changelog=""
if [[ -n "$changelog_file" && -s "$changelog_file" ]]; then changelog="$(cat "$changelog_file")"; fi
force="${THTVOICE_FORCE_UPDATE:-false}"
python3 - "$out/update.json" "$code" "$name" "$file" "$sha" "$changelog" "$force" <<'PY'
import json, sys
path, code, name, apk, sha, changelog, force = sys.argv[1:]
json.dump({"versionCode": int(code), "versionName": name, "apk": apk, "sha256": sha,
           "forceUpdate": force.strip().lower() == "true", "changelog": changelog},
          open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
echo "$sha  $file" > "$out/SHA256.txt"
{
  echo "versionCode=$code"
  echo "versionName=$name"
  echo "forceUpdate=$force"
  echo "apk=$file"
  echo "apk_sha256=$sha"
  echo "git_sha=${GITHUB_SHA:-$(git rev-parse HEAD)}"
  echo "built_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "status=RELEASE_CANDIDATE (not customer-ready until the Phase 10 hardware gate passes)"
} > "$out/RELEASE_INFO.txt"
echo "update.json written: versionCode=$code sha256=$sha"
