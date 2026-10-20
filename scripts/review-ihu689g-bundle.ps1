param(
    [Parameter(Mandatory = $true)][string]$BundlePath,
    [string]$OutputDirectory = "",
    [string]$MatrixPath = ""
)

$ErrorActionPreference = "Stop"
$item = Get-Item -LiteralPath $BundlePath -ErrorAction Stop

if ($item.PSIsContainer) {
    if ($OutputDirectory) { throw "-OutputDirectory chỉ dùng khi BundlePath là ZIP." }
    $bundleDir = $item.FullName
} elseif ($item.Extension -ieq ".zip") {
    $destination = if ($OutputDirectory) {
        [System.IO.Path]::GetFullPath($OutputDirectory)
    } else {
        Join-Path $item.DirectoryName ($item.BaseName + "-analysis")
    }
    if (Test-Path -LiteralPath $destination) {
        throw "Thư mục phân tích đã tồn tại: $destination. Chọn -OutputDirectory khác để không ghi đè evidence."
    }
    New-Item -ItemType Directory -Path $destination | Out-Null
    try {
        Expand-Archive -LiteralPath $item.FullName -DestinationPath $destination
    } catch {
        Remove-Item -LiteralPath $destination -Recurse -Force -ErrorAction SilentlyContinue
        throw
    }
    $candidates = @((Get-Item -LiteralPath $destination)) + @(Get-ChildItem -LiteralPath $destination -Directory -Recurse)
    $bundleDir = @($candidates | Where-Object {
        (Test-Path -LiteralPath (Join-Path $_.FullName "thtvoice-logcat.txt")) -and
        (Test-Path -LiteralPath (Join-Path $_.FullName "vhal-io-events.txt"))
    }) | Select-Object -First 1 -ExpandProperty FullName
    if (-not $bundleDir) {
        throw "ZIP thiếu thtvoice-logcat.txt hoặc vhal-io-events.txt; kiểm tra bundle collector."
    }
} else {
    throw "BundlePath phải là thư mục collector hoặc file .zip"
}

if (-not (Test-Path -LiteralPath (Join-Path $bundleDir "thtvoice-logcat.txt")) -or
    -not (Test-Path -LiteralPath (Join-Path $bundleDir "vhal-io-events.txt"))) {
    throw "Bundle thiếu logcat hoặc VHAL IO trace."
}

if (-not $MatrixPath) {
    $packagedMatrix = Join-Path $bundleDir "hardware-package-provenance\IHU689G_TEST_MATRIX.csv"
    if (Test-Path -LiteralPath $packagedMatrix -PathType Leaf) {
        $MatrixPath = $packagedMatrix
        Write-Host "[THTVoice] Dùng matrix provenance của chính hardware package: $MatrixPath"
    } else {
        $MatrixPath = Join-Path $PSScriptRoot "..\docs\phase10\IHU689G_TEST_MATRIX.csv"
        Write-Warning "Bundle cũ không có packaged IHU689G_TEST_MATRIX.csv; fallback về repo-local matrix. Không dùng fallback để chứng minh provenance của artifact cũ."
    }
}
Write-Host "[THTVoice] Phân tích cùng một bundle: $bundleDir"

foreach ($analyzer in @(
    "analyze-ihu689g-validation.ps1",
    "analyze-vhal-command-trace.ps1",
    "analyze-vhal-range.ps1",
    "analyze-media-navigation.ps1"
)) {
    & (Join-Path $PSScriptRoot $analyzer) -BundlePath $bundleDir | Out-Null
}

$evidence = Join-Path $bundleDir "VHAL_EVIDENCE_SUMMARY.csv"
if ((Test-Path -LiteralPath $evidence) -and (Test-Path -LiteralPath $MatrixPath)) {
    & (Join-Path $PSScriptRoot "build-test-matrix-evidence-review.ps1") -BundlePath $bundleDir -MatrixPath $MatrixPath | Out-Null
} elseif (-not (Test-Path -LiteralPath $evidence)) {
    Write-Warning "Chưa có command-scoped VHAL trace; không tạo matrix review từ evidence thiếu."
} else {
    Write-Warning "Không tìm thấy test matrix: $MatrixPath. Dùng -MatrixPath để tạo matrix review."
}

Write-Host "[THTVoice] Kết quả lưu tại: $bundleDir"
foreach ($name in @("ANALYSIS_SUMMARY.txt","VHAL_COMMAND_SUMMARY.csv","VHAL_EVIDENCE_SUMMARY.csv","VHAL_RANGE_ANALYSIS.txt","MEDIA_NAVIGATION_SUMMARY.txt","IHU689G_TEST_MATRIX_REVIEW.csv")) {
    if (Test-Path -LiteralPath (Join-Path $bundleDir $name)) { Write-Host "  $name" }
}
Write-Host "[THTVoice] Báo cáo chỉ hỗ trợ review; PASS cần quan sát xe thật và log cùng phiên."
