#!/usr/bin/env python3
"""Self-test for split-kokoro-pack.py (deterministic zip, manifest, APK part)."""
import hashlib
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("split-kokoro-pack.py")


def stage(root: Path) -> Path:
    full = root / "full" / "kokoro_vi"
    (full / "espeak-ng-data" / "voices").mkdir(parents=True)
    for name, body in {
        "kokoro_vi.onnx": b"model" * 100,
        "voices.bin": b"\0" * 64,
        "tokens.txt": b"a 1\n",
        "lexicon-vi.txt": b"xin x i n\n",
        "config.json": b"{}\n",
        "voice-map.txt": b"0 ngoc_huyen\n",
        "SOURCE.txt": b"revision=9f210d622209fcc216fe2ac6159fed2ff381cb8a\n",
        "espeak-ng-data/phontab": b"data",
        "espeak-ng-data/voices/vi": b"vi",
    }.items():
        (full / name).write_bytes(body)
    return full


def run(full: Path, assets: Path, out: Path) -> str:
    subprocess.run([sys.executable, str(SCRIPT), str(full), str(assets), str(out)], check=True, capture_output=True)
    return (out / "SHA256.txt").read_text().split()[0]


with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    full = stage(root)
    sha1 = run(full, root / "assets1", root / "out1")
    sha2 = run(full, root / "assets2", root / "out2")
    assert sha1 == sha2, "pack zip must be deterministic"
    pack = root / "out1" / "THTVoice-kokoro-vi-9f210d62.zip"
    assert hashlib.sha256(pack.read_bytes()).hexdigest() == sha1
    names = zipfile.ZipFile(pack).namelist()
    assert names[:2] == ["kokoro_vi.onnx", "voices.bin"], names
    assert "espeak-ng-data/voices/vi" in names and "PACK_SOURCE.txt" in names
    assert not any(n in names for n in ["lexicon-vi.txt", "tokens.txt"]), names
    assets = root / "assets1"
    assert sorted(p.name for p in assets.iterdir()) == sorted(
        ["tokens.txt", "lexicon-vi.txt", "config.json", "voice-map.txt", "SOURCE.txt", "PACK.txt"]
    )
    manifest = (assets / "PACK.txt").read_text()
    assert f"sha256={sha1}" in manifest and "file=THTVoice-kokoro-vi-9f210d62.zip" in manifest
    assert f"size={pack.stat().st_size}" in manifest
print("split-kokoro-pack self-test PASS")
