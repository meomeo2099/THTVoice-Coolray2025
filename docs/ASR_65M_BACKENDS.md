# THT Voice — Vietnamese 65M ASR backends

THT Voice supports two Sherpa transducer export styles behind the same microphone/parser pipeline.

## 1. `online-transducer`

Use this only for a true Sherpa `OnlineRecognizer` export with streaming encoder state.

Stage an already supplied streaming archive with:

```bash
bash scripts/prepare-sherpa65m-model.sh /path/to/sherpa-onnx-streaming-zipformer-vi-65M-int8.tar.bz2
```

The script writes:

```text
asr-backend.txt = online-transducer
```

At runtime `SherpaOnlineTransducerAsrEngine` uses `OnlineRecognizer`, provides partial text, and uses Sherpa's online endpoint detector.

## 2. `offline-transducer` — Gipformer 65M INT8

The public G-Group Gipformer ONNX reference implementation uses Sherpa `OfflineRecognizer.from_transducer`. It must not be loaded with `OnlineRecognizer` merely because the architecture is Zipformer/RNNT.

THT Voice pins a known model snapshot and published SHA-256 values for the large ONNX payloads:

```bash
bash scripts/fetch-gipformer65m-int8.sh
```

This stages:

```text
encoder-epoch-35-avg-6.int8.onnx
decoder-epoch-35-avg-6.int8.onnx
joiner-epoch-35-avg-6.int8.onnx
tokens.txt
bpe.model
asr-backend.txt = offline-transducer
model-source.txt = g-group-ai-lab/gipformer-65M-rnnt@8571259
```

`SherpaBufferedOfflineAsrEngine` accepts microphone chunks continuously, detects speech/trailing silence locally, then performs one offline decode at the endpoint. The typed parser, SafetyGate and vehicle executors are unchanged.

Default endpoint parameters are deliberately conservative for the first Coolray test:

```text
speech RMS threshold = 0.012
minimum speech       = 180 ms
trailing silence     = 700 ms
maximum session      = service limit (currently 12 s)
```

These values must be tuned on the real IHU689G microphone/noise environment rather than guessed further in code.

## Backend selection

`SherpaStreamingAsrEngine` is retained as a compatibility facade for the existing service. It reads `asr-backend.txt` and delegates to the correct implementation.

If a model has no marker, THT Voice defaults to `online-transducer` only for backward compatibility with the original explicitly streaming archive.

## GitHub Actions full-ASR test APK

The normal CI build remains lightweight and does not download the 65M model.

Run the `Android build` workflow manually and enable:

```text
include_gipformer65m = true
```

For one-shot automated validation, a push commit whose message starts with `[full-asr]` also stages the model. This is intended for controlled packaging tests only; ordinary commits remain lightweight.

The workflow stages the pinned Gipformer INT8 model before `assembleDebug` and uploads an artifact named:

```text
THTVoice-debug-gipformer65m-apk
```

This produces an APK with ASR model assets for Coolray testing. It is not yet a customer release until the selected TTS voice model(s) and stable release signing are also present.

## Model replacement on app update

`SherpaModelInstaller` compares packaged `asr-backend.txt` and `model-source.txt` with the already installed model. A backend/source change is staged in a temporary directory and swapped only after the required model files are complete, with rollback to the previous model if activation fails.
