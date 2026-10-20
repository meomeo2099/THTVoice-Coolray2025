#!/usr/bin/env bash
set -euo pipefail

HF_REPO="contextboxai/Kokoro-Vietnamese"
HF_REV="9f210d622209fcc216fe2ac6159fed2ff381cb8a"
HF_BASE="https://huggingface.co/${HF_REPO}/resolve/${HF_REV}"
ESPEAK_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"

MODEL_SHA256="da191277f58633649a9c0d2ae8012e80ef57ea8e2a56e30323c0f7df1ca29087"
NGOC_HUYEN_SHA256="2ae069207dedfd62700957d84d9dec12268a0f115adca63129faf58a6196812a"
DIEM_TRINH_SHA256="19ac1ed49f46256a8d04e434541d4836818216fe07a3a00dc0db9f3adec01fa3"
ESPEAK_SHA256="4135ccf82e1f40613491c0874d4945ae9e9c7840933d8e25a6f9e003d9ebf533"

DEST="${THTVOICE_KOKORO_TTS_DIR:-app/src/main/assets/models/tts/kokoro_vi}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fetch() {
  local url="$1" out="$2"
  echo "Downloading $(basename "$out")..."
  curl -fL --retry 4 --retry-delay 2 --connect-timeout 30 "$url" -o "$out"
  test -s "$out"
}

verify() {
  local expected="$1" file="$2"
  local actual
  actual="$(sha256sum "$file" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    echo "SHA-256 mismatch for $file" >&2
    echo "  expected=$expected" >&2
    echo "  actual=$actual" >&2
    exit 20
  fi
}

fetch "$HF_BASE/kokoro_vi.onnx?download=true" "$TMP/kokoro_vi.onnx"
fetch "$HF_BASE/config.json?download=true" "$TMP/config.json"
fetch "$HF_BASE/voicepacks/ngoc_huyen.pt?download=true" "$TMP/ngoc_huyen.pt"
fetch "$HF_BASE/voicepacks/diem_trinh.pt?download=true" "$TMP/diem_trinh.pt"
fetch "$ESPEAK_URL" "$TMP/espeak-ng-data.tar.bz2"

verify "$MODEL_SHA256" "$TMP/kokoro_vi.onnx"
verify "$NGOC_HUYEN_SHA256" "$TMP/ngoc_huyen.pt"
verify "$DIEM_TRINH_SHA256" "$TMP/diem_trinh.pt"
verify "$ESPEAK_SHA256" "$TMP/espeak-ng-data.tar.bz2"

tar -xjf "$TMP/espeak-ng-data.tar.bz2" -C "$TMP"
if [[ ! -d "$TMP/espeak-ng-data" ]]; then
  echo "espeak-ng-data archive layout changed" >&2
  exit 21
fi

# Build-time only: vig2p creates the training-compatible Vietnamese lexicon;
# onnx adds Sherpa metadata to the copied graph. Neither is needed on the car.
python3 -m pip install --disable-pip-version-check --quiet "vig2p==0.1.2" "onnx==1.17.0"
python3 scripts/prepare-kokoro-vi-runtime.py \
  --model "$TMP/kokoro_vi.onnx" \
  --config "$TMP/config.json" \
  --ngoc-huyen "$TMP/ngoc_huyen.pt" \
  --diem-trinh "$TMP/diem_trinh.pt" \
  --espeak-data "$TMP/espeak-ng-data" \
  --source-root app/src/main/java \
  --output "$DEST" \
  --source-revision "$HF_REV"

cat >> "$DEST/SOURCE.txt" <<EOF
source_model_expected_sha256=$MODEL_SHA256
ngoc_huyen_sha256=$NGOC_HUYEN_SHA256
diem_trinh_sha256=$DIEM_TRINH_SHA256
espeak_ng_data_sha256=$ESPEAK_SHA256
packager_onnx_version=1.17.0
EOF

for required in kokoro_vi.onnx voices.bin tokens.txt lexicon-vi.txt config.json voice-map.txt SOURCE.txt; do
  test -s "$DEST/$required"
done
test -d "$DEST/espeak-ng-data"
test "$(stat -c%s "$DEST/voices.bin")" -eq 1044480
grep -qx 'speaker_0=ngoc_huyen' "$DEST/SOURCE.txt"
grep -qx 'speaker_1=diem_trinh' "$DEST/SOURCE.txt"
grep -qx 'frontend=vig2p==0.1.2' "$DEST/SOURCE.txt"

echo "Kokoro Vietnamese TTS staged for THT Voice:"
find "$DEST" -maxdepth 1 -type f -printf '  %f  %s bytes\n' | sort
printf '  espeak-ng-data/  %s files\n' "$(find "$DEST/espeak-ng-data" -type f | wc -l)"
echo "  voices: sid=0 Ngọc Huyền | sid=1 Diễm Trinh"
echo "  source: ${HF_REPO}@${HF_REV}"
