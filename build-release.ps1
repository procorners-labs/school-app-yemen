param([switch]$SkipClean)

$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$gradlew    = Join-Path $projectDir 'gradlew.bat'
$outputAab  = Join-Path $projectDir 'app\build\outputs\bundle\release\app-release.aab'

Write-Host ''
Write-Host '==========================================' -ForegroundColor Cyan
Write-Host '  SchoolApp Yemen - Build Release AAB' -ForegroundColor Cyan
Write-Host '==========================================' -ForegroundColor Cyan
Write-Host ''

$buildFile   = Join-Path $projectDir 'app\build.gradle.kts'
$versionCode = (Select-String -Path $buildFile -Pattern 'versionCode\s*=\s*(\d+)').Matches[0].Groups[1].Value
$versionName = (Select-String -Path $buildFile -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
Write-Host "Version: versionCode=$versionCode  versionName=$versionName" -ForegroundColor Green
Write-Host ''

if (-not $SkipClean) {
    Write-Host 'Cleaning cache...' -ForegroundColor Yellow
    Remove-Item -Recurse -Force '.gradle\configuration-cache' -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force 'app\build\kotlin'             -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force 'app\build\snapshot'           -ErrorAction SilentlyContinue
    Write-Host '  Done.' -ForegroundColor Gray
    Write-Host ''
}

$ksFile = Join-Path $projectDir 'keystore.properties'
if (-not (Test-Path $ksFile)) {
    Write-Host 'ERROR: keystore.properties not found!' -ForegroundColor Red
    Write-Host 'Create it with:' -ForegroundColor Yellow
    Write-Host '  storeFile=C:/Users/osama/Workspace/Secure/schoolapp.jks' -ForegroundColor Gray
    Write-Host '  storePassword=123456' -ForegroundColor Gray
    Write-Host '  keyAlias=schoolapp' -ForegroundColor Gray
    Write-Host '  keyPassword=123456' -ForegroundColor Gray
    exit 1
}
Write-Host 'keystore.properties found.' -ForegroundColor Green

Write-Host ''
Write-Host 'Building AAB (bundleRelease)...' -ForegroundColor Cyan
$startTime = Get-Date
& $gradlew bundleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host 'BUILD FAILED! Check errors above.' -ForegroundColor Red
    exit 1
}
$duration = [math]::Round(((Get-Date) - $startTime).TotalSeconds)

$today   = (Get-Date).ToString('yyyyMMdd')
$dstName = "SchoolApp-v${versionName}-${today}.aab"
$desktop = [Environment]::GetFolderPath('Desktop')
$dstPath = Join-Path $desktop $dstName
Copy-Item $outputAab $dstPath

$sizeMB = [math]::Round((Get-Item $dstPath).Length / 1MB, 2)

Write-Host ''
Write-Host '==========================================' -ForegroundColor Cyan
Write-Host '  BUILD SUCCESSFUL!' -ForegroundColor Green
Write-Host '==========================================' -ForegroundColor Cyan
Write-Host "  File   : $dstPath" -ForegroundColor White
Write-Host "  Size   : $sizeMB MB" -ForegroundColor White
Write-Host "  Time   : $duration sec" -ForegroundColor White
Write-Host "  Version: versionCode=$versionCode  versionName=$versionName" -ForegroundColor White
Write-Host ''
Write-Host 'Next step: play.google.com/console -> Your app -> Production -> Upload AAB' -ForegroundColor Yellow
Write-Host ''

Invoke-Item $desktop
