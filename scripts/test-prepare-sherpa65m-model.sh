#!/usr/bin/env bash
set -euo pipefail

MODEL_ID="sherpa-onnx-streaming-zipformer-vi-65M-int8"
DEST="app/src/main/assets/models/${MODEL_ID}"
TMP="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP" "$DEST"
}
trap cleanup EXIT

SRC="$TMP/$MODEL_ID"
mkdir -p "$SRC"

# Deliberately make FP32 files larger; staging must still prefer INT8.
head -c 4096 /dev/zero > "$SRC/encoder.onnx"
head -c 1024 /dev/zero > "$SRC/encoder.int8.onnx"
head -c 2048 /dev/zero > "$SRC/joiner.onnx"
head -c 512 /dev/zero > "$SRC/joiner.int8.onnx"
head -c 768 /dev/zero > "$SRC/decoder.onnx"
printf '<blk> 0\n' > "$SRC/tokens.txt"
printf 'fake-bpe\n' > "$SRC/bpe.model"

ARCHIVE="$TMP/${MODEL_ID}.tar.bz2"
tar -cjf "$ARCHIVE" -C "$TMP" "$MODEL_ID"

bash scripts/prepare-sherpa65m-model.sh "$ARCHIVE"

test -f "$DEST/encoder.int8.onnx"
test ! -f "$DEST/encoder.onnx"
test -f "$DEST/joiner.int8.onnx"
test ! -f "$DEST/joiner.onnx"
test -f "$DEST/decoder.onnx"
test -f "$DEST/tokens.txt"
test -f "$DEST/bpe.model"
test "$(tr -d '\r\n' < "$DEST/asr-backend.txt")" = "online-transducer"

echo "Sherpa 65M staging test passed: INT8 files preferred and online backend marked."
