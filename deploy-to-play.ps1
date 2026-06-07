<#
.SYNOPSIS
    SchoolApp Yemen - Google Play Auto-Deploy
    Discovers app state, uploads AAB, assigns track.
.USAGE
    .\deploy-to-play.ps1
    .\deploy-to-play.ps1 -Track internal
    .\deploy-to-play.ps1 -Track production
#>

param(
    [ValidateSet("internal","alpha","beta","production")]
    [string]$Track = "internal",    # Start safe with internal
    [switch]$DryRun                 # Preview without uploading
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding          = [System.Text.Encoding]::UTF8
$ErrorActionPreference   = "Stop"

# ── Config ───────────────────────────────────────────────────────────────────
$PACKAGE_NAME  = "com.proconrers.schoolappyemen"
$PROJ          = "C:\Users\osama\AndroidStudioProjects\SchoolAppyemen"
$KEY_FILE      = "$PROJ\service-account-key.json"
$AAB_PATH      = "$PROJ\app\build\outputs\bundle\release\app-release.aab"
$PLAY_SCOPE    = "https://www.googleapis.com/auth/androidpublisher"
$TOKEN_URL     = "https://oauth2.googleapis.com/token"
$API_BASE      = "https://androidpublisher.googleapis.com/androidpublisher/v3"
$UPLOAD_BASE   = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"

Set-Location $PROJ

function Banner($m) { Write-Host $m -ForegroundColor Cyan }
function OK($m)     { Write-Host "  [OK]  $m" -ForegroundColor Green }
function ERR($m)    { Write-Host "  [!!]  $m" -ForegroundColor Red }
function WARN($m)   { Write-Host "  [>>]  $m" -ForegroundColor Yellow }
function INFO($m)   { Write-Host "  [--]  $m" -ForegroundColor Gray }
function STEP($m)   { Write-Host "`n>>> $m" -ForegroundColor White }

Banner ""
Banner "======================================================"
Banner "   SchoolApp Yemen - Google Play Auto-Deploy"
Banner "======================================================"
Banner "   Package : $PACKAGE_NAME"
Banner "   Track   : $Track$(if($DryRun){' [DRY RUN]'})"
Banner ""

# ════════════════════════════════════════════════════════════════════════════
# STEP 0: Check Service Account Key
# ════════════════════════════════════════════════════════════════════════════
STEP "Checking service account key..."

if (-not (Test-Path $KEY_FILE)) {
    ERR "service-account-key.json NOT FOUND"
    Banner ""
    Banner "======================================================"
    Banner "  ONE-TIME SETUP: Get your Service Account Key"
    Banner "======================================================"
    Banner ""
    Banner "  Follow these exact steps (takes ~5 minutes):"
    Banner ""
    Banner "  STEP A: Open Google Play Console"
    Banner "    https://play.google.com/console"
    Banner ""
    Banner "  STEP B: Setup -> API access"
    Banner "    Left menu -> Setup -> API access"
    Banner ""
    Banner "  STEP C: Link Google Cloud Project"
    Banner "    Click: 'Link to an existing Google Cloud project'"
    Banner "    OR click: 'Create a new Google Cloud project'"
    Banner ""
    Banner "  STEP D: Create Service Account"
    Banner "    Click: 'Create new service account'"
    Banner "    -> Opens Google Cloud Console"
    Banner "    -> Click 'CREATE SERVICE ACCOUNT'"
    Banner "    -> Name: 'schoolapp-play-deploy'"
    Banner "    -> Click 'CREATE AND CONTINUE'"
    Banner "    -> Role: 'Service Account User' -> DONE"
    Banner ""
    Banner "  STEP E: Grant Play Console Access"
    Banner "    Back in Play Console -> API access"
    Banner "    Find the service account -> click 'Grant access'"
    Banner "    Permissions: Check 'Release manager'"
    Banner "    Click 'Invite user'"
    Banner ""
    Banner "  STEP F: Download JSON Key"
    Banner "    Google Cloud Console -> IAM -> Service Accounts"
    Banner "    Find 'schoolapp-play-deploy' -> Keys -> ADD KEY"
    Banner "    -> Create new key -> JSON -> CREATE"
    Banner "    File downloads automatically"
    Banner ""
    Banner "  STEP G: Place the key file here:"
    Banner "    $KEY_FILE"
    Banner ""
    Banner "  Then run this script again: .\deploy-to-play.ps1"
    Banner ""
    Start-Process "https://play.google.com/console"
    exit 0
}

$keyJson = Get-Content $KEY_FILE -Raw | ConvertFrom-Json
OK "Service account: $($keyJson.client_email)"

# ════════════════════════════════════════════════════════════════════════════
# STEP 1: Check AAB exists and is signed
# ════════════════════════════════════════════════════════════════════════════
STEP "Checking AAB..."

if (-not (Test-Path $AAB_PATH)) {
    WARN "AAB not found. Building now..."
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
    & .\gradlew.bat bundleRelease 2>&1 | Select-Object -Last 5
    if (-not (Test-Path $AAB_PATH)) {
        ERR "AAB build failed. Run: .\gradlew.bat bundleRelease"
        exit 1
    }
}

$aabItem = Get-Item $AAB_PATH
$aabMB   = [math]::Round($aabItem.Length / 1MB, 1)
OK "AAB: $($aabItem.Name) ($aabMB MB)"
OK "Path: $AAB_PATH"

# Verify signed
$jsv = & "$env:JAVA_HOME\bin\jarsigner.exe" -verify $AAB_PATH 2>&1 | Out-String
if ($jsv -match "unsigned") {
    ERR "AAB is UNSIGNED. Run: .\build-and-test.ps1"
    exit 1
}
OK "AAB is signed"

# ════════════════════════════════════════════════════════════════════════════
# STEP 2: Authenticate with Google Play API (JWT -> OAuth2 token)
# ════════════════════════════════════════════════════════════════════════════
STEP "Authenticating with Google Play API..."

function New-GoogleJwt {
    param($email, $privateKeyPem, $scope)

    # Header
    $header  = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('{"alg":"RS256","typ":"JWT"}')) -replace '\+','-' -replace '/','_' -replace '=',''
    $iat = [int][double]::Parse((Get-Date -UFormat %s))
    $exp = $iat + 3600
    $payload = @{ iss=$email; scope=$scope; aud=$TOKEN_URL; exp=$exp; iat=$iat } | ConvertTo-Json -Compress
    $payloadB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload)) -replace '\+','-' -replace '/','_' -replace '=',''
    $signingInput = "$header.$payloadB64"

    # Load RSA private key
    $pem = $privateKeyPem -replace "-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s",""
    $keyBytes = [Convert]::FromBase64String($pem)
    $rsa = [System.Security.Cryptography.RSA]::Create()
    $rsa.ImportPkcs8PrivateKey($keyBytes, [ref]$null)

    # Sign
    $dataBytes = [Text.Encoding]::UTF8.GetBytes($signingInput)
    $sig = $rsa.SignData($dataBytes, [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $sigB64 = [Convert]::ToBase64String($sig) -replace '\+','-' -replace '/','_' -replace '=',''

    return "$signingInput.$sigB64"
}

$jwt = New-GoogleJwt -email $keyJson.client_email -privateKeyPem $keyJson.private_key -scope $PLAY_SCOPE

$tokenResp = Invoke-RestMethod -Uri $TOKEN_URL -Method Post -ContentType "application/x-www-form-urlencoded" -Body @{
    grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    assertion  = $jwt
}
$accessToken = $tokenResp.access_token
$authHeader  = @{ Authorization = "Bearer $accessToken" }
OK "Authenticated successfully"

# ════════════════════════════════════════════════════════════════════════════
# STEP 3: Discover current app state
# ════════════════════════════════════════════════════════════════════════════
STEP "Discovering current app state on Google Play..."

# Get current tracks
try {
    $tracksResp = Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/tracks" -Headers $authHeader
    Banner ""
    Banner "  Current Release State:"
    foreach ($t in $tracksResp.tracks) {
        $rel = $t.releases | Select-Object -First 1
        if ($rel) {
            $codes = ($rel.versionCodes -join ", ")
            Banner ("  [{0,-12}] v{1} (codes: {2}) - {3}" -f $t.track, $rel.name, $codes, $rel.status)
        }
    }
    Banner ""
} catch {
    WARN "Could not fetch tracks (app may be new on Play): $($_.Exception.Message.Split("`n")[0])"
}

# Get APKs/bundles info
try {
    $bundlesResp = Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/bundles" -Headers $authHeader
    if ($bundlesResp.bundles) {
        $latest = $bundlesResp.bundles | Sort-Object versionCode -Descending | Select-Object -First 1
        OK "Latest uploaded bundle: versionCode $($latest.versionCode)"
    }
} catch {
    INFO "No bundles found yet (first upload)"
}

if ($DryRun) {
    Banner ""
    Banner "  [DRY RUN] Would upload:"
    Banner "  - File: $($aabItem.Name) ($aabMB MB)"
    Banner "  - Track: $Track"
    Banner "  - versionCode: (from AAB)"
    Banner ""
    OK "Dry run complete. Run without -DryRun to actually upload."
    exit 0
}

# ════════════════════════════════════════════════════════════════════════════
# STEP 4: Create edit session
# ════════════════════════════════════════════════════════════════════════════
STEP "Creating Play edit session..."

$editResp = Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/edits" `
    -Method Post -Headers $authHeader -ContentType "application/json" -Body "{}"
$editId = $editResp.id
OK "Edit session created: $editId (expires: $($editResp.expiryTimeSeconds)s)"

# ════════════════════════════════════════════════════════════════════════════
# STEP 5: Upload AAB
# ════════════════════════════════════════════════════════════════════════════
STEP "Uploading AAB ($aabMB MB) to Google Play..."
INFO "This may take 1-3 minutes..."

$uploadUrl  = "$UPLOAD_BASE/applications/$PACKAGE_NAME/edits/$editId/bundles"
$aabBytes   = [System.IO.File]::ReadAllBytes($AAB_PATH)
$uploadHeaders = @{
    Authorization   = "Bearer $accessToken"
    "Content-Type"  = "application/octet-stream"
    "X-Goog-Upload-Protocol" = "raw"
}

$uploadResp = Invoke-RestMethod -Uri $uploadUrl `
    -Method Post -Headers $uploadHeaders -Body $aabBytes -TimeoutSec 300

$uploadedCode = $uploadResp.versionCode
OK "AAB uploaded successfully"
OK "versionCode: $uploadedCode"

# ════════════════════════════════════════════════════════════════════════════
# STEP 6: Assign to track
# ════════════════════════════════════════════════════════════════════════════
STEP "Assigning to track: $Track..."

# Read versionName from build.gradle.kts
$bk = Get-Content "$PROJ\app\build.gradle.kts" -Raw
$versionName = "2.2"
if ($bk -match 'versionName\s*=\s*"([\d\.]+)"') { $versionName = $Matches[1] }

$releaseNotes = @(
    @{
        language = "ar"
        text     = "v$versionName - تحديث يشمل: دعم Edge-to-Edge لاندرويد 15، حفظ الجلسة عند التحديث، تحسين زر الرجوع، دعم العمل دون اتصال، وإصلاحات عامة."
    },
    @{
        language = "en-US"
        text     = "v$versionName - Edge-to-Edge support for Android 15, session persistence on reload, improved back button, offline support, and bug fixes."
    }
)

$trackBody = @{
    releases = @(
        @{
            name         = "v$versionName"
            versionCodes = @($uploadedCode)
            status       = if ($Track -eq "production") { "completed" } else { "completed" }
            releaseNotes = $releaseNotes
        }
    )
} | ConvertTo-Json -Depth 5

$trackUrl  = "$API_BASE/applications/$PACKAGE_NAME/edits/$editId/tracks/$Track"
$trackResp = Invoke-RestMethod -Uri $trackUrl -Method Put -Headers $authHeader -ContentType "application/json" -Body $trackBody
OK "Assigned to track: $Track"

# ════════════════════════════════════════════════════════════════════════════
# STEP 7: Commit the edit (makes it live/visible)
# ════════════════════════════════════════════════════════════════════════════
STEP "Committing edit (finalizing upload)..."

$commitResp = Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/edits/$editId:commit" `
    -Method Post -Headers $authHeader
OK "Edit committed successfully"

# ════════════════════════════════════════════════════════════════════════════
# STEP 8: Verify upload
# ════════════════════════════════════════════════════════════════════════════
STEP "Verifying upload..."
Start-Sleep -Seconds 3

try {
    $newJwt   = New-GoogleJwt -email $keyJson.client_email -privateKeyPem $keyJson.private_key -scope $PLAY_SCOPE
    $newToken = (Invoke-RestMethod -Uri $TOKEN_URL -Method Post -ContentType "application/x-www-form-urlencoded" -Body @{
        grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        assertion  = $newJwt
    }).access_token
    $newAuth  = @{ Authorization = "Bearer $newToken" }

    $verifyTrack = Invoke-RestMethod -Uri "$API_BASE/applications/$PACKAGE_NAME/tracks/$Track" -Headers $newAuth
    $latestRel   = $verifyTrack.releases | Select-Object -First 1
    if ($latestRel.versionCodes -contains $uploadedCode) {
        OK "Verified on Play: versionCode $uploadedCode is live on [$Track]"
    }
} catch {
    WARN "Verification skipped: $($_.Exception.Message.Split("`n")[0])"
}

# Open Play Console
Start-Process "https://play.google.com/console/u/0/developers"

# ════════════════════════════════════════════════════════════════════════════
# FINAL SUMMARY
# ════════════════════════════════════════════════════════════════════════════
Banner ""
Banner "======================================================"
Banner "   DEPLOY COMPLETE"
Banner "======================================================"
Banner "   App     : $PACKAGE_NAME"
Banner "   Version : $versionName (code $uploadedCode)"
Banner "   Track   : $Track"
Banner "   Status  : Published"
Banner ""

if ($Track -eq "internal") {
    Banner "  NEXT: Test internally, then promote to production:"
    Banner "  Play Console -> Testing -> Internal testing -> Promote release"
    Banner ""
    Banner "  OR run: .\deploy-to-play.ps1 -Track production"
}
Banner "======================================================"
Banner ""
