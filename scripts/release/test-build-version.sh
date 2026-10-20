#!/usr/bin/env bash
# Prints "<versionCode> <versionName>" for a hardware-test build (owner decision 14/10/2026):
# same versionCode as the latest release-* tag, name "<release name>-test". With the same signing
# key, release N -> test, test -> release N and test -> release N+1 all install over each other.
# No release tag yet -> "1 1.0-test".
# Usage: test-build-version.sh [tag-list-file]   (default: git ls-remote --tags origin 'release-*')
set -euo pipefail
if [[ -n "${1:-}" ]]; then tags="$(cat "$1")"; else tags="$(git ls-remote --tags origin 'release-*' | awk '{print $2}' | sed 's#^refs/tags/##')"; fi
code=1; name="1.0"
while IFS= read -r tag; do
  [[ "$tag" =~ ^release-([0-9.]+)-([0-9]+)$ ]] || continue
  if (( BASH_REMATCH[2] > code )); then code=${BASH_REMATCH[2]}; name=${BASH_REMATCH[1]}; fi
done <<< "$tags"
echo "$code $name-test"
