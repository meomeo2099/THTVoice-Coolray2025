#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import tempfile
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("prepare-kokoro-vi-runtime.py")
spec = importlib.util.spec_from_file_location("tht_kokoro_packer", SCRIPT)
assert spec and spec.loader
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def expect_raises(fn, expected: str) -> None:
    try:
        fn()
    except Exception as exc:
        if expected not in str(exc):
            raise AssertionError(f"Expected {expected!r}, got {exc!r}") from exc
        return
    raise AssertionError(f"Expected failure containing {expected!r}")


with tempfile.TemporaryDirectory(prefix="tht-kokoro-packer-") as tmp_str:
    tmp = Path(tmp_str)
    ngoc = tmp / "ngoc_huyen.pt"
    diem = tmp / "diem_trinh.pt"
    payload_a = bytes([0x11]) * mod.STYLE_BYTES
    payload_b = bytes([0x22]) * mod.STYLE_BYTES

    for path, payload in ((ngoc, payload_a), (diem, payload_b)):
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as zf:
            zf.writestr("archive/data.pkl", b"pickle-is-never-executed")
            zf.writestr("archive/data/0", payload)
            zf.writestr("archive/version", b"3")

    assert mod.extract_style_storage(ngoc) == payload_a
    assert mod.extract_style_storage(diem) == payload_b

    voices = tmp / "voices.bin"
    voices.write_bytes(mod.extract_style_storage(ngoc) + mod.extract_style_storage(diem))
    assert voices.stat().st_size == 1_044_480
    assert voices.read_bytes()[:4] == b"\x11" * 4
    assert voices.read_bytes()[mod.STYLE_BYTES:mod.STYLE_BYTES + 4] == b"\x22" * 4

    bad = tmp / "bad.pt"
    with zipfile.ZipFile(bad, "w", compression=zipfile.ZIP_STORED) as zf:
        zf.writestr("archive/data/0", b"too-small")
    expect_raises(lambda: mod.extract_style_storage(bad), "expected exactly one")

    plain = tmp / "plain.pt"
    plain.write_bytes(b"not-a-zip")
    expect_raises(lambda: mod.extract_style_storage(plain), "not a torch zip archive")

    source = SCRIPT.read_text(encoding="utf-8")
    assert "torch.load" not in source
    assert "pickle.load" not in source

    # Kokoro vocab IDs are intentionally sparse. Validation follows n_token/max-id,
    # not len(vocab), while keeping the required space token at id 16.
    vocab = {chr(0x1000 + i): i for i in range(120) if i not in {7, 8, 22, 29, 40, 73, 84}}
    vocab.pop(chr(0x1000 + 16), None)
    vocab[" "] = 16
    config = tmp / "config.json"
    config.write_text(
        json.dumps({"n_token": 178, "vocab": vocab}, ensure_ascii=False),
        encoding="utf-8",
    )
    loaded = mod.load_vocab(config)
    assert loaded[" "] == 16
    assert len(loaded) < 150

    tokens = tmp / "tokens.txt"
    mod.write_tokens(loaded, tokens)
    token_text = tokens.read_text(encoding="utf-8")
    assert f"{chr(0x1000)} 0\n" in token_text

    invalid_config = tmp / "invalid-config.json"
    invalid_config.write_text(
        json.dumps({"n_token": max(loaded.values()), "vocab": loaded}, ensure_ascii=False),
        encoding="utf-8",
    )
    expect_raises(lambda: mod.load_vocab(invalid_config), "Unexpected Kokoro Vietnamese vocab contract")

    normalized = mod.normalize_runtime_text("Mở Wi-Fi và đọc IP, Follow-Me-Home")
    assert "wifi" in normalized
    assert "ip" in normalized
    assert "follow me home" in normalized

from types import SimpleNamespace as NS


def fake_graph(input_names, nodes, outputs=("audio",)):
    return NS(
        input=[NS(name=name) for name in input_names],
        output=[NS(name=name) for name in outputs],
        value_info=[],
        node=nodes,
    )


# contextboxai/Kokoro-Vietnamese @9f210d6 exports `ref_s`; Sherpa needs tokens, style, speed by position.
sub = fake_graph([], [NS(input=["ref_s"], output=["inner"], attribute=[])], outputs=("inner",))
graph = fake_graph(
    ["input_ids", "ref_s", "speed"],
    [
        NS(input=["ref_s", "input_ids"], output=["h"], attribute=[]),
        NS(input=["h", "speed"], output=["audio"], attribute=[NS(graphs=[], g=sub)]),
    ],
)
assert mod.normalize_kokoro_inputs(graph) == "ref_s"
assert [item.name for item in graph.input] == ["input_ids", "style", "speed"]
assert graph.node[0].input == ["style", "input_ids"]
assert sub.node[0].input == ["style"]

reordered = fake_graph(["speed", "style", "input_ids"], [NS(input=["style"], output=["audio"], attribute=[])])
assert mod.normalize_kokoro_inputs(reordered) == "style"
assert [item.name for item in reordered.input] == ["input_ids", "style", "speed"]

expect_raises(
    lambda: mod.normalize_kokoro_inputs(fake_graph(["input_ids", "speed"], [])),
    "Kokoro ONNX IO contract changed",
)
expect_raises(
    lambda: mod.normalize_kokoro_inputs(fake_graph(["input_ids", "ref_s", "style", "speed"], [])),
    "Kokoro ONNX IO contract changed",
)
expect_raises(
    lambda: mod.normalize_kokoro_inputs(
        fake_graph(["input_ids", "ref_s", "speed"], [NS(input=["ref_s"], output=["style"], attribute=[])])
    ),
    "already uses the name 'style'",
)

# vig2p leaves digits untouched; numeric lexicon keys must be spelled before phonemizing.
assert mod.spoken_form("0") == "không"
assert mod.spoken_form("10") == "mười"
assert mod.spoken_form("15") == "mười lăm"
assert mod.spoken_form("20") == "hai mươi"
assert mod.spoken_form("21") == "hai mươi mốt"
assert mod.spoken_form("22") == "hai mươi hai"
assert mod.spoken_form("24") == "hai mươi tư"
assert mod.spoken_form("25") == "hai mươi lăm"
assert mod.spoken_form("32") == "ba mươi hai"
assert mod.spoken_form("100") == "một trăm"
assert mod.spoken_form("wifi") == "wifi"
assert mod.spoken_form("mp3") == "mp ba"
expect_raises(lambda: mod.vietnamese_number_words(101), "outside packaged lexicon range")

import sys
import types

fake_vig2p = types.ModuleType("vig2p")
fake_vig2p.phonemize_text = lambda text: text  # identity: digits would leak through unspelled
sys.modules["vig2p"] = fake_vig2p
letters = {ch: i for i, ch in enumerate(sorted(set("không mười hai ba mươi mốt") - {" "}), start=17)}
letters[" "] = 16
entries = mod.phonemize_words(["0", "21"], letters)
assert entries == [("0", "không"), ("21", "hai mươi mốt")]
expect_raises(lambda: mod.phonemize_words(["xyz", "qqq"], letters), "'xyz'")
expect_raises(lambda: mod.phonemize_words(["xyz", "qqq"], letters), "'qqq'")
lenient = mod.phonemize_words(["xyz", "0"], letters, strict=False)
assert [w for w, _ in lenient] == ["0"], lenient
del sys.modules["vig2p"]

print("Kokoro Vietnamese runtime packer self-test PASS")


# Extra spoken sources: Vietnamese words only, lenient phonemize.
import tempfile as _tf
with _tf.TemporaryDirectory() as _d:
    root = Path(_d)
    (root / "voice").mkdir()
    (root / "voice" / "X.kt").write_text('val a = "Không nghe rõ lệnh"\nval b = "THT Voice core"\nval c = "log hash=1"\n', encoding="utf-8")
    (root / "VoiceAssistantService.kt").write_text('val m = "Micrô đang bận"', encoding="utf-8")
    extra = mod.collect_extra_words(root, {"lệnh"})
    assert "nghe" in extra and "rõ" in extra and "không" in extra and "micrô" in extra, extra
    assert "lệnh" not in extra and "voice" not in extra and "tht" not in extra and "hash" not in extra and "core" not in extra, extra
print("extra spoken words OK")
