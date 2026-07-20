param(
  [Parameter(Mandatory=$true)][string]$ZipPath,
  [string]$WorkDir = "$env:TEMP\jc-ip1175-verify"
)
$ErrorActionPreference = 'Stop'
Remove-Item -Recurse -Force $WorkDir -ErrorAction SilentlyContinue
$explorerDir = Join-Path $WorkDir 'explorer'
$sevenDir = Join-Path $WorkDir 'sevenzip'
New-Item -ItemType Directory -Force $explorerDir,$sevenDir | Out-Null
$shell = New-Object -ComObject Shell.Application
$zipNs = $shell.NameSpace((Resolve-Path $ZipPath).Path)
$dstNs = $shell.NameSpace($explorerDir)
if ($null -eq $zipNs -or $null -eq $dstNs) { throw 'Windows Shell ZIP namespace unavailable' }
$dstNs.CopyHere($zipNs.Items(), 16)
Start-Sleep -Seconds 3
$seven = Get-Command 7z.exe -ErrorAction Stop
& $seven.Source x -y "-o$sevenDir" $ZipPath | Out-Host
if ($LASTEXITCODE -ne 0) { throw "7-Zip extract failed: $LASTEXITCODE" }
function Manifest([string]$Path) {
  Get-ChildItem -Recurse -File $Path | ForEach-Object {
    $rel = $_.FullName.Substring($Path.Length).TrimStart('\')
    [PSCustomObject]@{ Path=$rel; SHA256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
  } | Sort-Object Path
}
$a=Manifest $explorerDir
$b=Manifest $sevenDir
$diff=Compare-Object ($a | ConvertTo-Csv -NoTypeInformation) ($b | ConvertTo-Csv -NoTypeInformation)
if ($diff) { $diff | Format-Table | Out-String | Write-Error; throw 'Explorer and 7-Zip manifests differ' }
"PASS files=$($a.Count)" | Write-Host
