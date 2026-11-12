"""Check the real downloaded Android runtime, not just its filename or Gradle pin."""
from pathlib import Path
import hashlib
import io
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify_runtime(path):
    script = (ROOT / 'scripts/fetch-sherpa-android.sh').read_text(encoding='utf-8')
    version = re.search(r'^VERSION="([^"]+)"', script, re.M).group(1)
    expected = re.search(r'^SHA256="([0-9a-f]{64})"', script, re.M).group(1)
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        raise ValueError('Sherpa AAR SHA256 does not match the verified release pin')
    with zipfile.ZipFile(path) as aar:
        manifest = ET.fromstring(aar.read('AndroidManifest.xml'))
        uses = manifest.find('uses-sdk')
        minimum = int(uses.get('{http://schemas.android.com/apk/res/android}minSdkVersion', '1')) if uses is not None else 1
        if minimum > 28:
            raise ValueError(f'Sherpa requires API {minimum}, above head-unit API28')
        with zipfile.ZipFile(io.BytesIO(aar.read('classes.jar'))) as classes:
            for name in ['OnlineRecognizer', 'OfflineRecognizer', 'KeywordSpotter', 'OfflineTts', 'OnlineSpeechDenoiser']:
                if f'com/k2fsa/sherpa/onnx/{name}.class' not in classes.namelist():
                    raise ValueError(f'Missing required Sherpa API {name}')
        for abi, elf_class, machine in [('armeabi-v7a', 1, 40), ('arm64-v8a', 2, 183)]:
            header = aar.read(f'jni/{abi}/libsherpa-onnx-jni.so')[:20]
            if header[:4] != b'\x7fELF' or header[4] != elf_class or header[5] != 1 or struct.unpack_from('<H', header, 18)[0] != machine:
                raise ValueError(f'Incorrect native architecture for {abi}')
    print(f'Sherpa {version} AAR verified: SHA256={actual}; minSdk={minimum}; ARM32+ARM64 JNI; ASR/KWS/TTS/denoiser APIs present')


if __name__ == '__main__':
    source = (ROOT / 'scripts/fetch-sherpa-android.sh').read_text(encoding='utf-8')
    version = re.search(r'^VERSION="([^"]+)"', source, re.M).group(1)
    verify_runtime(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / f'app/libs/sherpa-onnx-{version}.aar')
