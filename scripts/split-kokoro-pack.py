#!/usr/bin/env python3
"""Split the staged Kokoro runtime into the APK part and the downloadable voice pack.

Usage: split-kokoro-pack.py <full_dir> <apk_assets_dir> <pack_out_dir>

- <apk_assets_dir> gets the small files (tokens, lexicon, config, voice map, SOURCE) and
  PACK.txt, which pins the pack by file name, SHA-256 and size.
- <pack_out_dir> gets THTVoice-kokoro-vi-<rev8>.zip (kokoro_vi.onnx, voices.bin,
  espeak-ng-data/, PACK_SOURCE.txt) and SHA256.txt. The zip is deterministic (fixed
  timestamps, sorted entries), so the same inputs give the same SHA-256.
"""
import hashlib
import shutil
import sys
import zipfile
from pathlib import Path

SMALL = ["tokens.txt", "lexicon-vi.txt", "config.json", "voice-map.txt", "SOURCE.txt"]
HEAVY = ["kokoro_vi.onnx", "voices.bin"]
HEAVY_DIR = "espeak-ng-data"
FIXED_TIME = (2026, 1, 1, 0, 0, 0)


def revision(source: Path) -> str:
    for line in source.read_text(encoding="utf-8").splitlines():
        if line.startswith("revision="):
            return line.split("=", 1)[1].strip()
    return "unknown"


def add(z: zipfile.ZipFile, path: Path, arcname: str) -> None:
    info = zipfile.ZipInfo(arcname, FIXED_TIME)
    info.external_attr = 0o644 << 16
    # The ONNX graph barely compresses; store it to keep extraction fast on the head unit.
    info.compress_type = zipfile.ZIP_STORED if arcname.endswith(".onnx") else zipfile.ZIP_DEFLATED
    with path.open("rb") as src, z.open(info, "w") as dst:
        shutil.copyfileobj(src, dst, 1 << 20)


def main(full: Path, assets: Path, out: Path) -> None:
    for name in SMALL + HEAVY:
        if not (full / name).is_file() or (full / name).stat().st_size == 0:
            raise SystemExit(f"missing {name} in {full}")
    if not (full / HEAVY_DIR).is_dir():
        raise SystemExit(f"missing {HEAVY_DIR} in {full}")

    rev = revision(full / "SOURCE.txt")
    out.mkdir(parents=True, exist_ok=True)
    pack_name = f"THTVoice-kokoro-vi-{rev[:8]}.zip"
    pack = out / pack_name
    with zipfile.ZipFile(pack, "w") as z:
        for name in HEAVY:
            add(z, full / name, name)
        dir_info = zipfile.ZipInfo(HEAVY_DIR + "/", FIXED_TIME)
        dir_info.external_attr = 0o755 << 16
        z.writestr(dir_info, b"")
        for path in sorted(p for p in (full / HEAVY_DIR).rglob("*") if p.is_file()):
            add(z, path, path.relative_to(full).as_posix())
        add(z, full / "SOURCE.txt", "PACK_SOURCE.txt")

    sha = hashlib.sha256(pack.read_bytes()).hexdigest()
    size = pack.stat().st_size
    (out / "SHA256.txt").write_text(f"{sha}  {pack_name}\n", encoding="utf-8")

    if assets.exists():
        shutil.rmtree(assets)
    assets.mkdir(parents=True)
    for name in SMALL:
        shutil.copy2(full / name, assets / name)
    (assets / "PACK.txt").write_text(
        f"file={pack_name}\nsha256={sha}\nsize={size}\nrevision={rev}\n", encoding="utf-8"
    )
    print(f"voice pack {pack_name} size={size} sha256={sha}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    main(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
