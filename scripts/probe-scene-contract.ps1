param(
    [string]$AdbPath = "adb",
    [string]$Serial = "",
    [string]$OutputRoot = "artifacts/scene-contract",
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$PackageName = "com.flyme.auto.scenedirector"
$ProviderUri = "content://com.flyme.auto.scenedirector/scene"
$RestActivity = "com.flyme.auto.scenedirector.activities.nap.RestActivity"
$CampingActivity = "com.flyme.auto.scenedirector.activities.camping.CampingActivity"

function Assert-ReadOnlyContract {
    $forbidden = @(
        "content insert",
        "content update",
        "content delete",
        "content call",
        "am start",
        "pm clear",
        "settings put"
    )
    $scriptText = Get-Content -Raw -LiteralPath $PSCommandPath
    foreach ($needle in $forbidden) {
        if ($scriptText -match [regex]::Escape($needle)) {
            $occurrences = ([regex]::Matches($scriptText, [regex]::Escape($needle))).Count
            if ($occurrences -ne 1) {
                throw "Read-only guard failed: forbidden command appears outside self-test list: $needle"
            }
        }
    }
    if ($ProviderUri -ne "content://com.flyme.auto.scenedirector/scene") {
        throw "Unexpected scene provider URI"
    }
    if ($PackageName -ne "com.flyme.auto.scenedirector") {
        throw "Unexpected scene package"
    }
}

if ($SelfTest) {
    Assert-ReadOnlyContract
    Write-Host "Scene contract probe self-test PASS"
    exit 0
}

Assert-ReadOnlyContract

$adb = Get-Command $AdbPath -ErrorAction SilentlyContinue
if (-not $adb) {
    throw "Không tìm thấy adb: $AdbPath"
}

function Get-AdbArguments {
    param([string[]]$Arguments)
    if ($Serial -and $Serial.Trim().Length -gt 0) {
        return @("-s", $Serial) + $Arguments
    }
    return $Arguments
}

$stateArgs = Get-AdbArguments -Arguments @("get-state")
$state = (& $AdbPath @stateArgs 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $state -ne "device") {
    throw "ADB device chưa sẵn sàng. get-state=$state"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = Join-Path $OutputRoot $timestamp
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

function Invoke-AdbCapture {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    $path = Join-Path $outputDir "$Name.txt"
    $adbArgs = Get-AdbArguments -Arguments $Arguments
    $commandLine = "adb " + ($adbArgs -join " ")
    $lines = @()
    $lines += "command=$commandLine"
    $lines += "captured_at=$(Get-Date -Format o)"
    $result = & $AdbPath @adbArgs 2>&1
    $exitCode = $LASTEXITCODE
    $lines += "exit_code=$exitCode"
    $lines += "--- output ---"
    $lines += $result
    $lines | Set-Content -LiteralPath $path -Encoding UTF8
    return [pscustomobject]@{ Name = $Name; ExitCode = $exitCode; Path = $path }
}

$meta = @(
    "probe=IHU689G scene provider read-only contract",
    "captured_at=$(Get-Date -Format o)",
    "adb_serial=$Serial",
    "package=$PackageName",
    "provider_uri=$ProviderUri",
    "rest_activity=$RestActivity",
    "camping_activity=$CampingActivity",
    "mutation_allowed=false",
    "note=Raw scene_mode values are evidence only and MUST NOT be mapped to REST/CAMPING without verified semantics."
)
$meta | Set-Content -LiteralPath (Join-Path $outputDir "README.txt") -Encoding UTF8

$results = @()
$results += Invoke-AdbCapture -Name "device" -Arguments @("shell", "getprop", "ro.product.model")
$results += Invoke-AdbCapture -Name "build_fingerprint" -Arguments @("shell", "getprop", "ro.build.fingerprint")
$results += Invoke-AdbCapture -Name "package_path" -Arguments @("shell", "pm", "path", $PackageName)
$results += Invoke-AdbCapture -Name "package_dump" -Arguments @("shell", "dumpsys", "package", $PackageName)
$results += Invoke-AdbCapture -Name "scene_provider_query" -Arguments @("shell", "content", "query", "--uri", $ProviderUri)

$summaryPath = Join-Path $outputDir "SUMMARY.txt"
$summary = @(
    "Scene contract probe completed.",
    "This probe did not request any scene state mutation.",
    "Inspect scene_provider_query.txt for provider columns/raw values.",
    "Do not infer REST/CAMPING semantics from a raw value without independent validation.",
    ""
)
$summary += $results | ForEach-Object { "$($_.Name): exit=$($_.ExitCode) file=$($_.Path)" }
$summary | Set-Content -LiteralPath $summaryPath -Encoding UTF8

Write-Host "Scene read-only evidence saved to: $outputDir"
Write-Host "Summary: $summaryPath"
