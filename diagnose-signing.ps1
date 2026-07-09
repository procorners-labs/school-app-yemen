<#
.SYNOPSIS
    SchoolApp Yemen - Complete Signing Diagnostic
    Discovers everything about the app signing setup.
#>

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$KEYTOOL   = "C:\Program Files\Android\Android Studio1\jbr\bin\keytool.exe"
$JARSIGNER = "C:\Program Files\Android\Android Studio1\jbr\bin\jarsigner.exe"
$PROJ      = "C:\Users\osama\AndroidStudioProjects\SchoolAppyemen"
$PKG       = "com.proconrers.schoolappyemen"

function Title($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function OK($m)    { Write-Host "  [FOUND]    $m" -ForegroundColor Green }
function WARN($m)  { Write-Host "  [WARNING]  $m" -ForegroundColor Yellow }
function INFO($m)  { Write-Host "  [INFO]     $m" -ForegroundColor Gray }
function MATCH($m) { Write-Host "  [MATCH!]   $m" -ForegroundColor Green -BackgroundColor DarkGreen }
function DIFF($m)  { Write-Host "  [DIFFER]   $m" -ForegroundColor Red }

Write-Host ""
Write-Host "=========================================="
Write-Host "  Signing Diagnostic - SchoolApp Yemen"
Write-Host "  Package: $PKG"
Write-Host "  Date: $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
Write-Host "=========================================="

# ════════════════════════════════════════════════════════════════════════════
Title "1. Android Studio Last-Used Keystore (from workspace.xml)"
# ════════════════════════════════════════════════════════════════════════════
$wsFile = "$PROJ\.idea\workspace.xml"
if (Test-Path $wsFile) {
    $ws = Get-Content $wsFile -Raw
    if ($ws -match 'KEY_STORE_PATH.*?value="([^"]+)"') {
        $asPath = $Matches[1]
        OK "Android Studio remembers: $asPath"
        if (Test-Path $asPath) {
            OK "File EXISTS on disk"
        } else {
            WARN "File NOT found at that path!"
        }
    }
    if ($ws -match 'KEY_ALIAS.*?value="([^"]+)"') {
        INFO "Last alias used: $($Matches[1])"
    }
}

# ════════════════════════════════════════════════════════════════════════════
Title "2. All Keystore Files Found on This Computer"
# ════════════════════════════════════════════════════════════════════════════
$searchDirs = @(
    "C:\Users\osama",
    "C:\Users\osama\.android",
    "C:\Users\osama\Desktop",
    "C:\Users\osama\Documents",
    "C:\Users\osama\Downloads",
    "C:\Users\osama\AndroidStudioProjects"
)

$allKeystores = @()
foreach ($dir in $searchDirs) {
    if (-not (Test-Path $dir)) { continue }
    $files = Get-ChildItem $dir -Recurse -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -match "\.(jks|keystore|p12)$" -and $_.Length -gt 100 }
    foreach ($f in $files) {
        $allKeystores += $f
    }
}

Write-Host ""
if ($allKeystores.Count -eq 0) {
    WARN "No keystore files found!"
} else {
    Write-Host "  Found $($allKeystores.Count) keystore file(s):" -ForegroundColor White
    $ksInfo = @()
    foreach ($ks in ($allKeystores | Sort-Object LastWriteTime)) {
        Write-Host ""
        Write-Host "  File    : $($ks.FullName)" -ForegroundColor White
        Write-Host "  Size    : $($ks.Length) bytes"
        Write-Host "  Created : $($ks.CreationTime.ToString('yyyy-MM-dd HH:mm'))"
        Write-Host "  Modified: $($ks.LastWriteTime.ToString('yyyy-MM-dd HH:mm'))"

        # Try passwords
        $passwords = @("123456","android","password","","1234","schoolapp")
        foreach ($pw in $passwords) {
            $r = & $KEYTOOL -list -v -keystore $ks.FullName -storepass $pw 2>&1 | Out-String
            if ($r -match "PrivateKeyEntry|Your keystore contains") {
                Write-Host "  Password: $pw" -ForegroundColor Green
                # Extract alias
                if ($r -match "Alias name: +(.+)") { Write-Host "  Alias   : $($Matches[1].Trim())" }
                # Extract SHA256
                $fps = [regex]::Matches($r, "SHA256: +([A-F0-9:]+)")
                foreach ($fpMatch in $fps) {
                    $fp = $fpMatch.Groups[1].Value.Trim()
                    Write-Host "  SHA-256 : $fp" -ForegroundColor Yellow
                }
                # Extract owner
                if ($r -match "Owner: (.+)") { Write-Host "  Owner   : $($Matches[1].Trim())" }
                # Extract validity
                if ($r -match "Valid from: (.+?) until") { Write-Host "  Valid   : from $($Matches[1].Trim())" }
                $ksInfo += @{ path=$ks.FullName; pass=$pw; fingerprints=$fps }
                break
            }
        }
    }
}

# ════════════════════════════════════════════════════════════════════════════
Title "3. Current keystore.properties Configuration"
# ════════════════════════════════════════════════════════════════════════════
$kpFile = "$PROJ\keystore.properties"
if (Test-Path $kpFile) {
    $kp = Get-Content $kpFile
    OK "keystore.properties exists"
    $kp | ForEach-Object {
        if ($_ -match "Password") {
            $masked = $_ -replace "=.+","=***"
            INFO $masked
        } else {
            INFO $_
        }
    }

    # Extract path and verify
    if ($kp -match "storeFile=(.+)") {
        $cfgPath = $Matches[1].Trim().Replace("/","\")
        if (Test-Path $cfgPath) {
            OK "Configured keystore file EXISTS"
        } else {
            WARN "Configured keystore file NOT FOUND: $cfgPath"
        }
    }
} else {
    WARN "keystore.properties NOT FOUND - build will be unsigned!"
}

# ════════════════════════════════════════════════════════════════════════════
Title "4. Current AAB Signature"
# ════════════════════════════════════════════════════════════════════════════
$aabPath = "$PROJ\app\build\outputs\bundle\release\app-release.aab"
if (Test-Path $aabPath) {
    $aab = Get-Item $aabPath
    OK "app-release.aab found ($([math]::Round($aab.Length/1MB,1)) MB)"
    INFO "Built: $($aab.LastWriteTime.ToString('yyyy-MM-dd HH:mm'))"

    $verify = & $JARSIGNER -verify -verbose $aabPath 2>&1 | Out-String
    if ($verify -match "jar verified") {
        OK "AAB is SIGNED and verified"
        # Extract signer info
        if ($verify -match "Signed by .?(.+?)(?:`n|$)") {
            INFO "Signed by: $($Matches[1].Trim())"
        }
    } elseif ($verify -match "unsigned") {
        WARN "AAB is UNSIGNED - cannot upload to Play Store!"
    }
} else {
    WARN "No AAB found. Run: .\build-and-test.ps1"
}

# ════════════════════════════════════════════════════════════════════════════
Title "5. Play Console - What You Need to Check"
# ════════════════════════════════════════════════════════════════════════════
Write-Host ""
Write-Host "  The page you showed has these 2 fingerprints:" -ForegroundColor White
Write-Host "  [1] 50:87:3D:5B:C1:2B:3A:00..." -ForegroundColor Yellow
Write-Host "  [2] CF:63:D5:66:10:1F:6C:1D..." -ForegroundColor Yellow
Write-Host ""
Write-Host "  To find what those fingerprints mean, go to:" -ForegroundColor White
Write-Host "  Play Console -> Your App -> Setup -> App integrity" -ForegroundColor Cyan
Write-Host "  (NOT the home/account page)"
Write-Host ""
Write-Host "  The 'App integrity' page shows:" -ForegroundColor White
Write-Host "  - App signing key certificate (managed by Google)" -ForegroundColor Gray
Write-Host "  - Upload key certificate (YOUR key to sign AABs)" -ForegroundColor Gray

# ════════════════════════════════════════════════════════════════════════════
Title "6. Recommendation"
# ════════════════════════════════════════════════════════════════════════════
Write-Host ""

# Check if schoolapp.jks exists (the original)
$origKs = "C:\Users\osama\schoolapp.jks"
if (Test-Path $origKs) {
    $origR = & $KEYTOOL -list -v -keystore $origKs -storepass "123456" 2>&1 | Out-String
    if ($origR -match "SHA256: +([A-F0-9:]+)") {
        $origFp = $Matches[1].Trim()
        Write-Host "  ORIGINAL keystore (schoolapp.jks) fingerprint:" -ForegroundColor White
        Write-Host "  $origFp" -ForegroundColor Yellow

        $cfPrint = "CF:63:D5:66:10:1F:6C:1D:4D:3D:90:29:BD:8D:A6:89:A8:80:1A:BC:6A:2D:1F:F6:EE:62:87:F3:49:E0:FE:C9"
        $apPrint = "50:87:3D:5B:C1:2B:3A:00:C9:79:2D:E3:23:94:EB:DA:29:F0:BD:B4:09:CB:89:FE:33:66:4A:23:8F:74:91:A1"

        if ($origFp -eq $cfPrint) {
            MATCH "schoolapp.jks = Play Console upload key! USE THIS!"
        } elseif ($origFp -eq $apPrint) {
            MATCH "schoolapp.jks = Play Console app signing key!"
        } else {
            Write-Host ""
            Write-Host "  ACTION NEEDED:" -ForegroundColor Cyan
            Write-Host "  1. Go to Play Console -> App -> Setup -> App integrity" -ForegroundColor White
            Write-Host "  2. Look for 'Upload key certificate' section" -ForegroundColor White
            Write-Host "  3. Compare SHA-256 with: $origFp" -ForegroundColor Yellow
            Write-Host "  4. If they match -> use schoolapp.jks (already configured!)" -ForegroundColor White
            Write-Host "  5. Try uploading the AAB from Desktop and see if Play accepts it" -ForegroundColor White
        }
    }
}

Write-Host ""
Write-Host "  READY TO UPLOAD:" -ForegroundColor Green
$desktopAab = Get-ChildItem "$env:USERPROFILE\Desktop" -Filter "*ORIGINAL-KEY*.aab" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($desktopAab) {
    Write-Host "  $($desktopAab.FullName)" -ForegroundColor Green
    Write-Host "  -> Upload this to Play Console -> Production -> Create release" -ForegroundColor White
} else {
    Write-Host "  Run .\build-and-test.ps1 first to generate the AAB" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Diagnostic complete."
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
