#!/usr/bin/env bash
set -euo pipefail

ARCHIVE="${1:-sherpa-onnx-streaming-zipformer-vi-65M-int8.tar.bz2}"
MODEL_ID="sherpa-onnx-streaming-zipformer-vi-65M-int8"
DEST="app/src/main/assets/models/${MODEL_ID}"

if [[ ! -f "$ARCHIVE" ]]; then
  echo "Missing model archive: $ARCHIVE" >&2
  echo "Usage: bash scripts/prepare-sherpa65m-model.sh /path/to/${MODEL_ID}.tar.bz2" >&2
  exit 2
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

tar -xjf "$ARCHIVE" -C "$TMP"

# Prefer quantized INT8 binaries whenever the archive also contains FP32 files.
# Decoder is allowed to be FP32 because many Sherpa transducer packages ship it that way.
find_preferred() {
  local role="$1"
  local int8
  int8="$(find "$TMP" -type f -name "${role}*.int8.onnx" -print0 \
    | xargs -0 -r ls -1S \
    | awk 'NR==1 {print; exit}')"
  if [[ -n "$int8" ]]; then
    printf '%s\n' "$int8"
    return 0
  fi

  find "$TMP" -type f -name "${role}*.onnx" -print0 \
    | xargs -0 -r ls -1S \
    | awk 'NR==1 {print; exit}'
}

ENCODER="$(find_preferred encoder)"
DECODER="$(find_preferred decoder)"
JOINER="$(find_preferred joiner)"
TOKENS="$(find "$TMP" -type f -name tokens.txt | head -n 1)"

if [[ -z "$ENCODER" || -z "$DECODER" || -z "$JOINER" || -z "$TOKENS" ]]; then
  echo "Archive does not contain a complete streaming transducer model." >&2
  echo "Expected encoder*.onnx, decoder*.onnx, joiner*.onnx and tokens.txt." >&2
  exit 3
fi

rm -rf "$DEST"
mkdir -p "$DEST"
cp "$ENCODER" "$DEST/$(basename "$ENCODER")"
cp "$DECODER" "$DEST/$(basename "$DECODER")"
cp "$JOINER" "$DEST/$(basename "$JOINER")"
cp "$TOKENS" "$DEST/tokens.txt"
printf '%s\n' 'online-transducer' > "$DEST/asr-backend.txt"

# Copy BPE metadata when present; it is harmless and useful for diagnostics.
BPE="$(find "$TMP" -type f -name bpe.model | head -n 1 || true)"
if [[ -n "$BPE" ]]; then cp "$BPE" "$DEST/bpe.model"; fi

printf '%s\n' "Prepared ${MODEL_ID}:"
find "$DEST" -maxdepth 1 -type f -printf '  %f  %s bytes\n' | sort

echo "Model is staged locally for APK packaging and is intentionally ignored by Git."
