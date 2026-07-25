<#
.SYNOPSIS
    Builds the Reserveo Android app into a release APK.

.DESCRIPTION
    Runs the assembleRelease Gradle task for the composeApp Android target.

.EXAMPLE
    ./scripts/build-android-apk.ps1
#>

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    Write-Host "Building Android release APK for Reserveo..." -ForegroundColor Cyan
    & "$repoRoot\gradlew.bat" ":composeApp:assembleRelease"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $apkDir = Join-Path $repoRoot "composeApp\build\outputs\apk\release"
    $apk = Get-ChildItem -Path $apkDir -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1

    if ($apk) {
        Write-Host "`nAPK built successfully:" -ForegroundColor Green
        Write-Host "  $($apk.FullName)" -ForegroundColor Green
    } else {
        Write-Warning "Build finished but no .apk file was found in $apkDir"
    }
}
finally {
    Pop-Location
}
