param(
    [int]$Users = 180,
    [int]$Posts = 1800,
    [int]$Crews = 120,
    [int]$Seed = 20260825,
    [string]$BatchId = "local-v1",
    [string]$Anchor = "2026-09-01T10:00:00",
    [string]$OutputDir = "build/synthetic-db-local"
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Generator = Join-Path $PSScriptRoot "generate.py"

Push-Location $RepoRoot
try {
    & python $Generator `
        --schema-profile local-pre-v19 `
        --users $Users `
        --posts $Posts `
        --crews $Crews `
        --seed $Seed `
        --batch-id $BatchId `
        --anchor $Anchor `
        --output-dir $OutputDir

    if ($LASTEXITCODE -ne 0) {
        throw "Synthetic local corpus generation failed with exit code $LASTEXITCODE"
    }

    Write-Host "Generated local pre-V19 corpus: $OutputDir"
    Write-Host "Load $OutputDir/seed.sql into the LOCAL development PostgreSQL only."
}
finally {
    Pop-Location
}
