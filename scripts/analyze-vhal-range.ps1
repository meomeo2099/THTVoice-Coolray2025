param([Parameter(Mandatory=$true)][string]$BundlePath)
$ErrorActionPreference="Stop"
function Resolve-BundleDirectory([string]$Path) {
  if (-not (Test-Path $Path)) { throw "Không tìm thấy bundle: $Path" }
  $item=Get-Item $Path
  if ($item.PSIsContainer) { return $item.FullName }
  if ($item.Extension -ieq ".zip") { $tmp=Join-Path ([IO.Path]::GetTempPath()) ("thtvoice-vhal-"+[Guid]::NewGuid().ToString("N")); New-Item -ItemType Directory -Force $tmp|Out-Null; Expand-Archive $item.FullName $tmp -Force; return $tmp }
  throw "BundlePath phải là thư mục hoặc ZIP"
}
function Read-Safe([string]$Path) { if(Test-Path $Path){Get-Content $Path -Raw -ErrorAction SilentlyContinue}else{""} }
function Count-Matches([string]$Text,[string]$Pattern){ if([string]::IsNullOrWhiteSpace($Text)){0}else{([regex]::Matches($Text,$Pattern,[Text.RegularExpressions.RegexOptions]::IgnoreCase)).Count} }
$dir=Resolve-BundleDirectory $BundlePath
$vhal=Read-Safe (Join-Path $dir "vhal-io-events.txt")
if([string]::IsNullOrWhiteSpace($vhal)){$vhal=Read-Safe (Join-Path $dir "thtvoice-logcat.txt")}
$ok=Count-Matches $vhal "op=GET_RANGE .*result=OK .*source=VHAL_CONFIG"
$no=Count-Matches $vhal "op=GET_RANGE .*result=NO_CONFIG"
$invalid=Count-Matches $vhal "op=GET_RANGE .*result=INVALID_RANGE"
$failed=Count-Matches $vhal "op=GET_RANGE .*result=FAILED"
$denied=Count-Matches $vhal "status=SECURITY_DENIED"
$backend=Count-Matches $vhal "status=BACKEND_ERROR"
$report=Join-Path $dir "VHAL_RANGE_ANALYSIS.txt"
@"
THT Voice — VHAL range/backend analysis
Bundle: $dir
VHAL_CONFIG OK : $ok
NO_CONFIG      : $no
INVALID_RANGE  : $invalid
RANGE FAILED   : $failed
SECURITY_DENIED: $denied
BACKEND_ERROR  : $backend

Range chỉ mô tả miền firmware công bố. Không tự xác nhận chiều OPEN/CLOSE hoặc actuator.
Hardware PASS chỉ khi hành vi xe thật và read-back cùng khớp.
"@ | Out-File -Encoding utf8 $report
Get-Content $report
