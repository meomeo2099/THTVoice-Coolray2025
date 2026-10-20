#!/usr/bin/env python3
"""Kiểm tra whitelist priv-app khớp đúng danh sách quyền trong AndroidManifest."""
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
xml = (root / "scripts/privapp-permissions-com.tht.voice.coolray2025.xml").read_text(encoding="utf-8")
wanted = set(re.findall(r'uses-permission android:name="([^"]+)"', manifest))
listed = set(re.findall(r'<permission name="([^"]+)"/>', xml))
if 'package="com.tht.voice.coolray2025"' not in xml:
    sys.exit("privapp XML phải dùng package com.tht.voice.coolray2025")
missing, extra = wanted - listed, listed - wanted
if missing or extra:
    sys.exit(f"privapp XML lệch manifest. Thiếu: {sorted(missing)} Thừa: {sorted(extra)}")
print(f"privapp permissions OK ({len(listed)})")
