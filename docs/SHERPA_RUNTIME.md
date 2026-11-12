# Sherpa Android runtime

The owner requested the newest released Sherpa-ONNX runtime on 12 November 2026. The newest versioned library release checked was [v2.0.1](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1). GitHub's generic `/releases/latest` currently points to a separate forced-alignment model asset release; that tag is not an Android runtime upgrade.

- Runtime: official `sherpa-onnx-2.0.1.aar`, replacing 1.13.8 in Gradle, fetch script, hardware-build metadata defaults/fixtures and Python TTS probe.
- SHA256: `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`, from the [official release assets](https://github.com/k2-fsa/sherpa-onnx/releases/expanded_assets/v2.0.1).
- Download: `https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar`. Pin the version and checksum; do not dynamically select “latest” at build or app startup.
- `scripts/verify-sherpa-runtime.py` validates the actual AAR digest, its manifest minimum SDK against the head unit's API28, required ASR/KWS/TTS/online-denoiser JVM classes and ARM32/ARM64 JNI ELF architecture. CI compiles all production call sites against that same AAR before packaging.
- The upstream Android sample for v2.0.1 uses minSdk21 and compileSdk32; the downloaded library is checked independently. This is source/package compatibility evidence, not a head-unit performance benchmark.
- Existing Vietnamese 65M model files, backend marker, CPU provider, thread limits, KWS profile and TTS voice pack remain unchanged. Upgrading the runtime does not retrain the model, prove fewer word errors, or introduce Google/network recognition.
- Local shell cannot download GitHub binaries in this environment (DNS restricted). CI performs the pinned download, archive verification and full Android build; native initialization, microphone quality and latency on the actual vehicle remain to be tested after installation.
