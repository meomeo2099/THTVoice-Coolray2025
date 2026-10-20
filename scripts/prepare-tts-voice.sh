#!/usr/bin/env bash
set -euo pipefail

VOICE_ID="${1:-}"
SOURCE_DIR="${2:-}"
DEST_ROOT="app/src/main/assets/models/tts"

case "$VOICE_ID" in
  ngoc_huyen|diem_trinh) ;;
  *)
    echo "Voice id must be ngoc_huyen or diem_trinh" >&2
    echo "Usage: bash scripts/prepare-tts-voice.sh <voice_id> /path/to/vits-model-dir" >&2
    exit 2
    ;;
esac

if [[ -z "$SOURCE_DIR" || ! -d "$SOURCE_DIR" ]]; then
  echo "Missing TTS source directory: $SOURCE_DIR" >&2
  exit 3
fi

MODEL="$(find "$SOURCE_DIR" -maxdepth 1 -type f -name '*.onnx' | sort | head -n 1)"
TOKENS="$SOURCE_DIR/tokens.txt"
ESPEAK="$SOURCE_DIR/espeak-ng-data"

if [[ -z "$MODEL" || ! -f "$TOKENS" || ! -d "$ESPEAK" ]]; then
  echo "Incomplete VITS voice model." >&2
  echo "Expected one *.onnx file, tokens.txt and espeak-ng-data/." >&2
  exit 4
fi

DEST="$DEST_ROOT/$VOICE_ID"
rm -rf "$DEST"
mkdir -p "$DEST"
cp "$MODEL" "$DEST/$(basename "$MODEL")"
cp "$TOKENS" "$DEST/tokens.txt"
cp -R "$ESPEAK" "$DEST/espeak-ng-data"

# Sidecar metadata is optional but useful for diagnostics.
for file in "$SOURCE_DIR"/*.json; do
  [[ -f "$file" ]] && cp "$file" "$DEST/$(basename "$file")"
done

printf 'Prepared TTS voice %s:\n' "$VOICE_ID"
find "$DEST" -type f -printf '  %P  %s bytes\n' | sort | head -80

echo "TTS voice is staged locally for APK packaging and is intentionally ignored by Git."
