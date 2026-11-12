#!/usr/bin/env bash
set -euo pipefail

VERSION="1.13.8"
NAME="sherpa-onnx-${VERSION}.aar"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/${NAME}"
SHA256="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
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
