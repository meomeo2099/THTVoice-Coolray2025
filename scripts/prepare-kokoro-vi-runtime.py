#!/usr/bin/env python3
"""Prepare the Android Kokoro Vietnamese runtime without executing torch pickle code."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import shutil
import sys
import unicodedata
import zipfile
from pathlib import Path

STYLE_SHAPE = (510, 1, 256)
STYLE_BYTES = 510 * 1 * 256 * 4
VOICE_ORDER = (("ngoc_huyen", 0), ("diem_trinh", 1))
# sherpa-onnx OfflineTtsKokoroModel feeds tensors by position: tokens, style, speed.
SHERPA_INPUT_ORDER = ("input_ids", "style", "speed")
# contextboxai/Kokoro-Vietnamese exports the style vector as the PyTorch name `ref_s`.
STYLE_INPUT_ALIASES = ("style", "ref_s")
WORD_RE = re.compile(r"[0-9A-Za-zÀ-ỹĐđ]+", re.UNICODE)
STRING_RE = re.compile(r'"((?:\\.|[^"\\])*)"')
INTERPOLATION_RE = re.compile(r"\$\{[^}]*\}")
SPOKEN_SOURCE_FILES = {"VoiceResponseFormatter.kt"}

BASE_WORDS = {
    "wifi", "ip", "follow", "me", "home",
    "youtube", "vanced", "revanced", "morphe", "smarttube",
    "google", "maps", "zing", "mp3", "t", "h",
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def extract_style_storage(path: Path) -> bytes:
    """Read the one 510x1x256 float32 storage from a torch zip safely."""
    if not zipfile.is_zipfile(path):
        raise ValueError(f"{path.name} is not a torch zip archive; refusing pickle execution")
    with zipfile.ZipFile(path) as zf:
        candidates = [
            info for info in zf.infolist()
            if info.file_size == STYLE_BYTES and "/data/" in f"/{info.filename}"
        ]
        if len(candidates) != 1:
            found = [(i.filename, i.file_size) for i in zf.infolist() if "/data/" in f"/{i.filename}"]
            raise ValueError(
                f"{path.name}: expected exactly one {STYLE_BYTES}-byte style storage; "
                f"found {candidates!r}; data entries={found!r}"
            )
        payload = zf.read(candidates[0])
    if len(payload) != STYLE_BYTES:
        raise ValueError(f"{path.name}: style storage size changed: {len(payload)}")
    return payload


def load_vocab(config_path: Path) -> dict[str, int]:
    config = json.loads(config_path.read_text(encoding="utf-8"))
    vocab = config.get("vocab")
    if not isinstance(vocab, dict) or not vocab:
        raise ValueError("config.json has no Kokoro vocab")
    result = {str(symbol): int(idx) for symbol, idx in vocab.items()}
    ids = list(result.values())
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate token IDs in config.json")
    n_token = int(config.get("n_token", 0))
    if result.get(" ") != 16 or min(ids) < 0 or n_token <= max(ids):
        raise ValueError(
            f"Unexpected Kokoro Vietnamese vocab contract: n_token={n_token} "
            f"max_id={max(ids)} space_id={result.get(' ')}"
        )
    return result


def write_tokens(vocab: dict[str, int], output: Path) -> None:
    by_id = sorted(vocab.items(), key=lambda item: item[1])
    # Same format as sherpa-onnx scripts/kokoro/*/generate_tokens.py.
    output.write_text("".join(f"{symbol} {idx}\n" for symbol, idx in by_id), encoding="utf-8")


def _rename_input_refs(graph, old: str, new: str) -> None:
    """Rename every consumer of `old` in `graph` and nested subgraphs that do not shadow it."""
    for node in graph.node:
        for index, name in enumerate(node.input):
            if name == old:
                node.input[index] = new
        for attr in getattr(node, "attribute", []):
            subgraphs = list(getattr(attr, "graphs", []))
            single = getattr(attr, "g", None)
            if single is not None:
                subgraphs.append(single)
            for sub in subgraphs:
                if any(item.name == old for item in sub.input):
                    continue
                _rename_input_refs(sub, old, new)
    for item in graph.output:
        if item.name == old:
            item.name = new


def normalize_kokoro_inputs(graph) -> str:
    """Make the graph inputs exactly `input_ids, style, speed` in Sherpa's positional order.

    Returns the original style input name. Anything other than a pure rename or
    reorder of those three inputs is rejected so the packaged model stays faithful.
    """
    names = [item.name for item in graph.input]
    styles = [name for name in names if name in STYLE_INPUT_ALIASES]
    expected = {"input_ids", "speed"}
    if len(names) != 3 or not expected.issubset(names) or len(styles) != 1:
        raise ValueError(
            "Kokoro ONNX IO contract changed; required=['input_ids', 'speed', 'style'|'ref_s'] "
            f"actual={names}"
        )
    source_style = styles[0]
    if source_style != "style":
        if any(item.name == "style" for item in list(graph.output) + list(getattr(graph, "value_info", []))) or any(
            "style" in node.output for node in graph.node
        ):
            raise ValueError("Cannot rename Kokoro style input: graph already uses the name 'style'")
        _rename_input_refs(graph, source_style, "style")
        for item in graph.input:
            if item.name == source_style:
                item.name = "style"
    by_name = {item.name: copy.deepcopy(item) for item in graph.input}
    del graph.input[:]
    graph.input.extend(by_name[name] for name in SHERPA_INPUT_ORDER)
    return source_style


def add_sherpa_metadata(model_path: Path) -> dict[str, str]:
    """Validate ONNX IO and add the metadata required by sherpa-onnx Kokoro."""
    try:
        import onnx  # type: ignore
    except Exception as exc:  # pragma: no cover - hardware staging installs it
        raise RuntimeError("onnx==1.17.0 is required to package Kokoro for Sherpa") from exc

    model = onnx.load(str(model_path))
    source_style_input = normalize_kokoro_inputs(model.graph)

    speaker2id = ",".join(f"{name}->{sid}" for name, sid in VOICE_ORDER)
    id2speaker = ",".join(f"{sid}->{name}" for name, sid in VOICE_ORDER)
    metadata = {
        "model_type": "kokoro",
        "language": "Vietnamese",
        "has_espeak": "1",
        "sample_rate": "24000",
        "version": "2",
        "voice": "vi",
        "style_dim": ",".join(map(str, STYLE_SHAPE)),
        "n_speakers": str(len(VOICE_ORDER)),
        "id2speaker": id2speaker,
        "speaker2id": speaker2id,
        "speaker_names": ",".join(name for name, _ in VOICE_ORDER),
        "model_url": "https://huggingface.co/contextboxai/Kokoro-Vietnamese",
        "maintainer": "THTVoice packaging",
        "source_style_input": source_style_input,
        "comment": "Kokoro Vietnamese with vig2p lexicon; source graph preserved except style input "
        "name/order normalized for Sherpa; Sherpa metadata added.",
    }

    existing = {entry.key: entry.value for entry in model.metadata_props}
    merged = {**existing, **metadata}
    while model.metadata_props:
        model.metadata_props.pop()
    for key, value in merged.items():
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = str(value)
    onnx.save(model, str(model_path))
    return metadata


def normalize_runtime_text(text: str) -> str:
    text = re.sub(r"wi-fi", "wifi", text, flags=re.IGNORECASE)
    text = re.sub(r"follow-me-home", "follow me home", text, flags=re.IGNORECASE)
    text = re.sub(r"\bip\b", "ip", text, flags=re.IGNORECASE)
    return unicodedata.normalize("NFC", text)


def _spoken_sources(source_root: Path) -> list[Path]:
    if source_root.is_file():
        return [source_root]
    return sorted(path for path in source_root.rglob("*.kt") if path.name in SPOKEN_SOURCE_FILES)


def collect_runtime_words(source_root: Path) -> list[str]:
    words = set(BASE_WORDS)
    sources = _spoken_sources(source_root)
    if not sources:
        raise ValueError(f"No spoken response source found under {source_root}")
    for source in sources:
        content = source.read_text(encoding="utf-8")
        for match in STRING_RE.finditer(content):
            literal = INTERPOLATION_RE.sub(" ", match.group(1))
            literal = normalize_runtime_text(literal)
            for word in WORD_RE.findall(literal):
                words.add(word.lower())
    for value in range(0, 101):
        words.add(str(value))
    return sorted(word for word in words if word)


_VI_DIGITS = ("không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín")


def vietnamese_number_words(value: int) -> str:
    """Spell 0..100 the way VoiceResponseFormatter numbers are read aloud (21 -> hai mươi mốt)."""
    if not 0 <= value <= 100:
        raise ValueError(f"Number outside packaged lexicon range: {value}")
    if value == 100:
        return "một trăm"
    if value < 10:
        return _VI_DIGITS[value]
    tens, unit = divmod(value, 10)
    head = "mười" if tens == 1 else f"{_VI_DIGITS[tens]} mươi"
    if unit == 0:
        return head
    if unit == 5:
        tail = "lăm"
    elif unit == 1 and tens >= 2:
        tail = "mốt"
    elif unit == 4 and tens >= 2:
        tail = "tư"
    else:
        tail = _VI_DIGITS[unit]
    return f"{head} {tail}"


def spoken_form(word: str) -> str:
    """vig2p does not expand digits, so numeric lexicon keys are phonemized from their spelling."""
    return re.sub(r"\d+", lambda m: f" {vietnamese_number_words(int(m.group()))} ", word).strip()


# 12/10/2026 (tools/tts-probe): replies spoken outside VoiceResponseFormatter ("Không nghe rõ lệnh",
# executor reasons, multi-step / alias / safety messages) lost every word missing from the lexicon.
# These sources add their Vietnamese words too, leniently: only literals with Vietnamese letters,
# only words shaped like a Vietnamese syllable, and a word vig2p cannot phonemize is skipped.
EXTRA_SPOKEN_DIRS = ("voice",)
EXTRA_SPOKEN_FILES = {"VoiceAssistantService.kt"}
VI_MARK_RE = re.compile(r"[À-ỹĐđ]")
VI_SYLLABLE_RE = re.compile(
    r"^(?:ngh|ng|nh|ch|gh|gi|kh|ph|qu|th|tr|[bcdđghklmnprstvx])?"
    r"[aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ]{1,3}"
    r"(?:ng|nh|ch|[cmnpt])?$"
)


def _extra_sources(source_root: Path) -> list[Path]:
    if source_root.is_file():
        return []
    out = []
    for path in source_root.rglob("*.kt"):
        if path.name in EXTRA_SPOKEN_FILES or any(part in EXTRA_SPOKEN_DIRS for part in path.parts):
            out.append(path)
    return sorted(out)


def collect_extra_words(source_root: Path, exclude: set[str]) -> list[str]:
    words: set[str] = set()
    for source in _extra_sources(source_root):
        content = source.read_text(encoding="utf-8")
        for match in STRING_RE.finditer(content):
            literal = INTERPOLATION_RE.sub(" ", match.group(1))
            if not VI_MARK_RE.search(literal):
                continue
            literal = normalize_runtime_text(literal)
            for word in WORD_RE.findall(literal):
                w = word.lower()
                if w not in exclude and (VI_MARK_RE.search(w) or VI_SYLLABLE_RE.match(w)):
                    words.add(w)
    return sorted(words)


def phonemize_words(words: list[str], vocab: dict[str, int], strict: bool = True) -> list[tuple[str, str]]:
    try:
        from vig2p import phonemize_text  # type: ignore
    except Exception as exc:  # pragma: no cover - hardware staging installs it
        raise RuntimeError("vig2p==0.1.2 is required to build Vietnamese lexicon") from exc

    allowed = set(vocab)
    result: list[tuple[str, str]] = []
    problems: list[str] = []
    for word in words:
        spoken = spoken_form(word)
        phones = unicodedata.normalize("NFC", phonemize_text(spoken).strip())
        if not phones:
            problems.append(f"{word!r} -> {spoken!r}: empty phonemes")
            continue
        unsupported = sorted({ch for ch in phones if ch != " " and ch not in allowed})
        if unsupported:
            problems.append(f"{word!r} -> {spoken!r}: tokens outside Kokoro vocab {unsupported!r} ({phones!r})")
            continue
        result.append((word, phones))
    if problems and strict:
        # Report every uncovered word at once so one staging run shows the full gap.
        raise ValueError("vig2p lexicon build failed:\n  " + "\n  ".join(problems))
    if problems:
        print(f"  skipped {len(problems)} extra words vig2p cannot phonemize")
    return result


def write_lexicon(entries: list[tuple[str, str]], output: Path) -> None:
    with output.open("w", encoding="utf-8", newline="\n") as f:
        for word, phones in entries:
            symbols = [ch for ch in phones if ch != " "]
            if symbols:
                f.write(f"{word} {' '.join(symbols)}\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--ngoc-huyen", type=Path, required=True)
    parser.add_argument("--diem-trinh", type=Path, required=True)
    parser.add_argument("--espeak-data", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, default=Path("app/src/main/java"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-revision", required=True)
    args = parser.parse_args()

    for path in (args.model, args.config, args.ngoc_huyen, args.diem_trinh):
        if not path.is_file():
            raise FileNotFoundError(path)
    if not args.espeak_data.is_dir():
        raise FileNotFoundError(args.espeak_data)

    vocab = load_vocab(args.config)
    output = args.output
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)

    packaged_model = output / "kokoro_vi.onnx"
    shutil.copy2(args.model, packaged_model)
    source_model_sha = sha256(args.model)
    metadata = add_sherpa_metadata(packaged_model)
    packaged_model_sha = sha256(packaged_model)

    shutil.copy2(args.config, output / "config.json")
    shutil.copytree(args.espeak_data, output / "espeak-ng-data")
    write_tokens(vocab, output / "tokens.txt")

    with (output / "voices.bin").open("wb") as voices:
        voices.write(extract_style_storage(args.ngoc_huyen))
        voices.write(extract_style_storage(args.diem_trinh))
    if (output / "voices.bin").stat().st_size != STYLE_BYTES * len(VOICE_ORDER):
        raise ValueError("voices.bin size does not match two Kokoro speaker styles")

    words = collect_runtime_words(args.source_root)
    lexicon = phonemize_words(words, vocab)
    extra = collect_extra_words(args.source_root, set(words))
    lexicon += phonemize_words(extra, vocab, strict=False)
    write_lexicon(lexicon, output / "lexicon-vi.txt")

    (output / "voice-map.txt").write_text(
        "0 ngoc_huyen Ngọc Huyền\n1 diem_trinh Diễm Trinh\n",
        encoding="utf-8",
    )
    (output / "SOURCE.txt").write_text(
        "source=contextboxai/Kokoro-Vietnamese\n"
        f"revision={args.source_revision}\n"
        "frontend=vig2p==0.1.2\n"
        "speaker_0=ngoc_huyen\n"
        "speaker_1=diem_trinh\n"
        "voice_style_shape=510x1x256_float32\n"
        f"source_model_sha256={source_model_sha}\n"
        f"packaged_model_sha256={packaged_model_sha}\n"
        f"sherpa_model_type={metadata['model_type']}\n"
        f"sample_rate={metadata['sample_rate']}\n",
        encoding="utf-8",
    )

    print(f"Prepared Kokoro Vietnamese runtime: {output}")
    print(f"  vocab={len(vocab)} lexicon_words={len(lexicon)}")
    print(f"  voices.bin={(output / 'voices.bin').stat().st_size} bytes")
    print(f"  source_model_sha256={source_model_sha}")
    print(f"  packaged_model_sha256={packaged_model_sha}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
