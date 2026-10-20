param(
    [string]$HardwarePackagePath = "",
    [string]$ExpectedGitSha = "",
    [string]$ExpectedSourceMainSha = "",
    [string]$Serial = "",
    [int]$DurationSeconds = 180,
    [switch]$SkipInstall,
    [switch]$ZipBundle,
    [switch]$PreflightOnly
)

$ErrorActionPreference = "Stop"
$PackageName = "com.tht.voice.coolray2025"
$Launcher = "com.tht.voice.coolray2025/com.voicegeely.app.ActivationActivity"
$script:TempPackageRoot = $null

function Get-PowerShellExecutable {
    if (Get-Command pwsh -ErrorAction SilentlyContinue) { return "pwsh" }
    if (Get-Command powershell -ErrorAction SilentlyContinue) { return "powershell" }
    throw "Không tìm thấy pwsh/powershell để chạy script Phase 10."
}

function Get-AdbPrefix {
    if ($Serial -and $Serial.Trim().Length -gt 0) { return @("-s", $Serial) }
    return @()
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$CommandArgs)
    $prefix = Get-AdbPrefix
    $output = & adb @prefix @CommandArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb $($CommandArgs -join ' ') failed ($LASTEXITCODE): $($output -join ' ')" }
    return $output
}

function Resolve-HardwarePackageDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { throw "Không tìm thấy hardware package: $Path" }

    $required = @(
        "THTVoice-IHU689G-hardware-test.apk","SHA256.txt","BUILD_INFO.txt","ASSET_SHA256.txt",
        "IHU689G_TEST_MATRIX.csv","probe-scene-contract.ps1"
    )

    $item = Get-Item -LiteralPath $Path
    if ($item.PSIsContainer) {
        foreach ($name in $required) {
            if (-not (Test-Path -LiteralPath (Join-Path $item.FullName $name))) { throw "Hardware package thiếu file bắt buộc: $name" }
        }
        return $item.FullName
    }
    if ($item.Extension -ine ".zip") { throw "HardwarePackagePath phải là thư mục artifact đã giải nén hoặc file .zip" }

    $script:TempPackageRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-runner-package-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $script:TempPackageRoot | Out-Null
    Expand-Archive -LiteralPath $item.FullName -DestinationPath $script:TempPackageRoot -Force
    $candidates = @((Get-Item $script:TempPackageRoot)) + @(Get-ChildItem -Path $script:TempPackageRoot -Directory -Recurse)
    foreach ($candidate in $candidates) {
        $missing = @($required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $candidate.FullName $_)) })
        if ($missing.Count -eq 0) { return $candidate.FullName }
    }
    throw "ZIP hardware package không chứa đủ file bắt buộc gồm matrix tổng và Scene evidence."
}

function Confirm-InstalledApkMatches {
    param([string[]]$PackagePaths,[string]$ExpectedApkPath)
    $basePaths = @($PackagePaths | Where-Object { $_ -match '^package:.*/base\.apk$' })
    if ($basePaths.Count -ne 1) { throw "Không xác định được duy nhất base.apk của $PackageName. Dùng đường cài APK đã verify thay cho -SkipInstall." }
    $remoteApkPath = $basePaths[0].Substring("package:".Length).Trim()
    $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-installed-apk-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
    $pulledApkPath = Join-Path $tempDir "base.apk"
    try {
        $prefix = Get-AdbPrefix
        & adb @prefix pull $remoteApkPath $pulledApkPath | Out-Null
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $pulledApkPath -PathType Leaf)) { throw "Không đọc được APK đã cài từ thiết bị." }
        $expectedHash = (Get-FileHash -LiteralPath $ExpectedApkPath -Algorithm SHA256).Hash
        $installedHash = (Get-FileHash -LiteralPath $pulledApkPath -Algorithm SHA256).Hash
        if ($installedHash -ne $expectedHash) { throw "APK đã cài có SHA-256 khác hardware package. Không thu bundle với provenance sai; hãy cài lại APK đã verify." }
        Write-Host "[THTVoice] Installed APK SHA-256 khớp hardware package: $installedHash"
    } finally { Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue }
}

$hardwarePackageDir = $null
$apkPath = $null
$psExe = Get-PowerShellExecutable

try {
    if (-not $HardwarePackagePath) { throw "Thiếu -HardwarePackagePath. Runner Phase 10 luôn cần artifact đã verify, kể cả khi -SkipInstall." }
    $hardwarePackageDir = Resolve-HardwarePackageDirectory -Path $HardwarePackagePath
    $verifier = Join-Path $PSScriptRoot "verify-hardware-package.ps1"
    if (-not (Test-Path -LiteralPath $verifier)) { throw "Không tìm thấy verifier: $verifier" }

    Write-Host "[0/5] Kiểm tra hardware package provenance..."
    $verifyArgs = @("-ExecutionPolicy","Bypass","-File",$verifier,"-PackagePath",$hardwarePackageDir)
    if ($ExpectedGitSha) { $verifyArgs += @("-ExpectedGitSha",$ExpectedGitSha) }
    if ($ExpectedSourceMainSha) { $verifyArgs += @("-ExpectedSourceMainSha",$ExpectedSourceMainSha) }
    & $psExe @verifyArgs
    if ($LASTEXITCODE -ne 0) { throw "Hardware package preflight thất bại. Không cài APK lên xe." }
    $apkPath = Join-Path $hardwarePackageDir "THTVoice-IHU689G-hardware-test.apk"

    if ($PreflightOnly) {
        Write-Host "[THTVoice] Runner preflight-only PASS"
        Write-Host "  hardware_package=$hardwarePackageDir"
        Write-Host "  apk=$apkPath"
        Write-Host "  main_matrix=$(Join-Path $hardwarePackageDir 'IHU689G_TEST_MATRIX.csv')"
        Write-Host "  scene_probe=$(Join-Path $hardwarePackageDir 'probe-scene-contract.ps1')"
        return
    }

    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "Không tìm thấy adb trong PATH. Hãy cài Android platform-tools trước." }
    if (-not $Serial) {
        $devices = @(& adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" })
        if ($devices.Count -eq 0) { throw "Không tìm thấy thiết bị ADB." }
        if ($devices.Count -gt 1) { throw "Có nhiều thiết bị ADB. Hãy dùng -Serial <ADB_SERIAL>." }
    }

    Write-Host "=== THT Voice Phase 10 / IHU689G ==="
    if (-not $SkipInstall) {
        Write-Host "[1/5] Cài APK đã verify..."
        $prefix = Get-AdbPrefix
        & adb @prefix install -r $apkPath
        if ($LASTEXITCODE -ne 0) { throw "adb install -r thất bại." }
    } else { Write-Host "[1/5] Bỏ qua cài APK." }

    Write-Host "[2/5] Xác nhận package đã cài..."
    $packagePaths = @(Invoke-Adb shell pm path $PackageName)
    if (@($packagePaths | Where-Object { $_ -match '^package:' }).Count -eq 0) { throw "Không tìm thấy package $PackageName trên thiết bị sau bước cài đặt." }
    if ($SkipInstall) { Confirm-InstalledApkMatches -PackagePaths $packagePaths -ExpectedApkPath $apkPath }

    Write-Host "[3/5] Cold-start qua Activation/Security gate..."
    Invoke-Adb shell am force-stop $PackageName | Out-Null
    Invoke-Adb shell am start -n $Launcher | Write-Host
    Write-Host "  - Dùng IHU689G_TEST_MATRIX.csv nằm trong chính package để ghi kết quả."
    Write-Host "  - Scene probe chỉ QUERY provider, không launch Rest/Camping và không mutation."
    Read-Host "Nhấn Enter khi bạn đã sẵn sàng bắt đầu phiên test"

    Write-Host "[4/5] Chạy collector $DurationSeconds giây..."
    $collector = Join-Path $PSScriptRoot "collect-ihu689g-validation.ps1"
    if (-not (Test-Path $collector)) { throw "Không tìm thấy collector: $collector" }
    $collectorArgs = @("-ExecutionPolicy","Bypass","-File",$collector)
    if ($Serial) { $collectorArgs += @("-Serial",$Serial) }
    $collectorArgs += @("-DurationSeconds","$DurationSeconds","-ProvenanceDirectory",$hardwarePackageDir,
        "-SceneProbePath",(Join-Path $hardwarePackageDir "probe-scene-contract.ps1"))
    if ($ZipBundle) { $collectorArgs += "-ZipBundle" }
    & $psExe @collectorArgs
    if ($LASTEXITCODE -ne 0) { throw "Collector kết thúc với mã lỗi $LASTEXITCODE" }

    Write-Host "[5/5] Hoàn tất phiên test."
    Write-Host "Bundle đã kèm matrix tổng + provenance + packaged Scene evidence của đúng artifact vừa test."
} finally {
    if ($script:TempPackageRoot -and (Test-Path -LiteralPath $script:TempPackageRoot)) {
        Remove-Item -LiteralPath $script:TempPackageRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
