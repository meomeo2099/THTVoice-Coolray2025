$ErrorActionPreference = "Stop"

$root = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-preflight-test-" + [Guid]::NewGuid().ToString("N"))
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

    $verifier = Join-Path $PSScriptRoot "verify-hardware-package.ps1"
    & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha

    $wrongMainShaRejected = $false
    try {
        & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha "1111111111111111111111111111111111111111"
    } catch {
        if ($_.Exception.Message -match "source_main_sha không khớp") { $wrongMainShaRejected = $true } else { throw }
    }
    if (-not $wrongMainShaRejected) { throw "Verifier không phát hiện source_main_sha sai" }

    $originalBuildInfo = Get-Content -LiteralPath $buildInfo -Raw
    $badMatrixSha = $originalBuildInfo.Replace("test_matrix_sha256=$mainMatrixHash", "test_matrix_sha256=$dummyA")
    Set-Content -LiteralPath $buildInfo -Value $badMatrixSha -Encoding utf8
    $badMatrixShaRejected = $false
    try {
        & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha
    } catch {
        if ($_.Exception.Message -match "test_matrix_sha256 không khớp packaged|test_matrix_sha256 không khớp ASSET_SHA256.txt") { $badMatrixShaRejected = $true } else { throw }
    }
    if (-not $badMatrixShaRejected) { throw "Verifier không đối chiếu test_matrix_sha256 với packaged matrix" }
    [System.IO.File]::WriteAllText($buildInfo, $originalBuildInfo)

    $originalMainMatrix = Get-Content -LiteralPath $mainMatrix -Raw
    Add-Content -LiteralPath $mainMatrix -Value "tamper" -Encoding utf8
    $tamperedMainRejected = $false
    try {
        & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha
    } catch {
        if ($_.Exception.Message -match "test_matrix_sha256 không khớp packaged|hash không khớp packaged IHU689G_TEST_MATRIX.csv") { $tamperedMainRejected = $true } else { throw }
    }
    if (-not $tamperedMainRejected) { throw "Verifier không phát hiện main hardware matrix bị thay đổi" }
    [System.IO.File]::WriteAllText($mainMatrix, $originalMainMatrix)

    Remove-Item -LiteralPath $mainMatrix -Force
    $missingMainRejected = $false
    try {
        & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha
    } catch {
        if ($_.Exception.Message -match "Thiếu file bắt buộc") { $missingMainRejected = $true } else { throw }
    }
    if (-not $missingMainRejected) { throw "Verifier không phát hiện packaged main matrix bị thiếu" }
    [System.IO.File]::WriteAllText($mainMatrix, $originalMainMatrix)

    $originalSceneProbe = Get-Content -LiteralPath $sceneProbe -Raw
    Add-Content -LiteralPath $sceneProbe -Value "tamper" -Encoding utf8
    $tamperedSceneRejected = $false
    try {
        & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha
    } catch {
        if ($_.Exception.Message -match "scene_probe_sha256 không khớp|hash không khớp packaged") { $tamperedSceneRejected = $true } else { throw }
    }
    if (-not $tamperedSceneRejected) { throw "Verifier không phát hiện Scene probe bị thay đổi" }
    [System.IO.File]::WriteAllText($sceneProbe, $originalSceneProbe)

    Remove-Item -LiteralPath $sceneProbe -Force
    $missingSceneRejected = $false
    try {
        & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha
    } catch {
        if ($_.Exception.Message -match "Thiếu file bắt buộc") { $missingSceneRejected = $true } else { throw }
    }
    if (-not $missingSceneRejected) { throw "Verifier không phát hiện packaged Scene probe bị thiếu" }
    [System.IO.File]::WriteAllText($sceneProbe, $originalSceneProbe)

    $badWakeProfiles = $originalBuildInfo.Replace("wake_profiles=Hey THT|Hello Geely|Hey Geely", "wake_profiles=Hey THT|Hello Geely")
    Set-Content -LiteralPath $buildInfo -Value $badWakeProfiles -Encoding utf8
    $badProfilesRejected = $false
    try {
        & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha
    } catch {
        if ($_.Exception.Message -match "wake_profiles không đúng") { $badProfilesRejected = $true } else { throw }
    }
    if (-not $badProfilesRejected) { throw "Verifier không phát hiện wake profile bị thiếu" }
    [System.IO.File]::WriteAllText($buildInfo, $originalBuildInfo)

    $originalAssetManifest = Get-Content -LiteralPath $assetManifest -Raw
    $manifestWithoutHeyGeely = [string]::Join([Environment]::NewLine, @(
        Get-Content -LiteralPath $assetManifest | Where-Object { $_ -notmatch "keywords-hey-geely\.txt" }
    )) + [Environment]::NewLine
    [System.IO.File]::WriteAllText($assetManifest, $manifestWithoutHeyGeely)
    $badAssetHash = (Get-FileHash -LiteralPath $assetManifest -Algorithm SHA256).Hash.ToLowerInvariant()
    $badAssetBuildInfo = $originalBuildInfo.Replace("asset_manifest_sha256=$assetHash", "asset_manifest_sha256=$badAssetHash")
    [System.IO.File]::WriteAllText($buildInfo, $badAssetBuildInfo)
    $missingWakeAssetRejected = $false
    try {
        & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha
    } catch {
        if ($_.Exception.Message -match "thiếu bằng chứng asset bắt buộc: keywords-hey-geely\.txt") { $missingWakeAssetRejected = $true } else { throw }
    }
    if (-not $missingWakeAssetRejected) { throw "Verifier không phát hiện keyword asset Hey Geely bị thiếu" }
    [System.IO.File]::WriteAllText($assetManifest, $originalAssetManifest)
    $restoredAssetHash = (Get-FileHash -LiteralPath $assetManifest -Algorithm SHA256).Hash.ToLowerInvariant()
    $originalBuildInfo = $originalBuildInfo.Replace("asset_manifest_sha256=$assetHash", "asset_manifest_sha256=$restoredAssetHash")
    $assetHash = $restoredAssetHash
    [System.IO.File]::WriteAllText($buildInfo, $originalBuildInfo)

    Add-Content -LiteralPath $apk -Value "tamper" -Encoding ascii
    $failedAsExpected = $false
    try {
        & $verifier -PackagePath $package -ExpectedGitSha $gitSha -ExpectedSourceMainSha $sourceMainSha
    } catch {
        if ($_.Exception.Message -match "SHA-256 không khớp") { $failedAsExpected = $true } else { throw }
    }

    if (-not $failedAsExpected) { throw "Verifier không phát hiện APK bị thay đổi" }

    Write-Host "[THTVoice] Hardware package preflight self-test PASS"
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
