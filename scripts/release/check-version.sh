#!/usr/bin/env bash
# Release version gate: versionCode must be a positive integer strictly greater than every
# versionCode already used by an rc-*/release-* tag, and versionName must look like X.Y[.Z].
# Usage: check-version.sh <versionCode> <versionName> [tag-list-file]
set -euo pipefail
code="${1:-}"; name="${2:-}"; tags_file="${3:-}"
if ! [[ "$code" =~ ^[1-9][0-9]{0,8}$ ]]; then echo "versionCode must be a positive integer: '$code'" >&2; exit 10; fi
if ! [[ "$name" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then echo "versionName must look like 1.0 or 1.0.1: '$name'" >&2; exit 11; fi
if [[ -n "$tags_file" ]]; then tags="$(cat "$tags_file")"; else tags="$(git tag -l 'rc-*' 'release-*')"; fi
max=0
while IFS= read -r tag; do
  [[ "$tag" =~ ^(rc|release)-[0-9.]+-([0-9]+)$ ]] || continue
  used="${BASH_REMATCH[2]}"
  if (( used > max )); then max=$used; fi
done <<< "$tags"
if (( code <= max )); then echo "versionCode $code must be greater than the highest released/rc versionCode $max" >&2; exit 12; fi
echo "version gate OK: versionCode=$code (previous max $max) versionName=$name"
