# THT Voice — Offline model packaging

THT Voice keeps large ASR/TTS binaries out of normal Git history. A normal PR build verifies source/runtime logic without committing hundreds of MB of model data. The dedicated IHU689G hardware workflow stages the pinned production assets before APK assembly.

## 1. Streaming ASR — Vietnamese 65M INT8

Stage the project ASR with the existing pinned fetch/preparation path:

```bash
bash scripts/fetch-gipformer65m-int8.sh
```

Destination (ignored by Git):

```text
app/src/main/assets/models/sherpa-onnx-streaming-zipformer-vi-65M-int8/
```

The runtime backend is Sherpa `OfflineRecognizer` / offline transducer using the 65M INT8 encoder/decoder/joiner assets.

## 2. Offline response voices — Kokoro Vietnamese

Ngọc Huyền and Diễm Trinh are not two renamed VITS models. They are two Kokoro speaker styles using one shared Vietnamese acoustic model.

Pinned upstream:

```text
contextboxai/Kokoro-Vietnamese
revision 9f210d622209fcc216fe2ac6159fed2ff381cb8a
```

The upstream model repository declares Apache-2.0. Keep its license/notices with any redistribution process required by the product release workflow.

Production files used by THT Voice:

```text
kokoro_vi.onnx
voicepacks/ngoc_huyen.pt
voicepacks/diem_trinh.pt
config.json
```

The source ONNX and both voicepacks are SHA-256 pinned in `scripts/fetch-kokoro-vi-tts.sh`.

Stage them with:

```bash
bash scripts/fetch-kokoro-vi-tts.sh
```

Destination (ignored by Git):

```text
app/src/main/assets/models/tts/kokoro_vi/
```

Generated Android runtime layout:

```text
kokoro_vi.onnx       # copied source graph; style input `ref_s` renamed to `style`, inputs ordered input_ids/style/speed for Sherpa; Sherpa Kokoro metadata added
voices.bin           # sid 0 Ngọc Huyền, sid 1 Diễm Trinh
tokens.txt           # generated from the pinned Kokoro config vocab
lexicon-vi.txt       # generated with vig2p 0.1.2
config.json
voice-map.txt
SOURCE.txt
espeak-ng-data/
```

`voices.bin` is generated without executing PyTorch pickle. The packer reads only the expected raw float32 storage from each hash-pinned torch ZIP voicepack and requires the upstream Kokoro style shape `510 x 1 x 256`. The two styles are concatenated in a fixed order:

```text
sid=0 -> ngoc_huyen -> Ngọc Huyền
sid=1 -> diem_trinh -> Diễm Trinh
```

### Downloadable voice pack (owner decision 29/09/2026)

The ~330 MB Kokoro voice is no longer inside the APK by default. CI stages the full runtime in `build/kokoro-full/kokoro_vi`, then `scripts/split-kokoro-pack.py`:

- keeps the small files in the APK (`tokens.txt`, `lexicon-vi.txt`, `config.json`, `voice-map.txt`, `SOURCE.txt`) plus `PACK.txt` (`file`, `sha256`, `size`, `revision`) that pins the exact pack built with this APK;
- writes a deterministic `THTVoice-kokoro-vi-<rev8>.zip` (`kokoro_vi.onnx`, `voices.bin`, `espeak-ng-data/`, `PACK_SOURCE.txt`) and `SHA256.txt`.

The hardware-test workflow uploads the pack as the `THTVoice-kokoro-vi-voice-pack` artifact; the RC workflow puts it in `release-output/voice-pack/`. Add `tts_delivery=bundled` to `.hardware-test-trigger` for an all-in-one test APK.

In the app (**Cài đặt → Thiết lập giọng nói → Gói giọng đọc**): **Tải về** (progress %, **Hủy**), **Cài từ tệp** (pick the pack zip, or the GitHub artifact zip `THTVoice-kokoro-vi-voice-pack.zip` that wraps it — the inner pack is extracted automatically; works before the Worker serves packs) and **Xóa**. The zip is installed only if its size and SHA-256 match `PACK.txt`; entries are whitelisted (no path escapes) and extracted to a staging folder first. Until the pack is installed the assistant answers with beeps.

**Worker contract (to add on the Cloudflare Worker, outside this repo):**

```text
GET /api/model?name=kokoro_vi&sha256=<pinned sha>        headers: X-Geely-Request, X-Geely-License
  200 {"ok":true,"sha256":"<same sha>","sizeBytes":N,
       "downloadUrl":"https://<worker>/api/model/file?name=kokoro_vi&sha256=<sha>"}
  404 when no pack with that SHA is published
GET /api/model/file?name=kokoro_vi&sha256=<sha>          same license headers → zip bytes
```

The Worker streams the private GitHub Release asset whose SHA-256 matches; the app rejects any `downloadUrl` that is not `https://<worker host>/api/model/file`.

### Install into app storage

On first start the Kokoro runtime is copied from APK assets to app storage (`files/models/tts/kokoro_vi`). The copy is written to a `.installing` folder and swapped in only when complete, with an `.install-stamp` (APK install time + hash of `SOURCE.txt`). Installing a new APK, or a copy that was interrupted, therefore forces a fresh copy; an old test build's copy is never reused. The copy needs roughly the model size (≈330 MB) of free storage. When speech is not ready, Home shows the reason (`Giọng trợ lý • Chưa sẵn sàng • …`).

### Vietnamese pronunciation frontend

The Kokoro-Vietnamese training/inference project uses `vig2p`, not generic Vietnamese eSpeak, as its Vietnamese G2P frontend. THT Voice therefore builds `lexicon-vi.txt` with pinned `vig2p==0.1.2`.

At runtime `KokoroVietnameseTextFrontend` checks every word before synthesis. If a response contains a word missing from the generated lexicon, TTS fails closed and logs the missing word; it does not silently switch that word to a different Vietnamese G2P.

`espeak-ng-data` remains packaged because Sherpa Kokoro requires the data directory contract, but it is not used as a replacement for the project Vietnamese frontend.

## 3. Sherpa runtime

THT Voice already pins:

```text
sherpa-onnx-1.13.8.aar
```

The same AAR supports ASR/KWS and `OfflineTtsKokoroModelConfig`; no second Android ONNX runtime is added.

Kokoro setup uses:

```text
model       = kokoro_vi.onnx
voices      = voices.bin
tokens      = tokens.txt
lexicon     = lexicon-vi.txt
dataDir     = espeak-ng-data
provider    = cpu
numThreads  = 2
```

The model is rejected if Sherpa reports fewer than two speakers.

## 4. CI and hardware build

Normal PR CI intentionally stays light:

```bash
python3 scripts/test-prepare-kokoro-vi-runtime.py
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The packer self-test checks deterministic speaker packing and verifies that no `torch.load`/pickle execution is used. It does not download the 326 MB production model on every PR.

The dedicated `IHU689G hardware test APK` workflow additionally runs:

```bash
bash scripts/fetch-kokoro-vi-tts.sh
```

and verifies all Kokoro files before building the vehicle-test APK. `BUILD_INFO.txt` records:

```text
tts_voice_assets=ngoc_huyen+diem_trinh
tts_backend=kokoro-vietnamese-sherpa
tts_source_revision=<pinned revision>
```

`ASSET_SHA256.txt` contains hashes for the staged TTS runtime alongside ASR/KWS and the hardware evidence matrices.

## 5. Runtime behavior

ASR model missing:

```text
THT Voice reports the offline ASR is not ready
vehicle voice recognition does not fall back to cloud STT
```

Kokoro runtime missing/invalid:

```text
THT Voice reports the selected offline voice unavailable
it does not substitute a different speaker while keeping the selected name
```

Vietnamese lexicon coverage missing:

```text
TTS fails closed and records the uncovered word
no generic eSpeak-Vi fallback is announced as Ngọc Huyền/Diễm Trinh
```

Voice feedback disabled:

```text
TTS is not required
LISTENING / PROCESSING / SUCCESS / ERROR offline sound cues are used instead
```

## 6. Full-offline completion rule

A full-offline vehicle-test build must physically contain and verify:

```text
Sherpa Vietnamese 65M INT8 ASR
+ selectable Sherpa KWS profiles
+ Kokoro Vietnamese shared model
+ Ngọc Huyền speaker style
+ Diễm Trinh speaker style
+ vig2p-generated lexicon
+ local parser/typed vehicle commands
+ VHAL/Android backend
```

The final IHU689G PASS still comes from the real-car validation bundle (latency, audio output, memory/CPU and command feedback), not from CI/model presence alone.

Server side of `/api/model` + `/api/model/file`: `worker/src/update.js` (pack registered in the admin page, bytes in the R2 bucket `MODELS`; steps in `worker/README.md` → Gói giọng đọc).

### How Sherpa really uses the Vietnamese lexicon (30/09/2026)

For a Kokoro model with metadata `version=2`, Sherpa passes `lang` (or, when empty, the metadata `voice`, which can never be empty — default `en-us`) to the frontend, and every **non-CJK** segment then goes straight to eSpeak with that voice. Only segments in U+4E00..U+9FFF go through the lexicon. So until now the vig2p lexicon was never used: Kokoro got eSpeak `vi` phonemes it was not trained on (wrong Vietnamese). A first fix with ASCII aliases (PR #229) made it worse, since those aliases went to eSpeak too.

The app now writes at init:
- `lexicon-vi-sherpa.txt`: each vig2p entry under a one-character CJK alias (U+4E00 + index), ending with a private word-gap token U+E000;
- `tokens-sherpa.txt`: `tokens.txt` plus `U+E000 <id of the space token>`;

and hands Kokoro the aliased text (aliases + single `, . ! ?`, no spaces). Every word then reaches exactly its vig2p phonemes with the word gaps Kokoro was trained on (`KokoroSherpaLexiconTest`).

Server side (30/09/2026): the license Worker (`worker/src/update.js`) serves `/api/model` from the GitHub Releases the release workflow publishes (`voice-pack.json` + the zip; no R2 needed), or from R2 when a pack is registered in the admin page.

