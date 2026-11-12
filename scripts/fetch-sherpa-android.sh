#!/usr/bin/env bash
set -euo pipefail

VERSION="2.0.1"
NAME="sherpa-onnx-${VERSION}.aar"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/${NAME}"
SHA256="a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
DEST="app/libs/${NAME}"

mkdir -p app/libs

if [[ -f "$DEST" ]]; then
  CURRENT="$(sha256sum "$DEST" | awk '{print $1}')"
  if [[ "$CURRENT" == "$SHA256" ]]; then
    echo "Sherpa Android runtime already present and verified: $DEST"
    exit 0
  fi
  echo "Existing runtime checksum mismatch; replacing $DEST"
  rm -f "$DEST"
fi

curl --fail --location --retry 3 --output "$DEST.tmp" "$URL"
echo "$SHA256  $DEST.tmp" | sha256sum --check --status
mv "$DEST.tmp" "$DEST"
echo "Downloaded and verified $DEST"
