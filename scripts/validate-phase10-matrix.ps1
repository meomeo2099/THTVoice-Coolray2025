param(
    [string]$MatrixPath = ".\docs\phase10\IHU689G_TEST_MATRIX.csv"
)

$ErrorActionPreference = "Stop"

$requiredColumns = @("id", "group", "test_case", "expected", "result", "notes")
$allowedResults = @("PASS", "FAIL", "BLOCKED", "NOT_TESTED")

function Test-MatrixFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Label
    )

    if (-not (Test-Path $Path)) {
        throw "Không tìm thấy $Label test matrix: $Path"
    }

    $rows = @(Import-Csv -Path $Path)
    if ($rows.Count -eq 0) {
        throw "$Label test matrix đang trống."
    }

    $columns = @($rows[0].PSObject.Properties.Name)
    $missingColumns = @($requiredColumns | Where-Object { $_ -notin $columns })
    if ($missingColumns.Count -gt 0) {
        throw "$Label test matrix thiếu cột bắt buộc: $($missingColumns -join ', ')"
    }

    $errors = New-Object System.Collections.Generic.List[string]
    $seenIds = @{}

    foreach ($row in $rows) {
        $id = [string]$row.id
        if ([string]::IsNullOrWhiteSpace($id)) {
            $errors.Add("Có test case thiếu id.")
            continue
        }

        if ($seenIds.ContainsKey($id)) {
            $errors.Add("Trùng test id: $id")
        } else {
            $seenIds[$id] = $true
        }

        if ([string]::IsNullOrWhiteSpace([string]$row.group)) {
            $errors.Add("$id thiếu group.")
        }
        if ([string]::IsNullOrWhiteSpace([string]$row.test_case)) {
            $errors.Add("$id thiếu test_case.")
        }
        if ([string]::IsNullOrWhiteSpace([string]$row.expected)) {
            $errors.Add("$id thiếu expected.")
        }

        $result = ([string]$row.result).Trim().ToUpperInvariant()
        if ($result -notin $allowedResults) {
            $errors.Add("$id có result không hợp lệ '$($row.result)'. Chỉ dùng: $($allowedResults -join ', ')")
        }
    }

    if ($errors.Count -gt 0) {
        $errors | ForEach-Object { Write-Host "[MATRIX ERROR][$Label] $_" }
        throw "$Label test matrix validation failed với $($errors.Count) lỗi."
    }

    Write-Host "[THTVoice] $Label test matrix hợp lệ: $($rows.Count) test cases, ID unique, result schema đúng."
    return @($rows)
}

$mainRows = @(Test-MatrixFile -Path $MatrixPath -Label "Phase 10")

$allIds = @{}
foreach ($row in @($mainRows)) {
    $id = [string]$row.id
    if ($allIds.ContainsKey($id)) {
        throw "Test id bị trùng giữa các matrix Phase 10: $id"
    }
    $allIds[$id] = $true
}

$sceneProbePath = Join-Path $PSScriptRoot "probe-scene-contract.ps1"
if (-not (Test-Path -LiteralPath $sceneProbePath)) {
    throw "Thiếu Scene evidence probe: $sceneProbePath"
}

& $sceneProbePath -SelfTest
if ($LASTEXITCODE -ne 0) {
    throw "Scene evidence probe self-test failed"
}

Write-Host "[THTVoice] Phase 10 matrix + scene probe validation PASS."
