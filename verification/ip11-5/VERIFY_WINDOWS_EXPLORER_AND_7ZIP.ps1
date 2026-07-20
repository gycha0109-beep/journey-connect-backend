param(
  [Parameter(Mandatory=$true)][string]$ZipPath,
  [string]$WorkRoot = (Join-Path $env:TEMP "JC-IP-11-5-Tech-Verify")
)
$ErrorActionPreference = "Stop"
$ZipPath = (Resolve-Path $ZipPath).Path
$ExplorerOut = Join-Path $WorkRoot "explorer"
$SevenZipOut = Join-Path $WorkRoot "sevenzip"
Remove-Item $WorkRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $ExplorerOut,$SevenZipOut | Out-Null

$shell = New-Object -ComObject Shell.Application
$zipNs = $shell.NameSpace($ZipPath)
$outNs = $shell.NameSpace($ExplorerOut)
if ($null -eq $zipNs -or $null -eq $outNs) { throw "Windows Shell namespace unavailable" }
$outNs.CopyHere($zipNs.Items(), 0x10)
$deadline = (Get-Date).AddMinutes(5)
$lastCount = -1
$stable = 0
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Milliseconds 500
  $count = @(Get-ChildItem $ExplorerOut -Recurse -File -ErrorAction SilentlyContinue).Count
  if ($count -gt 0 -and $count -eq $lastCount) { $stable++ } else { $stable = 0 }
  if ($stable -ge 4) { break }
  $lastCount = $count
}
if (@(Get-ChildItem $ExplorerOut -Recurse -File).Count -eq 0) { throw "Explorer extraction produced no files" }

$sevenZipCandidates = @(
  (Get-Command 7z.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
  "$env:ProgramFiles\7-Zip\7z.exe",
  "${env:ProgramFiles(x86)}\7-Zip\7z.exe"
) | Where-Object { $_ -and (Test-Path $_) }
if (-not $sevenZipCandidates) { throw "7-Zip not found" }
$sevenZip = $sevenZipCandidates[0]
& $sevenZip x -y "-o$SevenZipOut" $ZipPath | Out-Host
if ($LASTEXITCODE -ne 0) { throw "7-Zip extraction failed: $LASTEXITCODE" }

function Get-TreeManifest([string]$Base) {
  $baseResolved = (Resolve-Path $Base).Path
  Get-ChildItem $Base -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($baseResolved.Length).TrimStart('\').Replace('\','/')
    [PSCustomObject]@{ Path=$rel; SHA256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
  } | Sort-Object Path
}
$explorerManifest = @(Get-TreeManifest $ExplorerOut)
$sevenManifest = @(Get-TreeManifest $SevenZipOut)
$explorerManifest | Export-Csv (Join-Path $WorkRoot "explorer-manifest.csv") -NoTypeInformation -Encoding UTF8
$sevenManifest | Export-Csv (Join-Path $WorkRoot "7zip-manifest.csv") -NoTypeInformation -Encoding UTF8
$diff = Compare-Object $explorerManifest $sevenManifest -Property Path,SHA256
if ($diff) {
  $diff | Format-Table | Out-String | Set-Content (Join-Path $WorkRoot "manifest-diff.txt")
  throw "Explorer and 7-Zip manifests differ"
}
$rootNames = @(Get-ChildItem $ExplorerOut -Directory | Select-Object -ExpandProperty Name)
if ($rootNames.Count -ne 1 -or $rootNames[0] -ne 'JC-IP-11-5-Tech-Final') { throw "Unexpected top-level folder: $($rootNames -join ',')" }
$maxPath = ($explorerManifest.Path | Measure-Object -Maximum Length).Maximum
if ($maxPath -gt 240) { throw "Internal path exceeds 240 characters: $maxPath" }
Write-Host "PASS: Windows Shell/Explorer and 7-Zip extraction manifests match. Files=$($explorerManifest.Count), MaxPath=$maxPath"
