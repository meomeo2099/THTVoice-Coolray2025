#!/usr/bin/env bash
set -euo pipefail

MODEL_ID="sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
ARCHIVE="${MODEL_ID}.tar.bz2"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/${ARCHIVE}"
ARCHIVE_SHA256="68447f4fbc67e70eee3a93961f36e81e98f47aef73ce7e7ca00885c6cd3616a6"
DEST="app/src/main/assets/models/${MODEL_ID}"

ENCODER="encoder-epoch-13-avg-2-chunk-8-left-64.int8.onnx"
DECODER="decoder-epoch-13-avg-2-chunk-8-left-64.onnx"
JOINER="joiner-epoch-13-avg-2-chunk-8-left-64.int8.onnx"
TOKENS="tokens.txt"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading pinned Sherpa KWS model..."
curl --fail --location --retry 3 --connect-timeout 15 "$URL" -o "$TMP/$ARCHIVE"

actual="$(sha256sum "$TMP/$ARCHIVE" | awk '{print $1}')"
if [[ "$actual" != "$ARCHIVE_SHA256" ]]; then
  echo "SHA-256 mismatch for $ARCHIVE" >&2
  echo "expected=$ARCHIVE_SHA256" >&2
  echo "actual=$actual" >&2
  exit 4
fi

tar -xjf "$TMP/$ARCHIVE" -C "$TMP"
SRC="$TMP/$MODEL_ID"
for name in "$ENCODER" "$DECODER" "$JOINER" "$TOKENS"; do
  [[ -s "$SRC/$name" ]] || { echo "Missing KWS model file: $name" >&2; exit 5; }
done

# The bilingual model exposes English phone tokens. Keep the wake profiles
# deterministic and independent from an Android-side tokenizer. Only one of the
# generated keyword files is loaded by the app at runtime.
for token in HH EY1 T IY1 CH AH0 L OW1 JH IY0 G EH0 EH1 EY0 AY1 Z; do
  awk -v token="$token" '$1 == token { found=1 } END { exit(found ? 0 : 1) }' "$SRC/$TOKENS" || {
    echo "Required wake-word token not present in model: $token" >&2
    exit 6
  }
done

rm -rf "$DEST"
mkdir -p "$DEST"
cp "$SRC/$ENCODER" "$DEST/$ENCODER"
cp "$SRC/$DECODER" "$DEST/$DECODER"
cp "$SRC/$JOINER" "$DEST/$JOINER"
cp "$SRC/$TOKENS" "$DEST/$TOKENS"

# Legacy/default profile: "Hey THT" pronounced "hey tee aitch tee".
printf '%s\n' 'HH EY1 T IY1 EY1 CH T IY1 @HEY_THT' > "$DEST/keywords.txt"

# Compatibility wake profiles requested for in-vehicle use.
# Geely: English "jee-lee" plus the ways Vietnamese drivers say it (owner report 03/10/2026:
# the ASR heard "hegel", "hay galay", "hay ghe la", "hây ghê", "hay ghe lay", "hay dây lay"...):
# ghi-li, ghê-li, ghê-lây, ghây-li, gi-li. "Hi" is accepted next to "Hey".
printf '%s\n' \
  'HH AH0 L OW1 JH IY1 L IY0 @HELLO_GEELY' \
  'HH AH0 L OW1 G IY1 L IY0 @HELLO_GEELY' \
  'HH AH0 L OW1 G EH1 L IY0 @HELLO_GEELY' \
  'HH AH0 L OW1 G EH1 L EY0 @HELLO_GEELY' \
  'HH AH0 L OW1 G EY1 L IY0 @HELLO_GEELY' \
  'HH EH0 L OW1 JH IY1 L IY0 @HELLO_GEELY' \
  'HH EH0 L OW1 G IY1 L IY0 @HELLO_GEELY' \
  'HH EH0 L OW1 G EH1 L IY0 @HELLO_GEELY' \
  'HH EH0 L OW1 G EH1 L EY0 @HELLO_GEELY' \
  'HH EH0 L OW1 G EY1 L IY0 @HELLO_GEELY' \
  > "$DEST/keywords-hello-geely.txt"
printf '%s\n' \
  'HH EY1 JH IY1 L IY0 @HEY_GEELY' \
  'HH EY1 G IY1 L IY0 @HEY_GEELY' \
  'HH EY1 G EH1 L IY0 @HEY_GEELY' \
  'HH EY1 G EH1 L EY0 @HEY_GEELY' \
  'HH EY1 G EY1 L IY0 @HEY_GEELY' \
  'HH EY1 Z IY1 L IY0 @HEY_GEELY' \
  'HH EY1 JH EH1 L IY0 @HEY_GEELY' \
  'HH AY1 JH IY1 L IY0 @HEY_GEELY' \
  'HH AY1 G IY1 L IY0 @HEY_GEELY' \
  'HH AY1 G EH1 L IY0 @HEY_GEELY' \
  'HH AY1 G EH1 L EY0 @HEY_GEELY' \
  > "$DEST/keywords-hey-geely.txt"

printf '%s\n' 'k2-fsa/sherpa-onnx release:kws-models asset:sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2' > "$DEST/model-source.txt"

printf '%s\n' "Prepared offline wake word model for THT Voice:"
find "$DEST" -maxdepth 1 -type f -printf '  %f  %s bytes\n' | sort
printf '%s\n' 'Wake profiles: Hey THT | Hello Geely | Hey Geely'
printf '%s\n' 'Only the selected profile is loaded by KWS at runtime.'
printf '%s\n' 'KWS assets are staged locally and remain ignored by Git.'
