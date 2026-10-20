#!/usr/bin/env bash
set -uo pipefail
dir="$(cd "$(dirname "$0")" && pwd)"; tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
printf 'rc-1.0.1-2\nrelease-1.0.2-3\nbackup/x\nrc-bad\n' > "$tmp"
fail=0
expect() {
  local want=$1; shift
  bash "$dir/check-version.sh" "$@" "$tmp" >/dev/null 2>&1
  local got=$?
  if [[ $got -ne $want ]]; then echo "FAIL: $* -> $got (want $want)"; fail=1; fi
}
expect 0 4 1.0.3
expect 12 3 1.0.3
expect 12 1 1.0
expect 10 0 1.0
expect 10 abc 1.0
expect 11 5 v1.0
expect 11 5 1
: > "$tmp"; expect 0 1 1.0
if [[ $fail -eq 0 ]]; then echo "release version gate self-test PASS"; else exit 1; fi

# next-version.sh: auto versionCode/versionName for one-click OTA releases.
nv_tags="$(mktemp)"
printf 'rc-1.0.1-2\nrelease-1.0.2-3\nnot-a-release\n' > "$nv_tags"
[[ "$(bash "$(dirname "$0")/next-version.sh" "" "" "$nv_tags")" == "4 1.0.3" ]] || { echo "next-version auto failed" >&2; exit 1; }
: > "$nv_tags"
[[ "$(bash "$(dirname "$0")/next-version.sh" "" "" "$nv_tags")" == "2 1.0.1" ]] || { echo "next-version first release failed" >&2; exit 1; }
[[ "$(bash "$(dirname "$0")/next-version.sh" 9 2.0 "$nv_tags")" == "9 2.0" ]] || { echo "next-version explicit failed" >&2; exit 1; }
rm -f "$nv_tags"
echo "next-version self-test OK"
