<#
.SYNOPSIS
    Builds the Reserveo desktop app into a Windows MSI installer.

.DESCRIPTION
    Runs the Compose Desktop packageMsi Gradle task. Requires the WiX Toolset
    to be installed (jpackage uses it to produce the MSI on Windows).

.EXAMPLE
    ./scripts/build-desktop-installer.ps1
#>

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    Write-Host "Building Windows MSI installer for Reserveo desktop app..." -ForegroundColor Cyan
    & "$repoRoot\gradlew.bat" ":composeApp:packageMsi"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $msiDir = Join-Path $repoRoot "composeApp\build\compose\binaries\main\msi"
    $msi = Get-ChildItem -Path $msiDir -Filter "*.msi" -ErrorAction SilentlyContinue | Select-Object -First 1

    if ($msi) {
        Write-Host "`nInstaller built successfully:" -ForegroundColor Green
        Write-Host "  $($msi.FullName)" -ForegroundColor Green
    } else {
        Write-Warning "Build finished but no .msi file was found in $msiDir"
    }
}
finally {
    Pop-Location
}
