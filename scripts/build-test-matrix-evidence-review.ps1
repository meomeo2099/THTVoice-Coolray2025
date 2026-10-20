param(
    [Parameter(Mandatory = $true)]
    [string]$BundlePath,

    [string]$MatrixPath = ".\docs\phase10\IHU689G_TEST_MATRIX.csv"
)

$ErrorActionPreference = "Stop"

function Resolve-BundleDirectory {
    param([string]$Path)

    if (-not (Test-Path $Path)) { throw "Không tìm thấy bundle: $Path" }
    $item = Get-Item $Path
    if ($item.PSIsContainer) { return $item.FullName }
    if ($item.Extension -ieq ".zip") {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-matrix-review-" + [Guid]::NewGuid().ToString("N"))
        New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
        Expand-Archive -Path $item.FullName -DestinationPath $tempRoot -Force
        return $tempRoot
    }
    throw "BundlePath phải là thư mục collector hoặc file .zip"
}

function Get-GroupEvidence {
    param(
        [object[]]$Evidence,
        [string]$Prefix
    )

    $items = @($Evidence | Where-Object { $_.Scope -like "$Prefix*" })
    if ($items.Count -eq 0) {
        return [pscustomobject]@{
            Count = 0
            SetFailed = 0
            ReadbackFailed = 0
            AcceptedNoReadback = 0
            ReadbackPresent = 0
            Insufficient = 0
        }
    }

    return [pscustomobject]@{
        Count = $items.Count
        SetFailed = @($items | Where-Object { $_.EvidenceStatus -eq "SET_FAILED" }).Count
        ReadbackFailed = @($items | Where-Object { $_.EvidenceStatus -eq "READBACK_IO_FAILED" }).Count
        AcceptedNoReadback = @($items | Where-Object { $_.EvidenceStatus -eq "ACCEPTED_NO_READBACK" }).Count
        ReadbackPresent = @($items | Where-Object { $_.EvidenceStatus -eq "READBACK_PRESENT_NEEDS_RUNTIME_RESULT" }).Count
        Insufficient = @($items | Where-Object { $_.EvidenceStatus -eq "INSUFFICIENT_EVIDENCE" }).Count
    }
}

function Get-Suggestion {
    param([object]$Stats)

    if ($Stats.Count -eq 0) {
        return [pscustomobject]@{
            Suggested = "PENDING"
            Detail = "Không có command-scoped evidence cho nhóm này trong bundle."
        }
    }
    if ($Stats.SetFailed -gt 0) {
        return [pscustomobject]@{
            Suggested = "REVIEW_FAIL"
            Detail = "Có SET_FAILED; cần đối chiếu command/test case trước khi ghi FAIL chính thức."
        }
    }
    if ($Stats.ReadbackFailed -gt 0) {
        return [pscustomobject]@{
            Suggested = "REVIEW_FAIL"
            Detail = "Có READBACK_IO_FAILED; write có thể đã được chấp nhận nhưng trạng thái xe chưa xác minh."
        }
    }
    if ($Stats.AcceptedNoReadback -gt 0) {
        return [pscustomobject]@{
            Suggested = "PENDING"
            Detail = "Có ACCEPTED_NO_READBACK; không đủ điều kiện PASS."
        }
    }
    if ($Stats.ReadbackPresent -gt 0) {
        return [pscustomobject]@{
            Suggested = "PENDING_HARDWARE_CONFIRMATION"
            Detail = "Có SET + GET, nhưng vẫn cần CommandExecutionResult và hành vi xe thật trước khi PASS."
        }
    }
    return [pscustomobject]@{
        Suggested = "PENDING"
        Detail = "Evidence chưa đủ để kết luận."
    }
}

$bundleDir = Resolve-BundleDirectory $BundlePath
if (-not (Test-Path $MatrixPath)) {
    throw "Không tìm thấy test matrix: $MatrixPath"
}

$evidencePath = Join-Path $bundleDir "VHAL_EVIDENCE_SUMMARY.csv"
if (-not (Test-Path $evidencePath)) {
    throw "Không tìm thấy VHAL_EVIDENCE_SUMMARY.csv. Hãy chạy analyze-vhal-command-trace.ps1 trước."
}

$matrix = @(Import-Csv -Path $MatrixPath)
$evidence = @(Import-Csv -Path $evidencePath)
$hvacStats = Get-GroupEvidence -Evidence $evidence -Prefix "HVAC:"
$windowStats = Get-GroupEvidence -Evidence $evidence -Prefix "WINDOW:"
$allStats = [pscustomobject]@{
    Count = $evidence.Count
    SetFailed = @($evidence | Where-Object { $_.EvidenceStatus -eq "SET_FAILED" }).Count
    ReadbackFailed = @($evidence | Where-Object { $_.EvidenceStatus -eq "READBACK_IO_FAILED" }).Count
    AcceptedNoReadback = @($evidence | Where-Object { $_.EvidenceStatus -eq "ACCEPTED_NO_READBACK" }).Count
    ReadbackPresent = @($evidence | Where-Object { $_.EvidenceStatus -eq "READBACK_PRESENT_NEEDS_RUNTIME_RESULT" }).Count
    Insufficient = @($evidence | Where-Object { $_.EvidenceStatus -eq "INSUFFICIENT_EVIDENCE" }).Count
}

$reviewRows = foreach ($row in $matrix) {
    $stats = $null
    switch ($row.group) {
        "HVAC" { $stats = $hvacStats }
        "Windows" { $stats = $windowStats }
        "VHAL" { $stats = $allStats }
        default { $stats = $null }
    }

    if ($null -ne $stats) {
        $suggestion = Get-Suggestion -Stats $stats
        $evidenceSummary = "commands=$($stats.Count); setFailed=$($stats.SetFailed); readbackFailed=$($stats.ReadbackFailed); acceptedNoReadback=$($stats.AcceptedNoReadback); readbackPresent=$($stats.ReadbackPresent); insufficient=$($stats.Insufficient)"
        $suggested = $suggestion.Suggested
        $evidenceDetail = $suggestion.Detail
    } else {
        $suggested = "MANUAL_REVIEW"
        $evidenceSummary = "Không có mapper tự động an toàn cho nhóm này."
        $evidenceDetail = "Giữ nguyên result hiện tại; cần đánh giá bằng log/quan sát chuyên biệt của test case."
    }

    [pscustomobject]@{
        id = $row.id
        group = $row.group
        test_case = $row.test_case
        expected = $row.expected
        result = $row.result
        notes = $row.notes
        evidence_suggestion = $suggested
        evidence_summary = $evidenceSummary
        evidence_detail = $evidenceDetail
    }
}

$outPath = Join-Path $bundleDir "IHU689G_TEST_MATRIX_REVIEW.csv"
$reviewRows | Export-Csv -Path $outPath -NoTypeInformation -Encoding utf8

Write-Host "[THTVoice] Đã tạo test-matrix review: $outPath"
Write-Host "[THTVoice] Script KHÔNG sửa matrix gốc và KHÔNG tự ghi PASS."
Write-Host "[THTVoice] REVIEW_FAIL chỉ là cờ triage; phải đối chiếu đúng test case trước khi ghi FAIL chính thức."
