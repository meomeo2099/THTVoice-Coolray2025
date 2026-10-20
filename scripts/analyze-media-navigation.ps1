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
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-media-analysis-" + [Guid]::NewGuid().ToString("N"))
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

$bundleDir = Resolve-BundleDirectory $BundlePath
$events = Read-TextSafe (Join-Path $bundleDir "media-navigation-events.txt")
$beforeSession = Read-TextSafe (Join-Path $bundleDir "media-session-before.txt")
$afterSession = Read-TextSafe (Join-Path $bundleDir "media-session-after.txt")

$openAppStarted = Count-Matches $events "op=OPEN_APP package=[^\s]+ result=STARTED"
$openAppFailed = Count-Matches $events "op=OPEN_APP .*result=(FAILED|NO_TARGET)"
$openUriStarted = Count-Matches $events "op=OPEN_URI package=[^\s]+ result=STARTED"
$openUriNoTarget = Count-Matches $events "op=OPEN_URI .*result=(NO_TARGET|NOT_RESOLVED|FAILED|INVALID_URI)"
$mediaDispatch = Count-Matches $events "op=MEDIA .*phase=DISPATCH"
$mediaConfirmed = Count-Matches $events "op=MEDIA .*result=CONFIRMED"
$mediaUnverified = Count-Matches $events "op=MEDIA .*result=ACCEPTED_UNVERIFIED"
$mediaFailed = Count-Matches $events "op=MEDIA .*result=FAILED"
$trackConfirmed = Count-Matches $events "op=MEDIA action=(NEXT|PREVIOUS) verify=TRACK_CHANGE .*result=CONFIRMED"
$trackUnverified = Count-Matches $events "op=MEDIA action=(NEXT|PREVIOUS) .*result=ACCEPTED_UNVERIFIED"
$volumeConfirmed = Count-Matches $events "op=MEDIA action=VOLUME_(UP|DOWN) verify=VOLUME .*result=CONFIRMED"
$sessionAvailable = Count-Matches $events "sessionAvailable=true"

$startedPackages = @()
if (-not [string]::IsNullOrWhiteSpace($events)) {
    foreach ($match in [regex]::Matches($events, 'op=(?:OPEN_APP|OPEN_URI) package=([^\s]+) result=STARTED')) {
        $startedPackages += $match.Groups[1].Value
    }
}
$startedPackages = @($startedPackages | Sort-Object -Unique)

$warnings = New-Object System.Collections.Generic.List[string]
if ([string]::IsNullOrWhiteSpace($events)) {
    $warnings.Add("Không có media-navigation-events.txt hoặc file rỗng. Chưa đủ dữ liệu để xác minh Media/Navigation.")
}
if ($trackUnverified -gt 0) {
    $warnings.Add("Có Next/Previous AcceptedUnverified. Không được nói đã đổi bài nếu MediaSession chưa xác nhận track change.")
}
if ($mediaFailed -gt 0) {
    $warnings.Add("Có media action FAILED. Đối chiếu timestamp với app/session đang active.")
}
if ($openUriNoTarget -gt 0) {
    $warnings.Add("Có OPEN_URI không resolve/start được app đích. Kiểm tra package đã cài; không thêm generic browser fallback.")
}
if ($mediaDispatch -gt 0 -and $mediaConfirmed -eq 0 -and $mediaUnverified -eq 0 -and $mediaFailed -eq 0) {
    $warnings.Add("Có media dispatch nhưng không thấy outcome marker. Kiểm tra log bị cắt hoặc phiên app kết thúc sớm.")
}

$packageLabel = if ($startedPackages.Count -gt 0) { $startedPackages -join ", " } else { "không có" }
$beforePresent = -not [string]::IsNullOrWhiteSpace($beforeSession)
$afterPresent = -not [string]::IsNullOrWhiteSpace($afterSession)
$reportPath = Join-Path $bundleDir "MEDIA_NAVIGATION_SUMMARY.txt"
$warningsText = if ($warnings.Count -eq 0) {
    "- Không có cảnh báo tự động. Vẫn phải đối chiếu app/màn hình và audio thật trên xe."
} else {
    ($warnings | ForEach-Object { "- $_" }) -join "`r`n"
}

@"
THT Voice — Media / Navigation validation analysis
Bundle: $bundleDir

APP / NAVIGATION
- OPEN_APP started : $openAppStarted
- OPEN_APP failures: $openAppFailed
- OPEN_URI started : $openUriStarted
- OPEN_URI unresolved/failed: $openUriNoTarget
- started packages : $packageLabel

MEDIA
- dispatch markers : $mediaDispatch
- confirmed outcomes: $mediaConfirmed
- accepted unverified: $mediaUnverified
- failed outcomes   : $mediaFailed
- volume confirmed  : $volumeConfirmed
- Next/Previous track confirmed: $trackConfirmed
- Next/Previous unverified      : $trackUnverified
- MediaSession available at dispatch: $sessionAvailable

SNAPSHOTS
- media-session-before present: $beforePresent
- media-session-after present : $afterPresent

WARNINGS / REVIEW
$warningsText

QUAN TRỌNG
- OPEN_APP/OPEN_URI STARTED chỉ chứng minh explicit Android intent được start; hãy nhìn app đích thật trên màn hình.
- Next/Previous chỉ được coi Confirmed khi MediaSession metadata chứng minh track đổi trên cùng package/session.
- Không thêm generic browser fallback để che lỗi package/navigation target.
- Báo cáo này không đọc hay xuất URI tìm kiếm, điểm đến hoặc metadata tên bài hát.
"@ | Out-File -Encoding utf8 $reportPath

Write-Host "[THTVoice] Đã tạo báo cáo: $reportPath"
Get-Content $reportPath
