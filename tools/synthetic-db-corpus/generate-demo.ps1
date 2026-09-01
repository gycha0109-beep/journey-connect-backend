param(
    [int]$Users = 180,
    [int]$Posts = 1800,
    [int]$Crews = 120,
    [int]$Seed = 20260825,
    [string]$BatchId = "demo-v2",
    [string]$OutputDir = "build/synthetic-db-corpus"
)

$ErrorActionPreference = "Stop"
$Generator = Join-Path $PSScriptRoot "generate.py"

python $Generator `
    --users $Users `
    --posts $Posts `
    --crews $Crews `
    --seed $Seed `
    --batch-id $BatchId `
    --output-dir $OutputDir

if ($LASTEXITCODE -ne 0) {
    throw "Synthetic DB corpus generation failed with exit code $LASTEXITCODE"
}

Write-Host "Synthetic DB corpus generated:" -ForegroundColor Green
Write-Host "  $OutputDir/seed.sql"
Write-Host "  $OutputDir/purge.sql"
Write-Host "  $OutputDir/manifest.json"
