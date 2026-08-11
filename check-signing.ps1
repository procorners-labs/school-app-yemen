param([string]$AabPath = '')

$jbr       = 'C:\Program Files\Android\Android Studio1\jbr\bin'
$keytool   = Join-Path $jbr 'keytool.exe'
$jarsigner = Join-Path $jbr 'jarsigner.exe'

Write-Host ''
Write-Host '=== Keystore Info ===' -ForegroundColor Cyan
$ksFile = 'C:\Users\osama\Workspace\Secure\schoolapp.jks'
if (Test-Path $ksFile) {
    & $keytool -list -keystore $ksFile -storepass 123456 -v 2>&1 |
        Select-String 'Alias name|Owner|Serial|Valid from|SHA256'
} else {
    Write-Host "NOT FOUND: $ksFile" -ForegroundColor Red
}

if (-not $AabPath) {
    $files = Get-ChildItem "$([Environment]::GetFolderPath('Desktop'))\SchoolApp-v*.aab" -ErrorAction SilentlyContinue |
             Sort-Object LastWriteTime -Descending
    if ($files) { $AabPath = $files[0].FullName }
}

if ($AabPath -and (Test-Path $AabPath)) {
    Write-Host ''
    Write-Host "=== Signing Check: $(Split-Path $AabPath -Leaf) ===" -ForegroundColor Cyan
    & $jarsigner -verify $AabPath -verbose 2>&1 | Select-String 'jar verified|FAILED|CN='
} else {
    Write-Host 'No AAB found. Pass path as argument: .\check-signing.ps1 path\to\file.aab' -ForegroundColor Yellow
}

Write-Host ''
