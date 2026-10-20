$ErrorActionPreference = "Stop"
$root = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-bundle-review-test-" + [Guid]::NewGuid().ToString("N"))
$bundle = Join-Path $root "collector"
$review = Join-Path $root "review"
New-Item -ItemType Directory -Path $bundle -Force | Out-Null

try {
    "THTVoice test logcat" | Set-Content -LiteralPath (Join-Path $bundle "thtvoice-logcat.txt")
    @(
        "trace=1 scope=HVAC:TEMP writeSeq=1 op=SET_FLOAT property=123 area=0 value=20 result=ACCEPTED",
        "trace=1 scope=HVAC:TEMP writeSeq=1 op=GET_FLOAT property=123 area=0 value=20 result=OK"
    ) | Set-Content -LiteralPath (Join-Path $bundle "vhal-io-events.txt")
    $zip = Join-Path $root "collector.zip"
    Compress-Archive -Path (Join-Path $bundle "*") -DestinationPath $zip

    & (Join-Path $PSScriptRoot "review-ihu689g-bundle.ps1") -BundlePath $zip -OutputDirectory $review | Out-Null
    foreach ($name in @("ANALYSIS_SUMMARY.txt", "VHAL_EVIDENCE_SUMMARY.csv", "VHAL_RANGE_ANALYSIS.txt", "MEDIA_NAVIGATION_SUMMARY.txt", "IHU689G_TEST_MATRIX_REVIEW.csv")) {
        if (-not (Test-Path -LiteralPath (Join-Path $review $name))) {
            throw "Bundle review thiếu kết quả: $name"
        }
    }
    $evidence = @(Import-Csv -LiteralPath (Join-Path $review "VHAL_EVIDENCE_SUMMARY.csv"))
    if ($evidence.Count -ne 1 -or $evidence[0].HardwareConfirmed -ne "False") {
        throw "Evidence review không được tự gán hardware-confirmed"
    }
    $matrix = @(Import-Csv -LiteralPath (Join-Path $review "IHU689G_TEST_MATRIX_REVIEW.csv"))
    $hvac = @($matrix | Where-Object { $_.id -eq "HVAC-01" })
    if ($hvac.Count -ne 1 -or $hvac[0].evidence_suggestion -ne "PENDING_HARDWARE_CONFIRMATION") {
        throw "Matrix review không nối được evidence HVAC cùng bundle"
    }
    $collisionRejected = $false
    try {
        & (Join-Path $PSScriptRoot "review-ihu689g-bundle.ps1") -BundlePath $zip -OutputDirectory $review | Out-Null
    } catch {
        if ($_.Exception.Message -match "đã tồn tại") { $collisionRejected = $true } else { throw }
    }
    if (-not $collisionRejected) { throw "Bundle review đã ghi đè kết quả cũ" }
    Write-Host "[THTVoice] Persistent ZIP bundle review PASS"
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
