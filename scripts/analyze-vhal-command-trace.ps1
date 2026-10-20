param(
    [Parameter(Mandatory = $true)]
    [string]$BundlePath
)

$ErrorActionPreference = "Stop"

function Resolve-BundleDirectory {
    param([string]$Path)

    if (-not (Test-Path $Path)) { throw "Không tìm thấy bundle: $Path" }
    $item = Get-Item $Path
    if ($item.PSIsContainer) { return $item.FullName }
    if ($item.Extension -ieq ".zip") {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-vhal-trace-" + [Guid]::NewGuid().ToString("N"))
        New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
        Expand-Archive -Path $item.FullName -DestinationPath $tempRoot -Force
        return $tempRoot
    }
    throw "BundlePath phải là thư mục collector hoặc file .zip"
}

function Get-EvidenceStatus {
    param(
        [int]$SetAccepted,
        [int]$SetFailed,
        [int]$GetOk,
        [int]$GetFailed
    )

    if ($SetFailed -gt 0) {
        return "SET_FAILED"
    }
    if ($SetAccepted -gt 0 -and $GetFailed -gt 0 -and $GetOk -eq 0) {
        return "READBACK_IO_FAILED"
    }
    if ($SetAccepted -gt 0 -and $GetOk -eq 0) {
        return "ACCEPTED_NO_READBACK"
    }
    if ($SetAccepted -gt 0 -and $GetOk -gt 0) {
        return "READBACK_PRESENT_NEEDS_RUNTIME_RESULT"
    }
    return "INSUFFICIENT_EVIDENCE"
}

function Get-EvidenceReason {
    param([string]$Status)

    switch ($Status) {
        "SET_FAILED" { return "Ít nhất một VHAL SET không được chấp nhận; không được coi actuator đã chạy." }
        "READBACK_IO_FAILED" { return "Write được chấp nhận nhưng read-back thất bại; trạng thái xe chưa xác minh được." }
        "ACCEPTED_NO_READBACK" { return "Write được chấp nhận nhưng không có GET OK; đây chỉ là AcceptedUnverified." }
        "READBACK_PRESENT_NEEDS_RUNTIME_RESULT" { return "Có write và read-back, nhưng vẫn cần CommandExecutionResult/hành vi xe thật trước khi gọi Confirmed." }
        default { return "Không đủ dữ liệu SET/GET để phân loại command." }
    }
}

$bundleDir = Resolve-BundleDirectory $BundlePath
$source = Join-Path $bundleDir "vhal-io-events.txt"
if (-not (Test-Path $source)) {
    throw "Không tìm thấy vhal-io-events.txt trong bundle."
}

$rows = New-Object System.Collections.Generic.List[object]
foreach ($line in (Get-Content -Path $source -ErrorAction Stop)) {
    if ($line -notmatch 'trace=(\d+)\s+scope=([^\s]+)\s+writeSeq=(\d+)\s+op=(GET_(?:INT|BOOL|FLOAT)|SET_(?:INT|BOOL|FLOAT))\s+property=(-?\d+)\s+area=(-?\d+)(?:\s+value=([^\s]+))?\s+result=([A-Z_]+)') {
        continue
    }

    $rows.Add([pscustomobject]@{
        TraceId = [int64]$Matches[1]
        Scope = $Matches[2]
        WriteSeq = [int64]$Matches[3]
        Operation = $Matches[4]
        PropertyId = [int64]$Matches[5]
        AreaId = [int64]$Matches[6]
        Value = $Matches[7]
        Result = $Matches[8]
    })
}

$detailPath = Join-Path $bundleDir "VHAL_COMMAND_TRACE.csv"
$summaryPath = Join-Path $bundleDir "VHAL_COMMAND_SUMMARY.csv"
$evidencePath = Join-Path $bundleDir "VHAL_EVIDENCE_SUMMARY.csv"

if ($rows.Count -eq 0) {
    "Không có structured command trace. APK có thể là bản cũ chưa có traceId/scope/writeSeq." |
        Out-File -Encoding utf8 (Join-Path $bundleDir "VHAL_COMMAND_TRACE_NOT_AVAILABLE.txt")
    Write-Host "[THTVoice] Không có command-scoped VHAL trace trong bundle."
    return
}

$rows | Sort-Object TraceId, WriteSeq, Operation | Export-Csv -Path $detailPath -NoTypeInformation -Encoding utf8

$summary = foreach ($group in ($rows | Where-Object { $_.TraceId -gt 0 } | Group-Object TraceId, Scope)) {
    $items = @($group.Group)
    $first = $items | Select-Object -First 1
    $setAccepted = @($items | Where-Object { $_.Operation -like 'SET_*' -and $_.Result -eq 'ACCEPTED' }).Count
    $setFailed = @($items | Where-Object { $_.Operation -like 'SET_*' -and $_.Result -ne 'ACCEPTED' }).Count
    $getOk = @($items | Where-Object { $_.Operation -like 'GET_*' -and $_.Result -eq 'OK' }).Count
    $getFailed = @($items | Where-Object { $_.Operation -like 'GET_*' -and $_.Result -ne 'OK' }).Count
    $properties = ($items | ForEach-Object { "$($_.PropertyId):$($_.AreaId)" } | Sort-Object -Unique) -join ';'
    $writeSeqs = @($items | Where-Object { $_.Operation -like 'SET_*' } | Select-Object -ExpandProperty WriteSeq)
    $evidenceStatus = Get-EvidenceStatus -SetAccepted $setAccepted -SetFailed $setFailed -GetOk $getOk -GetFailed $getFailed

    [pscustomobject]@{
        TraceId = $first.TraceId
        Scope = $first.Scope
        SetAccepted = $setAccepted
        SetFailed = $setFailed
        GetOk = $getOk
        GetFailed = $getFailed
        PropertyAreas = $properties
        FirstWriteSeq = if ($writeSeqs.Count -gt 0) { ($writeSeqs | Measure-Object -Minimum).Minimum } else { '' }
        LastWriteSeq = if ($writeSeqs.Count -gt 0) { ($writeSeqs | Measure-Object -Maximum).Maximum } else { '' }
        HasReadBack = ($getOk -gt 0)
        EvidenceStatus = $evidenceStatus
        EvidenceReason = Get-EvidenceReason -Status $evidenceStatus
        HardwareConfirmed = $false
    }
}

@($summary) | Sort-Object TraceId | Export-Csv -Path $summaryPath -NoTypeInformation -Encoding utf8
@($summary) |
    Select-Object TraceId, Scope, EvidenceStatus, EvidenceReason, SetAccepted, SetFailed, GetOk, GetFailed, PropertyAreas, HardwareConfirmed |
    Sort-Object TraceId |
    Export-Csv -Path $evidencePath -NoTypeInformation -Encoding utf8

$unscopedSets = @($rows | Where-Object { $_.TraceId -eq 0 -and $_.Operation -like 'SET_*' }).Count
$scopedCommands = @($summary).Count
$commandsWithoutReadback = @($summary | Where-Object { $_.EvidenceStatus -eq "ACCEPTED_NO_READBACK" }).Count
$commandsWithReadbackFailure = @($summary | Where-Object { $_.EvidenceStatus -eq "READBACK_IO_FAILED" }).Count
$commandsWithSetFailure = @($summary | Where-Object { $_.EvidenceStatus -eq "SET_FAILED" }).Count
$commandsWithReadback = @($summary | Where-Object { $_.EvidenceStatus -eq "READBACK_PRESENT_NEEDS_RUNTIME_RESULT" }).Count
$commandsInsufficient = @($summary | Where-Object { $_.EvidenceStatus -eq "INSUFFICIENT_EVIDENCE" }).Count

Write-Host "[THTVoice] VHAL command trace: $detailPath"
Write-Host "[THTVoice] VHAL command summary: $summaryPath"
Write-Host "[THTVoice] VHAL evidence summary: $evidencePath"
Write-Host "[THTVoice] Scoped commands: $scopedCommands"
Write-Host "[THTVoice] SET_FAILED: $commandsWithSetFailure"
Write-Host "[THTVoice] READBACK_IO_FAILED: $commandsWithReadbackFailure"
Write-Host "[THTVoice] ACCEPTED_NO_READBACK: $commandsWithoutReadback"
Write-Host "[THTVoice] READBACK_PRESENT_NEEDS_RUNTIME_RESULT: $commandsWithReadback"
Write-Host "[THTVoice] INSUFFICIENT_EVIDENCE: $commandsInsufficient"
Write-Host "[THTVoice] Unscoped SET operations: $unscopedSets"
Write-Host "[THTVoice] Không dòng nào được tự gán HardwareConfirmed=true chỉ từ VHAL SET/GET."
Write-Host "[THTVoice] Confirmed chỉ hợp lệ khi CommandExecutionResult + read-back + hành vi xe thật cùng khớp."
