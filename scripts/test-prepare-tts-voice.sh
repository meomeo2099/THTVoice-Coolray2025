#!/usr/bin/env bash
set -euo pipefail

TMP="$(mktemp -d)"
DEST_ROOT="app/src/main/assets/models/tts"
cleanup() {
  rm -rf "$TMP" "$DEST_ROOT"
}
trap cleanup EXIT

SOURCE="$TMP/voice"
mkdir -p "$SOURCE/espeak-ng-data"
head -c 1024 /dev/zero > "$SOURCE/test-voice.onnx"
printf 'a 1\n' > "$SOURCE/tokens.txt"
printf 'data\n' > "$SOURCE/espeak-ng-data/phontab"
printf '{"audio":{"sample_rate":22050}}\n' > "$SOURCE/test-voice.onnx.json"

bash scripts/prepare-tts-voice.sh ngoc_huyen "$SOURCE"

test -f "$DEST_ROOT/ngoc_huyen/test-voice.onnx"
test -f "$DEST_ROOT/ngoc_huyen/tokens.txt"
test -f "$DEST_ROOT/ngoc_huyen/espeak-ng-data/phontab"
test -f "$DEST_ROOT/ngoc_huyen/test-voice.onnx.json"
test ! -e "$DEST_ROOT/diem_trinh"

if bash scripts/prepare-tts-voice.sh invalid_voice "$SOURCE" >/dev/null 2>&1; then
  echo "Invalid voice id was unexpectedly accepted" >&2
  exit 5
fi

echo "Offline TTS staging test passed."
