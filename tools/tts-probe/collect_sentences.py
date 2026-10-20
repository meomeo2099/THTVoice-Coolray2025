#!/usr/bin/env python3
"""Collect every Vietnamese sentence THT Voice may speak (string literals with Vietnamese letters
in VoiceResponseFormatter, VoiceAssistantService and voice/), interpolations replaced by a sample
value. Regex fragments and log strings are skipped. Output: one sentence per line."""
import re, sys, pathlib
root = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2])
STRING_RE = re.compile(r'"((?:\\.|[^"\\])*)"')
VI = re.compile(r"[À-ỹĐđ]")
REGEX_HINT = re.compile(r"\(\?|\\\\[bdswp]|\[\^|\|\(|\)\?|\$\d|\\\\\\\\|\{\d|^\^|\$$")
files = [p for p in root.rglob("*.kt") if "/voice/" in str(p) or p.name in ("VoiceAssistantService.kt",)]
seen, sentences = set(), []
for p in sorted(files):
    for m in STRING_RE.finditer(p.read_text(encoding="utf-8")):
        s = m.group(1)
        if not VI.search(s) or REGEX_HINT.search(s) or "|" in s or len(s) < 6:
            continue
        s = re.sub(r"\$\{[^}]*\}", "3", s)
        s = re.sub(r"\$[a-zA-Z_]+", "3", s)
        s = s.replace('\\"', '"').replace("\\n", " ").strip()
        # Spoken replies are sentences: capitalised and ending with . ! ? (normalizer fragments are not).
        if not (s[:1].isupper() and s[-1:] in ".!?") or len(re.findall(r"[^\W_]+", s)) < 2 or s in seen:
            continue
        seen.add(s); sentences.append(s)
out.write_text("\n".join(sentences) + "\n", encoding="utf-8")
print(f"{len(sentences)} sentences from {len(files)} files")
