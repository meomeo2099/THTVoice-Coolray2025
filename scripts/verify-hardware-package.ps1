param(
    [Parameter(Mandatory = $true)]
    [string]$PackagePath,

    [string]$ExpectedGitSha,

    [string]$ExpectedSourceMainSha
)

$ErrorActionPreference = "Stop"

function Resolve-PackageDirectory {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Không tìm thấy hardware package: $Path"
    }

    $item = Get-Item -LiteralPath $Path
    if ($item.PSIsContainer) {
        return $item.FullName
    }

    if ($item.Extension -ieq ".zip") {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-hardware-preflight-" + [Guid]::NewGuid().ToString("N"))
        New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
        Expand-Archive -LiteralPath $item.FullName -DestinationPath $tempRoot -Force

        $required = @(
            "THTVoice-IHU689G-hardware-test.apk",
            "SHA256.txt",
            "BUILD_INFO.txt",
            "ASSET_SHA256.txt",
            "IHU689G_TEST_MATRIX.csv",
            "probe-scene-contract.ps1"
        )
        $candidate = Get-ChildItem -Path $tempRoot -Directory -Recurse | Where-Object {
            $dir = $_.FullName
            ($required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $dir $_)) }).Count -eq 0
        } | Select-Object -First 1

        if ($candidate) {
            return $candidate.FullName
        }

        return $tempRoot
    }

    throw "PackagePath phải là thư mục artifact đã giải nén hoặc file .zip"
}

function Read-KeyValueFile {
    param([string]$Path)
    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^([^#=][^=]*)=(.*)$') {
            $map[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
    return $map
}

function Require-File {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Thiếu file bắt buộc: $Path"
    }
    if ((Get-Item -LiteralPath $Path).Length -le 0) {
        throw "File rỗng: $Path"
    }
}

function Require-Key {
    param([hashtable]$Map, [string]$Key)
    if (-not $Map.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace($Map[$Key])) {
        throw "BUILD_INFO thiếu trường bắt buộc: $Key"
    }
}

function Assert-CommitSha {
    param([string]$Value, [string]$FieldName)
    if ($Value -notmatch '^[0-9a-fA-F]{40}$') {
        throw "$FieldName không phải Git commit SHA 40 ký tự hợp lệ: $Value"
    }
}

function Assert-ShaMatches {
    param([string]$Actual, [string]$Expected, [string]$FieldName)
    $actualNormalized = $Actual.ToLowerInvariant()
    $expectedNormalized = $Expected.ToLowerInvariant()
    if ($actualNormalized -ne $expectedNormalized -and -not $actualNormalized.StartsWith($expectedNormalized) -and -not $expectedNormalized.StartsWith($actualNormalized)) {
        throw "$FieldName không khớp. Expected=$Expected Actual=$Actual"
    }
}

function Get-ManifestHashForFile {
    param(
        [string[]]$AssetLines,
        [string]$FileName
    )
    $line = $AssetLines | Where-Object {
        $_ -match ('^[0-9a-fA-F]{64}\s+\*?.*' + [regex]::Escape($FileName) + '$')
    } | Select-Object -First 1
    if (-not $line) {
        throw "ASSET_SHA256.txt không có hash cho $FileName"
    }
    return $line.Substring(0, 64).ToLowerInvariant()
}

$packageDir = Resolve-PackageDirectory $PackagePath
$apkPath = Join-Path $packageDir "THTVoice-IHU689G-hardware-test.apk"
$shaPath = Join-Path $packageDir "SHA256.txt"
$buildInfoPath = Join-Path $packageDir "BUILD_INFO.txt"
$assetManifestPath = Join-Path $packageDir "ASSET_SHA256.txt"
$matrixPath = Join-Path $packageDir "IHU689G_TEST_MATRIX.csv"
$sceneProbePath = Join-Path $packageDir "probe-scene-contract.ps1"

@(
    $apkPath,
    $shaPath,
    $buildInfoPath,
    $assetManifestPath,
    $matrixPath,
    $sceneProbePath
) | ForEach-Object { Require-File $_ }

$apkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$assetManifestHash = (Get-FileHash -LiteralPath $assetManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$matrixHash = (Get-FileHash -LiteralPath $matrixPath -Algorithm SHA256).Hash.ToLowerInvariant()
$sceneProbeHash = (Get-FileHash -LiteralPath $sceneProbePath -Algorithm SHA256).Hash.ToLowerInvariant()
$apkSize = (Get-Item -LiteralPath $apkPath).Length

$shaLine = (Get-Content -LiteralPath $shaPath | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1)
if ($shaLine -notmatch '^([0-9a-fA-F]{64})\s+\*?(.+)$') {
    throw "SHA256.txt không đúng định dạng sha256sum"
}
$shaFileHash = $Matches[1].ToLowerInvariant()
$shaFileName = [System.IO.Path]::GetFileName($Matches[2].Trim())
if ($shaFileName -ne "THTVoice-IHU689G-hardware-test.apk") {
    throw "SHA256.txt trỏ tới file không đúng: $shaFileName"
}
if ($shaFileHash -ne $apkHash) {
    throw "APK SHA-256 không khớp SHA256.txt"
}

$info = Read-KeyValueFile $buildInfoPath
@(
    "git_sha",
    "source_main_sha",
    "apk_size_bytes",
    "apk_sha256",
    "sherpa_runtime",
    "sherpa_runtime_sha256",
    "asr_model",
    "asr_backend",
    "kws_model",
    "wake_phrase",
    "wake_profiles",
    "tts_voice_assets",
    "test_matrix_sha256",
    "scene_probe_sha256",
    "asset_manifest_sha256",
    "test_matrix",
    "scene_probe"
) | ForEach-Object { Require-Key $info $_ }

Assert-CommitSha $info["git_sha"] "git_sha"
Assert-CommitSha $info["source_main_sha"] "source_main_sha"

if ($info["apk_sha256"].ToLowerInvariant() -ne $apkHash) {
    throw "BUILD_INFO apk_sha256 không khớp APK"
}
if ([int64]$info["apk_size_bytes"] -ne $apkSize) {
    throw "BUILD_INFO apk_size_bytes không khớp kích thước APK"
}
if ($info["asset_manifest_sha256"].ToLowerInvariant() -ne $assetManifestHash) {
    throw "BUILD_INFO asset_manifest_sha256 không khớp ASSET_SHA256.txt"
}
if ($info["test_matrix_sha256"].ToLowerInvariant() -ne $matrixHash) {
    throw "BUILD_INFO test_matrix_sha256 không khớp packaged IHU689G_TEST_MATRIX.csv"
}
if ($info["scene_probe_sha256"].ToLowerInvariant() -ne $sceneProbeHash) {
    throw "BUILD_INFO scene_probe_sha256 không khớp file packaged"
}
if ($info["test_matrix"] -ne "IHU689G_TEST_MATRIX.csv") {
    throw "test_matrix không trỏ tới packaged matrix"
}
if ($info["scene_probe"] -ne "probe-scene-contract.ps1") {
    throw "scene_probe không trỏ tới packaged read-only probe"
}
if ($info["asr_backend"] -ne "offline-transducer") {
    throw "ASR backend không phải offline-transducer: $($info['asr_backend'])"
}
if ($info["wake_phrase"] -ne "Hey THT") {
    throw "Wake phrase mặc định không phải Hey THT: $($info['wake_phrase'])"
}
$expectedWakeProfiles = "Hey THT|Hello Geely|Hey Geely"
if ($info["wake_profiles"] -ne $expectedWakeProfiles) {
    throw "wake_profiles không đúng: $($info['wake_profiles'])"
}
if ($ExpectedGitSha) {
    Assert-ShaMatches $info["git_sha"] $ExpectedGitSha "git_sha"
}
if ($ExpectedSourceMainSha) {
    Assert-ShaMatches $info["source_main_sha"] $ExpectedSourceMainSha "source_main_sha"
}

$assetLines = Get-Content -LiteralPath $assetManifestPath | Where-Object {
    -not [string]::IsNullOrWhiteSpace($_) -and -not $_.TrimStart().StartsWith("#")
}
if ($assetLines.Count -lt 6) {
    throw "ASSET_SHA256.txt có quá ít entry để chứng minh runtime/model/matrix"
}
foreach ($line in $assetLines) {
    if ($line -notmatch '^[0-9a-fA-F]{64}\s+\*?.+$') {
        throw "ASSET_SHA256.txt có dòng sai định dạng: $line"
    }
}

$manifestText = $assetLines -join "`n"
$matrixManifestHash = Get-ManifestHashForFile -AssetLines $assetLines -FileName "IHU689G_TEST_MATRIX.csv"
if ($matrixManifestHash -ne $matrixHash) {
    throw "ASSET_SHA256.txt hash không khớp packaged IHU689G_TEST_MATRIX.csv"
}
if ($info["test_matrix_sha256"].ToLowerInvariant() -ne $matrixManifestHash) {
    throw "BUILD_INFO test_matrix_sha256 không khớp ASSET_SHA256.txt"
}

$evidenceChecks = @(
    @{ Name = "probe-scene-contract.ps1"; Actual = $sceneProbeHash; Info = $info["scene_probe_sha256"] }
)
foreach ($check in $evidenceChecks) {
    $manifestHash = Get-ManifestHashForFile -AssetLines $assetLines -FileName $check.Name
    if ($manifestHash -ne $check.Actual) {
        throw "ASSET_SHA256.txt hash không khớp packaged $($check.Name)"
    }
    if ($manifestHash -ne $check.Info.ToLowerInvariant()) {
        throw "BUILD_INFO hash không khớp manifest cho $($check.Name)"
    }
}

foreach ($requiredToken in @(
    "sherpa-onnx",
    "65M-int8",
    "kws",
    "keywords.txt",
    "keywords-hello-geely.txt",
    "keywords-hey-geely.txt",
    "IHU689G_TEST_MATRIX.csv",
    "probe-scene-contract.ps1"
)) {
    if ($manifestText -notmatch [regex]::Escape($requiredToken)) {
        throw "ASSET_SHA256.txt thiếu bằng chứng asset bắt buộc: $requiredToken"
    }
}

Write-Host "[THTVoice] Hardware package preflight PASS"
Write-Host "  package_dir=$packageDir"
Write-Host "  git_sha=$($info['git_sha'])"
Write-Host "  source_main_sha=$($info['source_main_sha'])"
Write-Host "  apk_sha256=$apkHash"
Write-Host "  apk_size_bytes=$apkSize"
Write-Host "  asr_backend=$($info['asr_backend'])"
Write-Host "  wake_phrase_default=$($info['wake_phrase'])"
Write-Host "  wake_profiles=$($info['wake_profiles'])"
Write-Host "  tts_voice_assets=$($info['tts_voice_assets'])"
Write-Host "  test_matrix_sha256=$matrixHash"
Write-Host "  scene_probe_sha256=$sceneProbeHash"
Write-Host "  asset_manifest_sha256=$assetManifestHash"
