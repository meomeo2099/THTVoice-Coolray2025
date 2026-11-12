#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR="${OUTPUT_DIR:-hardware-test-output}"
APK_PATH="${APK_PATH:-}"
ASR_DIR="${ASR_DIR:-app/src/main/assets/models/sherpa-onnx-streaming-zipformer-vi-65M-int8}"
KWS_DIR="${KWS_DIR:-app/src/main/assets/models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20}"
TTS_ROOT="${TTS_ROOT:-app/src/main/assets/models/tts}"
KOKORO_TTS_DIR="${KOKORO_TTS_DIR:-$TTS_ROOT/kokoro_vi}"
RUNTIME_AAR="${RUNTIME_AAR:-app/libs/sherpa-onnx-2.0.1.aar}"
MATRIX_PATH="${MATRIX_PATH:-docs/phase10/IHU689G_TEST_MATRIX.csv}"
SCENE_PROBE_PATH="${SCENE_PROBE_PATH:-scripts/probe-scene-contract.ps1}"
TRIGGER_PATH="${TRIGGER_PATH:-.hardware-test-trigger}"

if [[ -z "$APK_PATH" ]]; then
  APK_PATH="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n 1 || true)"
fi

require_file() { [[ -s "$1" ]] || { echo "Missing required file: $1" >&2; exit 3; }; }
require_dir() { [[ -d "$1" ]] || { echo "Missing required directory: $1" >&2; exit 3; }; }

require_file "$APK_PATH"
require_file "$RUNTIME_AAR"
require_file "$MATRIX_PATH"
require_file "$SCENE_PROBE_PATH"
require_dir "$ASR_DIR"
require_dir "$KWS_DIR"
require_file "$ASR_DIR/model-source.txt"
require_file "$ASR_DIR/asr-backend.txt"
require_file "$KWS_DIR/model-source.txt"
require_file "$KWS_DIR/keywords.txt"
require_file "$KWS_DIR/keywords-hello-geely.txt"
require_file "$KWS_DIR/keywords-hey-geely.txt"

mkdir -p "$OUTPUT_DIR"
PACKAGE_APK="$OUTPUT_DIR/THTVoice-IHU689G-hardware-test.apk"
PACKAGE_MATRIX="$OUTPUT_DIR/IHU689G_TEST_MATRIX.csv"
PACKAGE_SCENE_PROBE="$OUTPUT_DIR/probe-scene-contract.ps1"
cp "$APK_PATH" "$PACKAGE_APK"
cp "$MATRIX_PATH" "$PACKAGE_MATRIX"
cp "$SCENE_PROBE_PATH" "$PACKAGE_SCENE_PROBE"
(cd "$OUTPUT_DIR" && sha256sum "$(basename "$PACKAGE_APK")" > SHA256.txt)

hash_tree() {
  local root="$1"
  [[ -d "$root" ]] || return 0
  while IFS= read -r -d '' file; do sha256sum "$file"; done < <(find "$root" -type f -print0 | sort -z)
}

ASSET_MANIFEST="$OUTPUT_DIR/ASSET_SHA256.txt"
{
  echo "# THT Voice IHU689G hardware-test asset SHA-256"
  sha256sum "$RUNTIME_AAR"
  hash_tree "$ASR_DIR"
  hash_tree "$KWS_DIR"
  hash_tree "$TTS_ROOT"
  (
    cd "$OUTPUT_DIR"
    sha256sum \
      "$(basename "$PACKAGE_MATRIX")" \
      "$(basename "$PACKAGE_SCENE_PROBE")"
  )
} > "$ASSET_MANIFEST"

kokoro_ready() {
  [[ -d "$KOKORO_TTS_DIR" ]] || return 1
  for file in kokoro_vi.onnx voices.bin tokens.txt lexicon-vi.txt config.json voice-map.txt SOURCE.txt; do
    [[ -s "$KOKORO_TTS_DIR/$file" ]] || return 1
  done
  [[ -d "$KOKORO_TTS_DIR/espeak-ng-data" ]] || return 1
  [[ "$(stat -c%s "$KOKORO_TTS_DIR/voices.bin")" -eq 1044480 ]] || return 1
  grep -qx 'speaker_0=ngoc_huyen' "$KOKORO_TTS_DIR/SOURCE.txt" || return 1
  grep -qx 'speaker_1=diem_trinh' "$KOKORO_TTS_DIR/SOURCE.txt" || return 1
  grep -qx 'frontend=vig2p==0.1.2' "$KOKORO_TTS_DIR/SOURCE.txt" || return 1
}

if kokoro_ready; then
  TTS_STATUS="ngoc_huyen+diem_trinh"
  TTS_BACKEND="kokoro-vietnamese-sherpa"
  TTS_SOURCE="$(awk -F= '$1 == "revision" {print $2; exit}' "$KOKORO_TTS_DIR/SOURCE.txt" | tr -d '\r\n')"
else
  TTS_STATUS="not_staged"
  TTS_BACKEND="not_staged"
  TTS_SOURCE="not_staged"
fi

ASR_SOURCE="$(tr -d '\r\n' < "$ASR_DIR/model-source.txt")"
ASR_BACKEND="$(tr -d '\r\n' < "$ASR_DIR/asr-backend.txt")"
KWS_SOURCE="$(tr -d '\r\n' < "$KWS_DIR/model-source.txt")"
APK_SIZE="$(stat -c '%s' "$PACKAGE_APK")"
APK_SHA256="$(sha256sum "$PACKAGE_APK" | awk '{print $1}')"
MATRIX_SHA256="$(sha256sum "$PACKAGE_MATRIX" | awk '{print $1}')"
SCENE_PROBE_SHA256="$(sha256sum "$PACKAGE_SCENE_PROBE" | awk '{print $1}')"
ASSET_MANIFEST_SHA256="$(sha256sum "$ASSET_MANIFEST" | awk '{print $1}')"
RUNTIME_SHA256="$(sha256sum "$RUNTIME_AAR" | awk '{print $1}')"
GIT_SHA_VALUE="${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
GIT_REF_VALUE="${GITHUB_REF:-unknown}"
SOURCE_MAIN_SHA_VALUE="${SOURCE_MAIN_SHA:-}"

if [[ -z "$SOURCE_MAIN_SHA_VALUE" && -s "$TRIGGER_PATH" ]]; then
  SOURCE_MAIN_SHA_VALUE="$(awk -F= '$1 == "source_main_sha" || $1 == "base" {print $2; exit}' "$TRIGGER_PATH" | tr -d '\r\n')"
fi
if [[ -z "$SOURCE_MAIN_SHA_VALUE" ]]; then
  if [[ "$GIT_REF_VALUE" == refs/heads/hardware-test/* ]]; then
    echo "Missing source main SHA in $TRIGGER_PATH for hardware-test branch" >&2
    exit 4
  fi
  SOURCE_MAIN_SHA_VALUE="$GIT_SHA_VALUE"
fi
if [[ ! "$SOURCE_MAIN_SHA_VALUE" =~ ^[0-9a-fA-F]{40}$ ]]; then
  echo "Invalid source main SHA: $SOURCE_MAIN_SHA_VALUE" >&2
  exit 4
fi

cat > "$OUTPUT_DIR/BUILD_INFO.txt" <<EOF
THT Voice IHU689G hardware-test build
git_sha=${GIT_SHA_VALUE}
source_main_sha=${SOURCE_MAIN_SHA_VALUE}
git_ref=${GIT_REF_VALUE}
apk_size_bytes=${APK_SIZE}
apk_sha256=${APK_SHA256}
sherpa_runtime=$(basename "$RUNTIME_AAR")
sherpa_runtime_sha256=${RUNTIME_SHA256}
asr_model=${ASR_SOURCE}
asr_backend=${ASR_BACKEND}
kws_model=${KWS_SOURCE}
wake_phrase=Hey THT
wake_profiles=Hey THT|Hello Geely|Hey Geely
tts_voice_assets=${TTS_STATUS}
tts_backend=${TTS_BACKEND}
tts_source_revision=${TTS_SOURCE}
test_matrix_sha256=${MATRIX_SHA256}
scene_probe_sha256=${SCENE_PROBE_SHA256}
asset_manifest_sha256=${ASSET_MANIFEST_SHA256}
validation_guide=docs/phase10/PHASE10_QUICKSTART.md
readiness_gate=docs/phase10/HARDWARE_READINESS.md
test_matrix=IHU689G_TEST_MATRIX.csv
scene_probe=probe-scene-contract.ps1
EOF

cat "$OUTPUT_DIR/SHA256.txt"
cat "$OUTPUT_DIR/BUILD_INFO.txt"
echo "Asset manifest: $ASSET_MANIFEST"
