$ErrorActionPreference = "Stop"
$bundle = Join-Path ([System.IO.Path]::GetTempPath()) ("thtvoice-wake-analysis-test-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $bundle | Out-Null

try {
    @(
        "09-27 22:00:01 THTVoiceService: Wake word detected profile=HEY_THT keyword=HEY_THT",
        "09-27 22:00:02 THTVoiceService: Wake word detected profile=HELLO_GEELY keyword=HELLO_GEELY",
        "09-27 22:00:03 THTVoiceService: Wake word detected profile=HEY_GEELY keyword=HEY_GEELY"
    ) | Set-Content -Path (Join-Path $bundle "wake-events.txt") -Encoding utf8

    # Mic logs may repeat the wake line; a detection should only be counted once.
    "Wake word detected profile=HEY_THT" |
        Set-Content -Path (Join-Path $bundle "mic-handoff-events.txt") -Encoding utf8
    "session=1 firstPartial=300" |
        Set-Content -Path (Join-Path $bundle "asr-metrics.txt") -Encoding utf8

    & (Join-Path $PSScriptRoot "analyze-ihu689g-validation.ps1") -BundlePath $bundle | Out-Null
    $summary = Get-Content -Path (Join-Path $bundle "ANALYSIS_SUMMARY.txt") -Raw

    foreach ($expected in @(
        '- wake detections\s+: 3',
        '- Hey THT detections: 1',
        '- Hello Geely detections: 1',
        '- Hey Geely detections: 1'
    )) {
        if ($summary -notmatch $expected) {
            throw "Wake analysis mismatch: $expected"
        }
    }
    Write-Host "[THTVoice] Three-profile wake analysis PASS"
} finally {
    Remove-Item -LiteralPath $bundle -Recurse -Force -ErrorAction SilentlyContinue
}
