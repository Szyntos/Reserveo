# Builds the Windows MSI and Android release APK, then copies both to the repo root.
$ErrorActionPreference = "Stop"
$repoRoot = $PSScriptRoot

Set-Location $repoRoot

Write-Host "Building MSI..." -ForegroundColor Cyan
& "$repoRoot\gradlew.bat" :composeApp:packageMsi
if ($LASTEXITCODE -ne 0) { throw "MSI build failed" }

Write-Host "Building release APK..." -ForegroundColor Cyan
& "$repoRoot\gradlew.bat" :composeApp:assembleRelease
if ($LASTEXITCODE -ne 0) { throw "APK build failed" }

$msi = Get-ChildItem "$repoRoot\composeApp\build\compose\binaries\main\msi" -Filter *.msi | Select-Object -First 1
$apk = Get-ChildItem "$repoRoot\composeApp\build\outputs\apk\release" -Filter *.apk | Select-Object -First 1

if (-not $msi) { throw "No MSI found in composeApp\build\compose\binaries\main\msi" }
if (-not $apk) { throw "No APK found in composeApp\build\outputs\apk\release" }

Copy-Item $msi.FullName -Destination "$repoRoot\Reserveo.msi" -Force
Copy-Item $apk.FullName -Destination "$repoRoot\Reserveo.apk" -Force

Write-Host "Copied to:" -ForegroundColor Green
Write-Host "  $repoRoot\Reserveo.msi"
Write-Host "  $repoRoot\Reserveo.apk"
