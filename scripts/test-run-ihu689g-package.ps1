$ErrorActionPreference = "Stop"

$root = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-runner-test-" + [Guid]::NewGuid().ToString("N"))
$package = Join-Path $root "package"
New-Item -ItemType Directory -Force -Path $package | Out-Null

try {
    $apk = Join-Path $package "THTVoice-IHU689G-hardware-test.apk"
    $sha = Join-Path $package "SHA256.txt"
    $buildInfo = Join-Path $package "BUILD_INFO.txt"
    $assetManifest = Join-Path $package "ASSET_SHA256.txt"
    $mainMatrix = Join-Path $package "IHU689G_TEST_MATRIX.csv"
    $sceneProbe = Join-Path $package "probe-scene-contract.ps1"

    [System.IO.File]::WriteAllBytes($apk, [byte[]](1..128))
    $apkHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    $apkSize = (Get-Item -LiteralPath $apk).Length
    "$apkHash  THTVoice-IHU689G-hardware-test.apk" | Set-Content -LiteralPath $sha -Encoding ascii

    "id,group,test_case,expected,result,notes`nT-01,Test,main,main,NOT_TESTED," | Set-Content -LiteralPath $mainMatrix -Encoding utf8
    "param([switch]`$SelfTest)`nif (`$SelfTest) { Write-Host 'SCENE PASS' }" | Set-Content -LiteralPath $sceneProbe -Encoding utf8
    $mainMatrixHash = (Get-FileHash -LiteralPath $mainMatrix -Algorithm SHA256).Hash.ToLowerInvariant()
    $sceneProbeHash = (Get-FileHash -LiteralPath $sceneProbe -Algorithm SHA256).Hash.ToLowerInvariant()

    $dummyA = "a" * 64
    $dummyB = "b" * 64
    $dummyC = "c" * 64
    $dummyE = "e" * 64
    @(
        "# THT Voice IHU689G hardware-test asset SHA-256",
        "$dummyA  app/libs/sherpa-onnx-1.13.8.aar",
        "$dummyB  app/src/main/assets/models/sherpa-onnx-streaming-zipformer-vi-65M-int8/encoder.int8.onnx",
        "$dummyC  app/src/main/assets/models/sherpa-onnx-kws-zipformer-zh-en/keywords.txt",
        "$dummyE  app/src/main/assets/models/sherpa-onnx-kws-zipformer-zh-en/keywords-hello-geely.txt",
        "$dummyA  app/src/main/assets/models/sherpa-onnx-kws-zipformer-zh-en/keywords-hey-geely.txt",
        "$mainMatrixHash  IHU689G_TEST_MATRIX.csv",
        "$sceneProbeHash  probe-scene-contract.ps1"
    ) | Set-Content -LiteralPath $assetManifest -Encoding utf8

    $assetHash = (Get-FileHash -LiteralPath $assetManifest -Algorithm SHA256).Hash.ToLowerInvariant()
    $gitSha = "0123456789abcdef0123456789abcdef01234567"
    $sourceMainSha = "89abcdef0123456789abcdef0123456789abcdef"
    @(
        "THT Voice IHU689G hardware-test build",
        "git_sha=$gitSha",
        "source_main_sha=$sourceMainSha",
        "git_ref=refs/heads/hardware-test/test",
        "apk_size_bytes=$apkSize",
        "apk_sha256=$apkHash",
        "sherpa_runtime=sherpa-onnx-1.13.8.aar",
        "sherpa_runtime_sha256=$dummyA",
        "asr_model=test-65M-int8",
        "asr_backend=offline-transducer",
        "kws_model=test-kws",
        "wake_phrase=Hey THT",
        "wake_profiles=Hey THT|Hello Geely|Hey Geely",
        "tts_voice_assets=not_staged",
        "test_matrix_sha256=$mainMatrixHash",
        "scene_probe_sha256=$sceneProbeHash",
        "asset_manifest_sha256=$assetHash",
        "test_matrix=IHU689G_TEST_MATRIX.csv",
        "scene_probe=probe-scene-contract.ps1"
    ) | Set-Content -LiteralPath $buildInfo -Encoding utf8

    $runner = Join-Path $PSScriptRoot "run-ihu689g-test.ps1"
    & $runner -HardwarePackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha -PreflightOnly

    $zip = Join-Path $root "THTVoice-IHU689G-hardware-test.zip"
    Compress-Archive -Path (Join-Path $package "*") -DestinationPath $zip -Force
    & $runner -HardwarePackagePath $zip -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha -PreflightOnly

    $savedMain = Get-Content -LiteralPath $mainMatrix -Raw
    Remove-Item -LiteralPath $mainMatrix -Force
    $missingMainRejected = $false
    try { & $runner -HardwarePackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha -PreflightOnly }
    catch {
        if ($_.Exception.Message -match "thiếu file bắt buộc|Hardware package thiếu file bắt buộc") { $missingMainRejected = $true; $global:LASTEXITCODE = 0 } else { throw }
    }
    if (-not $missingMainRejected) { throw "Runner không chặn package thiếu main IHU689G matrix" }
    [System.IO.File]::WriteAllText($mainMatrix, $savedMain)

    $savedSceneProbe = Get-Content -LiteralPath $sceneProbe -Raw
    Remove-Item -LiteralPath $sceneProbe -Force
    $missingSceneRejected = $false
    try { & $runner -HardwarePackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha -PreflightOnly }
    catch {
        if ($_.Exception.Message -match "thiếu file bắt buộc|Hardware package thiếu file bắt buộc") { $missingSceneRejected = $true; $global:LASTEXITCODE = 0 } else { throw }
    }
    if (-not $missingSceneRejected) { throw "Runner không chặn package thiếu Scene probe" }
    [System.IO.File]::WriteAllText($sceneProbe, $savedSceneProbe)

    $wrongShaRejected = $false
    try { & $runner -HardwarePackagePath $package -ExpectedGitSha "ffffffffffffffffffffffffffffffffffffffff" -ExpectedSourceMainSha $sourceMainSha -PreflightOnly }
    catch {
        if ($_.Exception.Message -match "preflight thất bại|git_sha không khớp") { $wrongShaRejected = $true; $global:LASTEXITCODE = 0 } else { throw }
    }
    if (-not $wrongShaRejected) { throw "Runner không chặn package có ExpectedGitSha sai" }

    $wrongSourceMainRejected = $false
    try { & $runner -HardwarePackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha "ffffffffffffffffffffffffffffffffffffffff" -PreflightOnly }
    catch {
        if ($_.Exception.Message -match "preflight thất bại|source_main_sha không khớp") { $wrongSourceMainRejected = $true; $global:LASTEXITCODE = 0 } else { throw }
    }
    if (-not $wrongSourceMainRejected) { throw "Runner không chặn package có ExpectedSourceMainSha sai" }

    if ($IsLinux) {
        $fakeAdb = Join-Path $root "adb"
        @'
#!/usr/bin/env bash
case "$1" in
  devices) printf 'List of devices attached\nfake-device\tdevice\n' ;;
  shell) printf 'package:/data/app/com.tht.voice.coolray2025/base.apk\n' ;;
  pull) cp "$THT_TEST_INSTALLED_APK" "$3" ;;
  *) exit 90 ;;
esac
'@ | Set-Content -LiteralPath $fakeAdb -Encoding utf8
        & chmod +x $fakeAdb
        $otherApk = Join-Path $root "different-installed.apk"
        [System.IO.File]::WriteAllBytes($otherApk, [byte[]](128..1))
        $oldPath = $env:PATH
        $oldTestApk = $env:THT_TEST_INSTALLED_APK
        try {
            $env:PATH = "$root" + [System.IO.Path]::PathSeparator + $oldPath
            $env:THT_TEST_INSTALLED_APK = $otherApk
            $mismatchRejected = $false
            try { & $runner -HardwarePackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha -SkipInstall }
            catch {
                if ($_.Exception.Message -match "SHA-256 khác hardware package") { $mismatchRejected = $true; $global:LASTEXITCODE = 0 } else { throw }
            }
            if (-not $mismatchRejected) { throw "Runner không chặn -SkipInstall khi APK đã cài khác package" }
        } finally {
            $env:PATH = $oldPath
            $env:THT_TEST_INSTALLED_APK = $oldTestApk
        }
    }

    $global:LASTEXITCODE = 0
    Write-Host "[THTVoice] IHU689G runner package self-test PASS"
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
