#!/usr/bin/env bash
set -euo pipefail

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

APK="$TMP/app-debug.apk"
RUNTIME="$TMP/sherpa-onnx-1.13.8.aar"
MATRIX="$TMP/IHU689G_TEST_MATRIX.csv"
ASR="$TMP/asr"
KWS="$TMP/kws"
TTS="$TMP/tts"
KOKORO="$TTS/kokoro_vi"
OUT="$TMP/output"
TRIGGER="$TMP/.hardware-test-trigger"

mkdir -p "$ASR" "$KWS" "$KOKORO/espeak-ng-data"
printf 'fake-apk\n' > "$APK"
printf 'fake-runtime\n' > "$RUNTIME"
printf 'id,group,test_case,expected,result,notes\nT-01,Test,Example,Example,NOT_TESTED,\n' > "$MATRIX"
printf 'fake-encoder\n' > "$ASR/encoder.int8.onnx"
printf 'g-group/test@123\n' > "$ASR/model-source.txt"
printf 'offline-transducer\n' > "$ASR/asr-backend.txt"
printf 'fake-kws\n' > "$KWS/encoder.int8.onnx"
printf 'k2-fsa/test-kws\n' > "$KWS/model-source.txt"
printf '@HEY_THT\n' > "$KWS/keywords.txt"
printf '@HELLO_GEELY\n' > "$KWS/keywords-hello-geely.txt"
printf '@HEY_GEELY\n' > "$KWS/keywords-hey-geely.txt"
printf 'fake-kokoro-model\n' > "$KOKORO/kokoro_vi.onnx"
head -c 1044480 /dev/zero > "$KOKORO/voices.bin"
printf 'a 1\n' > "$KOKORO/tokens.txt"
printf 'xin x i n\n' > "$KOKORO/lexicon-vi.txt"
printf '{}\n' > "$KOKORO/config.json"
printf '0 ngoc_huyen Ngọc Huyền\n1 diem_trinh Diễm Trinh\n' > "$KOKORO/voice-map.txt"
printf 'source=contextboxai/Kokoro-Vietnamese\nrevision=9999999999999999999999999999999999999999\nfrontend=vig2p==0.1.2\nspeaker_0=ngoc_huyen\nspeaker_1=diem_trinh\n' > "$KOKORO/SOURCE.txt"
printf 'data\n' > "$KOKORO/espeak-ng-data/phontab"
printf 'trigger=test\nbase=1111111111111111111111111111111111111111\npurpose=regression\n' > "$TRIGGER"

GITHUB_SHA=2222222222222222222222222222222222222222 \
GITHUB_REF=refs/heads/hardware-test/test \
OUTPUT_DIR="$OUT" APK_PATH="$APK" ASR_DIR="$ASR" KWS_DIR="$KWS" TTS_ROOT="$TTS" \
RUNTIME_AAR="$RUNTIME" MATRIX_PATH="$MATRIX" TRIGGER_PATH="$TRIGGER" \
bash scripts/create-hardware-build-info.sh >/dev/null

for file in THTVoice-IHU689G-hardware-test.apk SHA256.txt BUILD_INFO.txt ASSET_SHA256.txt \
  IHU689G_TEST_MATRIX.csv probe-scene-contract.ps1; do
test -s "$OUT/$file"
done
(cd "$OUT" && sha256sum -c SHA256.txt >/dev/null)

grep -qx 'git_sha=2222222222222222222222222222222222222222' "$OUT/BUILD_INFO.txt"
grep -qx 'source_main_sha=1111111111111111111111111111111111111111' "$OUT/BUILD_INFO.txt"
grep -qx 'git_ref=refs/heads/hardware-test/test' "$OUT/BUILD_INFO.txt"
grep -qx 'asr_backend=offline-transducer' "$OUT/BUILD_INFO.txt"
grep -qx 'wake_phrase=Hey THT' "$OUT/BUILD_INFO.txt"
grep -qx 'wake_profiles=Hey THT|Hello Geely|Hey Geely' "$OUT/BUILD_INFO.txt"
grep -qx 'tts_voice_assets=ngoc_huyen+diem_trinh' "$OUT/BUILD_INFO.txt"
grep -qx 'tts_backend=kokoro-vietnamese-sherpa' "$OUT/BUILD_INFO.txt"
grep -qx 'tts_source_revision=9999999999999999999999999999999999999999' "$OUT/BUILD_INFO.txt"
grep -q '^apk_sha256=[0-9a-f]\{64\}$' "$OUT/BUILD_INFO.txt"
grep -q '^sherpa_runtime_sha256=[0-9a-f]\{64\}$' "$OUT/BUILD_INFO.txt"
grep -q '^test_matrix_sha256=[0-9a-f]\{64\}$' "$OUT/BUILD_INFO.txt"
grep -q '^scene_probe_sha256=[0-9a-f]\{64\}$' "$OUT/BUILD_INFO.txt"
grep -q '^asset_manifest_sha256=[0-9a-f]\{64\}$' "$OUT/BUILD_INFO.txt"
grep -qx 'test_matrix=IHU689G_TEST_MATRIX.csv' "$OUT/BUILD_INFO.txt"
grep -qx 'scene_probe=probe-scene-contract.ps1' "$OUT/BUILD_INFO.txt"
for token in sherpa-onnx-1.13.8.aar encoder.int8.onnx kokoro_vi.onnx voices.bin lexicon-vi.txt SOURCE.txt \
  IHU689G_TEST_MATRIX.csv probe-scene-contract.ps1; do
grep -q "$token" "$OUT/ASSET_SHA256.txt"
done

cmp -s "$OUT/IHU689G_TEST_MATRIX.csv" "$MATRIX"
cmp -s "$OUT/probe-scene-contract.ps1" scripts/probe-scene-contract.ps1

BAD_TRIGGER="$TMP/.hardware-test-trigger.bad"
printf 'base=not-a-sha\n' > "$BAD_TRIGGER"
if GITHUB_SHA=2222222222222222222222222222222222222222 \
   GITHUB_REF=refs/heads/hardware-test/test \
   OUTPUT_DIR="$TMP/bad-output" APK_PATH="$APK" ASR_DIR="$ASR" KWS_DIR="$KWS" TTS_ROOT="$TTS" \
   RUNTIME_AAR="$RUNTIME" MATRIX_PATH="$MATRIX" TRIGGER_PATH="$BAD_TRIGGER" \
   bash scripts/create-hardware-build-info.sh >/dev/null 2>&1; then
  echo "Invalid source main SHA was not rejected" >&2
  exit 1
fi

echo "Hardware build metadata test passed."
