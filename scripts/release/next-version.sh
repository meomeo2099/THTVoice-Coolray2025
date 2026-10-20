#!/usr/bin/env bash
# Prints "<versionCode> <versionName>" for the next release.
# Empty versionCode = highest rc-*/release-* tag code + 1 (at least 2; 1 is the first build).
# Empty versionName = 1.0.<versionCode-1>.
# Usage: next-version.sh [versionCode] [versionName] [tag-list-file]
set -euo pipefail
code="${1:-}"; name="${2:-}"; tags_file="${3:-}"
if [[ -z "$code" ]]; then
  if [[ -n "$tags_file" ]]; then tags="$(cat "$tags_file")"; else tags="$(git tag -l 'rc-*' 'release-*')"; fi
  max=1
  while IFS= read -r tag; do
    [[ "$tag" =~ ^(rc|release)-[0-9.]+-([0-9]+)$ ]] || continue
    (( BASH_REMATCH[2] > max )) && max=${BASH_REMATCH[2]}
  done <<< "$tags"
  code=$((max + 1))
fi
[[ -n "$name" ]] || name="1.0.$((code - 1))"
echo "$code $name"
