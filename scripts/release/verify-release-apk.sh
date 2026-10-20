#!/usr/bin/env bash
# Fails unless the APK is release-signed by the pinned certificate and carries the
# expected package / versionCode / versionName.
# Usage: verify-release-apk.sh <apk> <expected-cert-sha256> <versionCode> <versionName>
set -euo pipefail
apk="$1"; want_cert="$(echo "$2" | tr -d ': ' | tr 'A-F' 'a-f')"; code="$3"; name="$4"
bt="${ANDROID_SDK_ROOT:-$ANDROID_HOME}/build-tools/32.0.0"
[[ -s "$apk" ]] || { echo "APK missing: $apk" >&2; exit 20; }
[[ "$apk" != *unsigned* ]] || { echo "APK is unsigned: $apk" >&2; exit 21; }
certs="$("$bt/apksigner" verify --print-certs "$apk")"
got_cert="$(echo "$certs" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1 | tr 'A-F' 'a-f')"
[[ -n "$got_cert" ]] || { echo "No signing certificate found" >&2; exit 22; }
[[ "$got_cert" == "$want_cert" ]] || { echo "Signing certificate mismatch: $got_cert != pinned $want_cert" >&2; exit 23; }
badging="$("$bt/aapt" dump badging "$apk" | head -1)"
echo "$badging" | grep -q "name='com.tht.voice.coolray2025'" || { echo "Wrong package: $badging" >&2; exit 24; }
echo "$badging" | grep -q "versionCode='$code'" || { echo "Wrong versionCode: $badging" >&2; exit 25; }
echo "$badging" | grep -q "versionName='$name'" || { echo "Wrong versionName: $badging" >&2; exit 26; }
echo "release APK verified: cert=$got_cert $badging"
