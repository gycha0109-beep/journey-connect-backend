param(
    [Parameter(Mandatory = $true)]
    [string]$TeamRepoPath,
    [string]$BranchName = "feat/synthetic-db-corpus",
    [switch]$Commit
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$File,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$File failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

$SourceRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$TargetRoot = (Resolve-Path $TeamRepoPath).Path
$TargetGit = Join-Path $TargetRoot ".git"

if (-not (Test-Path $TargetGit)) {
    throw "Target is not a Git working tree: $TargetRoot"
}

$origin = (& git -C $TargetRoot remote get-url origin).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Cannot read target origin remote."
}
if ($origin -notmatch 'YTAK99/Journey-Connect(?:\.git)?$') {
    throw "Target origin is not YTAK99/Journey-Connect: $origin"
}

$dirty = (& git -C $TargetRoot status --porcelain)
if ($LASTEXITCODE -ne 0) {
    throw "Cannot inspect target working tree."
}
if ($dirty) {
    throw "Target working tree is not clean. Commit/stash existing work first."
}

Write-Host "[1/6] Fetching team develop..."
Invoke-Native git -C $TargetRoot fetch origin develop

& git -C $TargetRoot show-ref --verify --quiet "refs/heads/$BranchName"
$branchExists = ($LASTEXITCODE -eq 0)
if ($branchExists) {
    Write-Host "[2/6] Switching to existing $BranchName and fast-forwarding from origin/develop..."
    Invoke-Native git -C $TargetRoot switch $BranchName
    Invoke-Native git -C $TargetRoot merge --ff-only origin/develop
} else {
    Write-Host "[2/6] Creating $BranchName from origin/develop..."
    Invoke-Native git -C $TargetRoot switch -c $BranchName origin/develop
}

$TargetToolDir = Join-Path $TargetRoot "tools/synthetic-db-corpus"
$TargetWorkflowDir = Join-Path $TargetRoot ".github/workflows"
New-Item -ItemType Directory -Force -Path $TargetToolDir | Out-Null
New-Item -ItemType Directory -Force -Path $TargetWorkflowDir | Out-Null

Write-Host "[3/6] Copying synthetic DB corpus tooling..."
Get-ChildItem -Path $PSScriptRoot -Force |
    Where-Object { $_.Name -ne "install-into-team.ps1" } |
    ForEach-Object { Copy-Item $_.FullName -Destination $TargetToolDir -Recurse -Force }

$SourceWorkflow = Join-Path $SourceRepoRoot ".github/workflows/synthetic-db-corpus-ci.yml"
if (-not (Test-Path $SourceWorkflow)) {
    throw "Source CI workflow not found: $SourceWorkflow"
}
Copy-Item $SourceWorkflow (Join-Path $TargetWorkflowDir "synthetic-db-corpus-ci.yml") -Force

Write-Host "[4/6] Running compile/unit/smoke verification in team repository..."
Push-Location $TargetRoot
try {
    Invoke-Native python -m py_compile tools/synthetic-db-corpus/legacy_generate.py tools/synthetic-db-corpus/generate.py
    Invoke-Native python -m unittest discover -s tools/synthetic-db-corpus/tests -p test_*.py -v
    Invoke-Native python tools/synthetic-db-corpus/generate.py --users 20 --posts 50 --crews 10 --batch-id transfer-smoke --output-dir build/synthetic-db-transfer-smoke

    foreach ($required in @(
        "build/synthetic-db-transfer-smoke/seed.sql",
        "build/synthetic-db-transfer-smoke/purge.sql",
        "build/synthetic-db-transfer-smoke/manifest.json"
    )) {
        if (-not (Test-Path $required)) {
            throw "Missing smoke output: $required"
        }
    }

    $seedText = Get-Content "build/synthetic-db-transfer-smoke/seed.sql" -Raw
    if ($seedText -notmatch "INSERT INTO post_place") {
        throw "Smoke seed does not contain post_place materialization."
    }

    Write-Host "[5/6] Staging transfer files..."
    Invoke-Native git add tools/synthetic-db-corpus .github/workflows/synthetic-db-corpus-ci.yml

    if ($Commit) {
        & git diff --cached --quiet
        if ($LASTEXITCODE -eq 0) {
            Write-Host "No staged changes to commit."
        } else {
            Invoke-Native git commit -m "tools: add synthetic DB corpus generator"
        }
    }
} finally {
    Pop-Location
}

Write-Host "[6/6] Transfer ready." -ForegroundColor Green
Write-Host "Target:  $TargetRoot"
Write-Host "Branch:  $BranchName"
Write-Host ""
Write-Host "Next commands:"
Write-Host "  git -C `"$TargetRoot`" status"
if (-not $Commit) {
    Write-Host "  git -C `"$TargetRoot`" commit -m `"tools: add synthetic DB corpus generator`""
}
Write-Host "  git -C `"$TargetRoot`" push -u origin $BranchName"
Write-Host ""
Write-Host "After the team PR is merged, anyone can run:"
Write-Host "  python tools/synthetic-db-corpus/generate.py"
