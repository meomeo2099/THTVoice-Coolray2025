#!/usr/bin/env python3
"""Writes voice-pack.json next to the voice pack zip for the license Worker.

The Worker finds the pack an APK asks for (name + SHA-256 from its PACK.txt) by reading this
file from the GitHub Releases, then streams the zip asset. Usage:
  create-voice-pack-json.py <PACK.txt from the APK assets> <voice-pack dir>
"""
import hashlib
import json
import sys
from pathlib import Path

pack_txt, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
info = dict(line.split("=", 1) for line in pack_txt.read_text(encoding="utf-8").splitlines() if "=" in line)
zip_path = out_dir / info["file"]
digest = hashlib.sha256(zip_path.read_bytes()).hexdigest()
if digest != info["sha256"] or zip_path.stat().st_size != int(info["size"]):
    sys.exit(f"voice pack {zip_path} does not match PACK.txt")
(out_dir / "voice-pack.json").write_text(json.dumps(
    {"name": "kokoro_vi", "file": info["file"], "sha256": digest, "size": int(info["size"]), "revision": info.get("revision", "")},
    indent=2) + "\n", encoding="utf-8")
print(f"voice-pack.json: {info['file']} {digest}")
