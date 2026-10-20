param(
    [string]$ApkPath = "",
    [string]$Serial = "",
    [string]$OutputDirectory = "",
    [int]$StartupWaitSeconds = 3,
    [switch]$SkipInstall,
    [switch]$ZipBundle,
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"
$PackageName = "com.tht.voice.coolray2025"
$Launcher = "com.tht.voice.coolray2025/com.voicegeely.app.ActivationActivity"
$VoiceService = "com.voicegeely.app.VoiceAssistantService"

function Get-AdbPrefix {
    if ($Serial -and $Serial.Trim().Length -gt 0) {
        return @("-s", $Serial)
    }
    return @()
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$CommandArgs)
    $prefix = Get-AdbPrefix
    $output = & adb @prefix @CommandArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($CommandArgs -join ' ') failed ($LASTEXITCODE): $($output -join ' ')"
    }
    return @($output)
}

function Invoke-AdbBestEffort {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$CommandArgs)
    try {
        return @(Invoke-Adb @CommandArgs)
    } catch {
        Write-Warning $_.Exception.Message
        return @()
    }
}

function Assert-AdbReady {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw "Không tìm thấy adb trong PATH. Hãy cài Android platform-tools trước."
    }

    if ($Serial) {
        $state = (& adb -s $Serial get-state 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0 -or $state.Trim() -ne "device") {
            throw "Thiết bị ADB '$Serial' chưa ở trạng thái device."
        }
        return
    }

    $devices = @(& adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" })
    if ($devices.Count -eq 0) { throw "Không tìm thấy thiết bị ADB." }
    if ($devices.Count -gt 1) { throw "Có nhiều thiết bị ADB. Hãy dùng -Serial <ADB_SERIAL>." }
}

function Write-Lines {
    param([Parameter(Mandatory = $true)][string]$Path, [object[]]$Lines)
    $text = if ($null -eq $Lines) { "" } else { @($Lines) -join "`r`n" }
    [System.IO.File]::WriteAllText($Path, $text, [System.Text.UTF8Encoding]::new($false))
}

function Pull-InstalledBaseApk {
    param([Parameter(Mandatory = $true)][string[]]$PackagePaths, [Parameter(Mandatory = $true)][string]$Destination)
    $basePaths = @($PackagePaths | Where-Object { $_ -match '^package:.*/base\.apk$' })
    if ($basePaths.Count -ne 1) {
        throw "Không xác định được duy nhất base.apk của $PackageName."
    }

    $remote = $basePaths[0].Substring("package:".Length).Trim()
    $prefix = Get-AdbPrefix
    & adb @prefix pull $remote $Destination | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $Destination -PathType Leaf)) {
        throw "Không pull được base.apk đã cài từ thiết bị."
    }
}

function Get-ForegroundClassification {
    param([string]$ActivityDump, [string]$WindowDump)
    $combined = "$ActivityDump`n$WindowDump"
    if ($combined -match 'com\.tht\.voice\.coolray/(com\.voicegeely\.app\.)?ActivationActivity') {
        return "ANDROID_UI_OBSERVED_ACTIVATION_GATE"
    }
    if ($combined -match 'com\.tht\.voice\.coolray/(com\.voicegeely\.app\.)?MainActivity') {
        return "ANDROID_UI_OBSERVED_LICENSE_ACTIVE_REDIRECT"
    }
    if ($combined -match 'com\.tht\.voice\.coolray') {
        return "ANDROID_UI_OBSERVED_APP_FOREGROUND_UNKNOWN"
    }
    return "BLOCKED_PLATFORM_OR_UNEXPECTED_FOREGROUND"
}

if ($SelfTest) {
    $cases = @(
        @{
            Name = "activation"
            Activity = "mResumedActivity: ActivityRecord{123 com.tht.voice.coolray2025/com.voicegeely.app.ActivationActivity}"
            Window = ""
            Expected = "ANDROID_UI_OBSERVED_ACTIVATION_GATE"
        },
        @{
            Name = "main"
            Activity = ""
            Window = "mCurrentFocus=Window{456 u0 com.tht.voice.coolray2025/com.voicegeely.app.MainActivity}"
            Expected = "ANDROID_UI_OBSERVED_LICENSE_ACTIVE_REDIRECT"
        },
        @{
            Name = "unknown-app-activity"
            Activity = "mResumedActivity: ActivityRecord{789 com.tht.voice.coolray2025/com.voicegeely.app.OtherActivity}"
            Window = ""
            Expected = "ANDROID_UI_OBSERVED_APP_FOREGROUND_UNKNOWN"
        },
        @{
            Name = "other-package"
            Activity = "mResumedActivity: ActivityRecord{abc com.android.settings/.Settings}"
            Window = ""
            Expected = "BLOCKED_PLATFORM_OR_UNEXPECTED_FOREGROUND"
        }
    )

    foreach ($case in $cases) {
        $actual = Get-ForegroundClassification -ActivityDump $case.Activity -WindowDump $case.Window
        if ($actual -ne $case.Expected) {
            throw "SelfTest '$($case.Name)' failed. expected=$($case.Expected) actual=$actual"
        }
    }
    Write-Host "[THTVoice] no-car UI runner self-test PASS ($($cases.Count) cases)"
    return
}

if (-not $ApkPath) {
    throw "Thiếu -ApkPath. Chỉ được bỏ ApkPath khi chạy -SelfTest."
}

if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
    throw "Không tìm thấy APK: $ApkPath"
}

$apk = Get-Item -LiteralPath $ApkPath
if ($apk.Extension -ine ".apk") {
    throw "-ApkPath phải trỏ tới file .apk"
}

Assert-AdbReady

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path (Get-Location) "no-car-ui-$timestamp"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path

$apkHash = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "=== THT Voice / No-car UI validation ==="
Write-Host "APK: $($apk.FullName)"
Write-Host "SHA-256: $apkHash"
Write-Host "Output: $OutputDirectory"
Write-Host ""
Write-Host "Runner này không xóa app data, không tiêm license, không giả VIN và không giả trạng thái xe."

if (-not $SkipInstall) {
    Write-Host "[1/6] Cài APK..."
    $prefix = Get-AdbPrefix
    & adb @prefix install -r $apk.FullName
    if ($LASTEXITCODE -ne 0) { throw "adb install -r thất bại." }
} else {
    Write-Host "[1/6] SkipInstall: giữ bản đang cài nhưng vẫn xác minh SHA-256 base.apk."
}

Write-Host "[2/6] Xác minh package và APK provenance..."
$packagePaths = @(Invoke-Adb shell pm path $PackageName)
if (@($packagePaths | Where-Object { $_ -match '^package:' }).Count -eq 0) {
    throw "Không tìm thấy package $PackageName sau bước cài đặt."
}
Write-Lines -Path (Join-Path $OutputDirectory "pm-path.txt") -Lines $packagePaths

$installedApk = Join-Path $OutputDirectory "installed-base.apk"
Pull-InstalledBaseApk -PackagePaths $packagePaths -Destination $installedApk
$installedHash = (Get-FileHash -LiteralPath $installedApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($installedHash -ne $apkHash) {
    throw "APK đã cài có SHA-256 khác APK đầu vào. expected=$apkHash actual=$installedHash"
}
Remove-Item -LiteralPath $installedApk -Force

Write-Host "[3/6] Cold-start qua ActivationActivity..."
Invoke-Adb shell am force-stop $PackageName | Out-Null
Invoke-Adb shell logcat -c | Out-Null
Invoke-Adb shell am start -n $Launcher | Write-Host
Start-Sleep -Seconds ([Math]::Max(1, $StartupWaitSeconds))

Write-Host "[4/6] Thu evidence Android/UI..."
$activityDump = @(Invoke-AdbBestEffort shell dumpsys activity activities)
$windowDump = @(Invoke-AdbBestEffort shell dumpsys window windows)
$packageDump = @(Invoke-AdbBestEffort shell dumpsys package $PackageName)
$serviceDump = @(Invoke-AdbBestEffort shell dumpsys activity services $PackageName)
$securityLog = @(Invoke-AdbBestEffort shell logcat -d -v threadtime -s 'THTVoiceSecurity:*')

Write-Lines -Path (Join-Path $OutputDirectory "dumpsys-activity.txt") -Lines $activityDump
Write-Lines -Path (Join-Path $OutputDirectory "dumpsys-window.txt") -Lines $windowDump
Write-Lines -Path (Join-Path $OutputDirectory "dumpsys-package.txt") -Lines $packageDump
Write-Lines -Path (Join-Path $OutputDirectory "dumpsys-services.txt") -Lines $serviceDump
Write-Lines -Path (Join-Path $OutputDirectory "security-logcat.txt") -Lines $securityLog

$prefix = Get-AdbPrefix
$remoteUi = "/sdcard/thtvoice-no-car-ui.xml"
$remoteShot = "/sdcard/thtvoice-no-car-screen.png"
& adb @prefix shell uiautomator dump $remoteUi | Out-Null
if ($LASTEXITCODE -eq 0) {
    & adb @prefix pull $remoteUi (Join-Path $OutputDirectory "ui-hierarchy.xml") | Out-Null
    & adb @prefix shell rm -f $remoteUi | Out-Null
}
& adb @prefix shell screencap -p $remoteShot | Out-Null
if ($LASTEXITCODE -eq 0) {
    & adb @prefix pull $remoteShot (Join-Path $OutputDirectory "screen.png") | Out-Null
    & adb @prefix shell rm -f $remoteShot | Out-Null
}

Write-Host "[5/6] Phân loại gate hiện tại..."
$activityText = $activityDump -join "`n"
$windowText = $windowDump -join "`n"
$serviceText = $serviceDump -join "`n"
$classification = Get-ForegroundClassification -ActivityDump $activityText -WindowDump $windowText
$voiceServiceObserved = $serviceText -match [Regex]::Escape($VoiceService)
$gateConsistent = $true
$gateNote = ""

if ($classification -eq "ANDROID_UI_OBSERVED_ACTIVATION_GATE" -and $voiceServiceObserved) {
    $gateConsistent = $false
    $gateNote = "Activation gate đang foreground nhưng VoiceAssistantService vẫn xuất hiện trong active service dump."
} elseif ($classification -eq "ANDROID_UI_OBSERVED_ACTIVATION_GATE") {
    $gateNote = "Activation gate foreground và không thấy VoiceAssistantService active."
} elseif ($classification -eq "ANDROID_UI_OBSERVED_LICENSE_ACTIVE_REDIRECT") {
    $gateNote = "ActivationActivity đã redirect sang MainActivity; chỉ hợp lệ nếu thiết bị đã có license ACTIVE từ trước."
} else {
    $gateNote = "Không xác định được ActivationActivity/MainActivity foreground; cần xem screenshot/dumpsys."
}

$deviceSerial = ((Invoke-AdbBestEffort get-serialno) -join "").Trim()
$androidRelease = ((Invoke-AdbBestEffort shell getprop ro.build.version.release) -join "").Trim()
$androidSdk = ((Invoke-AdbBestEffort shell getprop ro.build.version.sdk) -join "").Trim()
$model = ((Invoke-AdbBestEffort shell getprop ro.product.model) -join "").Trim()
$wmSize = ((Invoke-AdbBestEffort shell wm size) -join " ").Trim()
$wmDensity = ((Invoke-AdbBestEffort shell wm density) -join " ").Trim()

$report = @(
    "THT Voice no-car UI validation",
    "timestamp=$(Get-Date -Format o)",
    "package=$PackageName",
    "launcher=$Launcher",
    "apk_sha256=$apkHash",
    "installed_apk_sha256=$installedHash",
    "device_serial=$deviceSerial",
    "android_release=$androidRelease",
    "android_sdk=$androidSdk",
    "device_model=$model",
    "wm_size=$wmSize",
    "wm_density=$wmDensity",
    "classification=$classification",
    "voice_service_observed=$($voiceServiceObserved.ToString().ToLowerInvariant())",
    "security_gate_consistent=$($gateConsistent.ToString().ToLowerInvariant())",
    "note=$gateNote",
    "",
    "Evidence scope:",
    "- ANDROID_UI_OBSERVED only; không phải HARDWARE_CONFIRMED.",
    "- Không xác minh VHAL, cabin KWS/ASR, ECARX key, VIN thật hoặc quyền OEM.",
    "- Runner không thay đổi license state ngoài hành vi tự nhiên của app khi launch."
)
Write-Lines -Path (Join-Path $OutputDirectory "NO_CAR_UI_REPORT.txt") -Lines $report

Write-Host "[6/6] Hoàn tất evidence bundle."
$report | ForEach-Object { Write-Host $_ }

if (-not $gateConsistent) {
    Write-Warning "Security gate observation cần kiểm tra: $gateNote"
}

if ($ZipBundle) {
    $zipPath = "$OutputDirectory.zip"
    if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
    Compress-Archive -Path (Join-Path $OutputDirectory '*') -DestinationPath $zipPath -Force
    Write-Host "ZIP: $zipPath"
}
