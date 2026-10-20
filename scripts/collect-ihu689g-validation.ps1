param(
    [string]$Serial = "",
    [int]$DurationSeconds = 180,
    [string]$OutputRoot = ".\thtvoice-validation",
    [string]$ProvenanceDirectory = "",
    [string]$SceneProbePath = "",
    [switch]$ZipBundle
)

$ErrorActionPreference = "Stop"
$PackageName = "com.tht.voice.coolray2025"

function Get-AdbPrefix {
    if ($Serial -and $Serial.Trim().Length -gt 0) { return @("-s", $Serial) }
    return @()
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$CommandArgs)
    $prefix = Get-AdbPrefix
    $output = & adb @prefix @CommandArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($CommandArgs -join ' ') failed ($LASTEXITCODE): $($output -join ' ')"
    }
    return $output
}

function Save-AdbText {
    param([string]$FileName,[string[]]$CommandArgs)
    $target = Join-Path $outDir $FileName
    try { Invoke-Adb @CommandArgs | Out-File -Encoding utf8 $target }
    catch { "unavailable: $($_.Exception.Message)" | Out-File -Encoding utf8 $target }
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "Không tìm thấy adb trong PATH. Hãy cài Android platform-tools trước."
}

if (-not $Serial) {
    $devices = @(& adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" })
    if ($devices.Count -eq 0) { throw "Không tìm thấy thiết bị ADB đang ở trạng thái device." }
    if ($devices.Count -gt 1) { throw "Có nhiều thiết bị ADB. Hãy chạy lại với -Serial <ADB_SERIAL>." }
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $OutputRoot $stamp
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if ($ProvenanceDirectory) {
    if (-not (Test-Path -LiteralPath $ProvenanceDirectory -PathType Container)) {
        throw "Không tìm thấy ProvenanceDirectory: $ProvenanceDirectory"
    }
    $provenanceOut = Join-Path $outDir "hardware-package-provenance"
    New-Item -ItemType Directory -Force -Path $provenanceOut | Out-Null
    foreach ($name in @(
        "SHA256.txt",
        "BUILD_INFO.txt",
        "ASSET_SHA256.txt",
        "IHU689G_TEST_MATRIX.csv",
        "probe-scene-contract.ps1"
    )) {
        $source = Join-Path $ProvenanceDirectory $name
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Hardware package provenance thiếu file: $name"
        }
        Copy-Item -LiteralPath $source -Destination (Join-Path $provenanceOut $name) -Force
    }
}

if ($SceneProbePath) {
    if (-not (Test-Path -LiteralPath $SceneProbePath -PathType Leaf)) {
        throw "Không tìm thấy packaged Scene probe: $SceneProbePath"
    }
} else {
    $repoSceneProbe = Join-Path $PSScriptRoot "probe-scene-contract.ps1"
    if (Test-Path -LiteralPath $repoSceneProbe -PathType Leaf) { $SceneProbePath = $repoSceneProbe }
}

Write-Host "[THTVoice] Output: $outDir"
Write-Host "[THTVoice] Kiểm tra thiết bị..."
Invoke-Adb get-state | Out-Null

Save-AdbText "device-list.txt" @("devices", "-l")
Save-AdbText "getprop-before.txt" @("shell", "getprop")
Save-AdbText "display-size.txt" @("shell", "wm", "size")
Save-AdbText "display-density.txt" @("shell", "wm", "density")
Save-AdbText "package-info-before.txt" @("shell", "dumpsys", "package", $PackageName)
Save-AdbText "appops-before.txt" @("shell", "appops", "get", $PackageName)
Save-AdbText "services-before.txt" @("shell", "dumpsys", "activity", "services", $PackageName)
Save-AdbText "meminfo-before.txt" @("shell", "dumpsys", "meminfo", $PackageName)
Save-AdbText "media-session-before.txt" @("shell", "dumpsys", "media_session")
Save-AdbText "car-service-before.txt" @("shell", "dumpsys", "car_service")

Invoke-Adb logcat -c
$logPath = Join-Path $outDir "thtvoice-logcat.txt"
$tags = @(
    "THTVoiceASR:I","THTVoiceService:I","THTVoiceVHAL:V","THTVoiceAndroid:I",
    "THTVoiceOverlay:I","THTVoiceKWS:I","THTVoiceWakeBridge:I","THTVoiceSecurity:I",
    "THTVoiceVIN:I","THTVoiceECARX:I","AndroidRuntime:E","*:S"
)

Write-Host ""
Write-Host "=== BẮT ĐẦU PHIÊN TEST THT VOICE ==="
Write-Host "Trong $DurationSeconds giây, ưu tiên test:"
Write-Host "  Mỗi phiên chỉ cần một nhóm case; chạy lại collector cho các nhóm còn lại."
Write-Host "  1. Security cold-start / activation gate"
Write-Host "  2. Nút Voice trên xe -> Security/Voice service routing"
Write-Host "  3. Settings: chọn lần lượt Hey THT / Hello Geely / Hey Geely."
Write-Host "  4. Kiểm tra KWS -> nhả mic -> ASR -> processing -> resume KWS"
Write-Host "  5. Nhiệt độ 20, 22, hai hai, HI và LO; tuyệt đối không nhảy sai HI"
Write-Host "  6. HVAC/kính/media/navigation/overlay theo packaged IHU689G_TEST_MATRIX.csv"
Write-Host "  9. Scene: đổi Rest/Camping chỉ bằng OEM UI khi cần; THT probe chỉ QUERY provider"
Write-Host ""

$prefix = Get-AdbPrefix
$adbArgs = @($prefix + @("logcat", "-v", "threadtime") + $tags)
$proc = Start-Process -FilePath "adb" -ArgumentList $adbArgs -RedirectStandardOutput $logPath -RedirectStandardError (Join-Path $outDir "logcat-error.txt") -PassThru -NoNewWindow
try { Start-Sleep -Seconds $DurationSeconds }
finally { if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } }

Save-AdbText "package-info-after.txt" @("shell", "dumpsys", "package", $PackageName)
Save-AdbText "appops-after.txt" @("shell", "appops", "get", $PackageName)
Save-AdbText "services-after.txt" @("shell", "dumpsys", "activity", "services", $PackageName)
Save-AdbText "meminfo-after.txt" @("shell", "dumpsys", "meminfo", $PackageName)
Save-AdbText "media-session-after.txt" @("shell", "dumpsys", "media_session")
Save-AdbText "car-service-after.txt" @("shell", "dumpsys", "car_service")

if (Test-Path $logPath) {
    Select-String -Path $logPath -Pattern "THTVoiceASR" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "asr-metrics.txt")
    Select-String -Path $logPath -Pattern "THTVoiceVHAL|property=|area=|read-back|READBACK|CONFIRMED|AcceptedUnverified|FAILED|Rejected" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "vehicle-events.txt")
    Select-String -Path $logPath -Pattern "THTVoiceVHAL.*op=(GET_|SET_|CONNECT|DISCONNECT).*result=" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "vhal-io-events.txt")
    Select-String -Path $logPath -Pattern "THTVoiceAndroid.*op=(OPEN_APP|OPEN_URI|MEDIA)" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "media-navigation-events.txt")
    Select-String -Path $logPath -Pattern "Wake word|Hey THT|Hello Geely|Hey Geely|KWS|wakeReady|THTVoiceKWS|THTVoiceWakeBridge" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "wake-events.txt")
    Select-String -Path $logPath -Pattern "Microphone active|ASR microphone acquire rejected|Unable to open microphone|Wake word detected|THT Voice đang nghe|processing|Sherpa" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "mic-handoff-events.txt")
    Select-String -Path $logPath -Pattern "THTVoiceOverlay|overlay_blocked|attached|detached|auto_hide|minimized|drag_saved|attach_failed|detach_failed" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "overlay-events.txt")
    Select-String -Path $logPath -Pattern "license|activation|binding|VIN|vin|expired|blocked|THTVoiceSecurity|THTVoiceVIN" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "security-vin-events.txt")
    Select-String -Path $logPath -Pattern "THTVoiceECARX|ecarx_key_received|ecarx_route|wake_activity|voice_service_start" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "ecarx-events.txt")
    Select-String -Path $logPath -Pattern "AndroidRuntime|FATAL EXCEPTION|ANR in|Process: com.tht.voice.coolray2025" | ForEach-Object { $_.Line } | Out-File -Encoding utf8 (Join-Path $outDir "crash-events.txt")
}

$sceneSummary = "- scene-evidence/: không có probe khả dụng"
if ($SceneProbePath) {
    $sceneRoot = Join-Path $outDir "scene-evidence"
    $sceneArgs = @("-OutputRoot", $sceneRoot)
    if ($Serial) { $sceneArgs += @("-Serial", $Serial) }
    Write-Host "[THTVoice] Thu Scene provider read-only evidence bằng packaged probe..."
    & $SceneProbePath @sceneArgs
    if ($LASTEXITCODE -ne 0) { throw "Scene read-only evidence probe thất bại với mã $LASTEXITCODE" }
    $sceneSummary = "- scene-evidence/: package/activity/provider QUERY; không launch/mutation"
}

$provenanceSummary = if ($ProvenanceDirectory) {
    "- hardware-package-provenance/: matrix tổng + package metadata + Scene probe của đúng artifact đã test"
} else {
    "- hardware-package-provenance/: không có (collector chạy độc lập / SkipInstall)"
}

@"
THT Voice IHU689G validation bundle
Timestamp: $stamp
DurationSeconds: $DurationSeconds
Serial: $Serial
Package: $PackageName

Files:
- thtvoice-logcat.txt       : log gốc của phiên test
- asr-metrics.txt           : metrics ASR 65M / VAD
- vehicle-events.txt        : VHAL / verify / result liên quan xe
- vhal-io-events.txt        : structured CarProperty GET/SET
- wake/mic/overlay/security/ecarx/crash events: các file lọc tương ứng
- package/appops/services/meminfo/display/media-session/car-service snapshots
$sceneSummary
$provenanceSummary

RIÊNG TƯ: bundle có thể chứa VIN/serial/identifier kỹ thuật; không upload public.
Không tự đánh dấu hardware-confirmed chỉ vì CI/parser/MOCK chạy đúng.
Review phải ưu tiên hardware-package-provenance/IHU689G_TEST_MATRIX.csv của chính artifact đã test.
Scene probe chỉ QUERY provider; raw scene_mode chưa được map REST/CAMPING nếu chưa có before/after OEM UI evidence ổn định.
"@ | Out-File -Encoding utf8 (Join-Path $outDir "README.txt")

if ($ZipBundle) {
    $zipPath = "$outDir.zip"
    if (Test-Path $zipPath) { Remove-Item -Force $zipPath }
    Compress-Archive -Path (Join-Path $outDir "*") -DestinationPath $zipPath -Force
    Write-Host "[THTVoice] ZIP: $zipPath"
}

Write-Host "[THTVoice] Đã thu thập xong: $outDir"
