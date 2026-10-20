#!/usr/bin/env python3
"""Synthesize THT Voice replies with the real Kokoro pack, the same way the app does
(KokoroVietnameseTextFrontend: one CJK alias per lexicon word, punctuation only), and report
where sound sits so trailing artifacts can be measured. Output: results/report.txt + PNGs."""
import re, sys, wave, struct, math, subprocess, pathlib, unicodedata
import numpy as np
import sherpa_onnx

pack = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
SPACE = ""; BASE = 0x4E00
WORD = re.compile(r"[^\W_]+", re.UNICODE)
PUNCT = {',': ',', '.': '.', '!': '!', '?': '?', ';': ',', ':': ',', '…': '.'}

entries = {}
for line in (pack / "lexicon-vi.txt").read_text(encoding="utf-8").splitlines():
    t = line.strip()
    if not t or t.startswith("#"): continue
    w = t.split(" ", 1)[0].strip().lower(); ph = t.split(" ", 1)[1].strip() if " " in t else ""
    if w and ph and w not in entries: entries[w] = ph
alias = {w: chr(BASE + i) for i, w in enumerate(entries)}
lex = out / "lexicon-vi-sherpa.txt"
lex.write_text("".join(f"{alias[w]} {p} {SPACE}\n" for w, p in entries.items()), encoding="utf-8")
toks = [l for l in (pack / "tokens.txt").read_text(encoding="utf-8").split("\n") if l]
space_id = next(l.rsplit(" ", 1)[1].strip() for l in toks if l.startswith(" ") and l.rsplit(" ", 1)[0].strip() == "")
tok = out / "tokens-sherpa.txt"; tok.write_text("\n".join(toks) + "\n" + f"{SPACE} {space_id}\n", encoding="utf-8")

_terms_src = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/java/com/voicegeely/app/voice/SpokenTerms.kt"
SPOKEN = re.findall(r'"([^"]+)" to "([^"]*)"', _terms_src.read_text(encoding="utf-8")) if _terms_src.exists() else []


def spoken_terms(s):
    """Python port of SpokenTerms.forSpeech (keep in sync)."""
    for term, spoken in SPOKEN:
        s = re.sub(r"(?<![\w])" + re.escape(term) + r"(?![\w])", spoken, s, flags=re.IGNORECASE)
    s = re.sub(r"[ \t]+", " ", s); s = re.sub(r" ([,.!?;:])", r"\1", s)
    return s.strip()


def normalize(s):
    s = spoken_terms(s.strip()).replace("Wi-Fi", "wifi").replace("wi-fi", "wifi")
    return unicodedata.normalize("NFC", s).lower()

def model_text(raw, final_punct="."):
    text = normalize(raw)
    words = set(WORD.findall(text)); missing = {w for w in words if w not in entries}
    if missing:
        text = WORD.sub(lambda m: "" if m.group(0) in missing else m.group(0), text)
        text = re.sub(r"\s+([,.!?])", r"\1", text); text = re.sub(r"\s+", " ", text).strip()
    o = []; pend = None; i = 0
    while i < len(text):
        m = WORD.search(text, i); end = m.start() if m else len(text)
        for c in text[i:end]:
            if c in PUNCT: pend = PUNCT[c]
        if not m: break
        a = alias.get(m.group(0))
        if a:
            if pend and o: o.append(pend)
            pend = None; o.append(a)
        i = m.end()
    if o and final_punct is not None: o.append(pend or final_punct)
    elif o and final_punct is None and pend and pend != '.': pass
    return "".join(o), sorted(missing)

cfg = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
        model=str(pack / "kokoro_vi.onnx"), voices=str(pack / "voices.bin"), tokens=str(tok),
        data_dir=str(pack / "espeak-ng-data"), lexicon=str(lex), lang="", length_scale=1.0),
    num_threads=2, debug=False, provider="cpu"))
tts = sherpa_onnx.OfflineTts(cfg)

def segments(x, sr):
    f = sr // 100; n = (len(x) + f - 1) // f
    r = np.array([math.sqrt(float(np.mean(x[k*f:(k+1)*f] ** 2))) if len(x[k*f:(k+1)*f]) else 0 for k in range(n)])
    if r.max() <= 0: return [], r
    s = r >= r.max() * 0.08; segs = []; st = None; gap = 0
    for k, v in enumerate(s):
        if v:
            if st is None: st = k
            last = k; gap = 0
        elif st is not None:
            gap += 1
            if gap >= 4: segs.append((st * 10, (last + 1) * 10, round(float(r[st:last+1].max() / r.max()), 2))); st = None
    if st is not None: segs.append((st * 10, (last + 1) * 10, round(float(r[st:last+1].max() / r.max()), 2)))
    return segs, r

def save(name, x, sr):
    p = out / f"{name}.wav"
    with wave.open(str(p), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in x))
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(p), "-lavfi", "showspectrumpic=s=900x260:legend=1", str(out / f"{name}.png")])

import os
SENT_FILE = pathlib.Path(os.environ.get("SENTENCES", str(pathlib.Path(__file__).parent / "sentences.txt")))
sentences = [l.strip() for l in SENT_FILE.read_text(encoding="utf-8").splitlines() if l.strip()]


def tail_trim(x, sr):
    """Python port of app TtsTailTrim (keep in sync)."""
    frame = max(1, sr * 10 // 1000); n = (len(x) + frame - 1) // frame
    if n == 0: return x
    rms = np.array([math.sqrt(float(np.mean(x[k*frame:(k+1)*frame] ** 2))) for k in range(n)])
    peak = rms.max()
    if peak <= 0: return x
    snd = rms >= peak * 0.08
    idx = np.where(snd)[0]
    if len(idx) == 0: return x
    last = int(idx[-1]); start = last; silent = 0; i = last - 1
    while i >= 0:
        if snd[i]: start = i; silent = 0
        else:
            silent += 1
            if silent * 10 >= 40: break
        i -= 1
    prev = start - 1
    while prev >= 0 and not snd[prev]: prev -= 1
    gap = (start - prev - 1) * 10; tail = (last - start + 1) * 10
    touches = (n - 1 - last) * 10 <= 40
    if prev >= 0 and ((gap >= 150 and tail <= 200) or (touches and gap >= 100 and tail <= 320)): last = prev
    end = min(len(x), (last + 1) * frame + sr * 60 // 1000)
    return x[:end]


if os.environ.get("AUDIT") == "1":
    # Every reply, both voices, exactly as the app plays it (TtsTailTrim). Flags:
    #  TAIL    = a short separate burst still sits at the end after trimming (extra sound left)
    #  TRIMMED = the trim removed a burst; listen to confirm it was noise, not a real last word
    # WAVs (before/after) of every flagged sentence go to results/flagged/ for listening.
    flagged_dir = out / "flagged"; flagged_dir.mkdir(exist_ok=True)
    lines = []; miss_count = 0; counts = {"TAIL": 0, "TRIMMED": 0}; missing_all = {}; n_flag = 0
    for idx, s in enumerate(sentences):
        mt, miss = model_text(s, ".")
        for w in miss: missing_all[w] = missing_all.get(w, 0) + 1
        if miss: lines.append(f"MISSING {','.join(miss)}  ::  {s}"); miss_count += 1
        if not mt:
            lines.append(f"EMPTY  ::  {s}"); continue
        for sid in (0, 1):
            a = tts.generate(mt, sid=sid, speed=1.0)
            raw = np.array(a.samples, dtype=np.float32); sr = a.sample_rate
            x = tail_trim(raw, sr)
            raw_segs, _ = segments(raw, sr); segs, _ = segments(x, sr)
            flags = []
            if len(segs) >= 2:
                (ps, pe, _), (ls, le, _) = segs[-2], segs[-1]
                if ls - pe >= 100 and le - ls <= 320:
                    flags.append(f"TAIL {ls}-{le}ms after gap {ls-pe}ms"); counts["TAIL"] += 1
            if raw_segs and segs and raw_segs[-1][1] > segs[-1][1] + 20:
                cs, ce, _ = raw_segs[-1]
                flags.append(f"TRIMMED {cs}-{ce}ms"); counts["TRIMMED"] += 1
            if flags:
                n_flag += 1
                name = f"{idx:03d}_v{sid}"
                save(f"flagged/{name}_truoc", raw, sr); save(f"flagged/{name}_sau", x, sr)
                lines.append(f"[{name}] giong={'Ngoc Huyen' if sid == 0 else 'Diem Trinh'} " + " | ".join(flags) + f"  ::  {s}")
    summary = [f"sentences={len(sentences)} voices=2 with_missing={miss_count} tail_left={counts['TAIL']} trimmed={counts['TRIMMED']}",
               "missing words by count: " + ", ".join(f"{w}({c})" for w, c in sorted(missing_all.items(), key=lambda kv: -kv[1]))]
    (out / "audit.txt").write_text("\n".join(summary + [""] + lines) + "\n", encoding="utf-8")
    print("\n".join(summary))
    sys.exit(0)
report = []
for sid in (0, 1):
    for idx, s in enumerate(sentences):
        for variant, fp in (("dot", "."), ("none", None), ("comma", ",")):
            mt, miss = model_text(s, fp)
            if not mt: report.append(f"sid={sid} [{variant}] {s!r} -> nothing speakable missing={miss}"); continue
            a = tts.generate(mt, sid=sid, speed=1.0)
            x = np.array(a.samples, dtype=np.float32)
            segs, _ = segments(x, a.sample_rate)
            report.append(f"sid={sid} [{variant}] {s!r} missing={miss} dur={len(x)*1000//a.sample_rate}ms segs={segs}")
            if idx < 6 and sid == 0: save(f"s{idx}_{variant}", x, a.sample_rate)
(out / "report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
print("\n".join(report))
