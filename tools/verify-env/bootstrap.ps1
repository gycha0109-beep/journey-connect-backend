param(
    [string]$EnvRoot = "$PSScriptRoot\..\..\.verify-env",
    [switch]$SkipPostgresImport
)

$ErrorActionPreference = "Stop"

$files = @(
    @{
        Name = "gradle-8.14.5-bin.zip"
        Id   = "1z7VkRw22cc9dvzUjLbjKFkwaYDtSRZit"
        Sha256 = "2F0C8A659F5E650B93F94333252CA8420E3D8D43F561422E0A5216D9E61A4A70"
    },
    @{
        Name = "gradle-offline-cache.zip"
        Id   = "1wwX5U4R4Jz_JnYowf34r3g1_A_Zqscna"
        Sha256 = "2C761602FD7897CC4D4ACC7D8B865FEBF0B96F9D2CD6151FECD4F6F27053D77F"
    },
    @{
        Name = "postgres15-bookworm-rootfs.tar"
        Id   = "1-n2QgkD1El3lN_sC0lZK9ESuBI0JsC3m"
        Sha256 = "F8BE0B888052855E587BC3080849D6C3F2DE38905BAF19BD3F446C57655610A6"
    }
)

$downloadDir = Join-Path $EnvRoot "downloads"
$gradleDir = Join-Path $EnvRoot "gradle"
$gradleUserHome = Join-Path $EnvRoot "gradle-user-home"
$postgresTar = Join-Path $downloadDir "postgres15-bookworm-rootfs.tar"
$postgresImage = "jc-postgres15:bookworm-rootfs"

New-Item -ItemType Directory -Force -Path $downloadDir, $gradleDir, $gradleUserHome | Out-Null

function Assert-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "필수 명령을 찾을 수 없습니다: $Name"
    }
}

function Install-Gdown {
    Assert-Command "python"

    python -m gdown --version *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[bootstrap] gdown 설치"
        python -m pip install --user gdown

        if ($LASTEXITCODE -ne 0) {
            throw "gdown 설치 실패"
        }
    }
}

function Test-Hash {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$Expected
    )

    if ([string]::IsNullOrWhiteSpace($Expected)) {
        return $true
    }

    $actual = (Get-FileHash -Path $Path -Algorithm SHA256).Hash.ToUpperInvariant()
    return $actual -eq $Expected.ToUpperInvariant()
}

function Save-DriveFile {
    param(
        [Parameter(Mandatory = $true)][hashtable]$File
    )

    $target = Join-Path $downloadDir $File.Name

    if (Test-Path $target) {
        if (Test-Hash -Path $target -Expected $File.Sha256) {
            Write-Host "[bootstrap] 재사용: $($File.Name)"
            return
        }

        Write-Host "[bootstrap] 기존 파일 해시 불일치, 재다운로드: $($File.Name)"
        Remove-Item -Force $target
    }

    Write-Host "[bootstrap] 다운로드: $($File.Name)"
    python -m gdown --continue "https://drive.google.com/uc?id=$($File.Id)" -O $target

    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $target)) {
        throw "다운로드 실패: $($File.Name)"
    }

    if (-not (Test-Hash -Path $target -Expected $File.Sha256)) {
        Remove-Item -Force $target
        throw "SHA-256 검증 실패: $($File.Name)"
    }
}

Install-Gdown

foreach ($file in $files) {
    Save-DriveFile -File $file
}

$gradleZip = Join-Path $downloadDir "gradle-8.14.5-bin.zip"
$gradleHome = Join-Path $gradleDir "gradle-8.14.5"

if (-not (Test-Path (Join-Path $gradleHome "bin\gradle.bat"))) {
    Write-Host "[bootstrap] Gradle 압축 해제"
    Expand-Archive -Path $gradleZip -DestinationPath $gradleDir -Force
} else {
    Write-Host "[bootstrap] Gradle 재사용"
}

$cacheMarker = Join-Path $gradleUserHome ".jc-cache-ready"
$cacheZip = Join-Path $downloadDir "gradle-offline-cache.zip"

if (-not (Test-Path $cacheMarker)) {
    Write-Host "[bootstrap] Gradle offline cache 압축 해제"
    Expand-Archive -Path $cacheZip -DestinationPath $gradleUserHome -Force
    New-Item -ItemType File -Force -Path $cacheMarker | Out-Null
} else {
    Write-Host "[bootstrap] Gradle offline cache 재사용"
}

$env:GRADLE_HOME = $gradleHome
$env:GRADLE_USER_HOME = $gradleUserHome
$env:Path = "$gradleHome\bin;$env:Path"

if (-not $SkipPostgresImport) {
    $runtime = $null

    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $runtime = "docker"
    } elseif (Get-Command podman -ErrorAction SilentlyContinue) {
        $runtime = "podman"
    }

    if ($runtime) {
        & $runtime image inspect $postgresImage *> $null

        if ($LASTEXITCODE -ne 0) {
            Write-Host "[bootstrap] PostgreSQL rootfs 이미지 import"
            Get-Content -Raw -Encoding Byte $postgresTar | & $runtime import - $postgresImage

            if ($LASTEXITCODE -ne 0) {
                throw "PostgreSQL rootfs 이미지 import 실패"
            }
        } else {
            Write-Host "[bootstrap] PostgreSQL 이미지 재사용"
        }
    } else {
        Write-Warning "docker/podman이 없어 PostgreSQL rootfs import를 건너뜁니다."
    }
}

Write-Host ""
Write-Host "=== Journey Connect 검증환경 준비 완료 ==="
Write-Host "GRADLE_HOME      = $env:GRADLE_HOME"
Write-Host "GRADLE_USER_HOME = $env:GRADLE_USER_HOME"
Write-Host "POSTGRES_ROOTFS  = $postgresTar"
Write-Host ""
Write-Host "확인 명령:"
Write-Host "  gradle --version"
Write-Host "  .\gradlew.bat --offline tasks"