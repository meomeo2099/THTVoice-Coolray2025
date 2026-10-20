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
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-analysis-" + [Guid]::NewGuid().ToString("N"))
        New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
        Expand-Archive -Path $item.FullName -DestinationPath $tempRoot -Force
        return $tempRoot
    }
    throw "BundlePath phải là thư mục collector hoặc file .zip"
}

function Read-TextSafe {
    param([string]$Path)
    if (Test-Path $Path) { return Get-Content -Path $Path -Raw -ErrorAction SilentlyContinue }
    return ""
}

function Count-Matches {
    param([string]$Text, [string]$Pattern)
    if ([string]::IsNullOrWhiteSpace($Text)) { return 0 }
    return ([regex]::Matches($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)).Count
}

function Extract-MemTotalKb {
    param([string]$Text)
    if ($Text -match "TOTAL\s+(\d+)") { return [int64]$Matches[1] }
    if ($Text -match "TOTAL PSS:\s*(\d+)") { return [int64]$Matches[1] }
    return $null
}

function Build-VhalCorrelationRows {
    param([string]$Text)

    $groups = @{}
    if ([string]::IsNullOrWhiteSpace($Text)) { return @() }

    foreach ($line in ($Text -split "`r?`n")) {
        if ($line -notmatch 'op=(GET_(?:INT|BOOL|FLOAT)|SET_(?:INT|BOOL|FLOAT))\s+property=(-?\d+)\s+area=(-?\d+)(?:\s+value=([^\s]+))?\s+result=([A-Z_]+)') {
            continue
        }

        $op = $Matches[1]
        $propertyId = [int64]$Matches[2]
        $areaId = [int64]$Matches[3]
        $value = $Matches[4]
        $result = $Matches[5]
        $key = "$propertyId|$areaId"

        if (-not $groups.ContainsKey($key)) {
            $groups[$key] = [pscustomobject]@{
                PropertyId = $propertyId
                AreaId = $areaId
                SetAccepted = 0
                SetFailed = 0
                GetOk = 0
                GetFailed = 0
                LastSetValue = ""
                LastGetValue = ""
            }
        }

        $group = $groups[$key]
        if ($op -like "SET_*") {
            if ($result -eq "ACCEPTED") { $group.SetAccepted += 1 } else { $group.SetFailed += 1 }
            if (-not [string]::IsNullOrWhiteSpace($value)) { $group.LastSetValue = $value }
        } else {
            if ($result -eq "OK") { $group.GetOk += 1 } else { $group.GetFailed += 1 }
            if ($result -eq "OK" -and -not [string]::IsNullOrWhiteSpace($value)) { $group.LastGetValue = $value }
        }
    }

    $rows = foreach ($group in $groups.Values) {
        $relation = if ([string]::IsNullOrWhiteSpace($group.LastSetValue) -or [string]::IsNullOrWhiteSpace($group.LastGetValue)) {
            "INSUFFICIENT_DATA"
        } elseif ($group.LastSetValue -eq $group.LastGetValue) {
            "LAST_VALUE_MATCH"
        } else {
            "LAST_VALUE_DIFF_OR_MOVING"
        }

        [pscustomobject]@{
            PropertyId = $group.PropertyId
            AreaId = $group.AreaId
            SetAccepted = $group.SetAccepted
            SetFailed = $group.SetFailed
            GetOk = $group.GetOk
            GetFailed = $group.GetFailed
            LastSetValue = $group.LastSetValue
            LastGetValue = $group.LastGetValue
            LastRelation = $relation
        }
    }

    return @($rows | Sort-Object PropertyId, AreaId)
}

$bundleDir = Resolve-BundleDirectory $BundlePath
$log = Read-TextSafe (Join-Path $bundleDir "thtvoice-logcat.txt")
$asr = Read-TextSafe (Join-Path $bundleDir "asr-metrics.txt")
$mic = Read-TextSafe (Join-Path $bundleDir "mic-handoff-events.txt")
$vehicle = Read-TextSafe (Join-Path $bundleDir "vehicle-events.txt")
$vhalIo = Read-TextSafe (Join-Path $bundleDir "vhal-io-events.txt")
$wake = Read-TextSafe (Join-Path $bundleDir "wake-events.txt")
$security = Read-TextSafe (Join-Path $bundleDir "security-vin-events.txt")
$crash = Read-TextSafe (Join-Path $bundleDir "crash-events.txt")
$memBeforeText = Read-TextSafe (Join-Path $bundleDir "meminfo-before.txt")
$memAfterText = Read-TextSafe (Join-Path $bundleDir "meminfo-after.txt")
$displaySize = (Read-TextSafe (Join-Path $bundleDir "display-size.txt")).Trim()
$displayDensity = (Read-TextSafe (Join-Path $bundleDir "display-density.txt")).Trim()

$crashCount = Count-Matches $crash "FATAL EXCEPTION|ANR in|AndroidRuntime"
$asrSessionCount = Count-Matches $asr "session|computeRTF|firstPartial|finishLatency"
$kwsCount = Count-Matches $wake "Hey THT|Hello Geely|Hey Geely|Wake word|KWS|wakeReady"
$wakeDetectedCount = Count-Matches $wake "Wake word detected"
$wakeHeyThtCount = Count-Matches $wake "Wake word detected profile=HEY_THT\b"
$wakeHelloGeelyCount = Count-Matches $wake "Wake word detected profile=HELLO_GEELY\b"
$wakeHeyGeelyCount = Count-Matches $wake "Wake word detected profile=HEY_GEELY\b"
$micActiveCount = Count-Matches $mic "Microphone active"
$micBusyCount = Count-Matches $mic "ASR microphone acquire rejected"
$micOpenFailCount = Count-Matches $mic "Unable to open microphone|Không khởi tạo được micrô|Unable to start source"
$vehicleConfirmed = Count-Matches $vehicle "CONFIRMED|Confirmed"
$vehicleUnverified = Count-Matches $vehicle "AcceptedUnverified"
$vehicleFailed = Count-Matches $vehicle "FAILED|Failed"
$vehicleRejected = Count-Matches $vehicle "Rejected|REJECTED"
$vinMarkers = Count-Matches $security "VIN|vin"
$securityMarkers = Count-Matches $security "license|activation|binding|expired|blocked|THTVoiceSecurity"

$vhalConnectOk = Count-Matches $vhalIo "op=CONNECT result=OK"
$vhalConnectFailed = Count-Matches $vhalIo "op=CONNECT result=(FAILED|NO_PROPERTY_MANAGER)"
$vhalSetCount = Count-Matches $vhalIo "op=SET_(INT|BOOL|FLOAT).*result=ACCEPTED"
$vhalGetCount = Count-Matches $vhalIo "op=GET_(INT|BOOL|FLOAT).*result=OK"
$vhalIoFailed = Count-Matches $vhalIo "op=(GET_|SET_).+result=FAILED"
$vhalSetFailed = Count-Matches $vhalIo "op=SET_(INT|BOOL|FLOAT).*result=FAILED"
$vhalGetFailed = Count-Matches $vhalIo "op=GET_(INT|BOOL|FLOAT).*result=FAILED"

$vhalRows = @(Build-VhalCorrelationRows $vhalIo)
$vhalSummaryPath = Join-Path $bundleDir "VHAL_IO_SUMMARY.csv"
if ($vhalRows.Count -gt 0) {
    $vhalRows | Export-Csv -Path $vhalSummaryPath -NoTypeInformation -Encoding utf8
}
$vhalPairCount = $vhalRows.Count
$vhalDiffCount = @($vhalRows | Where-Object { $_.LastRelation -eq "LAST_VALUE_DIFF_OR_MOVING" }).Count
$vhalInsufficientCount = @($vhalRows | Where-Object { $_.LastRelation -eq "INSUFFICIENT_DATA" }).Count
$vhalGroupFailureCount = @($vhalRows | Where-Object { $_.SetFailed -gt 0 -or $_.GetFailed -gt 0 }).Count

$hiMarkers = Count-Matches ($log + "`n" + $vehicle) "\bHI\b|CLIMATE_SET_HI|temperature.*hi"
$temp20Markers = Count-Matches ($log + "`n" + $vehicle) "\b20(?:\.0)?\b|20C|20°C"
$temp22Markers = Count-Matches ($log + "`n" + $vehicle) "\b22(?:\.0)?\b|22C|22°C"

$memBefore = Extract-MemTotalKb $memBeforeText
$memAfter = Extract-MemTotalKb $memAfterText
$memDelta = if ($null -ne $memBefore -and $null -ne $memAfter) { $memAfter - $memBefore } else { $null }

$reportPath = Join-Path $bundleDir "ANALYSIS_SUMMARY.txt"
$memBeforeLabel = if ($null -ne $memBefore) { "$memBefore KB" } else { "không đọc được" }
$memAfterLabel = if ($null -ne $memAfter) { "$memAfter KB" } else { "không đọc được" }
$memDeltaLabel = if ($null -ne $memDelta) { "$memDelta KB" } else { "không tính được" }

$warnings = New-Object System.Collections.Generic.List[string]
if ($crashCount -gt 0) { $warnings.Add("Có marker crash/ANR. Kiểm tra crash-events.txt và log gốc.") }
if ($hiMarkers -gt 0 -and ($temp20Markers -gt 0 -or $temp22Markers -gt 0)) {
    $warnings.Add("Cùng bundle có marker HI và 20/22. Cần đối chiếu timestamp/typed command để loại trừ regression nhiệt độ.")
}
if ($vehicleFailed -gt 0 -or $vehicleRejected -gt 0) {
    $warnings.Add("Có vehicle command Failed/Rejected. Cần đối chiếu property/area/read-back trước khi sửa mapping.")
}
if ($vhalConnectFailed -gt 0) { $warnings.Add("CarPropertyManager/VHAL connect không sẵn sàng ở ít nhất một thời điểm. Kiểm tra quyền OEM/car service trước khi sửa command mapping.") }
if ($vhalSetFailed -gt 0) { $warnings.Add("Có SET VHAL bị FAILED. Ưu tiên kiểm tra permission/property availability/area trước parser.") }
if ($vhalGetFailed -gt 0) { $warnings.Add("Có GET VHAL bị FAILED. Read-back không đáng tin ở các property/area tương ứng.") }
if ($vhalSetCount -gt 0 -and $vhalGetCount -eq 0) { $warnings.Add("Có write được chấp nhận nhưng không thấy GET read-back trong trace. Không được coi actuator là Confirmed.") }
if ($vhalDiffCount -gt 0) { $warnings.Add("Có property/area mà SET cuối và GET cuối khác nhau. Với kính có thể đang di chuyển; hãy đối chiếu timestamp/hành vi xe trước khi kết luận mapping sai.") }
if ($vhalInsufficientCount -gt 0) { $warnings.Add("Có property/area thiếu SET hoặc GET để correlation. Kiểm tra VHAL_IO_SUMMARY.csv trước khi đánh PASS.") }
if ($micBusyCount -gt 0) { $warnings.Add("Có ASR mic acquire rejected: khả năng KWS/ASR handoff hoặc re-entry chưa nhả ownership đúng lúc.") }
if ($micOpenFailCount -gt 0) { $warnings.Add("Có lỗi mở/start mic. Kiểm tra RECORD_AUDIO, AudioSource và recorder khác đang giữ mic.") }
if ($wakeDetectedCount -gt ($wakeHeyThtCount + $wakeHelloGeelyCount + $wakeHeyGeelyCount)) { $warnings.Add("Có wake detection không gắn profile đã biết; kiểm tra wake-events.txt và bản APK của phiên test.") }
if ($wakeDetectedCount -gt 0 -and $asrSessionCount -eq 0) { $warnings.Add("Có wake detection nhưng không thấy ASR metrics: nghi ngờ lỗi KWS -> ASR handoff.") }
if ($vinMarkers -eq 0) { $warnings.Add("Không thấy marker VIN trong log đã lọc; không được bật VIN-required binding từ bundle này.") }
if ($asrSessionCount -eq 0) { $warnings.Add("Không thấy ASR metric marker; kiểm tra tag THTVoiceASR hoặc phiên test chưa chạy ASR.") }

$warningsText = if ($warnings.Count -eq 0) {
    "- Không có cảnh báo tự động. Vẫn phải đối chiếu hành vi xe thật + test matrix."
} else {
    ($warnings | ForEach-Object { "- $_" }) -join "`r`n"
}

@"
THT Voice — IHU689G validation analysis
Bundle: $bundleDir

DISPLAY
- size: $displaySize
- density: $displayDensity

STABILITY
- crash/ANR markers: $crashCount
- memory before: $memBeforeLabel
- memory after : $memAfterLabel
- memory delta : $memDeltaLabel

VOICE / MIC HANDOFF
- ASR metric markers: $asrSessionCount
- KWS/wake markers : $kwsCount
- wake detections   : $wakeDetectedCount
- Hey THT detections: $wakeHeyThtCount
- Hello Geely detections: $wakeHelloGeelyCount
- Hey Geely detections: $wakeHeyGeelyCount
- mic active markers: $micActiveCount
- mic busy rejects  : $micBusyCount
- mic open failures : $micOpenFailCount
- temperature 20 markers: $temp20Markers
- temperature 22 markers: $temp22Markers
- HI markers: $hiMarkers

VHAL IO
- connect OK       : $vhalConnectOk
- connect failures : $vhalConnectFailed
- accepted SET     : $vhalSetCount
- successful GET   : $vhalGetCount
- IO failures total: $vhalIoFailed
- SET failures     : $vhalSetFailed
- GET failures     : $vhalGetFailed
- property/area groups: $vhalPairCount
- groups with IO failure: $vhalGroupFailureCount
- last SET/GET differ or moving: $vhalDiffCount
- groups with insufficient correlation: $vhalInsufficientCount
- detailed correlation: $vhalSummaryPath

VEHICLE RESULTS
- Confirmed: $vehicleConfirmed
- AcceptedUnverified: $vehicleUnverified
- Failed: $vehicleFailed
- Rejected: $vehicleRejected

SECURITY / VIN
- security markers: $securityMarkers
- VIN markers: $vinMarkers

WARNINGS / REVIEW
$warningsText

QUAN TRỌNG
Số wake detection theo profile chỉ đếm sự kiện đã phát hiện; missed wake và false wake phải ghi theo số lần thử thực tế trong test matrix.
Báo cáo này chỉ hỗ trợ triage. `SET ... result=ACCEPTED` chỉ có nghĩa CarPropertyManager không ném lỗi tại thời điểm write.
`LAST_VALUE_MATCH` cũng không tự chứng minh actuator cơ khí đã hoàn tất; phải đối chiếu hành vi xe thật và CommandExecutionResult.
`LAST_VALUE_DIFF_OR_MOVING` không tự chứng minh mapping sai vì kính/actuator có thể đang di chuyển khi GET được ghi.
Báo cáo KHÔNG tự suy đoán Property ID/Area ID và không đủ để bật VIN-required binding.
Hãy điền docs/phase10/IHU689G_TEST_MATRIX.csv dựa trên hành vi xe thật + read-back/log tương ứng.
"@ | Out-File -Encoding utf8 $reportPath

Write-Host "[THTVoice] Đã tạo báo cáo: $reportPath"
if (Test-Path $vhalSummaryPath) { Write-Host "[THTVoice] VHAL correlation: $vhalSummaryPath" }
Get-Content $reportPath
