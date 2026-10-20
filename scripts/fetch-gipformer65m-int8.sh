#!/usr/bin/env bash
set -euo pipefail

# G-Group Gipformer 65M RNNT is an OFFLINE Sherpa transducer export.
# Keep this separate from prepare-sherpa65m-model.sh, which stages a true OnlineRecognizer model.
REPO="g-group-ai-lab/gipformer-65M-rnnt"
REVISION="8571259"
MODEL_ID="sherpa-onnx-streaming-zipformer-vi-65M-int8"
DEST="app/src/main/assets/models/${MODEL_ID}"
BASE="https://huggingface.co/${REPO}/resolve/${REVISION}"

ENCODER="encoder-epoch-35-avg-6.int8.onnx"
DECODER="decoder-epoch-35-avg-6.int8.onnx"
JOINER="joiner-epoch-35-avg-6.int8.onnx"
TOKENS="tokens.txt"
BPE="bpe.model"

ENCODER_SHA256="3cc1a719f04d6051210e03833eef0d42939352c6f4704b67c34655ceecb98809"
DECODER_SHA256="2b6235b8a8be57b5ba57024f6119398a1aace6aff23347a04d59c9bd2686c1f6"
JOINER_SHA256="eb74e2ead853d64161ee7af2aa2bce5fcc64752d07f72e0683926b238296d58f"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fetch() {
  local name="$1"
  echo "Downloading ${name} from pinned ${REPO}@${REVISION}..."
  curl --fail --location --retry 3 --connect-timeout 15 \
    "${BASE}/${name}?download=true" -o "${TMP}/${name}"
}

verify() {
  local name="$1"
  local expected="$2"
  local actual
  actual="$(sha256sum "${TMP}/${name}" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    echo "SHA-256 mismatch for ${name}" >&2
    echo "expected=${expected}" >&2
    echo "actual=${actual}" >&2
    exit 4
  fi
}

fetch "$ENCODER"
fetch "$DECODER"
fetch "$JOINER"
fetch "$TOKENS"
fetch "$BPE"

verify "$ENCODER" "$ENCODER_SHA256"
verify "$DECODER" "$DECODER_SHA256"
verify "$JOINER" "$JOINER_SHA256"

# The immutable revision pins tokens/BPE to the same model snapshot. Large executable
# ONNX payloads additionally require explicit published SHA-256 checks above.
[[ -s "${TMP}/${TOKENS}" ]] || { echo "tokens.txt is empty" >&2; exit 5; }
[[ -s "${TMP}/${BPE}" ]] || { echo "bpe.model is empty" >&2; exit 5; }

rm -rf "$DEST"
mkdir -p "$DEST"
cp "${TMP}/${ENCODER}" "$DEST/$ENCODER"
cp "${TMP}/${DECODER}" "$DEST/$DECODER"
cp "${TMP}/${JOINER}" "$DEST/$JOINER"
cp "${TMP}/${TOKENS}" "$DEST/tokens.txt"
cp "${TMP}/${BPE}" "$DEST/bpe.model"
printf '%s\n' 'offline-transducer' > "$DEST/asr-backend.txt"
printf '%s\n' "${REPO}@${REVISION}" > "$DEST/model-source.txt"

printf '%s\n' "Prepared Gipformer 65M INT8 for THT Voice:"
find "$DEST" -maxdepth 1 -type f -printf '  %f  %s bytes\n' | sort
printf '%s\n' "Backend: offline-transducer (Sherpa OfflineRecognizer)"
printf '%s\n' "Model assets are staged locally and remain ignored by Git."
