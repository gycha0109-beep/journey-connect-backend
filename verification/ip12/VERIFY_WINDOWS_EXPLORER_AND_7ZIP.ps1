param(
  [Parameter(Mandatory=$true)][string]$ZipPath,
  [string]$WorkRoot = "$env:TEMP\JC-IP12-Zip-Verify"
)
$ErrorActionPreference = 'Stop'
Remove-Item -Recurse -Force $WorkRoot -ErrorAction SilentlyContinue
$explorer = Join-Path $WorkRoot 'windows-shell'
$seven = Join-Path $WorkRoot 'sevenzip'
New-Item -ItemType Directory -Force $explorer,$seven | Out-Null
Expand-Archive -LiteralPath $ZipPath -DestinationPath $explorer -Force
$sevenExe = (Get-Command 7z.exe -ErrorAction Stop).Source
& $sevenExe x '-y' "-o$seven" $ZipPath | Out-Host
function Manifest([string]$Root) {
  Get-ChildItem -LiteralPath $Root -Recurse -File | ForEach-Object {
    [PSCustomObject]@{ Path=$_.FullName.Substring($Root.Length+1).Replace('\','/'); SHA256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
  } | Sort-Object Path
}
$a=Manifest $explorer; $b=Manifest $seven
$diff=Compare-Object ($a|ConvertTo-Json -Compress) ($b|ConvertTo-Json -Compress)
if($diff){ throw 'Windows Shell and 7-Zip manifests differ' }
"WINDOWS_EXPLORER_AND_7ZIP=PASS files=$($a.Count)"
